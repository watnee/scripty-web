#!/usr/bin/env bash
# From-scratch deploy bootstrap for Scripty: Railway (app + MySQL) and
# Cloudflare Containers, plus the GitHub Actions secrets CI needs.
#
# One command per stage, all idempotent — safe to re-run any time:
#   ./scripts/bootstrap-deploy.sh doctor      # read-only: what is set up, what is missing, how to fix it
#   ./scripts/bootstrap-deploy.sh github      # create/verify the repo, enable Actions, 'production' environment
#   ./scripts/bootstrap-deploy.sh railway     # link/create project, apply IaC, domain, backups, RAILWAY_SERVICE_ID
#   ./scripts/bootstrap-deploy.sh secrets     # push GitHub Actions secrets (Railway token, Cloudflare token, MYSQL* fallback)
#   ./scripts/bootstrap-deploy.sh cloudflare  # first wrangler deploy (Worker + container image + MySQL secrets)
#   ./scripts/bootstrap-deploy.sh ci          # dispatch the CI/CD workflow on main and watch it (deploys both platforms)
#   ./scripts/bootstrap-deploy.sh email       # production email via Cloudflare Email Sending: EMAIL_WORKER_URL/SECRET + MAIL_FROM
#   ./scripts/bootstrap-deploy.sh verify      # curl /health on the deployed platforms
#   ./scripts/bootstrap-deploy.sh all         # github → railway → secrets → cloudflare (or ci) → email → verify
#
# Reuses the focused scripts rather than duplicating them:
#   scripts/cf-token.sh                Cloudflare CI token mint/rotate (no dashboard tokens)
#   scripts/sync-railway-cloudflare.sh Railway MySQL TCP proxy → Cloudflare/GitHub secrets
#   scripts/railway-mysql-backups.sh   MySQL volume snapshot schedules
#
# Requirements by stage (doctor reports all of this):
#   railway CLI (logged in)            railway / secrets / verify
#   gh CLI (authenticated)             secrets, doctor's GitHub checks
#   node + npm ci                      cloudflare (wrangler is a devDependency)
#   Docker running                     cloudflare (builds the container image)
#
# The only credential that cannot be minted from a CLI is the Railway project
# token (dashboard → Project → Settings → Tokens); `secrets` prompts for it
# once and pushes it to GitHub, everything else is fully automated.
set -euo pipefail

ACTION="${1:-doctor}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

WEB_SERVICE="${WEB_SERVICE:-web}"
MYSQL_SERVICE="${MYSQL_SERVICE:-MySQL}"
WRANGLER_CONFIG="cloudflare/wrangler.jsonc"

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

# Prints "<project_id>\t<project_name>\t<env>\t<web_id>\t<mysql_id>" or fails.
railway_context() {
  local status_json
  status_json="$(railway status --json 2>/dev/null)" || return 1
  RAILWAY_STATUS_JSON="$status_json" python3 - "$WEB_SERVICE" "$MYSQL_SERVICE" <<'PY'
import json, os, sys
data = json.loads(os.environ["RAILWAY_STATUS_JSON"])
web_name, mysql_name = sys.argv[1], sys.argv[2]
project = data.get("id") or ""
name = data.get("name") or ""
env_name, web_id, mysql_id = "", "", ""
for env_edge in (data.get("environments") or {}).get("edges", []):
    env = env_edge.get("node") or {}
    if not env_name or env.get("name") == "production":
        env_name = env.get("name") or env_name
        for svc_edge in (env.get("serviceInstances") or {}).get("edges", []):
            svc = svc_edge.get("node") or {}
            if svc.get("serviceName") == web_name:
                web_id = svc.get("serviceId") or ""
            if svc.get("serviceName") == mysql_name:
                mysql_id = svc.get("serviceId") or ""
if not project:
    sys.exit(1)
print(f"{project}\t{name}\t{env_name}\t{web_id}\t{mysql_id}")
PY
}

railway_web_domain() {
  railway domain list --service "$WEB_SERVICE" --json 2>/dev/null | python3 -c '
import json, sys
try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(0)
if isinstance(data, dict):
    data = (data.get("serviceDomains") or []) + (data.get("customDomains") or []) or data.get("domains") or []
for d in data if isinstance(data, list) else []:
    domain = d.get("domain") if isinstance(d, dict) else None
    if domain:
        print(domain)
        break
'
}

gh_secret_present() {
  gh secret list 2>/dev/null | awk '{print $1}' | grep -qx "$1"
}

gh_ready() {
  have_cmd gh && gh auth status >/dev/null 2>&1
}

# Prints owner/name of the repo this directory maps to, or fails.
gh_repo() {
  gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null
}

# Repo the Railway IaC builds from, e.g. github("watnee/scripty").
iac_source_repo() {
  sed -n 's/.*github("\([^"]*\)").*/\1/p' .railway/railway.ts | head -n1
}

