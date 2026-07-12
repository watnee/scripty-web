#!/usr/bin/env bash
# Download a gzipped MySQL dump from Cloudflare R2 via wrangler and restore it.
#
# Usage:
#   ./scripts/restore-mysql.sh [backup_filename]
#
# Environment variables:
#   R2_BUCKET (e.g. scripty-db-backups)
#   MYSQLHOST, MYSQLPORT, MYSQLUSER, MYSQLPASSWORD, MYSQLDATABASE
#
# Optional:
#   MYSQL_BACKUP_* - dedicated backup/restore coordinates (preferred over MYSQL*)
#   CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID - required ONLY if resolving "latest"
#   FORCE=1 - bypass confirmation prompts (e.g. in CI)
#
set -euo pipefail

require_var() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "error: missing required env var: ${name}" >&2
    exit 1
  fi
}

resolve_mysql_coordinates() {
  # 1. Try to read from local cloudflare/.dev.vars first if variables are not set
  if [[ -f "cloudflare/.dev.vars" ]]; then
    echo "Reading database coordinates from cloudflare/.dev.vars..." >&2
    local dev_vars
    dev_vars=$(cat cloudflare/.dev.vars)
    # Extract values only if they are not already set in env
    MYSQLHOST="${MYSQLHOST:-$(echo "$dev_vars" | grep "^MYSQLHOST=" | cut -d'=' -f2-)}"
    MYSQLPORT="${MYSQLPORT:-$(echo "$dev_vars" | grep "^MYSQLPORT=" | cut -d'=' -f2-)}"
    MYSQLUSER="${MYSQLUSER:-$(echo "$dev_vars" | grep "^MYSQLUSER=" | cut -d'=' -f2-)}"
    MYSQLPASSWORD="${MYSQLPASSWORD:-$(echo "$dev_vars" | grep "^MYSQLPASSWORD=" | cut -d'=' -f2-)}"
    MYSQLDATABASE="${MYSQLDATABASE:-$(echo "$dev_vars" | grep "^MYSQLDATABASE=" | cut -d'=' -f2-)}"
  fi

  # 2. If variables are still missing, try to fetch from Railway CLI
  if [[ -z "${MYSQLHOST:-}" || -z "${MYSQLPASSWORD:-}" ]]; then
    if command -v railway >/dev/null 2>&1; then
      local variables
      if variables=$(railway variable list --service MySQL --json 2>/dev/null); then
        echo "Auto-detected MySQL coordinates from Railway CLI." >&2
        local r_host r_port r_user r_pass r_db
        r_host=$(echo "$variables" | python3 -c "import sys, json; print(json.load(sys.stdin).get('RAILWAY_TCP_PROXY_DOMAIN', ''))")
        r_port=$(echo "$variables" | python3 -c "import sys, json; print(json.load(sys.stdin).get('RAILWAY_TCP_PROXY_PORT', ''))")
        r_user=$(echo "$variables" | python3 -c "import sys, json; print(json.load(sys.stdin).get('MYSQLUSER', ''))")
        r_pass=$(echo "$variables" | python3 -c "import sys, json; print(json.load(sys.stdin).get('MYSQLPASSWORD', ''))")
        r_db=$(echo "$variables" | python3 -c "import sys, json; print(json.load(sys.stdin).get('MYSQLDATABASE', ''))")

        # If TCP proxy is not enabled or not returned, fall back to standard host/port
        if [[ -z "$r_host" ]]; then
          r_host=$(echo "$variables" | python3 -c "import sys, json; print(json.load(sys.stdin).get('MYSQLHOST', ''))")
          r_port=$(echo "$variables" | python3 -c "import sys, json; print(json.load(sys.stdin).get('MYSQLPORT', ''))")
        fi

        MYSQLHOST="${MYSQLHOST:-$r_host}"
        MYSQLPORT="${MYSQLPORT:-$r_port}"
        MYSQLUSER="${MYSQLUSER:-$r_user}"
        MYSQLPASSWORD="${MYSQLPASSWORD:-$r_pass}"
        MYSQLDATABASE="${MYSQLDATABASE:-$r_db}"
      fi
    fi
  fi
}

resolve_mysql_coordinates

# Fall back to deploy credentials if dedicated backup coordinates are not set
MYSQL_BACKUP_HOST="${MYSQL_BACKUP_HOST:-${MYSQLHOST:-}}"
MYSQL_BACKUP_PORT="${MYSQL_BACKUP_PORT:-${MYSQLPORT:-}}"
MYSQL_BACKUP_USER="${MYSQL_BACKUP_USER:-${MYSQLUSER:-}}"
MYSQL_BACKUP_PASSWORD="${MYSQL_BACKUP_PASSWORD:-${MYSQLPASSWORD:-}}"
MYSQL_BACKUP_DATABASE="${MYSQL_BACKUP_DATABASE:-${MYSQLDATABASE:-}}"
R2_BUCKET="${R2_BUCKET:-scripty-db-backups}"

