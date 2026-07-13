#!/usr/bin/env bash
# Dump Railway MySQL and upload a gzipped SQL file to Cloudflare R2 via wrangler.
#
# Required env vars (GitHub Actions already has the MYSQL* / CLOUDFLARE_* deploy secrets):
#   MYSQLHOST, MYSQLPORT, MYSQLUSER, MYSQLPASSWORD, MYSQLDATABASE
#   CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID
#   R2_BUCKET (e.g. scripty-db-backups)
#
# Optional:
#   MYSQL_BACKUP_* — dedicated backup credentials (preferred over MYSQL*)
#   BACKUP_MIN_BYTES — fail if gzipped dump is smaller than this (default: 1024)
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

# Prefer dedicated backup secrets when set; fall back to deploy secrets.
MYSQL_BACKUP_HOST="${MYSQL_BACKUP_HOST:-${MYSQLHOST:-}}"
MYSQL_BACKUP_PORT="${MYSQL_BACKUP_PORT:-${MYSQLPORT:-}}"
MYSQL_BACKUP_USER="${MYSQL_BACKUP_USER:-${MYSQLUSER:-}}"
MYSQL_BACKUP_PASSWORD="${MYSQL_BACKUP_PASSWORD:-${MYSQLPASSWORD:-}}"
MYSQL_BACKUP_DATABASE="${MYSQL_BACKUP_DATABASE:-${MYSQLDATABASE:-}}"
BACKUP_MIN_BYTES="${BACKUP_MIN_BYTES:-1024}"
R2_BUCKET="${R2_BUCKET:-scripty-db-backups}"

require_var MYSQL_BACKUP_HOST
require_var MYSQL_BACKUP_PORT
require_var MYSQL_BACKUP_USER
require_var MYSQL_BACKUP_PASSWORD
require_var MYSQL_BACKUP_DATABASE
require_var R2_BUCKET

# Fail fast on placeholder/garbage coordinates (e.g. secrets accidentally set to "-").
if ! [[ "${MYSQL_BACKUP_PORT}" =~ ^[0-9]+$ ]]; then
  echo "error: MYSQL_BACKUP_PORT is not a number — the MYSQLPORT/MYSQL_BACKUP_PORT secret looks corrupted" >&2
  exit 1
fi
if [[ "${MYSQL_BACKUP_HOST}" != *.* ]]; then
  echo "error: MYSQL_BACKUP_HOST does not look like a hostname — the MYSQLHOST/MYSQL_BACKUP_HOST secret looks corrupted" >&2
  exit 1
fi

if [[ -z "${CLOUDFLARE_API_TOKEN:-}" ]] && ! npx --yes wrangler whoami >/dev/null 2>&1; then
  echo "error: set CLOUDFLARE_API_TOKEN or run: npx wrangler login" >&2
  exit 1
fi

for cmd in mysqldump gzip gunzip openssl; do
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
CNF_PATH="${WORKDIR}/my.cnf"
OBJECT_KEY="${FILENAME}"

# Keep the password out of process argv / shell history.
umask 077
cat > "${CNF_PATH}" <<EOF
[client]
host=${MYSQL_BACKUP_HOST}
port=${MYSQL_BACKUP_PORT}
user=${MYSQL_BACKUP_USER}
password=${MYSQL_BACKUP_PASSWORD}
EOF

echo "Dumping ${MYSQL_BACKUP_DATABASE} from ${MYSQL_BACKUP_HOST}:${MYSQL_BACKUP_PORT}..."
mysqldump \
  --defaults-extra-file="${CNF_PATH}" \
  --single-transaction \
  --routines \
  --triggers \
  --events \
  --hex-blob \
  --set-gtid-purged=OFF \
  --column-statistics=0 \
  --default-character-set=utf8mb4 \
  "${MYSQL_BACKUP_DATABASE}" \
  | gzip -9 -c > "${DUMP_PATH}"

# Drop credentials as soon as the dump finishes.
rm -f "${CNF_PATH}"

SIZE="$(wc -c < "${DUMP_PATH}" | tr -d ' ')"
if [[ "${SIZE}" -lt "${BACKUP_MIN_BYTES}" ]]; then
  echo "error: dump is only ${SIZE} bytes (min ${BACKUP_MIN_BYTES}); refusing to upload" >&2
  exit 1
fi

echo "Verifying gzip integrity..."
gunzip -t "${DUMP_PATH}"

# Quick content sanity: decompressed dump should look like SQL.
# (head closes early → SIGPIPE; ignore that under pipefail.)
SAMPLE="$(gunzip -c "${DUMP_PATH}" | head -c 65536 || true)"
if ! grep -Eq 'CREATE TABLE|INSERT INTO|Database:' <<<"${SAMPLE}"; then
  echo "error: dump does not look like a MySQL SQL dump" >&2
  exit 1
fi

SHA256="$(openssl dgst -sha256 "${DUMP_PATH}" | awk '{print $NF}')"
echo "Created ${FILENAME} (${SIZE} bytes, sha256=${SHA256}). Uploading to r2://${R2_BUCKET}/${OBJECT_KEY}..."

npx --yes wrangler r2 object put "${R2_BUCKET}/${OBJECT_KEY}" \
  --file "${DUMP_PATH}" \
  --remote

# Emit machine-readable metadata for CI summaries.
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "filename=${FILENAME}"
    echo "size_bytes=${SIZE}"
    echo "sha256=${SHA256}"
    echo "object_key=${OBJECT_KEY}"
    echo "bucket=${R2_BUCKET}"
  } >> "${GITHUB_OUTPUT}"
fi

echo "Upload complete: r2://${R2_BUCKET}/${OBJECT_KEY}"
echo "sha256: ${SHA256}"
echo "Prefer an R2 lifecycle rule to expire objects after 30 days."
