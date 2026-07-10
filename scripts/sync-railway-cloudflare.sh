#!/usr/bin/env bash
# Keep Cloudflare (and optional GitHub Actions) MySQL secrets aligned with Railway.
#
# Railway's web service uses the private MySQL hostname. Cloudflare Containers
# cannot reach Railway private networking, so they need the MySQL TCP proxy
# host/port plus the same credentials.
#
# Usage:
#   ./scripts/sync-railway-cloudflare.sh status
#   ./scripts/sync-railway-cloudflare.sh write          # → cloudflare/.deploy-secrets
#   ./scripts/sync-railway-cloudflare.sh write-dev      # → cloudflare/.dev.vars
#   ./scripts/sync-railway-cloudflare.sh push-cloudflare
#   ./scripts/sync-railway-cloudflare.sh push-github
#   ./scripts/sync-railway-cloudflare.sh sync           # write + push-cloudflare + push-github
#
# Requires: railway CLI (logged in, or RAILWAY_TOKEN set).
# Optional: wrangler (push-cloudflare), gh (push-github).
#
# Env overrides:
#   RAILWAY_PROJECT_ID, RAILWAY_ENVIRONMENT, MYSQL_SERVICE (default: MySQL)
#   CLOUDFLARE_DIR (default: cloudflare), WRANGLER_CONFIG
set -euo pipefail

ACTION="${1:-status}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT_ID="${RAILWAY_PROJECT_ID:-ac630c3e-e3ce-4518-bc62-037b0860defb}"
ENVIRONMENT="${RAILWAY_ENVIRONMENT:-production}"
MYSQL_SERVICE="${MYSQL_SERVICE:-MySQL}"
CLOUDFLARE_DIR="${CLOUDFLARE_DIR:-${ROOT}/cloudflare}"
WRANGLER_CONFIG="${WRANGLER_CONFIG:-${CLOUDFLARE_DIR}/wrangler.jsonc}"
DEPLOY_SECRETS="${CLOUDFLARE_DIR}/.deploy-secrets"
DEV_VARS="${CLOUDFLARE_DIR}/.dev.vars"

# Secrets Cloudflare needs for the Spring Boot container datasource.
SYNC_KEYS=(
  MYSQLHOST
  MYSQLPORT
  MYSQLUSER
  MYSQLPASSWORD
  MYSQLDATABASE
  MYSQL_SSL_MODE
  MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL
)

die() {
  echo "error: $*" >&2
  exit 1
}

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

need_cmd railway
need_cmd python3

# Load Railway MySQL vars and rewrite host/port to the public TCP proxy.
# Prints KEY=VALUE lines to stdout (includes secrets — do not log casually).
load_cloudflare_mysql_env() {
  local raw
  raw="$(
    railway variable list \
      --service "$MYSQL_SERVICE" \
      --project "$PROJECT_ID" \
      --environment "$ENVIRONMENT" \
      --json
  )" || die "failed to list Railway variables for ${MYSQL_SERVICE}"

  python3 - "$raw" <<'PY'
import json, sys

vars = json.loads(sys.argv[1])
if not isinstance(vars, dict):
    sys.exit("unexpected railway variable list JSON shape")

host = (vars.get("RAILWAY_TCP_PROXY_DOMAIN") or "").strip()
port = str(vars.get("RAILWAY_TCP_PROXY_PORT") or "").strip()
user = (vars.get("MYSQLUSER") or "").strip()
password = vars.get("MYSQLPASSWORD") or ""
database = (vars.get("MYSQLDATABASE") or vars.get("MYSQL_DATABASE") or "").strip()

missing = [n for n, v in [
    ("RAILWAY_TCP_PROXY_DOMAIN", host),
    ("RAILWAY_TCP_PROXY_PORT", port),
    ("MYSQLUSER", user),
    ("MYSQLPASSWORD", password),
    ("MYSQLDATABASE", database),
] if not v]
if missing:
    sys.exit("missing Railway MySQL fields: " + ", ".join(missing))

# Prefer TLS to the public proxy; container can override via Worker secret.
ssl_mode = (vars.get("MYSQL_SSL_MODE") or "PREFERRED").strip() or "PREFERRED"
allow_pk = (vars.get("MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL") or "true").strip() or "true"

out = {
    "MYSQLHOST": host,
    "MYSQLPORT": port,
    "MYSQLUSER": user,
    "MYSQLPASSWORD": password,
    "MYSQLDATABASE": database,
    "MYSQL_SSL_MODE": ssl_mode,
    "MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL": allow_pk,
}
for key, value in out.items():
    # Escape nothing exotic; values are host/port/creds without newlines.
    if "\n" in value or "\r" in value:
        sys.exit(f"refusing to emit {key}: contains newline")
    print(f"{key}={value}")
PY
}

mask_value() {
  local v="$1"
  local n="${#v}"
  if [[ "$n" -le 4 ]]; then
    printf '%s' "****"
  else
    printf '%s****%s' "${v:0:2}" "${v: -2}"
  fi
}