require_var MYSQL_BACKUP_HOST
require_var MYSQL_BACKUP_PORT
require_var MYSQL_BACKUP_USER
require_var MYSQL_BACKUP_PASSWORD
require_var MYSQL_BACKUP_DATABASE
require_var R2_BUCKET

BACKUP_FILENAME="${1:-latest}"

for cmd in mysql gzip gunzip python3; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "error: required command not found: ${cmd}" >&2
    exit 1
  fi
done

if [[ -z "${CLOUDFLARE_API_TOKEN:-}" ]] && ! npx --yes wrangler whoami >/dev/null 2>&1; then
  echo "error: set CLOUDFLARE_API_TOKEN or run: npx wrangler login" >&2
  exit 1
fi

# Auto-resolve CLOUDFLARE_ACCOUNT_ID if missing but CLOUDFLARE_API_TOKEN is present
if [[ -n "${CLOUDFLARE_API_TOKEN:-}" && -z "${CLOUDFLARE_ACCOUNT_ID:-}" ]]; then
  echo "CLOUDFLARE_ACCOUNT_ID not set. Attempting to auto-resolve from Cloudflare API..." >&2
  RESP_ACCTS=$(curl -sS -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" "https://api.cloudflare.com/client/v4/accounts?per_page=50" || true)
  if [[ -n "${RESP_ACCTS}" ]]; then
    DETECTED_ACCOUNT_ID=$(python3 - "${RESP_ACCTS}" <<'PY'
import json, sys
try:
    data = json.loads(sys.argv[1])
    accounts = data.get("result") or []
    if len(accounts) == 1:
        print(accounts[0]["id"])
    elif len(accounts) > 1:
        print(accounts[0]["id"])
except Exception:
    pass
PY
)
    if [[ -n "${DETECTED_ACCOUNT_ID}" ]]; then
      export CLOUDFLARE_ACCOUNT_ID="${DETECTED_ACCOUNT_ID}"
      echo "Auto-resolved CLOUDFLARE_ACCOUNT_ID to: ${CLOUDFLARE_ACCOUNT_ID}" >&2
    fi
  fi
fi

OBJECT_KEY="${BACKUP_FILENAME}"
if [[ "${OBJECT_KEY}" == "latest" || -z "${OBJECT_KEY}" ]]; then
  if [[ -z "${CLOUDFLARE_API_TOKEN:-}" || -z "${CLOUDFLARE_ACCOUNT_ID:-}" ]]; then
    echo "error: CLOUDFLARE_API_TOKEN and CLOUDFLARE_ACCOUNT_ID are required to auto-detect the 'latest' backup." >&2
    echo "Alternatively, specify the backup filename as the first argument: ./scripts/restore-mysql.sh scripty-YYYYMMDD-HHMMSS.sql.gz" >&2
    exit 1
  fi

  echo "Resolving latest backup from R2 bucket '${R2_BUCKET}'..."
  API_URL="https://api.cloudflare.com/client/v4/accounts/${CLOUDFLARE_ACCOUNT_ID}/r2/buckets/${R2_BUCKET}/objects"
  
  RESP=$(curl -sSf -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" "${API_URL}" || {
    echo "error: failed to fetch bucket objects from Cloudflare API" >&2
    exit 1
  })

  OBJECT_KEY=$(python3 - "${RESP}" <<'PY'
import json, sys
try:
    data = json.loads(sys.argv[1])
except Exception:
    sys.exit("Cloudflare API returned non-JSON response")

if not data.get("success"):
    errs = data.get("errors") or [{"message": "unknown error"}]
    msgs = "; ".join(f"{e.get('code', '?')}: {e.get('message', '?')}" for e in errs)
    sys.exit(f"Cloudflare API error: {msgs}")

# The v4 R2 listing returns "result" as a list of objects; older payloads
# nested them under result.objects. Accept both.
result = data.get("result") or []
objects = result if isinstance(result, list) else result.get("objects", [])
backups = [obj for obj in objects if obj.get("key", "").startswith("scripty-") and obj.get("key", "").endswith(".sql.gz")]
if not backups:
    sys.exit("no backups matching scripty-*.sql.gz found in bucket")

# Sort descending to get chronologically latest
backups.sort(key=lambda x: x["key"], reverse=True)
print(backups[0]["key"])
PY
  )
