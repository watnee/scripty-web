#!/usr/bin/env bash
# Provision and rotate the Cloudflare API token CI uses (CLOUDFLARE_API_TOKEN)
# so nobody has to hand-create tokens in the Cloudflare dashboard.
#
# The token is minted via the Cloudflare API, scoped to one account with the
# minimum deploy permissions (Workers Scripts write, Containers/Cloudchamber
# write, Account Settings read), then pushed straight into GitHub Actions
# secrets. The secret value is never written to disk.
#
# Local dev needs no token at all — `npx wrangler login` (OAuth) covers
# `npm run cf:dev` / `npm run cf:deploy` from a laptop.
#
# Usage:
#   ./scripts/cf-token.sh setup           # create the token (or roll it if it exists) + push to GitHub
#   ./scripts/cf-token.sh rotate          # roll the existing token's value + push to GitHub
#   ./scripts/cf-token.sh status          # token + GitHub secret state
#   ./scripts/cf-token.sh revoke          # delete the token in Cloudflare
#   ./scripts/cf-token.sh seed-bootstrap  # mint a token-minting token + push to GitHub, so CI
#                                         # can self-provision deploy tokens (see ci-resolve)
#   ./scripts/cf-token.sh ci-resolve      # CI-only: print a working deploy token to stdout —
#                                         # verifies $CLOUDFLARE_API_TOKEN, else mints a
#                                         # short-lived one from bootstrap creds (no prompts)
#
# Bootstrap credentials (pick one; needed for setup/rotate/revoke and richer status):
#   CLOUDFLARE_API_KEY + CLOUDFLARE_EMAIL   Global API Key — already exists on every
#                                           account, nothing to create: dashboard →
#                                           My Profile → API Tokens → Global API Key → View
#   CLOUDFLARE_BOOTSTRAP_TOKEN              an API token that has "API Tokens Write"
# If neither is set and the shell is interactive, the script prompts (input
# hidden, used for this run only, never stored).
#
# Env overrides:
#   CF_TOKEN_NAME          token name in Cloudflare (default: scripty-ci-deploy)
#   CF_TOKEN_TTL_HOURS     lifetime of ci-resolve's ephemeral tokens (default: 4)
#   CLOUDFLARE_ACCOUNT_ID  skip account autodetection
#   GH_SECRET_NAME         GitHub secret to set (default: CLOUDFLARE_API_TOKEN)
#   PRINT_TOKEN=1          print the new token instead of pushing to GitHub
set -euo pipefail

ACTION="${1:-setup}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
API="https://api.cloudflare.com/client/v4"
TOKEN_NAME="${CF_TOKEN_NAME:-scripty-ci-deploy}"
GH_SECRET_NAME="${GH_SECRET_NAME:-CLOUDFLARE_API_TOKEN}"
ACCOUNT_ID="${CLOUDFLARE_ACCOUNT_ID:-}"

BOOTSTRAP_TOKEN="${CLOUDFLARE_BOOTSTRAP_TOKEN:-}"
GLOBAL_KEY="${CLOUDFLARE_API_KEY:-}"
GLOBAL_EMAIL="${CLOUDFLARE_EMAIL:-}"

die() {
  echo "error: $*" >&2
  exit 1
}

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

need_cmd curl
need_cmd python3

have_bootstrap() {
  [[ -n "$BOOTSTRAP_TOKEN" || (-n "$GLOBAL_KEY" && -n "$GLOBAL_EMAIL") ]]
}

ensure_bootstrap() {
  have_bootstrap && return 0
  if [[ ! -t 0 ]]; then
    die "no bootstrap credentials. Set CLOUDFLARE_API_KEY + CLOUDFLARE_EMAIL (Global API Key: dashboard → My Profile → API Tokens → Global API Key → View) or CLOUDFLARE_BOOTSTRAP_TOKEN (token with 'API Tokens Write')."
  fi
  echo "One-time bootstrap credentials (used for this run only, never stored)."
  echo "Global API Key: Cloudflare dashboard → My Profile → API Tokens → Global API Key → View"
  read -rp "Cloudflare account email: " GLOBAL_EMAIL
  read -rsp "Global API Key: " GLOBAL_KEY
  echo
  [[ -n "$GLOBAL_EMAIL" && -n "$GLOBAL_KEY" ]] || die "email and key are both required"
}

