#!/usr/bin/env bash
# Keep Cloudflare (and optional GitHub Actions) MySQL secrets aligned with Railway.
#
# Railway's web service uses the private MySQL hostname. Cloudflare Containers
# cannot reach Railway private networking, so they need the MySQL TCP proxy
# host/port plus the same credentials.
#
# Usage:
#   ./scripts/sync-railway-cloudflare.sh status
#   ./scripts/sync-railway-cloudflare.sh check          # drift report (exit 1 if out of sync)
#   ./scripts/sync-railway-cloudflare.sh write          # → cloudflare/.deploy-secrets
#   ./scripts/sync-railway-cloudflare.sh write-dev      # → cloudflare/.dev.vars
#   ./scripts/sync-railway-cloudflare.sh push-cloudflare
#   ./scripts/sync-railway-cloudflare.sh push-github
#   ./scripts/sync-railway-cloudflare.sh sync           # push-cloudflare + push-github
#
# Requires: railway CLI (logged in, or RAILWAY_TOKEN set).
# Optional: wrangler (push-cloudflare / check), gh (push-github / check).
#
# Env overrides:
#   RAILWAY_PROJECT_ID, RAILWAY_ENVIRONMENT, MYSQL_SERVICE (default: MySQL)
#   CLOUDFLARE_DIR (default: cloudflare), WRANGLER_CONFIG
#   Prefer a linked project (`railway link`); PROJECT_ID is only a fallback.
set -euo pipefail

ACTION="${1:-status}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# Fallback when the directory is not linked (CI often passes RAILWAY_TOKEN only).
DEFAULT_PROJECT_ID="ac630c3e-e3ce-4518-bc62-037b0860defb"
PROJECT_ID="${RAILWAY_PROJECT_ID:-}"
ENVIRONMENT="${RAILWAY_ENVIRONMENT:-}"
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

# GitHub Actions fallback secrets (CI prefers live Railway sync when RAILWAY_TOKEN is set).
GITHUB_KEYS=(
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

# Resolve project/environment: explicit env → linked status → hardcoded fallback.
resolve_railway_context() {
  if [[ -n "$PROJECT_ID" && -n "$ENVIRONMENT" ]]; then
    return 0
  fi

  local status_json=""
  if status_json="$(railway status --json 2>/dev/null)"; then
    local linked
    linked="$(
      python3 - "$status_json" <<'PY'
import json, sys
try:
    data = json.loads(sys.argv[1])
except Exception:
    sys.exit(0)

project = (
    data.get("id")
    or data.get("projectId")
    or (data.get("project") or {}).get("id")
    or ""
)

env = ""
raw_env = data.get("environment")
if isinstance(raw_env, dict):
    env = raw_env.get("name") or raw_env.get("id") or ""
elif isinstance(raw_env, str):
    env = raw_env
env = data.get("environmentName") or data.get("environmentId") or env
print(f"{project}\t{env}")
PY
    )"
    if [[ -n "$linked" ]]; then
      local linked_project linked_env
      linked_project="$(printf '%s' "$linked" | cut -f1)"
      linked_env="$(printf '%s' "$linked" | cut -f2)"
      PROJECT_ID="${PROJECT_ID:-$linked_project}"
      ENVIRONMENT="${ENVIRONMENT:-$linked_env}"
    fi
  fi

  PROJECT_ID="${PROJECT_ID:-$DEFAULT_PROJECT_ID}"
  ENVIRONMENT="${ENVIRONMENT:-production}"
}

# Load Railway MySQL vars and rewrite host/port to the public TCP proxy.
# Prints KEY=VALUE lines to stdout (includes secrets — do not log casually).
load_cloudflare_mysql_env() {
  resolve_railway_context

  local raw proxy_json=""

  raw="$(
    railway variable list \
      --service "$MYSQL_SERVICE" \
      --project "$PROJECT_ID" \
      --environment "$ENVIRONMENT" \
      --json
  )" || die "failed to list Railway variables for ${MYSQL_SERVICE}"

  # Prefer explicit TCP proxy API when service vars omit RAILWAY_TCP_PROXY_*.
  if proxy_json="$(
    railway tcp-proxy list \
      --service "$MYSQL_SERVICE" \
      --project "$PROJECT_ID" \
      --environment "$ENVIRONMENT" \
      --json 2>/dev/null
  )"; then
    :
  else
    proxy_json="[]"
  fi

  python3 - "$raw" "$proxy_json" <<'PY'
