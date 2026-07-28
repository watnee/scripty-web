#!/bin/bash
# Boot the prod profile against a real MySQL, so Hibernate's schema validation
# runs before a deploy does it for us.
#
# Why this exists: only application-prod.yml sets `ddl-auto: validate`.
# application-dev.yml and src/test/resources/application-test.yml both run H2
# with `ddl-auto: none`, so neither a local run nor `mvn verify` ever compares
# the Flyway schema against the entity mappings. Drift (V52 created
# api_token.token_hash as CHAR(64) where the mapping expects VARCHAR(64)) first
# surfaced on Railway, where the app failed to start, the healthcheck never
# passed, and the deploy was rolled back.
#
# H2 is not a usable stand-in: booting dev with `validate` fails on unrelated
# case-sensitivity drift ("missing table [actor]"), and Spring runs
# src/main/resources/schema.sql on embedded databases only — so the H2 schema is
# not even the Flyway schema. This runs the same MySQL major version Railway
# runs (mysql:9.4) over an empty database, applies every migration, and boots.
#
# Usage: scripts/schema-check.sh
#
# Environment (all optional, defaults target a local `docker run mysql:9.4`):
#   MYSQLHOST/MYSQLPORT/MYSQLUSER/MYSQLPASSWORD/MYSQLDATABASE  connection
#   PORT             port to boot the app on (default 8079)
#   STARTUP_TIMEOUT  seconds to wait for /health (default 180)
#   SKIP_BUILD       set to 1 to reuse an existing target/scripty.jar
#
# Locally:
#   docker run --rm -d --name scripty-schema-check -p 3306:3306 \
#     -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=scripty mysql:9.4
#   scripts/schema-check.sh
#   docker rm -f scripty-schema-check
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${REPO_ROOT}"

PORT="${PORT:-8079}"
STARTUP_TIMEOUT="${STARTUP_TIMEOUT:-180}"
JAR="${REPO_ROOT}/target/scripty.jar"
LOG_FILE="${REPO_ROOT}/schema-check.log"

export MYSQLHOST="${MYSQLHOST:-127.0.0.1}"
export MYSQLPORT="${MYSQLPORT:-3306}"
export MYSQLUSER="${MYSQLUSER:-root}"
export MYSQLPASSWORD="${MYSQLPASSWORD:-root}"
export MYSQLDATABASE="${MYSQLDATABASE:-scripty}"
# A CI/dev MySQL has no TLS and no external identity to protect.
export MYSQL_SSL_MODE="${MYSQL_SSL_MODE:-DISABLED}"
export MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL="${MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL:-true}"

# Prod defaults that would reach for things this check has no business needing.
export PORT
export LOG_FORMAT=""          # plain text, so a validation failure is readable
export MAIL_ENABLED="false"
export TRACING_ENABLED="false"
# ADMIN_USERNAME/ADMIN_PASSWORD stay unset on purpose: the startup guard then
# seeds an admin with a generated password it logs and throws away with the
# database. Nothing here ever signs in — /health is permitAll.

APP_PID=""

cleanup() {
  if [[ -n "${APP_PID}" ]] && kill -0 "${APP_PID}" 2>/dev/null; then
    kill "${APP_PID}" 2>/dev/null || true
    wait "${APP_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

fail() {
  echo "" >&2
  echo "ERROR: $*" >&2
  echo "" >&2
  echo "--- last 120 lines of ${LOG_FILE} ---" >&2
  tail -n 120 "${LOG_FILE}" >&2 2>/dev/null || true
  echo "--- end log ---" >&2
  # Hibernate reports every mismatch it found; surface them on their own so the
  # actual drift is not buried in the stack trace above.
  if grep -qE 'Schema-validation|SchemaManagementException' "${LOG_FILE}" 2>/dev/null; then
    echo "" >&2
    echo "Schema validation findings:" >&2
    grep -E 'Schema-validation|SchemaManagementException' "${LOG_FILE}" >&2 || true
    echo "" >&2
    echo "Fix by adding a Flyway migration under src/main/resources/db/migration" >&2
    echo "that brings the column/table into line with the entity mapping." >&2
  fi
  exit 1
}

if [[ "${SKIP_BUILD:-0}" != "1" || ! -f "${JAR}" ]]; then
  echo "Building ${JAR} (mvn -B -DskipTests package)…"
  mvn -B -DskipTests package
fi
[[ -f "${JAR}" ]] || fail "no jar at ${JAR}"

echo "Booting prod profile against mysql://${MYSQLHOST}:${MYSQLPORT}/${MYSQLDATABASE} on port ${PORT}…"
echo "  Flyway applies every migration to the empty database, then Hibernate"
echo "  validates the result against the entity mappings (ddl-auto: validate)."

java -jar "${JAR}" --spring.profiles.active=prod > "${LOG_FILE}" 2>&1 &
APP_PID=$!

deadline=$(( $(date +%s) + STARTUP_TIMEOUT ))
while [[ "$(date +%s)" -lt "${deadline}" ]]; do
  if ! kill -0 "${APP_PID}" 2>/dev/null; then
    fail "the app exited before becoming healthy — schema validation or startup failed."
  fi
  if body=$(curl -sf --max-time 5 "http://127.0.0.1:${PORT}/health" 2>/dev/null); then
    echo ""
    echo "Healthy: ${body}"
    echo "Schema check passed: every migration applied and validate found no drift."
    exit 0
  fi
  sleep 3
done

fail "the app did not answer /health within ${STARTUP_TIMEOUT}s."