# cf_api METHOD PATH [JSON_BODY] — auth headers go via curl config on stdin so
# secrets never appear in argv / process list.
cf_api() {
  local method="$1" path="$2" body="${3:-}"
  local args=(-sS -K - -X "$method" "${API}${path}" -H "Content-Type: application/json")
  [[ -n "$body" ]] && args+=(--data "$body")
  if [[ -n "$BOOTSTRAP_TOKEN" ]]; then
    curl "${args[@]}" <<EOF
header = "Authorization: Bearer ${BOOTSTRAP_TOKEN}"
EOF
  else
    curl "${args[@]}" <<EOF
header = "X-Auth-Email: ${GLOBAL_EMAIL}"
header = "X-Auth-Key: ${GLOBAL_KEY}"
EOF
  fi
}

# api_result JSON [JQ-ish python expr] — dies with Cloudflare's error messages
# when success=false, else prints the raw JSON for further parsing.
api_ok() {
  python3 - "$1" <<'PY'
import json, sys
try:
    data = json.loads(sys.argv[1])
except Exception:
    sys.exit("Cloudflare API returned non-JSON response")
if not data.get("success"):
    errs = data.get("errors") or [{"message": "unknown Cloudflare API error"}]
    msgs = "; ".join(f"{e.get('code', '?')}: {e.get('message', '?')}" for e in errs)
    sys.exit(f"Cloudflare API error — {msgs}")
PY
}

resolve_account_id() {
  [[ -n "$ACCOUNT_ID" ]] && return 0
  local resp
  resp="$(cf_api GET "/accounts?per_page=50")"
  api_ok "$resp" || die "could not list accounts (set CLOUDFLARE_ACCOUNT_ID to skip autodetection)"
  ACCOUNT_ID="$(
    python3 - "$resp" <<'PY'
import json, sys
accounts = json.loads(sys.argv[1]).get("result") or []
if len(accounts) == 1:
    print(accounts[0]["id"])
elif not accounts:
    sys.exit("no Cloudflare accounts visible to these credentials")
else:
    listing = "\n".join(f"  {a['id']}  {a.get('name','')}" for a in accounts)
    sys.exit(f"multiple accounts visible — set CLOUDFLARE_ACCOUNT_ID to one of:\n{listing}")
PY
  )" || exit 1
  echo "Account: ${ACCOUNT_ID}" >&2
}

# Find the existing deploy token id by name (empty output if absent).
find_token_id() {
  local page=1 resp line found count
  while :; do
    resp="$(cf_api GET "/user/tokens?per_page=50&page=${page}")" || return 1
    api_ok "$resp" || return 1
    line="$(
      python3 - "$resp" "$TOKEN_NAME" <<'PY'
import json, sys
result = json.loads(sys.argv[1]).get("result") or []
match = next((t["id"] for t in result if t.get("name") == sys.argv[2]), "")
print(f"{match}\t{len(result)}")
PY
    )"
    found="$(printf '%s' "$line" | cut -f1)"
    count="$(printf '%s' "$line" | cut -f2)"
    if [[ -n "$found" ]]; then
      printf '%s\n' "$found"
      return 0
    fi
    if [[ "$count" -lt 50 || "$page" -ge 10 ]]; then
      return 0
    fi
    page=$((page + 1))
  done
}