fi

WORKDIR="${TMPDIR:-/tmp}/scripty-db-restore-$$"
mkdir -p "$WORKDIR"
trap 'rm -rf "$WORKDIR"' EXIT

DOWNLOAD_PATH="${WORKDIR}/${OBJECT_KEY}"

echo "Downloading backup file 'r2://${R2_BUCKET}/${OBJECT_KEY}'..."
npx --yes wrangler r2 object get "${R2_BUCKET}/${OBJECT_KEY}" \
  --file "${DOWNLOAD_PATH}" \
  --remote

echo "Verifying downloaded backup file size and integrity..."
if [[ ! -f "${DOWNLOAD_PATH}" ]]; then
  echo "error: downloaded backup file not found at ${DOWNLOAD_PATH}" >&2
  exit 1
fi

SIZE="$(wc -c < "${DOWNLOAD_PATH}" | tr -d ' ')"
echo "Downloaded ${SIZE} bytes."

echo "Verifying gzip integrity..."
gunzip -t "${DOWNLOAD_PATH}"

echo "Sanity checking SQL contents..."
SAMPLE="$(gunzip -c "${DOWNLOAD_PATH}" | head -c 65536 || true)"
if ! grep -Eq 'CREATE TABLE|INSERT INTO|Database:' <<<"${SAMPLE}"; then
  echo "error: backup does not look like a valid MySQL SQL dump" >&2
  exit 1
fi

FORCE="${FORCE:-0}"
if [[ "${FORCE}" -ne 1 ]]; then
  echo "========================================================================"
  echo "WARNING: DESTRUCTIVE OPERATION"
  echo "This will OVERWRITE the database '${MYSQL_BACKUP_DATABASE}' on host '${MYSQL_BACKUP_HOST}:${MYSQL_BACKUP_PORT}'!"
  echo "========================================================================"
  read -rp "Are you sure you want to restore '${OBJECT_KEY}'? Type 'yes' to proceed: " CONFIRM
  if [[ "${CONFIRM}" != "yes" ]]; then
    echo "Restore aborted by user."
    exit 0
  fi
fi

echo "Restoring database from '${OBJECT_KEY}'..."
CNF_PATH="${WORKDIR}/restore.cnf"
umask 077
cat > "${CNF_PATH}" <<EOF
[client]
host=${MYSQL_BACKUP_HOST}
port=${MYSQL_BACKUP_PORT}
user=${MYSQL_BACKUP_USER}
password=${MYSQL_BACKUP_PASSWORD}
EOF

gunzip -c "${DOWNLOAD_PATH}" | mysql \
  --defaults-extra-file="${CNF_PATH}" \
  --default-character-set=utf8mb4 \
  "${MYSQL_BACKUP_DATABASE}"

# Remove the cnf configuration immediately
rm -f "${CNF_PATH}"

# Verification queries
echo "Verifying database tables and row counts after restore..."
CNF_PATH_VERIFY="${WORKDIR}/verify.cnf"
cat > "${CNF_PATH_VERIFY}" <<EOF
[client]
host=${MYSQL_BACKUP_HOST}
port=${MYSQL_BACKUP_PORT}
user=${MYSQL_BACKUP_USER}
password=${MYSQL_BACKUP_PASSWORD}
EOF

TABLES=$(mysql --defaults-extra-file="${CNF_PATH_VERIFY}" -N -e "SHOW TABLES;" "${MYSQL_BACKUP_DATABASE}" 2>/dev/null || true)

if [[ -z "${TABLES}" ]]; then
  echo "error: verification failed! No tables found in database '${MYSQL_BACKUP_DATABASE}'." >&2
  rm -f "${CNF_PATH_VERIFY}"
  exit 1
fi

TABLE_COUNT=$(echo "${TABLES}" | wc -l | tr -d ' ')
echo "Database successfully restored. Total tables: ${TABLE_COUNT}"

# Print row counts of key application tables
echo "Row counts for key application tables:"
for tbl in user project scene actor person block; do
  if grep -q -w "${tbl}" <<<"${TABLES}"; then
    ROW_COUNT=$(mysql --defaults-extra-file="${CNF_PATH_VERIFY}" -N -e "SELECT COUNT(*) FROM \`${tbl}\`;" "${MYSQL_BACKUP_DATABASE}" 2>/dev/null || echo "0")
    echo "  - Table '${tbl}': ${ROW_COUNT} rows"
  else
    echo "  - Table '${tbl}': NOT found in database"
  fi
done

rm -f "${CNF_PATH_VERIFY}"
echo "Restore complete!"
