#!/usr/bin/env bash
# One-command ease-of-use for the Grafana + Prometheus observability stack.
#
#   ./scripts/observability.sh doctor   # read-only: is the whole metrics pipeline healthy, what is missing, how to fix it
#   ./scripts/observability.sh open     # ensure the Railway grafana service has a public domain, print/open it
#   ./scripts/observability.sh up       # local stack: docker compose up + wait for Prometheus/Grafana health
#   ./scripts/observability.sh down     # local stack: docker compose down
#   ./scripts/observability.sh status   # local stack: docker compose ps
#
# All subcommands are idempotent — safe to re-run any time. npm aliases:
#   npm run obs:doctor / obs:open / obs:up / obs:down
#
# Requirements: railway CLI (logged in, project linked) for doctor/open;
# Docker for up/down/status. Service names can be overridden with
# GRAFANA_SERVICE / PROMETHEUS_SERVICE / WEB_SERVICE.
set -euo pipefail

ACTION="${1:-doctor}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

GRAFANA_SERVICE="${GRAFANA_SERVICE:-grafana}"
PROMETHEUS_SERVICE="${PROMETHEUS_SERVICE:-prometheus}"
WEB_SERVICE="${WEB_SERVICE:-web}"
OBS_DIR="observability"
TOKEN_FILE="$OBS_DIR/prometheus/token"
TOKEN_EXAMPLE="$OBS_DIR/prometheus/token.example"

BOLD=$'\033[1m'; RED=$'\033[31m'; GRN=$'\033[32m'; YEL=$'\033[33m'; RST=$'\033[0m'
[[ -t 1 ]] || { BOLD=""; RED=""; GRN=""; YEL=""; RST=""; }

MISSING_REQUIRED=0
NEXT_STEPS=()

die()  { echo "error: $*" >&2; exit 1; }
ok()   { printf '  %s✓%s %s\n' "$GRN" "$RST" "$*"; }
warn() { printf '  %s!%s %s\n' "$YEL" "$RST" "$*"; }
miss() {
  # miss "<what is missing>" "<command that fixes it>"
  printf '  %s✗%s %s\n' "$RED" "$RST" "$1"
  MISSING_REQUIRED=1
  [[ -n "${2:-}" ]] && NEXT_STEPS+=("$2")
}
section() { printf '\n%s%s%s\n' "$BOLD" "$*" "$RST"; }
have_cmd() { command -v "$1" >/dev/null 2>&1; }
need_cmd() { have_cmd "$1" || die "required command not found: $1"; }
interactive() { [[ -t 0 ]]; }

# --- Railway helpers ---------------------------------------------------------

railway_logged_in() {
  [[ -n "${RAILWAY_TOKEN:-}" ]] && return 0
  railway whoami >/dev/null 2>&1
}

# Prints the service names in the linked project, one per line.
railway_services() {
  railway status --json 2>/dev/null | python3 -c '
import json, sys
data = json.load(sys.stdin)
names = set()
for env_edge in (data.get("environments") or {}).get("edges", []):
    for si in ((env_edge.get("node") or {}).get("serviceInstances") or {}).get("edges", []):
        name = (si.get("node") or {}).get("serviceName")
        if name:
            names.add(name)
for n in sorted(names):
    print(n)
'
}

# Prints the first domain of a service, or nothing.
service_domain() {
  railway domain list -s "$1" --json 2>/dev/null | python3 -c '
import json, sys
data = json.load(sys.stdin)
domains = [d.get("domain") for d in data.get("domains", []) if d.get("domain")]
if domains:
    print(domains[0])
'
}

# Prints the value of a variable on a service, or nothing. Value never echoed.
service_variable() {
  railway variable list -s "$1" --json 2>/dev/null | python3 -c '
import json, sys
key = sys.argv[1]
data = json.load(sys.stdin)
if isinstance(data, dict):
    value = data.get(key)
else:  # tolerate a [{"name": ..., "value": ...}] shape
    value = next((d.get("value") for d in data if d.get("name") == key), None)
if value:
    print(value)
' "$2"
}

