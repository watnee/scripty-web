#!/usr/bin/env bash
# Encrypted snapshot of Scripty's readable secrets, stored in the existing
# Cloudflare R2 backup bucket alongside the database dumps.
#
# What it captures:
#   - Railway variables for the web + MySQL services (the upstream secret store)
#   - cloudflare/.dev.vars if present locally
#
# What it can NOT capture: GitHub Actions secrets and Cloudflare Worker secrets
# are write-only by design. They are re-pushed from Railway by
# `./scripts/bootstrap-deploy.sh secrets` and `./scripts/sync-railway-cloudflare.sh`,
# so a Railway snapshot covers everything recoverable.
#
# Usage:
#   ./scripts/backup-secrets.sh backup [--no-upload]   # snapshot + encrypt (+ upload)
#   ./scripts/backup-secrets.sh restore [file|latest] [--keys]
#                                                      # decrypt to stdout
#                                                      # --keys: names only, no values
#   ./scripts/backup-secrets.sh list                   # list snapshots in R2
#
# Env:
#   SECRETS_BACKUP_PASSPHRASE - encryption passphrase (prompted if unset).
#                               Store it in your password manager.
#   R2_BUCKET                 - bucket (default: scripty-db-backups)
#   WEB_SERVICE               - Railway web service name (default: web)
#   MYSQL_SERVICE             - Railway MySQL service name (default: MySQL)
#   CLOUDFLARE_API_TOKEN / CLOUDFLARE_ACCOUNT_ID - required for `list` and
#                               `restore latest`; account id is auto-resolved
#                               from the token when possible.
#
# Plaintext never touches disk: the bundle is built in memory and piped
# straight into `openssl enc -aes-256-cbc -pbkdf2`. Restore decrypts to stdout.
set -euo pipefail

WEB_SERVICE="${WEB_SERVICE:-web}"
MYSQL_SERVICE="${MYSQL_SERVICE:-MySQL}"
R2_BUCKET="${R2_BUCKET:-scripty-db-backups}"
R2_PREFIX="secrets"
OPENSSL_ARGS=(-aes-256-cbc -pbkdf2 -iter 310000 -salt)

die() { echo "error: $*" >&2; exit 1; }

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

prompt_passphrase() {
  # $1 = "new" (prompt twice) or "existing" (prompt once)
  if [[ -n "${SECRETS_BACKUP_PASSPHRASE:-}" ]]; then
    return
  fi
  [[ -t 0 ]] || die "SECRETS_BACKUP_PASSPHRASE is not set and stdin is not a TTY"
  local p1 p2
  read -r -s -p "Encryption passphrase: " p1; echo >&2
  [[ -n "$p1" ]] || die "empty passphrase"
  if [[ "$1" == "new" ]]; then
    read -r -s -p "Confirm passphrase: " p2; echo >&2
    [[ "$p1" == "$p2" ]] || die "passphrases do not match"
  fi
  export SECRETS_BACKUP_PASSPHRASE="$p1"
}

resolve_account_id() {
  if [[ -z "${CLOUDFLARE_ACCOUNT_ID:-}" && -n "${CLOUDFLARE_API_TOKEN:-}" ]]; then
    CLOUDFLARE_ACCOUNT_ID="$(curl -sS -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" \
      "https://api.cloudflare.com/client/v4/accounts?per_page=50" 2>/dev/null \
      | python3 -c 'import sys,json
r = json.load(sys.stdin).get("result") or []
print(r[0]["id"] if len(r) == 1 else "")' 2>/dev/null || true)"
  fi
}

railway_vars_json() {
  # $1 = service name; prints the raw `railway variable list --json` payload
  railway variable list --service "$1" --json 2>/dev/null \
    || die "failed to read Railway variables for service '$1' — run: railway login (and railway link)"
}

