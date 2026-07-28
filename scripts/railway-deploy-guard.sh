#!/bin/bash
# Tell "the deploy pipeline went green" apart from "the new build is live".
#
# `railway up --ci` exits 0 even when the deployment it created goes on to fail
# its healthcheck: Railway then rolls back to the previous image and keeps
# serving it. That is how two merges (2026-07-26 and 2026-07-27) got green CI
# runs while production kept serving the 2026-07-25 build for two days.
#
# Usage:
#   scripts/railway-deploy-guard.sh snapshot > before.json
#   railway up --service "$RAILWAY_SERVICE_ID" --ci
#   scripts/railway-deploy-guard.sh confirm before.json
#   scripts/railway-deploy-guard.sh status          # one-shot human report
#
# `snapshot` records which deployments already exist and which one is live.
# `confirm` finds the deployment that appeared since (that is ours — deploys
# from an uploaded directory carry no commit hash to match on), waits for it to
# reach a terminal status, and requires that it is both SUCCESS and the one
# Railway is actually serving. Anything else exits non-zero.
#
# Environment:
#   RAILWAY_TOKEN       required (project token, production environment)
#   RAILWAY_SERVICE_ID  required — the service to guard
#   POLL_TIMEOUT        seconds to wait for a terminal status (default 900,
#                       matching healthcheckTimeout in .railway/railway.ts)
#   POLL_INTERVAL       seconds between polls (default 20)
set -euo pipefail

POLL_TIMEOUT="${POLL_TIMEOUT:-900}"
POLL_INTERVAL="${POLL_INTERVAL:-20}"

# RAILWAY_TOKEN is deliberately not required: CI supplies it, and a developer
# running `status` locally is already authenticated via `railway login`. The CLI
# reports an auth failure clearly enough on its own.
require_env() {
  if [[ -z "${RAILWAY_SERVICE_ID:-}" ]]; then
    echo "ERROR: RAILWAY_SERVICE_ID is not set (Railway → service → Settings → copy ID)." >&2
    exit 1
  fi
}

# `railway deployment list --json` has returned both a bare array and an object
# with a .deployments key across CLI versions; normalise to an array.
deployments() {
  railway deployment list --json --limit 20 --service "$RAILWAY_SERVICE_ID" 2>/dev/null \
    | jq -c 'if type == "array" then . else (.deployments // []) end' 2>/dev/null \
    || echo '[]'
}