# Build the create-token body: fetch permission groups and match by name so we
# never hardcode ids (they differ across API versions).
# Args: NAME [EXPIRES_ON] — EXPIRES_ON is an RFC3339 UTC timestamp or empty.
build_create_body() {
  local name="$1" expires_on="${2:-}"
  local groups_resp
  groups_resp="$(cf_api GET "/user/tokens/permission_groups")" || return 1
  api_ok "$groups_resp" || return 1
  python3 - "$groups_resp" "$name" "$ACCOUNT_ID" "$expires_on" <<'PY'
import json, sys

groups = json.loads(sys.argv[1]).get("result") or []
token_name, account_id, expires_on = sys.argv[2], sys.argv[3], sys.argv[4]
by_name = {}
for g in groups:
    by_name.setdefault((g.get("name") or "").lower(), g["id"])

def pick(*names):
    for n in names:
        gid = by_name.get(n.lower())
        if gid:
            return {"id": gid, "name": n}
    return None

chosen = []

workers = pick("Workers Scripts Write", "Workers Scripts Edit")
if not workers:
    sys.exit("permission group 'Workers Scripts Write' not found — cannot build deploy token")
chosen.append(workers)

# Containers deploys ride the Cloudchamber API; include every container-ish
# write group that exists so the token survives Cloudflare renames.
container_groups = [
    pick("Containers Write", "Containers Edit"),
    pick("Cloudchamber Write", "Cloudchamber Edit"),
]
container_groups = [g for g in container_groups if g]
if not container_groups:
    print("warn: no Containers/Cloudchamber permission group found — "
          "container deploys may fail with this token", file=sys.stderr)
chosen.extend(container_groups)

settings = pick("Account Settings Read")
if not settings:
    sys.exit("permission group 'Account Settings Read' not found")
chosen.append(settings)

body = {
    "name": token_name,
    "policies": [{
        "effect": "allow",
        "resources": {f"com.cloudflare.api.account.{account_id}": "*"},
        "permission_groups": [{"id": g["id"]} for g in chosen],
    }],
}
if expires_on:
    body["expires_on"] = expires_on
print("granting: " + ", ".join(g["name"] for g in chosen), file=sys.stderr)
print(json.dumps(body))
PY
}

extract_token_value() {
  python3 - "$1" <<'PY'
import json, sys
result = json.loads(sys.argv[1]).get("result")
value = result if isinstance(result, str) else (result or {}).get("value") or ""
if not value:
    sys.exit("Cloudflare response did not include a token value")
print(value)
PY
}

# Non-fatal probe: is this token value accepted and active right now?
check_token_active() {
  local value="$1" resp
  resp="$(
    curl -sS -K - "${API}/user/tokens/verify" <<EOF
header = "Authorization: Bearer ${value}"
EOF
  )" || return 1
  python3 - "$resp" <<'PY'
import json, sys
try:
    data = json.loads(sys.argv[1])
except Exception:
    sys.exit(1)
ok = data.get("success") and (data.get("result") or {}).get("status") == "active"
sys.exit(0 if ok else 1)
PY
}

verify_token() {
  local value="$1" resp
  resp="$(
    curl -sS -K - "${API}/user/tokens/verify" <<EOF
header = "Authorization: Bearer ${value}"
EOF
  )"
  api_ok "$resp"
  echo "Token verified (status: $(
    python3 - "$resp" <<'PY'
import json, sys
print((json.loads(sys.argv[1]).get("result") or {}).get("status", "?"))
PY
  ))"
}

deliver_token() {
  local value="$1"
  if [[ "${PRINT_TOKEN:-0}" == "1" ]]; then
    echo ""
    echo "${GH_SECRET_NAME}=${value}"
    echo "(PRINT_TOKEN=1 — not pushed to GitHub; paste it wherever it is needed)"
    return 0
  fi
  need_cmd gh
  # No --body flag: gh reads the value from stdin ("--body -" would store a literal "-").
  printf '%s' "$value" | gh secret set "$GH_SECRET_NAME" --app actions
  echo "GitHub secret ${GH_SECRET_NAME} updated."
  if [[ -n "$ACCOUNT_ID" ]]; then
    printf '%s' "$ACCOUNT_ID" | gh secret set CLOUDFLARE_ACCOUNT_ID --app actions
    echo "GitHub secret CLOUDFLARE_ACCOUNT_ID updated."
  fi
}