cmd_backup() {
  local upload=1
  [[ "${1:-}" == "--no-upload" ]] && upload=0

  need_cmd railway
  need_cmd openssl
  need_cmd python3

  echo "Reading Railway variables ('${WEB_SERVICE}', '${MYSQL_SERVICE}')..."
  local web_json mysql_json dev_vars=""
  web_json="$(railway_vars_json "$WEB_SERVICE")"
  mysql_json="$(railway_vars_json "$MYSQL_SERVICE")"
  if [[ -f "cloudflare/.dev.vars" ]]; then
    dev_vars="$(cat cloudflare/.dev.vars)"
    echo "Including local cloudflare/.dev.vars."
  fi

  # Assemble the JSON bundle in memory (also validates the Railway payloads).
  local bundle
  bundle="$(
    SNAP_WEB="$web_json" SNAP_MYSQL="$mysql_json" SNAP_DEVVARS="$dev_vars" \
    SNAP_WEB_NAME="$WEB_SERVICE" SNAP_MYSQL_NAME="$MYSQL_SERVICE" \
    python3 <<'PY'
import datetime, json, os, sys

try:
    web = json.loads(os.environ["SNAP_WEB"])
    mysql = json.loads(os.environ["SNAP_MYSQL"])
except json.JSONDecodeError as e:
    sys.exit(f"unexpected railway variable list JSON shape: {e}")

bundle = {
    "format": "scripty-secrets-snapshot/1",
    "created_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
    "railway": {
        os.environ["SNAP_WEB_NAME"]: web,
        os.environ["SNAP_MYSQL_NAME"]: mysql,
    },
    "local_files": {},
}
dev_vars = os.environ.get("SNAP_DEVVARS", "")
if dev_vars:
    bundle["local_files"]["cloudflare/.dev.vars"] = dev_vars
print(json.dumps(bundle, indent=2, sort_keys=True))
PY
  )" || exit 1

  prompt_passphrase new

  local stamp filename
  stamp="$(date -u +%Y%m%d-%H%M%S)"
  filename="scripty-secrets-${stamp}.json.enc"

  umask 077
  printf '%s' "$bundle" \
    | openssl enc "${OPENSSL_ARGS[@]}" -pass env:SECRETS_BACKUP_PASSPHRASE -out "$filename"

  # Round-trip check: the ciphertext must decrypt back with the same passphrase.
  openssl enc -d "${OPENSSL_ARGS[@]}" -pass env:SECRETS_BACKUP_PASSPHRASE -in "$filename" \
    | python3 -c 'import sys,json; json.load(sys.stdin)' \
    || { rm -f "$filename"; die "round-trip decrypt failed; snapshot discarded"; }

  local size sha256
  size="$(wc -c < "$filename" | tr -d ' ')"
  sha256="$(openssl dgst -sha256 "$filename" | awk '{print $NF}')"
  echo "Created ${filename} (${size} bytes, sha256=${sha256})."

  if [[ "$upload" -eq 1 ]]; then
    if [[ -z "${CLOUDFLARE_API_TOKEN:-}" ]] && ! npx --yes wrangler whoami >/dev/null 2>&1; then
      die "set CLOUDFLARE_API_TOKEN or run: npx wrangler login (or use --no-upload)"
    fi
    echo "Uploading to r2://${R2_BUCKET}/${R2_PREFIX}/${filename}..."
    npx --yes wrangler r2 object put "${R2_BUCKET}/${R2_PREFIX}/${filename}" \
      --file "$filename" \
      --remote
    echo "Upload complete."
  else
    echo "Skipped upload (--no-upload). Encrypted file kept locally."
  fi
  echo "Keep the passphrase in your password manager — without it this snapshot is unreadable."
}

resolve_latest() {
  need_cmd curl
  [[ -n "${CLOUDFLARE_API_TOKEN:-}" ]] || die "CLOUDFLARE_API_TOKEN is required to resolve 'latest'"
  resolve_account_id
  [[ -n "${CLOUDFLARE_ACCOUNT_ID:-}" ]] || die "CLOUDFLARE_ACCOUNT_ID is required (auto-detect failed)"
  curl -sSf -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" \
    "https://api.cloudflare.com/client/v4/accounts/${CLOUDFLARE_ACCOUNT_ID}/r2/buckets/${R2_BUCKET}/objects?prefix=${R2_PREFIX}/" \
    | python3 -c 'import sys,json
objs = json.load(sys.stdin).get("result") or []
keys = sorted(o["key"] for o in objs if o["key"].endswith(".json.enc"))
print(keys[-1] if keys else "")'
}

