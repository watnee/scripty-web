#!/usr/bin/env bash
# Verify the newest gzipped MySQL dump in Cloudflare R2 without touching any database.
#
# Checks: a scripty-*.sql.gz object exists, is recent, is not anomalously small
# vs. the previous dump, gunzips cleanly, and contains the expected schema/data.
#
# Required credentials (no MYSQL* vars needed):
#   CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID - listing objects via the R2 REST API
#     (CLOUDFLARE_ACCOUNT_ID is auto-resolved from the token when possible)
#   Download uses wrangler: the same token, or local OAuth via `npx wrangler login`.
#
# Optional:
#   R2_BUCKET        - bucket name (default: scripty-db-backups)
#   MAX_AGE_HOURS    - fail if newest dump is older than this (default: 26)
#   MIN_SIZE_RATIO   - fail if newest dump is smaller than this % of the previous one (default: 70)
#   BACKUP_MIN_BYTES - fail if newest dump is smaller than this (default: 1024)
#   VERIFY_RESTORE=1 - (reserved) test-restore into a throwaway Docker MySQL; not implemented yet
#
# Exit code 0 = all checks passed; 1 = one or more checks failed (all checks
# still run so a single invocation reports everything).
set -euo pipefail

R2_BUCKET="${R2_BUCKET:-scripty-db-backups}"
MAX_AGE_HOURS="${MAX_AGE_HOURS:-26}"
MIN_SIZE_RATIO="${MIN_SIZE_RATIO:-70}"
BACKUP_MIN_BYTES="${BACKUP_MIN_BYTES:-1024}"
VERIFY_RESTORE="${VERIFY_RESTORE:-0}"

FAILURES=()
fail() {
  echo "error: $1" >&2
  FAILURES+=("$1")
}
ok() {
  echo "ok: $1"
}

for cmd in gunzip python3 curl; do
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
    if accounts:
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

if [[ -z "${CLOUDFLARE_API_TOKEN:-}" || -z "${CLOUDFLARE_ACCOUNT_ID:-}" ]]; then
  echo "error: CLOUDFLARE_API_TOKEN and CLOUDFLARE_ACCOUNT_ID are required to list backups in R2." >&2
  exit 1
fi

# --- Check 1: list backups -------------------------------------------------
echo "Listing backups in R2 bucket '${R2_BUCKET}'..."
API_URL="https://api.cloudflare.com/client/v4/accounts/${CLOUDFLARE_ACCOUNT_ID}/r2/buckets/${R2_BUCKET}/objects"