# --- Email helpers (Cloudflare Email Sending) --------------------------------

# Prints the value of one variable on the Railway web service (raw), or "".
railway_web_var() {
  railway variable list --service "$WEB_SERVICE" --json 2>/dev/null \
    | RAILWAY_VAR_NAME="$1" python3 -c 'import json,os,sys; d=json.load(sys.stdin) or {}; print(d.get(os.environ["RAILWAY_VAR_NAME"]) or "")' 2>/dev/null || true
}

# Domain of "Name <user@domain>" or "user@domain"; empty if unparsable.
mail_from_domain() {
  printf '%s' "$1" | sed -n 's/.*@\([A-Za-z0-9._-]*\).*/\1/p' | tr '[:upper:]' '[:lower:]'
}

# Domains onboarded to Cloudflare Email Sending, one per line (may be empty).
email_sending_domains() {
  npx wrangler email sending list 2>/dev/null \
    | sed -n 's/^│ *[^│]*│ *\([a-z0-9.-]*\.[a-z]*\) *│ *yes.*/\1/p' | sort -u
}

# HTTP status of an unauthenticated POST to the email Worker endpoint.
# 401 = deployed + auth enforced (healthy), 503 = EMAIL_PROXY_SECRET missing.
email_worker_probe() {
  curl -s -o /dev/null -w '%{http_code}' --max-time 20 -X POST \
    -H 'Content-Type: application/json' -d '{}' "$1" 2>/dev/null || true
}

wrangler_local() {
  [[ -x node_modules/.bin/wrangler ]]
}

# --- doctor ------------------------------------------------------------------