import json, sys

vars = json.loads(sys.argv[1])
if not isinstance(vars, dict):
    sys.exit("unexpected railway variable list JSON shape")

host = (vars.get("RAILWAY_TCP_PROXY_DOMAIN") or "").strip()
port = str(vars.get("RAILWAY_TCP_PROXY_PORT") or "").strip()

# Fallback: railway tcp-proxy list --json
if not host or not port:
    try:
        proxies = json.loads(sys.argv[2])
    except Exception:
        proxies = []
    if isinstance(proxies, dict):
        proxies = proxies.get("proxies") or proxies.get("tcpProxies") or proxies.get("items") or []
    if isinstance(proxies, list) and proxies:
        p0 = proxies[0] if isinstance(proxies[0], dict) else {}
        # Common shapes: domain/host + proxyPort/port
        host = host or (
            p0.get("domain")
            or p0.get("host")
            or p0.get("endpoint")
            or ""
        )
        if isinstance(host, str) and ":" in host and not port:
            # endpoint like host:port
            maybe_host, maybe_port = host.rsplit(":", 1)
            if maybe_port.isdigit():
                host, port = maybe_host, maybe_port
        port = port or str(
            p0.get("proxyPort")
            or p0.get("port")
            or p0.get("externalPort")
            or ""
        )
        host = (host or "").strip()
        port = str(port or "").strip()

user = (vars.get("MYSQLUSER") or "").strip()
password = vars.get("MYSQLPASSWORD") or ""
database = (vars.get("MYSQLDATABASE") or vars.get("MYSQL_DATABASE") or "").strip()

missing = [n for n, v in [
    ("RAILWAY_TCP_PROXY_DOMAIN (or tcp-proxy)", host),
    ("RAILWAY_TCP_PROXY_PORT (or tcp-proxy)", port),
    ("MYSQLUSER", user),
    ("MYSQLPASSWORD", password),
    ("MYSQLDATABASE", database),
] if not v]
if missing:
    sys.exit("missing Railway MySQL fields: " + ", ".join(missing))

if ".railway.internal" in host.lower():
    sys.exit(
        f"refusing private Railway hostname for Cloudflare: {host} "
        "(need the public TCP proxy domain)"
    )

# A non-numeric port pushed to secrets fails downstream with mysqldump's
# cryptic "option 'port': value adjusted to 0" (2026-07-10 incident).
if not port.isdigit():
    sys.exit(f"refusing non-numeric MySQL port for sync: {port!r}")
if "/" in host or ":" in host or " " in host:
    sys.exit(f"refusing malformed MySQL host for sync: {host!r}")

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

fingerprint() {
  # Short non-reversible token for drift checks (never print the secret).
  printf '%s' "$1" | python3 -c 'import hashlib,sys; print(hashlib.sha256(sys.stdin.buffer.read()).hexdigest()[:12])'
}

kv_get() {
  local lines="$1" key="$2"
  printf '%s\n' "$lines" | sed -n "s/^${key}=//p" | head -n1
}

