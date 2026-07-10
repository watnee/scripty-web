#!/usr/bin/env bash
# Dump Railway MySQL and upload a gzipped SQL file to Cloudflare R2 via wrangler.
#
# Required env vars (GitHub Actions already has the MYSQL* / CLOUDFLARE_* deploy secrets):
#   MYSQLHOST, MYSQLPORT, MYSQLUSER, MYSQLPASSWORD, MYSQLDATABASE
#   CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID
#   R2_BUCKET (e.g. scripty-db-backups)
#
# Locally you can also use wrangler OAuth (omit CLOUDFLARE_API_TOKEN) after `npx wrangler login`.
set -euo pipefail

require_var() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "error: missing required env var: ${name}" >&2
    exit 1
  fi
}

# Prefer dedicated backup secrets when set; fall back to deploy secrets.
MYSQL_BACKUP_HOST="${MYSQL_BACKUP_HOST:-${MYSQLHOST:-}}"
MYSQL_BACKUP_PORT="${MYSQL_BACKUP_PORT:-${MYSQLPORT:-}}"
MYSQL_BACKUP_USER="${MYSQL_BACKUP_USER:-${MYSQLUSER:-}}"
MYSQL_BACKUP_PASSWORD="${MYSQL_BACKUP_PASSWORD:-${MYSQLPASSWORD:-}}"
MYSQL_BACKUP_DATABASE="${MYSQL_BACKUP_DATABASE:-${MYSQLDATABASE:-}}"

require_var MYSQL_BACKUP_HOST
require_var MYSQL_BACKUP_PORT
require_var MYSQL_BACKUP_USER
require_var MYSQL_BACKUP_PASSWORD
require_var MYSQL_BACKUP_DATABASE
require_var R2_BUCKET

if [[ -z "${CLOUDFLARE_API_TOKEN:-}" ]] && ! npx --yes wrangler whoami >/dev/null 2>&1; then
  echo "error: set CLOUDFLARE_API_TOKEN or run: npx wrangler login" >&2
  exit 1
fi

for cmd in mysqldump gzip; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "error: required command not found: ${cmd}" >&2
    exit 1
  fi
done

STAMP="$(date -u +%Y%m%d-%H%M%S)"
FILENAME="scripty-${STAMP}.sql.gz"
WORKDIR="${TMPDIR:-/tmp}/scripty-db-backup-$$"
mkdir -p "$WORKDIR"
trap 'rm -rf "$WORKDIR"' EXIT

DUMP_PATH="${WORKDIR}/${FILENAME}"
OBJECT_KEY="${FILENAME}"

echo "Dumping ${MYSQL_BACKUP_DATABASE} from ${MYSQL_BACKUP_HOST}:${MYSQL_BACKUP_PORT}..."
mysqldump \
  --host="${MYSQL_BACKUP_HOST}" \
  --port="${MYSQL_BACKUP_PORT}" \
  --user="${MYSQL_BACKUP_USER}" \
  --password="${MYSQL_BACKUP_PASSWORD}" \
  --single-transaction \
  --routines \
  --triggers \
  --hex-blob \
  --set-gtid-purged=OFF \
  "${MYSQL_BACKUP_DATABASE}" \
  | gzip -c > "${DUMP_PATH}"

SIZE="$(wc -c < "${DUMP_PATH}" | tr -d ' ')"
echo "Created ${FILENAME} (${SIZE} bytes). Uploading to r2://${R2_BUCKET}/${OBJECT_KEY}..."

npx --yes wrangler r2 object put "${R2_BUCKET}/${OBJECT_KEY}" \
  --file "${DUMP_PATH}" \
  --remote

echo "Upload complete: r2://${R2_BUCKET}/${OBJECT_KEY}"
echo "Prefer an R2 lifecycle rule to expire objects after 30 days."