do_doctor() {
  section "Tools"
  local cmd
  for cmd in railway gh python3 curl; do
    if have_cmd "$cmd"; then ok "$cmd"; else miss "$cmd not installed" "install $cmd"; fi
  done
  if have_cmd node && have_cmd npx; then
    if wrangler_local; then
      ok "node + wrangler (node_modules)"
    else
      miss "wrangler not installed locally" "npm ci"
    fi
  else
    miss "node/npx not installed" "install Node.js 22+"
  fi
  if have_cmd docker && docker info >/dev/null 2>&1; then
    ok "docker (running)"
  elif have_cmd docker; then
    warn "docker installed but not running — needed only for 'cloudflare' (container image build)"
  else
    warn "docker not installed — needed only for 'cloudflare' (CI can deploy Cloudflare without it)"
  fi

  section "Auth"
  if have_cmd railway; then
    if railway_logged_in; then ok "railway ($(railway whoami 2>/dev/null || echo 'RAILWAY_TOKEN'))"; else miss "railway not logged in" "railway login"; fi
  fi
  if have_cmd gh; then
    if gh auth status >/dev/null 2>&1; then ok "gh ($(gh api user -q .login 2>/dev/null || echo authenticated))"; else miss "gh not authenticated" "gh auth login"; fi
  fi
  if wrangler_local; then
    if [[ -n "${CLOUDFLARE_API_TOKEN:-}" ]] || npx wrangler whoami >/dev/null 2>&1; then
      ok "wrangler (OAuth or CLOUDFLARE_API_TOKEN)"
    else
      miss "wrangler not logged in" "npx wrangler login"
    fi
  fi

  section "GitHub repo & Actions"
  if gh_ready; then
    local repo=""
    if repo="$(gh_repo)"; then
      ok "repo: ${repo}"
      local actions_enabled
      actions_enabled="$(gh api "repos/${repo}/actions/permissions" -q .enabled 2>/dev/null || echo unknown)"
      case "$actions_enabled" in
        true)  ok "Actions enabled" ;;
        false) miss "Actions disabled" "./scripts/bootstrap-deploy.sh github" ;;
        *)     warn "could not read Actions state" ;;
      esac
      if gh api "repos/${repo}/environments/production" >/dev/null 2>&1; then
        ok "environment 'production' exists"
      else
        warn "environment 'production' absent — auto-created on first deploy, or: ./scripts/bootstrap-deploy.sh github"
      fi
      local iac_repo
      iac_repo="$(iac_source_repo || true)"
      if [[ -n "$iac_repo" && "$iac_repo" != "$repo" ]]; then
        warn "IaC builds from github(\"${iac_repo}\") but this repo is ${repo} — update .railway/railway.ts"
      fi
      local last_run
      last_run="$(gh run list --workflow 'CI/CD' --branch main --limit 1 --json status,conclusion -q '.[0] | (.conclusion // .status)' 2>/dev/null || true)"
      case "$last_run" in
        success)      ok "last CI/CD run on main: success" ;;
        "")           warn "no CI/CD runs on main yet — first deploy: ./scripts/bootstrap-deploy.sh ci" ;;
        *)            warn "last CI/CD run on main: ${last_run} (gh run list --workflow CI/CD)" ;;
      esac
    else
      miss "no GitHub repo for this directory (origin remote missing or repo not created)" "./scripts/bootstrap-deploy.sh github"
    fi
  else
    warn "gh unavailable — skipped repo checks"
  fi

  section "Railway project"
  local ctx="" project_id project_name env_name web_id mysql_id
  if have_cmd railway && railway_logged_in && ctx="$(railway_context)"; then
    IFS=$'\t' read -r project_id project_name env_name web_id mysql_id <<<"$ctx"
    ok "linked: ${project_name} (${project_id}) env=${env_name}"
    if [[ -n "$web_id" ]]; then ok "service '${WEB_SERVICE}' (${web_id})"; else miss "service '${WEB_SERVICE}' missing" "./scripts/bootstrap-deploy.sh railway   # railway config apply"; fi
    if [[ -n "$mysql_id" ]]; then ok "service '${MYSQL_SERVICE}' (${mysql_id})"; else miss "service '${MYSQL_SERVICE}' missing" "./scripts/bootstrap-deploy.sh railway   # railway config apply"; fi
    local domain
    domain="$(railway_web_domain || true)"
    if [[ -n "$domain" ]]; then ok "domain: https://${domain}"; else warn "no domain on '${WEB_SERVICE}' — bootstrap 'railway' creates one"; fi
  else
    miss "no linked Railway project" "./scripts/bootstrap-deploy.sh railway   # railway link or railway init"
  fi

  section "Email (Cloudflare Email Sending, password recovery)"
  if have_cmd railway && railway_logged_in && [[ -n "${ctx:-}" ]]; then
    local worker_url worker_secret mail_from
    worker_url="$(railway_web_var EMAIL_WORKER_URL)"
    worker_secret="$(railway_web_var EMAIL_WORKER_SECRET)"
    mail_from="$(railway_web_var MAIL_FROM)"
    if [[ -z "$worker_url" || -z "$worker_secret" ]]; then
      miss "EMAIL_WORKER_URL/EMAIL_WORKER_SECRET not set on Railway '${WEB_SERVICE}' — password-recovery email is off" "./scripts/bootstrap-deploy.sh email"
    else
      ok "EMAIL_WORKER_URL + EMAIL_WORKER_SECRET set on Railway"
      local probe
      probe="$(email_worker_probe "$worker_url")"
      case "$probe" in
        401) ok "email Worker endpoint live (auth enforced): ${worker_url}" ;;
        503) miss "email Worker endpoint has no EMAIL_PROXY_SECRET" "./scripts/bootstrap-deploy.sh email" ;;
        *)   miss "email Worker endpoint unreachable (HTTP ${probe:-none}): ${worker_url}" "./scripts/bootstrap-deploy.sh cloudflare  # deploy the Worker first" ;;
      esac
    fi
    if [[ -z "$mail_from" ]]; then
      warn "MAIL_FROM unset — falls back to 'Scripty <noreply@localhost>' (Email Sending will reject it)"
    else
      ok "MAIL_FROM: ${mail_from}"
      local from_domain
      from_domain="$(mail_from_domain "$mail_from")"
      if [[ -n "$from_domain" ]] && wrangler_local; then
        if email_sending_domains | grep -qx "$from_domain"; then
          ok "sender domain ${from_domain} onboarded to Cloudflare Email Sending"
        else
          warn "sender domain ${from_domain} not onboarded — run: npx wrangler email sending enable ${from_domain}"
        fi
      fi
    fi
  else
    warn "railway unavailable or project not linked — skipped email checks"
  fi

  section "GitHub Actions secrets (CI deploy)"
  if have_cmd gh && gh auth status >/dev/null 2>&1; then
    local name
    for name in RAILWAY_TOKEN RAILWAY_SERVICE_ID CLOUDFLARE_API_TOKEN CLOUDFLARE_ACCOUNT_ID; do
      if gh_secret_present "$name"; then ok "$name"; else miss "$name missing" "./scripts/bootstrap-deploy.sh secrets"; fi
    done
    for name in CLOUDFLARE_BOOTSTRAP_TOKEN MYSQLHOST MYSQLPASSWORD R2_BUCKET; do
      if gh_secret_present "$name"; then ok "$name (optional)"; else warn "$name absent (optional — see README / docs/BACKUP.md)"; fi
    done
  else
    warn "gh unavailable — skipped secret checks"
  fi

  section "Cloudflare Worker"
  if wrangler_local && { [[ -n "${CLOUDFLARE_API_TOKEN:-}" ]] || npx wrangler whoami >/dev/null 2>&1; }; then
    local cf_secrets
    if cf_secrets="$(npx wrangler secret list --config "$WRANGLER_CONFIG" 2>/dev/null)"; then
      local name missing_cf=0
      for name in MYSQLHOST MYSQLPORT MYSQLUSER MYSQLPASSWORD MYSQLDATABASE; do
        if printf '%s' "$cf_secrets" | python3 -c 'import json,sys; names={x.get("name") for x in json.load(sys.stdin)}; sys.exit(0 if sys.argv[1] in names else 1)' "$name" 2>/dev/null; then
          ok "Worker secret $name"
        else
          missing_cf=1
        fi
      done
      if [[ "$missing_cf" -ne 0 ]]; then
        miss "Worker missing required MySQL secrets (or Worker not deployed yet)" "./scripts/bootstrap-deploy.sh cloudflare"
      fi
      for name in EMAIL_PROXY_SECRET EMAIL_WORKER_URL EMAIL_WORKER_SECRET MAIL_FROM; do
        if printf '%s' "$cf_secrets" | python3 -c 'import json,sys; names={x.get("name") for x in json.load(sys.stdin)}; sys.exit(0 if sys.argv[1] in names else 1)' "$name" 2>/dev/null; then
          ok "Worker secret $name (optional)"
        else
          warn "Worker secret $name absent — no password-recovery email from the Cloudflare instance; fix: ./scripts/bootstrap-deploy.sh email"
        fi
      done
    else
      miss "Worker 'scripty' not deployed (or token lacks access)" "./scripts/bootstrap-deploy.sh cloudflare"
    fi
  else
    warn "wrangler unavailable/not logged in — skipped Worker checks"
  fi

  echo
  if [[ "$MISSING_REQUIRED" -eq 0 ]]; then
    echo "${GRN}${BOLD}All checks passed.${RST} Deploys run from GitHub Actions on main; local: npm run cf:deploy / railway up."
    return 0
  fi
  echo "${BOLD}Next steps (in order):${RST}"
  # bash 3.2 + set -u: expanding an empty array errors, so guard on length.
  if [[ "${#NEXT_STEPS[@]}" -gt 0 ]]; then
    printf '%s\n' "${NEXT_STEPS[@]}" | awk '!seen[$0]++ {print "  " ++n ". " $0}'
  fi
  return 1
}