print_status() {
  local lines host port user database ssl allow
  lines="$(load_cloudflare_mysql_env)"
  host="$(kv_get "$lines" MYSQLHOST)"
  port="$(kv_get "$lines" MYSQLPORT)"
  user="$(kv_get "$lines" MYSQLUSER)"
  database="$(kv_get "$lines" MYSQLDATABASE)"
  ssl="$(kv_get "$lines" MYSQL_SSL_MODE)"
  allow="$(kv_get "$lines" MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL)"

  echo "Railway → Cloudflare MySQL sync"
  echo "  project:     ${PROJECT_ID}"
  echo "  environment: ${ENVIRONMENT}"
  echo "  service:     ${MYSQL_SERVICE}"
  echo "  TCP proxy:   ${host}:${port}  (Cloudflare MYSQLHOST/MYSQLPORT)"
  echo "  user:        ${user}"
  echo "  database:    ${database}"
  echo "  ssl mode:    ${ssl}"
  echo "  allow pk:    ${allow}"
  echo "  password:    $(mask_value "$(kv_get "$lines" MYSQLPASSWORD)")"
  echo "  fingerprint: $(fingerprint "$(kv_get "$lines" MYSQLPASSWORD)")"
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
    for name in "${GITHUB_KEYS[@]}" CLOUDFLARE_API_TOKEN RAILWAY_TOKEN; do
      if gh secret list 2>/dev/null | awk '{print $1}' | grep -qx "$name"; then
        present="yes"
      else
        present="no"
      fi
      printf '  %-32s %s\n' "$name" "$present"
    done
  fi

  echo ""
  echo "Next: ./scripts/sync-railway-cloudflare.sh sync"
  echo "Drift: ./scripts/sync-railway-cloudflare.sh check"
}