cmd_restore() {
  local target="${1:-latest}" keys_only=0
  [[ "${2:-}" == "--keys" || "${1:-}" == "--keys" ]] && keys_only=1
  [[ "$target" == "--keys" ]] && target="latest"

  need_cmd openssl
  need_cmd python3

  local enc_path
  if [[ -f "$target" ]]; then
    enc_path="$target"
  else
    local object_key
    if [[ "$target" == "latest" ]]; then
      echo "Resolving latest secrets snapshot in r2://${R2_BUCKET}/${R2_PREFIX}/..." >&2
      object_key="$(resolve_latest)"
      [[ -n "$object_key" ]] || die "no secrets snapshots found in r2://${R2_BUCKET}/${R2_PREFIX}/"
    else
      object_key="${R2_PREFIX}/${target}"
    fi
    local workdir
    workdir="$(mktemp -d "${TMPDIR:-/tmp}/scripty-secrets-restore.XXXXXX")"
    trap 'rm -rf "$workdir"' EXIT
    enc_path="${workdir}/snapshot.enc"
    echo "Downloading r2://${R2_BUCKET}/${object_key}..." >&2
    npx --yes wrangler r2 object get "${R2_BUCKET}/${object_key}" \
      --file "$enc_path" \
      --remote >&2
  fi

  prompt_passphrase existing

  # Decrypt in memory and validate before printing anything: with a wrong
  # passphrase OpenSSL can emit garbage instead of failing.
  local plain
  plain="$(openssl enc -d "${OPENSSL_ARGS[@]}" -pass env:SECRETS_BACKUP_PASSPHRASE -in "$enc_path" 2>/dev/null)" \
    || die "decryption failed — wrong passphrase?"
  printf '%s' "$plain" | python3 -c 'import sys,json; json.load(sys.stdin)' 2>/dev/null \
    || die "decrypted data is not a valid snapshot — wrong passphrase?"

  if [[ "$keys_only" -eq 1 ]]; then
    printf '%s' "$plain" \
      | python3 -c 'import sys,json
b = json.load(sys.stdin)
print("snapshot created:", b.get("created_utc"))
for svc, vars_ in sorted((b.get("railway") or {}).items()):
    print(f"railway/{svc}: {len(vars_)} vars")
    for k in sorted(vars_):
        print(f"  {k}")
for path in sorted(b.get("local_files") or {}):
    print(f"local file: {path}")'
  else
    echo "warning: printing secret values to stdout" >&2
    printf '%s\n' "$plain"
    echo "Re-provision from these values with: ./scripts/bootstrap-deploy.sh secrets && npm run cf:sync" >&2
  fi
}

cmd_list() {
  need_cmd curl
  need_cmd python3
  [[ -n "${CLOUDFLARE_API_TOKEN:-}" ]] || die "CLOUDFLARE_API_TOKEN is required for list"
  resolve_account_id
  [[ -n "${CLOUDFLARE_ACCOUNT_ID:-}" ]] || die "CLOUDFLARE_ACCOUNT_ID is required (auto-detect failed)"
  curl -sSf -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" \
    "https://api.cloudflare.com/client/v4/accounts/${CLOUDFLARE_ACCOUNT_ID}/r2/buckets/${R2_BUCKET}/objects?prefix=${R2_PREFIX}/" \
    | python3 -c 'import sys,json
objs = json.load(sys.stdin).get("result") or []
if not objs:
    print("no secrets snapshots found")
for o in sorted(objs, key=lambda o: o["key"]):
    print(f'"'"'{o["key"]}  {o.get("size","?")} bytes  {o.get("uploaded","")}'"'"')'
}

case "${1:-}" in
  backup)  shift; cmd_backup "$@" ;;
  restore) shift; cmd_restore "$@" ;;
  list)    shift; cmd_list "$@" ;;
  *)
    sed -n '2,33p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