# --- github ------------------------------------------------------------------

do_github() {
  need_cmd gh
  gh_ready || die "gh not authenticated — run: gh auth login"

  local repo=""
  if repo="$(gh_repo)"; then
    echo "GitHub repo: ${repo}"
  else
    echo "No GitHub repo detected (origin remote missing or repo not created)."
    if ! interactive; then
      die "create one first: gh repo create scripty --private --source=. --remote=origin --push"
    fi
    local name vis
    read -rp "Repository name [scripty]: " name
    name="${name:-scripty}"
    read -rp "Visibility [private/public] (default private): " vis
    [[ "$vis" == "public" ]] || vis="private"
    gh repo create "$name" "--${vis}" --source=. --remote=origin --push
    repo="$(gh_repo)" || die "repo created but 'gh repo view' still fails — check the origin remote"
    echo "Created ${repo} and pushed the current branch."
  fi

  # CI/CD (deploys) and backup-db both run on Actions.
  local actions_enabled
  actions_enabled="$(gh api "repos/${repo}/actions/permissions" -q .enabled 2>/dev/null || echo unknown)"
  if [[ "$actions_enabled" == "false" ]]; then
    gh api --method PUT "repos/${repo}/actions/permissions" \
        -F enabled=true -f allowed_actions=all >/dev/null \
      && echo "Enabled GitHub Actions (was off)." \
      || warn "could not enable Actions — repo Settings → Actions → General"
  else
    echo "GitHub Actions: ${actions_enabled}"
  fi

  # Deploy jobs target this environment; creating it up front means the first
  # run cannot stall on it, and you can add required reviewers for a manual gate.
  if gh api --method PUT "repos/${repo}/environments/production" >/dev/null 2>&1; then
    echo "Environment 'production' ensured (optional approval gate: Settings → Environments → production → required reviewers)."
  else
    warn "could not create the 'production' environment (needs repo admin) — it is auto-created on the first deploy run"
  fi

  local iac_repo
  iac_repo="$(iac_source_repo || true)"
  if [[ -n "$iac_repo" && "$iac_repo" != "$repo" ]]; then
    warn "Railway IaC builds from github(\"${iac_repo}\") but this repo is ${repo} — update .railway/railway.ts before 'railway config apply'"
  fi

  echo
  echo "GitHub stage done. Next: ./scripts/bootstrap-deploy.sh railway"
}

# --- ci ----------------------------------------------------------------------