create_token() {
  local body resp value
  body="$(build_create_body "$TOKEN_NAME")"
  echo "Creating Cloudflare API token '${TOKEN_NAME}'..."
  resp="$(cf_api POST "/user/tokens" "$body")"
  api_ok "$resp"
  value="$(extract_token_value "$resp")"
  verify_token "$value"
  deliver_token "$value"
}

roll_token() {
  local token_id="$1" resp value
  echo "Rolling value of existing token '${TOKEN_NAME}' (${token_id})..."
  resp="$(cf_api PUT "/user/tokens/${token_id}/value" '{}')"
  api_ok "$resp"
  value="$(extract_token_value "$resp")"
  verify_token "$value"
  deliver_token "$value"
}

# Delete expired ephemeral tokens left over from previous CI runs (best effort).
cleanup_expired_ephemerals() {
  local resp ids id
  resp="$(cf_api GET "/user/tokens?per_page=50")" || return 0
  ids="$(
    python3 - "$resp" "${TOKEN_NAME}-ephemeral-" <<'PY'
import datetime, json, sys
now = datetime.datetime.now(datetime.timezone.utc)
for t in json.loads(sys.argv[1]).get("result") or []:
    if not (t.get("name") or "").startswith(sys.argv[2]):
        continue
    expires = t.get("expires_on") or ""
    try:
        when = datetime.datetime.fromisoformat(expires.replace("Z", "+00:00"))
    except ValueError:
        continue
    if when < now:
        print(t["id"])
PY
  )" || return 0
  for id in $ids; do
    cf_api DELETE "/user/tokens/${id}" >/dev/null 2>&1 &&
      echo "cleaned up expired ephemeral token ${id}" >&2 || true
  done
}

# Mint a short-lived deploy token and print ONLY its value to stdout.
mint_ephemeral() {
  local ttl_hours="${CF_TOKEN_TTL_HOURS:-4}"
  local name="${TOKEN_NAME}-ephemeral-${GITHUB_RUN_ID:-$(date +%s)}"
  local expires_on body resp value
  expires_on="$(
    python3 - "$ttl_hours" <<'PY'
import datetime, sys
when = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=float(sys.argv[1]))
print(when.strftime("%Y-%m-%dT%H:%M:%SZ"))
PY
  )"
  cleanup_expired_ephemerals
  echo "Minting ephemeral deploy token '${name}' (expires ${expires_on})..." >&2
  body="$(build_create_body "$name" "$expires_on")" || return 1
  resp="$(cf_api POST "/user/tokens" "$body")" || return 1
  api_ok "$resp" || return 1
  value="$(extract_token_value "$resp")"
  verify_token "$value" >&2
  printf '%s\n' "$value"
}

# CI entry point: print a working deploy token to stdout, no prompts.
# 1. $CLOUDFLARE_API_TOKEN (the stored secret), if it still verifies as active.
# 2. Otherwise mint a short-lived token from bootstrap creds.
do_ci_resolve() {
  if [[ -n "${CLOUDFLARE_API_TOKEN:-}" ]]; then
    if check_token_active "$CLOUDFLARE_API_TOKEN"; then
      echo "stored CLOUDFLARE_API_TOKEN is active — using it" >&2
      printf '%s\n' "$CLOUDFLARE_API_TOKEN"
      return 0
    fi
    echo "stored CLOUDFLARE_API_TOKEN is missing/inactive — trying bootstrap mint" >&2
  else
    echo "no stored CLOUDFLARE_API_TOKEN — trying bootstrap mint" >&2
  fi
  have_bootstrap || die "no CLOUDFLARE_BOOTSTRAP_TOKEN (or Global API Key) available — cannot self-provision. Seed one with: ./scripts/cf-token.sh seed-bootstrap"
  [[ -n "$ACCOUNT_ID" ]] || resolve_account_id
  mint_ephemeral
}