http_code() {
  # http_code <url> [extra curl args...]  — prints 000 when the request fails
  local url="$1" code; shift
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$@" "$url" 2>/dev/null)" || true
  echo "${code:-000}"
}

wait_for_http() {
  # wait_for_http <name> <url> <timeout_seconds> [expected_code]
  local name="$1" url="$2" timeout="$3" expected="${4:-200}" waited=0 code
  while (( waited < timeout )); do
    code="$(http_code "$url")"
    if [[ "$code" == "$expected" ]]; then
      ok "$name is up ($url)"
      return 0
    fi
    sleep 3; waited=$(( waited + 3 ))
  done
  warn "$name did not answer $expected at $url within ${timeout}s (last: $code)"
  return 1
}

require_railway() {
  need_cmd railway
  railway_logged_in || die "railway CLI is not logged in — run: railway login"
  railway status >/dev/null 2>&1 || die "no linked Railway project here — run: railway link"
}

compose() { docker compose -f "$OBS_DIR/docker-compose.yml" "$@"; }

ensure_token_file() {
  if [[ ! -f "$TOKEN_FILE" ]]; then
    if [[ -f "$TOKEN_EXAMPLE" ]]; then
      cp "$TOKEN_EXAMPLE" "$TOKEN_FILE"
    else
      : > "$TOKEN_FILE"
    fi
    echo "created $TOKEN_FILE (empty is fine for the dev profile; put METRICS_TOKEN in it to scrape a prod-like instance)"
  fi
}

# --- doctor ------------------------------------------------------------------

cmd_doctor() {
  section "Railway CLI"
  if have_cmd railway; then
    ok "railway CLI installed ($(railway --version 2>/dev/null))"
  else
    miss "railway CLI not installed" "brew install railway  # or: npm i -g @railway/cli"
  fi
  local railway_ready=0
  if have_cmd railway && railway_logged_in; then
    ok "logged in"
    if railway status >/dev/null 2>&1; then
      ok "project linked"
      railway_ready=1
    else
      miss "no Railway project linked in this directory" "railway link"
    fi
  elif have_cmd railway; then
    miss "railway CLI not logged in" "railway login"
  fi

  if (( railway_ready )); then
    section "Railway services"
    local services
    services="$(railway_services)" || services=""
    for svc in "$GRAFANA_SERVICE" "$PROMETHEUS_SERVICE" "$WEB_SERVICE"; do
      if grep -qx "$svc" <<<"$services"; then
        ok "service '$svc' exists"
      else
        miss "service '$svc' not found in the project" "railway config apply   # creates it from .railway/railway.ts"
      fi
    done

    section "Grafana access"
    local grafana_domain
    grafana_domain="$(service_domain "$GRAFANA_SERVICE")" || grafana_domain=""
    if [[ -n "$grafana_domain" ]]; then
      ok "public domain: https://$grafana_domain"
      case "$(http_code "https://$grafana_domain/api/health")" in
        200) ok "Grafana answers on it" ;;
        *)   warn "Grafana not answering yet on https://$grafana_domain (domain may still be provisioning)" ;;
      esac
      case "$(http_code "https://$grafana_domain/api/org" -u admin:admin)" in
        200) warn "Grafana still accepts the DEFAULT admin/admin login — open it and change the password NOW: https://$grafana_domain" ;;
        401) ok "default admin/admin credentials are disabled" ;;
        *)   warn "could not verify Grafana credentials state" ;;
      esac
    else
      miss "grafana has no public domain — dashboards are unreachable" "npm run obs:open"
    fi

    section "Metrics pipeline (web → Prometheus)"
    local metrics_token web_domain
    metrics_token="$(service_variable "$WEB_SERVICE" METRICS_TOKEN)" || metrics_token=""
    web_domain="$(service_domain "$WEB_SERVICE")" || web_domain=""
    if [[ -z "$metrics_token" ]]; then
      miss "METRICS_TOKEN is not set on '$WEB_SERVICE' — the scrape endpoint stays closed" \
           "railway variable set METRICS_TOKEN=\"\$(openssl rand -hex 32)\" --service $WEB_SERVICE"
    else
      ok "METRICS_TOKEN is set on '$WEB_SERVICE'"
    fi
    if [[ -n "$web_domain" ]]; then
      local unauth
      unauth="$(http_code "https://$web_domain/actuator/prometheus")"
      case "$unauth" in
        # 302 = Spring Security redirecting the anonymous request to /login
        301|302|401|403) ok "scrape endpoint rejects unauthenticated requests ($unauth)" ;;
        200)             miss "scrape endpoint is OPEN without a token — check the prod security config" ;;
        *)               warn "scrape endpoint returned $unauth without a token (expected 302/401/403)" ;;
      esac
      if [[ -n "$metrics_token" ]]; then
        case "$(http_code "https://$web_domain/actuator/prometheus" -H "Authorization: Bearer $metrics_token")" in
          200) ok "scrape endpoint serves metrics with the token" ;;
          *)   miss "scrape endpoint does not accept METRICS_TOKEN — Prometheus cannot scrape" ;;
        esac
      fi
    else
      warn "'$WEB_SERVICE' has no public domain; skipping live scrape-endpoint checks"
    fi
  fi

  section "Local stack (optional)"
  if have_cmd docker; then
    ok "docker installed"
    [[ -f "$TOKEN_FILE" ]] && ok "$TOKEN_FILE exists" \
      || warn "$TOKEN_FILE missing — 'npm run obs:up' creates it automatically"
    local running
    running="$(compose ps --services --status running 2>/dev/null || true)"
    if [[ -n "$running" ]]; then
      ok "local stack running: $(tr '\n' ' ' <<<"$running")— Prometheus http://localhost:9090, Grafana http://localhost:3000"
    else
      warn "local stack not running — start it with: npm run obs:up"
    fi
  else
    warn "docker not installed — local stack unavailable (Railway stack unaffected)"
  fi

  if (( MISSING_REQUIRED )); then
    section "Next steps"
    printf '  %s\n' "${NEXT_STEPS[@]}"
    exit 1
  fi
  section "All good"
}