# First (or any) deploy without local Docker: let Actions build and ship both
# platforms. Requires the 'secrets' stage and the workflow on main.
do_ci() {
  need_cmd gh
  gh_ready || die "gh not authenticated — run: gh auth login"
  gh_repo >/dev/null || die "no GitHub repo — run: ./scripts/bootstrap-deploy.sh github"

  echo "Dispatching CI/CD on main (Verify → Deploy Railway ∥ Deploy Cloudflare) …"
  gh workflow run "CI/CD" --ref main -f reason="bootstrap-deploy.sh ci" \
    || die "dispatch failed — is .github/workflows/ci-cd.yml on main in the remote repo?"

  echo "Waiting for the run to register …"
  sleep 5
  local run_id
  run_id="$(gh run list --workflow 'CI/CD' --branch main --limit 1 --json databaseId -q '.[0].databaseId' 2>/dev/null || true)"
  if [[ -z "$run_id" ]]; then
    warn "could not locate the dispatched run — follow it with: gh run list --workflow CI/CD"
    return 0
  fi
  echo "Watching run ${run_id} (Ctrl-C is safe — the run keeps going on GitHub) …"
  gh run watch "$run_id" --exit-status
  echo
  echo "CI deploy finished. Next: ./scripts/bootstrap-deploy.sh verify"
}

# --- railway -----------------------------------------------------------------

do_railway() {
  need_cmd railway
  need_cmd python3
  railway_logged_in || die "not logged in to Railway — run: railway login"

  if ! railway status >/dev/null 2>&1; then
    echo "No linked Railway project in this directory."
    if ! interactive; then
      die "link one first: 'railway link' (existing) or 'railway init -n scripty' (new), then re-run"
    fi
    local choice
    read -rp "Link an existing project or create a new one? [link/init] " choice
    case "$choice" in
      init) railway init -n scripty ;;
      *) railway link ;;
    esac
  fi

  echo
  echo "Previewing infrastructure changes from .railway/railway.ts …"
  # --decrypt-variables: without it, existing encrypted variables read back as
  # preserve() markers, so the plan re-sets every variable and apply is
  # rejected by the backend ("Invalid RailwayChangeSet patch").
  railway config plan --decrypt-variables || warn "config plan failed — check .railway/railway.ts"
  if interactive; then
    local apply
    read -rp "Apply this plan now (creates/updates web + MySQL + volumes)? [y/N] " apply
    if [[ "$apply" == [yY]* ]]; then
      railway config apply --decrypt-variables
    else
      echo "Skipped apply — run 'railway config apply --decrypt-variables' when ready."
    fi
  else
    echo "Non-interactive: review the plan above and run 'railway config apply --decrypt-variables' yourself."
  fi

  # Public domain for the web service (needed for APP_BASE_URL and verify).
  local domain
  domain="$(railway_web_domain || true)"
  if [[ -z "$domain" ]]; then
    echo "Generating a Railway domain for '${WEB_SERVICE}' …"
    railway domain --service "$WEB_SERVICE" || warn "could not create domain — create one in the dashboard"
    domain="$(railway_web_domain || true)"
  fi
  if [[ -n "$domain" ]]; then
    echo "Domain: https://${domain}"
    local app_base
    app_base="$(railway variable list --service "$WEB_SERVICE" --json 2>/dev/null | python3 -c 'import json,sys; print((json.load(sys.stdin) or {}).get("APP_BASE_URL") or "")' || true)"
    if [[ -z "$app_base" ]]; then
      railway variable set "APP_BASE_URL=https://${domain}" --service "$WEB_SERVICE" --skip-deploys \
        && echo "Set APP_BASE_URL=https://${domain}" \
        || warn "could not set APP_BASE_URL — set it manually on '${WEB_SERVICE}'"
    fi
  fi

  # MySQL volume snapshot schedules (Daily/Weekly/Monthly) — idempotent.
  if [[ -x scripts/railway-mysql-backups.sh ]]; then
    ./scripts/railway-mysql-backups.sh ensure || warn "backup schedule setup failed — see docs/BACKUP.md"
  fi

  push_railway_service_id
  echo
  echo "Railway stage done. Next: ./scripts/bootstrap-deploy.sh secrets"
}

push_railway_service_id() {
  have_cmd gh && gh auth status >/dev/null 2>&1 || { warn "gh unavailable — set RAILWAY_SERVICE_ID GitHub secret manually"; return 0; }
  local ctx web_id
  ctx="$(railway_context)" || { warn "cannot resolve service id (project not linked)"; return 0; }
  web_id="$(cut -f4 <<<"$ctx")"
  [[ -n "$web_id" ]] || { warn "service '${WEB_SERVICE}' not found — apply the IaC plan first"; return 0; }
  # No --body flag: gh reads the value from stdin ("--body -" would store a literal "-").
  printf '%s' "$web_id" | gh secret set RAILWAY_SERVICE_ID
  echo "GitHub secret RAILWAY_SERVICE_ID set (${web_id})."
}

# --- secrets -----------------------------------------------------------------