# Mint the long-lived token-minting token CI uses to self-provision deploy
# tokens, and store it as the CLOUDFLARE_BOOTSTRAP_TOKEN GitHub secret.
# Needs the Global API Key (it must read /user and create tokens).
do_seed_bootstrap() {
  ensure_bootstrap
  resolve_account_id
  local user_resp user_id
  user_resp="$(cf_api GET "/user")"
  api_ok "$user_resp" || die "could not read /user — seed-bootstrap needs the Global API Key (CLOUDFLARE_API_KEY + CLOUDFLARE_EMAIL)"
  user_id="$(
    python3 - "$user_resp" <<'PY'
import json, sys
uid = (json.loads(sys.argv[1]).get("result") or {}).get("id") or ""
if not uid:
    sys.exit("no user id in /user response")
print(uid)
PY
  )"

  local groups_resp body resp value
  groups_resp="$(cf_api GET "/user/tokens/permission_groups")"
  api_ok "$groups_resp"
  body="$(
    python3 - "$groups_resp" "${TOKEN_NAME}-minter" "$ACCOUNT_ID" "$user_id" <<'PY'
import json, sys

groups = json.loads(sys.argv[1]).get("result") or []
token_name, account_id, user_id = sys.argv[2], sys.argv[3], sys.argv[4]
by_name = {}
for g in groups:
    by_name.setdefault((g.get("name") or "").lower(), g["id"])

def pick(*names):
    for n in names:
        gid = by_name.get(n.lower())
        if gid:
            return {"id": gid, "name": n}
    return None

tokens_write = pick("API Tokens Write", "API Tokens Edit")
if not tokens_write:
    sys.exit("permission group 'API Tokens Write' not found")
settings_read = pick("Account Settings Read")
if not settings_read:
    sys.exit("permission group 'Account Settings Read' not found")

body = {
    "name": token_name,
    "policies": [
        {
            "effect": "allow",
            "resources": {f"com.cloudflare.api.user.{user_id}": "*"},
            "permission_groups": [{"id": tokens_write["id"]}],
        },
        {
            "effect": "allow",
            "resources": {f"com.cloudflare.api.account.{account_id}": "*"},
            "permission_groups": [{"id": settings_read["id"]}],
        },
    ],
}
print(f"granting: {tokens_write['name']} (user) + {settings_read['name']} (account)",
      file=sys.stderr)
print(json.dumps(body))
PY
  )"
  echo "Creating bootstrap token '${TOKEN_NAME}-minter' (lets CI mint its own deploy tokens)..."
  resp="$(cf_api POST "/user/tokens" "$body")"
  api_ok "$resp"
  value="$(extract_token_value "$resp")"
  if [[ "${PRINT_TOKEN:-0}" == "1" ]]; then
    echo ""
    echo "CLOUDFLARE_BOOTSTRAP_TOKEN=${value}"
    echo "(PRINT_TOKEN=1 — not pushed to GitHub)"
  else
    need_cmd gh
    printf '%s' "$value" | gh secret set CLOUDFLARE_BOOTSTRAP_TOKEN --app actions
    printf '%s' "$ACCOUNT_ID" | gh secret set CLOUDFLARE_ACCOUNT_ID --app actions
    echo "GitHub secrets CLOUDFLARE_BOOTSTRAP_TOKEN + CLOUDFLARE_ACCOUNT_ID updated."
  fi
  echo ""
  echo "Deploys now self-provision: if CLOUDFLARE_API_TOKEN is ever missing or dead,"
  echo "CI mints a short-lived deploy token on the spot. Nothing left to do by hand."
  echo "Revoke any time: CF_TOKEN_NAME=${TOKEN_NAME}-minter $0 revoke"
}

