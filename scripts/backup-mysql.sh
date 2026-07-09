#!/usr/bin/env bash
# Dump Railway MySQL and upload a gzipped SQL file to Cloudflare R2.
# Required env vars: MYSQL_BACKUP_HOST, MYSQL_BACKUP_PORT, MYSQL_BACKUP_USER,
# MYSQL_BACKUP_PASSWORD, MYSQL_BACKUP_DATABASE, R2_ACCOUNT_ID, R2_ACCESS_KEY_ID,
# R2_SECRET_ACCESS_KEY, R2_BUCKET
set -euo pipefail

require_var() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "error: missing required env var: ${name}" >&2
    exit 1
  fi
}

require_var MYSQL_BACKUP_HOST
require_var MYSQL_BACKUP_PORT
require_var MYSQL_BACKUP_USER
require_var MYSQL_BACKUP_PASSWORD
require_var MYSQL_BACKUP_DATABASE
require_var R2_ACCOUNT_ID
require_var R2_ACCESS_KEY_ID
require_var R2_SECRET_ACCESS_KEY
require_var R2_BUCKET

for cmd in mysqldump gzip aws; do
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
ENDPOINT_URL="https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com"
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
echo "Created ${FILENAME} (${SIZE} bytes). Uploading to s3://${R2_BUCKET}/${OBJECT_KEY}..."

export AWS_ACCESS_KEY_ID="${R2_ACCESS_KEY_ID}"
export AWS_SECRET_ACCESS_KEY="${R2_SECRET_ACCESS_KEY}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-auto}"

aws s3 cp "${DUMP_PATH}" "s3://${R2_BUCKET}/${OBJECT_KEY}" \
  --endpoint-url "${ENDPOINT_URL}"

echo "Upload complete: s3://${R2_BUCKET}/${OBJECT_KEY}"
echo "Prefer an R2 lifecycle rule to expire objects after 30 days."