do_secrets() {
  need_cmd gh
  gh_ready || die "gh not authenticated — run: gh auth login"
  gh_repo >/dev/null || die "no GitHub repo to push secrets to — run: ./scripts/bootstrap-deploy.sh github"

  push_railway_service_id

  if gh_secret_present RAILWAY_TOKEN; then
    echo "GitHub secret RAILWAY_TOKEN already set."
  elif interactive; then
    echo "RAILWAY_TOKEN is the one credential no CLI can mint:"
    echo "  Railway dashboard → Project → Settings → Tokens → create for 'production'"
    local token
    read -rsp "Paste the project token (input hidden, pushed straight to GitHub): " token
    echo
    if [[ -n "$token" ]]; then
      printf '%s' "$token" | gh secret set RAILWAY_TOKEN
      echo "GitHub secret RAILWAY_TOKEN set."
    else
      warn "skipped RAILWAY_TOKEN (empty input)"
    fi
  else
    warn "RAILWAY_TOKEN missing — create it in the Railway dashboard and run: gh secret set RAILWAY_TOKEN"
  fi

  if gh_secret_present CLOUDFLARE_API_TOKEN; then
    echo "GitHub secret CLOUDFLARE_API_TOKEN already set."
  else
    echo "Minting the scoped Cloudflare CI token (cf-token.sh setup) …"
    ./scripts/cf-token.sh setup || warn "cf-token setup failed — see docs/CLOUDFLARE.md (bootstrap credentials)"
  fi
  if ! gh_secret_present CLOUDFLARE_BOOTSTRAP_TOKEN; then
    echo "Optional: 'npm run cf:token:seed' lets CI self-provision deploy tokens (trade-off in docs/CLOUDFLARE.md)."
  fi

  # MYSQL* fallback secrets so CI can deploy Cloudflare even without RAILWAY_TOKEN.
  if have_cmd railway && railway_logged_in; then
    ./scripts/sync-railway-cloudflare.sh push-github || warn "MYSQL* fallback push failed (needs MySQL TCP proxy — see docs/CLOUDFLARE.md)"
  else
    warn "railway unavailable — skipped MYSQL* fallback secrets"
  fi

  echo
  echo "Secrets stage done. Next: ./scripts/bootstrap-deploy.sh cloudflare  (or 'ci')"
}

# --- email -------------------------------------------------------------------