do_setup() {
  ensure_bootstrap
  resolve_account_id
  local token_id
  token_id="$(find_token_id)" || die "could not list Cloudflare tokens with these credentials"
  if [[ -n "$token_id" ]]; then
    roll_token "$token_id"
  else
    create_token
  fi
  echo ""
  echo "Done. CI deploys now authenticate with the freshly minted '${TOKEN_NAME}' token."
  echo "Optional, for fully self-healing deploys: $0 seed-bootstrap"
  echo "Local dev never needs a token: npx wrangler login"
}

do_rotate() {
  ensure_bootstrap
  resolve_account_id
  local token_id
  token_id="$(find_token_id)" || die "could not list Cloudflare tokens with these credentials"
  [[ -n "$token_id" ]] || die "no token named '${TOKEN_NAME}' found — run: $0 setup"
  roll_token "$token_id"
}

do_revoke() {
  ensure_bootstrap
  local token_id resp
  token_id="$(find_token_id)" || die "could not list Cloudflare tokens with these credentials"
  [[ -n "$token_id" ]] || die "no token named '${TOKEN_NAME}' found"
  resp="$(cf_api DELETE "/user/tokens/${token_id}")"
  api_ok "$resp"
  echo "Deleted Cloudflare token '${TOKEN_NAME}' (${token_id})."
  echo "Note: the GitHub secret ${GH_SECRET_NAME} still holds the (now dead) value."
}

do_status() {
  echo "Cloudflare CI token status"
  echo "  token name:   ${TOKEN_NAME}"
  if have_bootstrap; then
    local token_id
    if ! token_id="$(find_token_id)"; then
      echo "  cloudflare:   (API error — check bootstrap credentials)"
    elif [[ -n "$token_id" ]]; then
      local resp
      resp="$(cf_api GET "/user/tokens/${token_id}")"
      api_ok "$resp"
      python3 - "$resp" <<'PY'
import json, sys
t = json.loads(sys.argv[1]).get("result") or {}
print(f"  cloudflare:   present (id={t.get('id','?')}, status={t.get('status','?')}, modified={t.get('modified_on','?')})")
PY
    else
      echo "  cloudflare:   ABSENT — run: $0 setup"
    fi
  else
    echo "  cloudflare:   (unknown — no bootstrap credentials; see header of this script)"
  fi
  if command -v gh >/dev/null 2>&1; then
    local name
    for name in "$GH_SECRET_NAME" CLOUDFLARE_ACCOUNT_ID; do
      if gh secret list 2>/dev/null | awk '{print $1}' | grep -qx "$name"; then
        printf '  github:       %-24s present\n' "$name"
      else
        printf '  github:       %-24s MISSING\n' "$name"
      fi
    done
  else
    echo "  github:       (gh not installed)"
  fi
  echo ""
  echo "Setup/rotate: $0 setup   (prompts for Global API Key if env vars unset)"
  echo "Local dev:    npx wrangler login   (no API token needed)"
}

cd "$ROOT"

case "$ACTION" in
  setup)          do_setup ;;
  rotate)         do_rotate ;;
  status)         do_status ;;
  revoke)         do_revoke ;;
  seed-bootstrap) do_seed_bootstrap ;;
  ci-resolve)     do_ci_resolve ;;
  *)
    cat >&2 <<EOF
usage: $0 <setup|rotate|status|revoke|seed-bootstrap|ci-resolve>

setup           create (or roll) the '${TOKEN_NAME}' token and push it to GitHub secrets
rotate          roll the existing token's value and push it to GitHub secrets
status          show Cloudflare token + GitHub secret state
revoke          delete the token in Cloudflare (CF_TOKEN_NAME overrides which one)
seed-bootstrap  mint a token-minting token + push to GitHub so CI self-provisions
ci-resolve      (CI) print a working deploy token: stored secret if active, else
                mint a short-lived one from CLOUDFLARE_BOOTSTRAP_TOKEN
EOF
    exit 2
    ;;
esac