# --- open --------------------------------------------------------------------

cmd_open() {
  require_railway
  local domain
  domain="$(service_domain "$GRAFANA_SERVICE")" || domain=""
  if [[ -z "$domain" ]]; then
    echo "no public domain on '$GRAFANA_SERVICE' yet — generating one (port 3000)..."
    railway domain -s "$GRAFANA_SERVICE" -p 3000 >/dev/null
    domain="$(service_domain "$GRAFANA_SERVICE")"
    [[ -n "$domain" ]] || die "domain generation did not stick — check: railway domain list -s $GRAFANA_SERVICE"
  fi

  local url="https://$domain"
  echo "Grafana: $url"
  wait_for_http "Grafana" "$url/api/health" 120 || true

  if [[ "$(http_code "$url/api/org" -u admin:admin)" == "200" ]]; then
    printf '%s\n' \
      "" \
      "${YEL}First login:${RST} the URL above is public from now on." \
      "  Log in as admin/admin ${BOLD}right away${RST} — Grafana forces a password change," \
      "  and the new password persists on the grafana volume across redeploys."
  fi

  if interactive && have_cmd open; then
    open "$url"
  fi
}

# --- local stack -------------------------------------------------------------

cmd_up() {
  need_cmd docker
  ensure_token_file
  compose up -d
  wait_for_http "Prometheus" "http://localhost:9090/-/ready" 60 || true
  wait_for_http "Grafana" "http://localhost:3000/api/health" 60 || true
  echo
  echo "Prometheus: http://localhost:9090"
  echo "Grafana:    http://localhost:3000  (admin/admin)"
  echo "Prometheus scrapes the app on localhost:8080 — start it with scripts/dev-server.sh start"
}

cmd_down()   { need_cmd docker; compose down; }
cmd_status() { need_cmd docker; compose ps; }

# --- dispatch ----------------------------------------------------------------

case "$ACTION" in
  doctor) cmd_doctor ;;
  open)   cmd_open ;;
  up)     cmd_up ;;
  down)   cmd_down ;;
  status) cmd_status ;;
  *) die "unknown action '$ACTION' (expected: doctor | open | up | down | status)" ;;
esac