LISTING=""
if RESP=$(curl -sSf -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" "${API_URL}"); then
  # Newest-first "key<TAB>size" lines for scripty-*.sql.gz objects.
  LISTING=$(python3 - "${RESP}" <<'PY'
import json, sys
try:
    data = json.loads(sys.argv[1])
except Exception:
    sys.exit("Cloudflare API returned non-JSON response")

if not data.get("success"):
    errs = data.get("errors") or [{"message": "unknown error"}]
    msgs = "; ".join(f"{e.get('code', '?')}: {e.get('message', '?')}" for e in errs)
    sys.exit(f"Cloudflare API error: {msgs}")

objects = data.get("result", {}).get("objects", [])
backups = [obj for obj in objects if obj.get("key", "").startswith("scripty-") and obj.get("key", "").endswith(".sql.gz")]
backups.sort(key=lambda x: x["key"], reverse=True)
for obj in backups:
    print(f"{obj['key']}\t{obj.get('size', 0)}")
PY
) || { fail "failed to parse R2 object listing: ${LISTING:-see above}"; LISTING=""; }
else
  fail "failed to fetch object listing from Cloudflare API (bucket '${R2_BUCKET}')"
fi

NEWEST_KEY=""
NEWEST_SIZE=0
PREV_KEY=""
PREV_SIZE=0
AGE_HOURS=""

if [[ -z "${LISTING}" ]]; then
  fail "no backups matching scripty-*.sql.gz found in bucket '${R2_BUCKET}'"
else
  BACKUP_COUNT=$(wc -l <<<"${LISTING}" | tr -d ' ')
  NEWEST_KEY=$(head -n1 <<<"${LISTING}" | cut -f1)
  NEWEST_SIZE=$(head -n1 <<<"${LISTING}" | cut -f2)
  if [[ "${BACKUP_COUNT}" -ge 2 ]]; then
    PREV_KEY=$(sed -n '2p' <<<"${LISTING}" | cut -f1)
    PREV_SIZE=$(sed -n '2p' <<<"${LISTING}" | cut -f2)
  fi
  ok "found ${BACKUP_COUNT} backup(s); newest: ${NEWEST_KEY} (${NEWEST_SIZE} bytes)"
fi

if [[ -n "${NEWEST_KEY}" ]]; then
  # --- Check 2: freshness --------------------------------------------------
  # Keys look like scripty-YYYYMMDD-HHMMSS.sql.gz (UTC timestamps).
  STAMP=$(sed -E 's/^scripty-([0-9]{8}-[0-9]{6})\.sql\.gz$/\1/' <<<"${NEWEST_KEY}")
  if [[ "${STAMP}" == "${NEWEST_KEY}" ]]; then
    fail "cannot parse timestamp from backup name '${NEWEST_KEY}'"
  else
    AGE_HOURS=$(python3 - "${STAMP}" "${MAX_AGE_HOURS}" <<'PY'
import sys
from datetime import datetime, timezone
stamp = datetime.strptime(sys.argv[1], "%Y%m%d-%H%M%S").replace(tzinfo=timezone.utc)
age = (datetime.now(timezone.utc) - stamp).total_seconds() / 3600
print(f"{age:.1f}")
PY
)
    if python3 -c "import sys; sys.exit(0 if float(sys.argv[1]) <= float(sys.argv[2]) else 1)" "${AGE_HOURS}" "${MAX_AGE_HOURS}"; then
      ok "newest backup is ${AGE_HOURS}h old (max ${MAX_AGE_HOURS}h)"
    else
      fail "newest backup is ${AGE_HOURS}h old, exceeding MAX_AGE_HOURS=${MAX_AGE_HOURS}"
    fi
  fi

  # --- Check 3: size anomaly -----------------------------------------------
  if [[ "${NEWEST_SIZE}" -lt "${BACKUP_MIN_BYTES}" ]]; then
    fail "newest backup is only ${NEWEST_SIZE} bytes (min ${BACKUP_MIN_BYTES})"
  else
    ok "newest backup size ${NEWEST_SIZE} bytes >= min ${BACKUP_MIN_BYTES}"
  fi
  if [[ -n "${PREV_KEY}" && "${PREV_SIZE}" -gt 0 ]]; then
    RATIO=$(( NEWEST_SIZE * 100 / PREV_SIZE ))
    if [[ "${RATIO}" -lt "${MIN_SIZE_RATIO}" ]]; then
      fail "newest backup is ${RATIO}% the size of previous '${PREV_KEY}' (${PREV_SIZE} bytes); min ${MIN_SIZE_RATIO}%"
    else
      ok "newest backup is ${RATIO}% the size of previous '${PREV_KEY}' (min ${MIN_SIZE_RATIO}%)"
    fi
  else
    echo "note: only one backup in bucket; skipping size-ratio check"
  fi

  # --- Check 4: download + gzip integrity ----------------------------------
  WORKDIR="${TMPDIR:-/tmp}/scripty-db-verify-$$"
  mkdir -p "$WORKDIR"
  trap 'rm -rf "$WORKDIR"' EXIT
  DOWNLOAD_PATH="${WORKDIR}/${NEWEST_KEY}"

  echo "Downloading 'r2://${R2_BUCKET}/${NEWEST_KEY}'..."
  if npx --yes wrangler r2 object get "${R2_BUCKET}/${NEWEST_KEY}" \
      --file "${DOWNLOAD_PATH}" \
      --remote \
    && [[ -f "${DOWNLOAD_PATH}" ]]; then
    LOCAL_SIZE="$(wc -c < "${DOWNLOAD_PATH}" | tr -d ' ')"
    ok "downloaded ${LOCAL_SIZE} bytes"
    if gunzip -t "${DOWNLOAD_PATH}"; then
      ok "gzip integrity check passed"

      # --- Check 5: content sanity ------------------------------------------
      # Full decompress: a truncated tail would pass a head-only sample.
      SQL_PATH="${WORKDIR}/dump.sql"
      gunzip -c "${DOWNLOAD_PATH}" > "${SQL_PATH}"
      TABLE_COUNT=$(grep -c 'CREATE TABLE' "${SQL_PATH}" || true)
      INSERT_COUNT=$(grep -c 'INSERT INTO' "${SQL_PATH}" || true)
      if [[ "${TABLE_COUNT}" -gt 0 ]]; then
        ok "dump defines ${TABLE_COUNT} table(s)"
      else
        fail "dump contains no CREATE TABLE statements"
      fi
      if [[ "${INSERT_COUNT}" -gt 0 ]]; then
        ok "dump contains ${INSERT_COUNT} INSERT statement(s)"
      else
        fail "dump contains no INSERT INTO statements"
      fi
      MISSING_TABLES=()
      for tbl in user project scene actor person block; do
        if ! grep -Eq "CREATE TABLE [\`\"]?${tbl}[\`\"]? " "${SQL_PATH}"; then
          MISSING_TABLES+=("${tbl}")
        fi
      done
      if [[ ${#MISSING_TABLES[@]} -eq 0 ]]; then
        ok "all key application tables present (user project scene actor person block)"
      else
        fail "key application tables missing from dump: ${MISSING_TABLES[*]}"
      fi
    else
      fail "gzip integrity check failed for '${NEWEST_KEY}'"
    fi
  else
    fail "failed to download 'r2://${R2_BUCKET}/${NEWEST_KEY}' via wrangler"
  fi
fi

if [[ "${VERIFY_RESTORE}" == "1" ]]; then
  echo "note: VERIFY_RESTORE=1 (test-restore into throwaway MySQL) is not implemented yet"
fi

# --- Summary ----------------------------------------------------------------
HEALTHY=true
[[ ${#FAILURES[@]} -gt 0 ]] && HEALTHY=false

echo
echo "==== Backup verification summary ===="
echo "bucket:   ${R2_BUCKET}"
echo "newest:   ${NEWEST_KEY:-none}"
echo "size:     ${NEWEST_SIZE:-0} bytes"
echo "age:      ${AGE_HOURS:-?} hours"
echo "healthy:  ${HEALTHY}"
if [[ ${#FAILURES[@]} -gt 0 ]]; then
  echo "failures:"
  printf '  - %s\n' "${FAILURES[@]}"
fi

SUMMARY_JSON=$(python3 - "${HEALTHY}" "${NEWEST_KEY:-}" "${NEWEST_SIZE:-0}" "${AGE_HOURS:-}" "${FAILURES[@]:-}" <<'PY'
import json, sys
healthy, obj, size, age = sys.argv[1:5]
failures = [f for f in sys.argv[5:] if f]
print(json.dumps({
    "healthy": healthy == "true",
    "object": obj or None,
    "size_bytes": int(size),
    "age_hours": float(age) if age else None,
    "failures": failures,
}))
PY
)
echo "${SUMMARY_JSON}"

# Emit machine-readable metadata for CI summaries.
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "healthy=${HEALTHY}"
    echo "object_key=${NEWEST_KEY:-}"
    echo "size_bytes=${NEWEST_SIZE:-0}"
    echo "age_hours=${AGE_HOURS:-}"
    echo "summary=${SUMMARY_JSON}"
  } >> "${GITHUB_OUTPUT}"
fi

[[ "${HEALTHY}" == "true" ]] || exit 1
echo "Backup verification passed."