print_status() {
  local lines host port user database ssl allow
  lines="$(load_cloudflare_mysql_env)"
  host="$(printf '%s\n' "$lines" | sed -n 's/^MYSQLHOST=//p')"
  port="$(printf '%s\n' "$lines" | sed -n 's/^MYSQLPORT=//p')"
  user="$(printf '%s\n' "$lines" | sed -n 's/^MYSQLUSER=//p')"
  database="$(printf '%s\n' "$lines" | sed -n 's/^MYSQLDATABASE=//p')"
  ssl="$(printf '%s\n' "$lines" | sed -n 's/^MYSQL_SSL_MODE=//p')"
  allow="$(printf '%s\n' "$lines" | sed -n 's/^MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL=//p')"

  echo "Railway → Cloudflare MySQL sync"
  echo "  project:     ${PROJECT_ID}"
  echo "  environment: ${ENVIRONMENT}"
  echo "  service:     ${MYSQL_SERVICE}"
  echo "  TCP proxy:   ${host}:${port}  (Cloudflare MYSQLHOST/MYSQLPORT)"
  echo "  user:        ${user}"
  echo "  database:    ${database}"
  echo "  ssl mode:    ${ssl}"
  echo "  allow pk:    ${allow}"
  echo "  password:    $(mask_value "$(printf '%s\n' "$lines" | sed -n 's/^MYSQLPASSWORD=//p')")"
  echo ""

  if command -v npx >/dev/null 2>&1; then
    echo "Cloudflare Worker secrets (names only):"
    if npx --yes wrangler secret list --config "$WRANGLER_CONFIG" 2>/dev/null; then
      :
    else
      echo "  (could not list — set CLOUDFLARE_API_TOKEN or run wrangler login)"
    fi
  fi

  if command -v gh >/dev/null 2>&1; then
    echo ""
    echo "GitHub Actions secrets present:"
    local name present
    for name in MYSQLHOST MYSQLPORT MYSQLUSER MYSQLPASSWORD MYSQLDATABASE CLOUDFLARE_API_TOKEN RAILWAY_TOKEN; do
      if gh secret list 2>/dev/null | awk '{print $1}' | grep -qx "$name"; then
        present="yes"
      else
        present="no"
      fi
      printf '  %-22s %s\n' "$name" "$present"
    done
  fi

  echo ""
  echo "Next: ./scripts/sync-railway-cloudflare.sh sync"
}

write_env_file() {
  local dest="$1"
  local label="$2"
  local tmp
  mkdir -p "$(dirname "$dest")"
  umask 077
  # Write to a temp file first so a Railway failure does not truncate dest.
  tmp="$(mktemp "${TMPDIR:-/tmp}/scripty-cf-secrets.XXXXXX")"
  # shellcheck disable=SC2064
  trap 'rm -f "$tmp"' RETURN
  load_cloudflare_mysql_env > "$tmp"
  mv "$tmp" "$dest"
  trap - RETURN
  echo "Wrote ${label}: ${dest}"
  echo "  MYSQLHOST=$(sed -n 's/^MYSQLHOST=//p' "$dest")"
  echo "  MYSQLPORT=$(sed -n 's/^MYSQLPORT=//p' "$dest")"
  echo "  MYSQLUSER=$(sed -n 's/^MYSQLUSER=//p' "$dest")"
  echo "  MYSQLDATABASE=$(sed -n 's/^MYSQLDATABASE=//p' "$dest")"
}

push_cloudflare() {
  need_cmd npx
  write_env_file "$DEPLOY_SECRETS" "deploy secrets"
  echo "Pushing secrets to Cloudflare Worker via wrangler..."
  # Update secrets without a full container rebuild when possible.
  # wrangler secret bulk (or deploy --secrets-file) keeps values off argv.
  if npx --yes wrangler secret bulk --help >/dev/null 2>&1; then
    npx --yes wrangler secret bulk "$DEPLOY_SECRETS" --config "$WRANGLER_CONFIG"
  else
    # Fallback: deploy only updates secrets when used with --secrets-file;
    # prefer secret put loop for older wrangler.
    local key
    for key in "${SYNC_KEYS[@]}"; do
      local value
      value="$(sed -n "s/^${key}=//p" "$DEPLOY_SECRETS")"
      [[ -n "$value" ]] || continue
      printf '%s' "$value" | npx --yes wrangler secret put "$key" --config "$WRANGLER_CONFIG"
    done
  fi
  rm -f "$DEPLOY_SECRETS"
  echo "Cloudflare Worker secrets updated."
}

push_github() {
  need_cmd gh
  local lines key value
  lines="$(load_cloudflare_mysql_env)"
  echo "Updating GitHub Actions secrets (MYSQL*) from Railway TCP proxy..."
  for key in MYSQLHOST MYSQLPORT MYSQLUSER MYSQLPASSWORD MYSQLDATABASE; do
    value="$(printf '%s\n' "$lines" | sed -n "s/^${key}=//p")"
    [[ -n "$value" ]] || die "missing ${key} from Railway sync payload"
    printf '%s' "$value" | gh secret set "$key" --body -
    echo "  set ${key}"
  done
  echo "GitHub Actions MYSQL* secrets updated."
}

case "$ACTION" in
  status)
    print_status
    ;;
  write)
    write_env_file "$DEPLOY_SECRETS" "deploy secrets"
    ;;
  write-dev)
    write_env_file "$DEV_VARS" "dev vars"
    ;;
  push-cloudflare)
    push_cloudflare
    ;;
  push-github)
    push_github
    ;;
  sync)
    push_cloudflare
    if command -v gh >/dev/null 2>&1; then
      push_github
    else
      echo "skip push-github: gh not installed"
    fi
    echo "Sync complete."
    ;;
  *)
    cat >&2 <<EOF
usage: $0 <status|write|write-dev|push-cloudflare|push-github|sync>
EOF
    exit 2
    ;;
esac