# Exit 0 when Cloudflare secret names + GitHub fallback secrets look aligned.
# Cannot read secret values from CF/GH APIs — compares Railway payload to local
# .dev.vars / .deploy-secrets when present, and required name presence elsewhere.
check_sync() {
  local lines drift=0
  local host port user database ssl allow fp
  lines="$(load_cloudflare_mysql_env)"
  host="$(kv_get "$lines" MYSQLHOST)"
  port="$(kv_get "$lines" MYSQLPORT)"
  user="$(kv_get "$lines" MYSQLUSER)"
  database="$(kv_get "$lines" MYSQLDATABASE)"
  ssl="$(kv_get "$lines" MYSQL_SSL_MODE)"
  allow="$(kv_get "$lines" MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL)"
  fp="$(fingerprint "$(kv_get "$lines" MYSQLPASSWORD)")"

  echo "Sync check (Railway source of truth)"
  echo "  project=${PROJECT_ID} env=${ENVIRONMENT} service=${MYSQL_SERVICE}"
  echo "  TCP ${host}:${port} user=${user} db=${database} ssl=${ssl} allow_pk=${allow}"
  echo "  password fingerprint=${fp}"
  echo ""

  # Local files written by this script
  local file
  for file in "$DEV_VARS" "$DEPLOY_SECRETS"; do
    if [[ -f "$file" ]]; then
      local f_host f_port f_user f_db f_ssl f_fp
      f_host="$(sed -n 's/^MYSQLHOST=//p' "$file" | head -n1)"
      f_port="$(sed -n 's/^MYSQLPORT=//p' "$file" | head -n1)"
      f_user="$(sed -n 's/^MYSQLUSER=//p' "$file" | head -n1)"
      f_db="$(sed -n 's/^MYSQLDATABASE=//p' "$file" | head -n1)"
      f_ssl="$(sed -n 's/^MYSQL_SSL_MODE=//p' "$file" | head -n1)"
      f_fp="$(fingerprint "$(sed -n 's/^MYSQLPASSWORD=//p' "$file" | head -n1)")"
      echo "Local $(basename "$file"):"
      if [[ "$f_host" == "$host" && "$f_port" == "$port" && "$f_user" == "$user" && "$f_db" == "$database" && "$f_fp" == "$fp" ]]; then
        echo "  OK (host/port/user/db/password match Railway)"
        if [[ -n "$f_ssl" && "$f_ssl" != "$ssl" ]]; then
          echo "  WARN ssl mode local=${f_ssl} railway=${ssl}"
          drift=1
        fi
      else
        echo "  DRIFT vs Railway"
        echo "    local  ${f_host}:${f_port} user=${f_user} db=${f_db} fp=${f_fp}"
        echo "    railway ${host}:${port} user=${user} db=${database} fp=${fp}"
        drift=1
      fi
    else
      echo "Local $(basename "$file"): (absent)"
    fi
  done
  echo ""

  if command -v npx >/dev/null 2>&1; then
    echo "Cloudflare Worker required secret names:"
    local cf_json missing_cf=0 name
    if cf_json="$(npx --yes wrangler secret list --config "$WRANGLER_CONFIG" 2>/dev/null)"; then
      for name in MYSQLHOST MYSQLPORT MYSQLUSER MYSQLPASSWORD MYSQLDATABASE; do
        if printf '%s' "$cf_json" | python3 -c 'import json,sys; names={x.get("name") for x in json.load(sys.stdin) if isinstance(x,dict)}; sys.exit(0 if sys.argv[1] in names else 1)' "$name" 2>/dev/null; then
          printf '  %-28s present\n' "$name"
        else
          printf '  %-28s MISSING\n' "$name"
          missing_cf=1
          drift=1
        fi
      done
      for name in MYSQL_SSL_MODE MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL; do
        if printf '%s' "$cf_json" | python3 -c 'import json,sys; names={x.get("name") for x in json.load(sys.stdin) if isinstance(x,dict)}; sys.exit(0 if sys.argv[1] in names else 1)' "$name" 2>/dev/null; then
          printf '  %-28s present (optional)\n' "$name"
        else
          printf '  %-28s absent (optional; defaults apply)\n' "$name"
        fi
      done
      if [[ "$missing_cf" -eq 0 ]]; then
        echo "  (values not readable via API — run push-cloudflare after Railway password rotates)"
      fi
    else
      echo "  (could not list — set CLOUDFLARE_API_TOKEN or run wrangler login)"
    fi
    echo ""
  fi

  if command -v gh >/dev/null 2>&1; then
    echo "GitHub Actions fallback secrets:"
    local name present
    for name in "${GITHUB_KEYS[@]}"; do
      if gh secret list 2>/dev/null | awk '{print $1}' | grep -qx "$name"; then
        present="present"
      else
        present="MISSING"
        # SSL keys are optional for older setups; still report.
        if [[ "$name" == MYSQLHOST || "$name" == MYSQLPORT || "$name" == MYSQLUSER || "$name" == MYSQLPASSWORD || "$name" == MYSQLDATABASE ]]; then
          drift=1
        fi
      fi
      printf '  %-32s %s\n' "$name" "$present"
    done
    if gh secret list 2>/dev/null | awk '{print $1}' | grep -qx RAILWAY_TOKEN; then
      echo "  RAILWAY_TOKEN                     present (CI prefers live Railway sync)"
    else
      echo "  RAILWAY_TOKEN                     MISSING (CI will need MYSQL* fallback)"
      drift=1
    fi
    echo ""
  fi

  if [[ "$drift" -ne 0 ]]; then
    echo "Result: DRIFT — run: ./scripts/sync-railway-cloudflare.sh sync"
    return 1
  fi
  echo "Result: OK (names/local files aligned; push after any Railway credential change)"
  return 0
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
  echo "  MYSQL_SSL_MODE=$(sed -n 's/^MYSQL_SSL_MODE=//p' "$dest")"
  echo "  fingerprint=$(fingerprint "$(sed -n 's/^MYSQLPASSWORD=//p' "$dest")")"
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
  for key in "${GITHUB_KEYS[@]}"; do
    value="$(kv_get "$lines" "$key")"
    [[ -n "$value" ]] || die "missing ${key} from Railway sync payload"
    # No --body flag: gh reads the value from stdin ("--body -" would store a literal "-").
    printf '%s' "$value" | gh secret set "$key"
    echo "  set ${key}"
  done
  echo "GitHub Actions MYSQL* secrets updated (incl. SSL mode)."
}

case "$ACTION" in
  status)
    print_status
    ;;
  check)
    check_sync
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
usage: $0 <status|check|write|write-dev|push-cloudflare|push-github|sync>
EOF
    exit 2
    ;;
esac