# The deployment Railway is currently serving for this service, or "" if the
# status call fails (a project token may not reach every field).
live_deployment_id() {
  railway status --json 2>/dev/null | jq -r --arg svc "$RAILWAY_SERVICE_ID" '
      [ .environments.edges[].node.serviceInstances.edges[].node
        | select(.serviceId == $svc)
        | (.activeDeployments // [])[]
        | select(.deploymentStopped != true)
        | .id ] | first // ""
    ' 2>/dev/null || echo ""
}

# Custom domains first, then the generated *.up.railway.app one.
public_health_url() {
  local domain
  domain="$(railway status --json 2>/dev/null | jq -r --arg svc "$RAILWAY_SERVICE_ID" '
      [ .environments.edges[].node.serviceInstances.edges[].node
        | select(.serviceId == $svc)
        | ((.domains.customDomains // []) + (.domains.serviceDomains // []))[].domain ]
      | map(select(. != null and . != "")) | first // ""
    ' 2>/dev/null || echo "")"
  [[ -n "$domain" ]] && printf 'https://%s/health' "$domain"
}

cmd_snapshot() {
  require_env
  jq -n --argjson ids "$(deployments | jq -c '[.[].id]')" \
        --arg live "$(live_deployment_id)" \
        '{knownIds: $ids, liveId: $live}'
}

cmd_status() {
  require_env
  local live newest
  live="$(live_deployment_id)"
  newest="$(deployments | jq -r '.[0].id // ""')"
  deployments | jq -r --arg live "$live" '
      .[:5][] | "\(.createdAt)  \(.status)\(if .id == $live then "  <- live" else "" end)"'
  if [[ -n "$live" && -n "$newest" && "$live" != "$newest" ]]; then
    echo ""
    echo "WARNING: the newest deployment is not the one being served — the last deploy rolled back." >&2
    return 1
  fi
}

cmd_confirm() {
  require_env
  local snapshot_file="${1:-}"
  [[ -f "$snapshot_file" ]] || { echo "ERROR: pass the file written by 'snapshot'." >&2; exit 1; }

  local known
  known="$(jq -c '.knownIds' "$snapshot_file")"

  # Our deployment: the newest one that did not exist before `railway up`.
  local deploy_id="" deadline
  deadline=$(( $(date +%s) + 120 ))
  while [[ "$(date +%s)" -lt "$deadline" ]]; do
    deploy_id="$(deployments | jq -r --argjson known "$known" \
      '[ .[] | select(.id as $id | ($known | index($id)) | not) ] | first | .id // ""')"
    [[ -n "$deploy_id" ]] && break
    sleep 10
  done
  if [[ -z "$deploy_id" ]]; then
    echo "::error title=No Railway deployment created::\`railway up\` produced no new deployment for service $RAILWAY_SERVICE_ID. Nothing was deployed." >&2
    exit 1
  fi
  echo "deployment_id=$deploy_id" >> "${GITHUB_OUTPUT:-/dev/null}"
  echo "Tracking deployment $deploy_id (created by this run)."

  # Wait for it to reach a terminal status.
  local status="" outcome=timeout
  deadline=$(( $(date +%s) + POLL_TIMEOUT ))
  while [[ "$(date +%s)" -lt "$deadline" ]]; do
    status="$(deployments | jq -r --arg id "$deploy_id" \
      '[ .[] | select(.id == $id) ] | first | .status // ""')"
    echo "$(date -u +%H:%M:%S) deployment $deploy_id: ${status:-<unknown>}"
    case "$status" in
      SUCCESS)                        outcome=success; break ;;
      FAILED|CRASHED|REMOVED|SKIPPED) outcome=failed;  break ;;
      # QUEUED / BUILDING / DEPLOYING / INITIALIZING / WAITING, or a transient
      # API hiccup — keep polling.
      *)                              sleep "$POLL_INTERVAL" ;;
    esac
  done

  if [[ "$outcome" == "failed" ]]; then
    echo "::error title=Railway deploy failed::Deployment $deploy_id ended in status $status. Production is still serving the previous build. Check the deployment's logs in the Railway dashboard." >&2
    exit 1
  fi
  if [[ "$outcome" == "timeout" ]]; then
    echo "::error title=Railway deploy unconfirmed::Deployment $deploy_id never reached a terminal status within ${POLL_TIMEOUT}s (last seen: ${status:-<unknown>}). Do not assume it shipped." >&2
    exit 1
  fi

  # SUCCESS is Railway's verdict on the deployment. Confirm independently that
  # it is the deployment being served — this is the claim CI was getting wrong.
  local live
  live="$(live_deployment_id)"
  if [[ -z "$live" ]]; then
    echo "::warning title=Liveness unconfirmed::Could not read the active deployment from 'railway status --json'. Relying on the deployment's SUCCESS status alone." >&2
  elif [[ "$live" != "$deploy_id" ]]; then
    echo "::error title=Deploy rolled back::Deployment $deploy_id reported SUCCESS, but Railway is serving $live. Production is not running this build." >&2
    exit 1
  else
    echo "Confirmed live: Railway is serving deployment $deploy_id."
  fi

  # Third check, from outside Railway: ask the running app which deployment it
  # is. /health echoes RAILWAY_DEPLOYMENT_ID when Railway injects it.
  local url body served
  url="$(public_health_url)"
  if [[ -z "$url" ]]; then
    echo "No public domain found for the service — skipping the /health check."
    return 0
  fi
  echo "Checking $url …"
  local health_deadline
  health_deadline=$(( $(date +%s) + 120 ))
  while [[ "$(date +%s)" -lt "$health_deadline" ]]; do
    if body="$(curl -sf --max-time 10 "$url" 2>/dev/null)"; then
      served="$(printf '%s' "$body" | jq -r '.deploymentId // ""' 2>/dev/null || echo "")"
      if [[ -z "$served" ]]; then
        echo "/health did not report a deploymentId; body: $body"
        return 0
      fi
      if [[ "$served" == "$deploy_id" ]]; then
        echo "/health confirms the live build: $body"
        return 0
      fi
      echo "/health still answering from deployment $served; waiting for $deploy_id …"
    else
      echo "/health not answering yet …"
    fi
    sleep 10
  done
  echo "::error title=Old build still serving::$url did not report deployment $deploy_id within 120s of it going live. Traffic is still on the previous build." >&2
  exit 1
}

case "${1:-}" in
  snapshot) cmd_snapshot ;;
  confirm)  shift; cmd_confirm "$@" ;;
  status)   cmd_status ;;
  *)
    echo "Usage: $0 {snapshot|confirm <snapshot-file>|status}" >&2
    exit 1
    ;;
esac