# Production email (password recovery) goes through Cloudflare Email Sending:
# the Worker exposes POST /internal/email (protected by EMAIL_PROXY_SECRET)
# and the Railway web service calls it with EMAIL_WORKER_URL +
# EMAIL_WORKER_SECRET. No third-party account, no Cloudflare API token —
# `npx wrangler login` (OAuth) covers everything here.
do_email() {
  need_cmd railway
  need_cmd curl
  need_cmd python3
  need_cmd npx
  railway_logged_in || die "not logged in to Railway — run: railway login"
  wrangler_local || die "wrangler not installed — run: npm ci"
  if [[ -z "${CLOUDFLARE_API_TOKEN:-}" ]] && ! npx wrangler whoami >/dev/null 2>&1; then
    die "wrangler not logged in — run: npx wrangler login"
  fi

  # 1. Sender domain: needs a zone in the Cloudflare account, onboarded once
  #    (adds bounce MX + SPF + DKIM + DMARC DNS records automatically).
  local domains
  domains="$(email_sending_domains)"
  if [[ -z "$domains" ]]; then
    die "no domain onboarded to Cloudflare Email Sending — run: npx wrangler email sending enable <your-zone>, then re-run this stage"
  fi
  echo "Email Sending domains: $(printf '%s' "$domains" | tr '\n' ' ')"

  # 2. MAIL_FROM — keep the existing value when its domain is onboarded,
  #    else default to noreply@<first onboarded domain>.
  local mail_from from_domain
  mail_from="$(railway_web_var MAIL_FROM)"
  from_domain="$(mail_from_domain "$mail_from")"
  if [[ -z "$mail_from" ]] || ! printf '%s\n' "$domains" | grep -qx "$from_domain"; then
    local default_from
    default_from="Scripty <noreply@$(printf '%s\n' "$domains" | head -n1)>"
    [[ -n "$mail_from" ]] && warn "MAIL_FROM '${mail_from}' is not on an onboarded domain — replacing"
    if interactive; then
      read -rp "MAIL_FROM [${default_from}]: " mail_from
    else
      mail_from=""
    fi
    mail_from="${mail_from:-$default_from}"
  fi
  echo "MAIL_FROM: ${mail_from}"

  # 3. Worker endpoint URL: prefer the existing Railway value, else the URL
  #    cached by the 'cloudflare' stage, else the deployed Worker.
  local worker_url
  worker_url="$(railway_web_var EMAIL_WORKER_URL)"
  if [[ -z "$worker_url" && -s cloudflare/.worker-url ]]; then
    worker_url="$(cat cloudflare/.worker-url)/internal/email"
  fi
  [[ -n "$worker_url" ]] || die "cannot determine the email Worker URL — deploy the Worker first (./scripts/bootstrap-deploy.sh cloudflare or ci), or set EMAIL_WORKER_URL on Railway '${WEB_SERVICE}'"

  # 4. Shared secret: reuse Railway's, else mint one and set it as the
  #    Worker's EMAIL_PROXY_SECRET (survives future deploys).
  local secret
  secret="$(railway_web_var EMAIL_WORKER_SECRET)"
  if [[ -z "$secret" ]]; then
    need_cmd openssl
    secret="$(openssl rand -hex 32)"
    printf '%s' "$secret" | npx wrangler secret put EMAIL_PROXY_SECRET --config "$WRANGLER_CONFIG" >/dev/null \
      || die "could not set the Worker EMAIL_PROXY_SECRET (Worker not deployed yet?)"
    echo "Minted EMAIL_PROXY_SECRET and set it on the Worker."
  fi

  # 5. Railway web service vars (skip-deploys: the next deploy picks them up).
  if [[ "$(railway_web_var EMAIL_WORKER_URL)" == "$worker_url" \
     && "$(railway_web_var EMAIL_WORKER_SECRET)" == "$secret" \
     && "$(railway_web_var MAIL_FROM)" == "$mail_from" ]]; then
    echo "Railway web service already has EMAIL_WORKER_URL + EMAIL_WORKER_SECRET + MAIL_FROM."
  else
    railway variable set "EMAIL_WORKER_URL=${worker_url}" "EMAIL_WORKER_SECRET=${secret}" \
        "MAIL_FROM=${mail_from}" --service "$WEB_SERVICE" --skip-deploys \
      && echo "Set EMAIL_WORKER_URL + EMAIL_WORKER_SECRET + MAIL_FROM on Railway '${WEB_SERVICE}'." \
      || die "could not set Railway variables"
  fi
  if [[ -n "$(railway_web_var RESEND_API_KEY)" ]]; then
    railway variable delete --service "$WEB_SERVICE" RESEND_API_KEY >/dev/null 2>&1 \
      && echo "Removed legacy RESEND_API_KEY from Railway '${WEB_SERVICE}'." || true
  fi

  # 6. Same trio as Worker secrets so the Cloudflare container can send too.
  if printf '%s' "$worker_url" | npx wrangler secret put EMAIL_WORKER_URL --config "$WRANGLER_CONFIG" >/dev/null 2>&1 \
     && printf '%s' "$secret" | npx wrangler secret put EMAIL_WORKER_SECRET --config "$WRANGLER_CONFIG" >/dev/null 2>&1 \
     && printf '%s' "$mail_from" | npx wrangler secret put MAIL_FROM --config "$WRANGLER_CONFIG" >/dev/null 2>&1; then
    echo "Set EMAIL_WORKER_URL + EMAIL_WORKER_SECRET + MAIL_FROM Worker secrets."
  else
    warn "could not set Worker secrets (Worker not deployed yet?) — re-run this stage after 'cloudflare'/'ci'"
  fi

  # 7. Probe: an unauthenticated POST must bounce with 401 (deployed + auth on).
  local probe
  probe="$(email_worker_probe "$worker_url")"
  case "$probe" in
    401) echo "Email Worker endpoint live (auth enforced): ${worker_url}" ;;
    503) warn "endpoint reachable but EMAIL_PROXY_SECRET missing on the Worker — re-run this stage" ;;
    *)   warn "endpoint probe returned HTTP ${probe:-none} — deploy the Worker (./scripts/bootstrap-deploy.sh cloudflare or ci), then re-run this stage" ;;
  esac

  echo
  echo "Email stage done. Next: ./scripts/bootstrap-deploy.sh verify"
}

# --- cloudflare --------------------------------------------------------------

do_cloudflare() {
  need_cmd npx
  wrangler_local || die "wrangler not installed — run: npm ci"
  have_cmd docker && docker info >/dev/null 2>&1 \
    || die "Docker must be running: wrangler builds the container image locally. No Docker? Deploy from CI instead: ./scripts/bootstrap-deploy.sh ci"
  if [[ -z "${CLOUDFLARE_API_TOKEN:-}" ]] && ! npx wrangler whoami >/dev/null 2>&1; then
    die "wrangler not logged in — run: npx wrangler login"
  fi
  need_cmd railway
  railway_logged_in || die "not logged in to Railway (needed to read MySQL TCP proxy credentials) — run: railway login"

  echo "Writing Worker MySQL secrets from Railway's TCP proxy …"
  trap 'rm -f cloudflare/.deploy-secrets' EXIT
  ./scripts/sync-railway-cloudflare.sh write

  echo "Deploying Worker + container image (first build takes a few minutes) …"
  local deploy_log
  deploy_log="$(mktemp "${TMPDIR:-/tmp}/scripty-cf-deploy.XXXXXX")"
  trap 'rm -f cloudflare/.deploy-secrets "$deploy_log"' EXIT
  # pipefail: tee passes wrangler's failure through
  (cd cloudflare && npx wrangler deploy --secrets-file .deploy-secrets) 2>&1 | tee "$deploy_log"
  rm -f cloudflare/.deploy-secrets
  # Cache the workers.dev URL wrangler printed so 'verify' can curl it later.
  grep -oE 'https://[a-z0-9.-]+\.workers\.dev' "$deploy_log" | head -n1 > cloudflare/.worker-url || true
  rm -f "$deploy_log"
  trap - EXIT
  [[ -s cloudflare/.worker-url ]] && echo "Worker URL cached for verify: $(cat cloudflare/.worker-url)"

  echo
  echo "Cloudflare stage done. Next: ./scripts/bootstrap-deploy.sh verify"
}

# --- verify ------------------------------------------------------------------

do_verify() {
  local failed=0

  section "Railway"
  if have_cmd railway && railway_logged_in; then
    local domain
    domain="$(railway_web_domain || true)"
    if [[ -n "$domain" ]]; then
      if curl -fsS --max-time 30 "https://${domain}/health" >/dev/null; then
        ok "https://${domain}/health is UP"
      else
        miss "https://${domain}/health not responding (first deploy may still be building — check the Railway dashboard)"
        failed=1
      fi
    else
      warn "no Railway domain found — run: ./scripts/bootstrap-deploy.sh railway"
    fi
  else
    warn "railway unavailable — skipped"
  fi

  section "Cloudflare"
  local worker_url="${SCRIPTY_WORKER_URL:-}"
  if [[ -z "$worker_url" && -s cloudflare/.worker-url ]]; then
    worker_url="$(head -n1 cloudflare/.worker-url)"
  fi
  if [[ -z "$worker_url" && -n "${CLOUDFLARE_API_TOKEN:-}" && -n "${CLOUDFLARE_ACCOUNT_ID:-}" ]]; then
    local sub
    sub="$(curl -fsS --max-time 15 -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" \
      "https://api.cloudflare.com/client/v4/accounts/${CLOUDFLARE_ACCOUNT_ID}/workers/subdomain" \
      | python3 -c 'import json,sys; print((json.load(sys.stdin).get("result") or {}).get("subdomain") or "")' 2>/dev/null || true)"
    [[ -n "$sub" ]] && worker_url="https://scripty.${sub}.workers.dev"
  fi
  if [[ -n "$worker_url" ]]; then
    if curl -fsS --max-time 120 "${worker_url}/health" >/dev/null; then
      ok "${worker_url}/health is UP"
    else
      miss "${worker_url}/health not responding (container cold start can take a few minutes — retry shortly)"
      failed=1
    fi
  else
    warn "Worker URL unknown — set SCRIPTY_WORKER_URL=https://scripty.<subdomain>.workers.dev and re-run, or check the Cloudflare dashboard"
  fi

  return "$failed"
}

# --- dispatch ----------------------------------------------------------------

case "$ACTION" in
  doctor)     do_doctor ;;
  github)     do_github ;;
  railway)    do_railway ;;
  secrets)    do_secrets ;;
  email)      do_email ;;
  cloudflare) do_cloudflare ;;
  ci)         do_ci ;;
  verify)     do_verify ;;
  all)
    do_github
    do_railway
    do_secrets
    if have_cmd docker && docker info >/dev/null 2>&1; then
      do_cloudflare
    else
      echo
      echo "Docker not available — deploying both platforms via GitHub Actions instead."
      do_ci
    fi
    do_email
    do_verify
    ;;
  *)
    cat >&2 <<EOF
usage: $0 <doctor|github|railway|secrets|cloudflare|ci|email|verify|all>

  doctor      read-only report: tools, auth, GitHub repo/Actions, Railway project, email, secrets, Worker state
  github      create/verify the GitHub repo, enable Actions, ensure the 'production' environment
  railway     link/create the Railway project, apply .railway/railway.ts, domain, backups
  secrets     push the GitHub Actions secrets CI deploys with
  cloudflare  first local wrangler deploy (Worker + container + MySQL secrets; needs Docker)
  ci          dispatch the CI/CD workflow on main and watch it (deploys both platforms, no local Docker)
  email       production email via Cloudflare Email Sending: EMAIL_WORKER_URL/SECRET + MAIL_FROM (Railway + Worker)
  verify      curl /health on Railway and the Cloudflare Worker
  all         github → railway → secrets → cloudflare (or ci without Docker) → email → verify
EOF
    exit 2
    ;;
esac
