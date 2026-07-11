#!/usr/bin/env bash
# Rotate the production admin password and remove ADMIN_PASSWORD from Railway,
# so the live credential exists only as a bcrypt hash in the database.
#
# Flow:
#   1. Read the current ADMIN_PASSWORD from Railway (or $CURRENT_ADMIN_PASSWORD).
#   2. Generate a strong random replacement.
#   3. Sign in and change the password through the app (POST /account/password),
#      exactly like a user would — the app enforces its own password policy.
#   4. Verify: old password rejected, new password accepted.
#   5. Delete ADMIN_PASSWORD from the Railway service (only after step 4 passes).
#   6. Print the new password once — unless --discard, which throws it away for
#      passkey-only sign-in.
#
# Usage:
#   scripts/rotate-admin-password.sh [--discard] [--yes]
#       [--base-url URL] [--username NAME] [--service NAME]
#
#   --discard   Do not print the new password. Only do this after registering a
#               passkey, or you will not be able to sign in. Recovery is always
#               possible by setting ADMIN_PASSWORD (12+ chars) and
#               ADMIN_PASSWORD_RESET=true on the service and redeploying.
#   --yes       Skip the --discard confirmation prompt (for non-interactive use).
set -euo pipefail

BASE_URL="https://web-production-ce5bc3.up.railway.app"
USERNAME="admin"
SERVICE="web"
DISCARD=false
ASSUME_YES=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --discard) DISCARD=true; shift ;;
    --yes) ASSUME_YES=true; shift ;;
    --base-url) BASE_URL="$2"; shift 2 ;;
    --username) USERNAME="$2"; shift 2 ;;
    --service) SERVICE="$2"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

fail() { echo "ERROR: $*" >&2; exit 1; }

command -v railway >/dev/null || fail "railway CLI not found"
command -v curl >/dev/null || fail "curl not found"

if $DISCARD && ! $ASSUME_YES; then
  echo "--discard means the new password is shown to NO ONE."
  echo "Only continue if a passkey is already registered for '${USERNAME}'."
  read -r -p "Type 'discard' to continue: " answer
  [[ "$answer" == "discard" ]] || fail "aborted"
fi

# --- 1. Current password ---------------------------------------------------
CURRENT="${CURRENT_ADMIN_PASSWORD:-}"
if [[ -z "$CURRENT" ]]; then
  CURRENT=$(railway variable list --service "$SERVICE" --kv 2>/dev/null \
    | grep '^ADMIN_PASSWORD=' | head -1 | cut -d= -f2- || true)
fi
[[ -n "$CURRENT" ]] || fail "no current password: ADMIN_PASSWORD is not set on Railway and CURRENT_ADMIN_PASSWORD is empty"

# --- 2. New password ---------------------------------------------------------
# Finite read first: an unbounded urandom pipe dies with SIGPIPE under pipefail.
NEW=$(head -c 512 /dev/urandom | LC_ALL=C tr -dc 'A-HJ-NP-Za-km-z2-9' | head -c 24)
[[ ${#NEW} -eq 24 ]] || fail "password generation failed"

# --- 3. Sign in and change the password -------------------------------------
JAR=$(mktemp)
PAGE=$(mktemp)
trap 'rm -f "$JAR" "$PAGE"' EXIT

csrf_from() {
  LC_ALL=C grep -ao 'name="_csrf" value="[^"]*"' "$1" | head -1 | sed 's/.*value="//;s/"$//'
}

curl -sf -c "$JAR" "$BASE_URL/login" -o "$PAGE" || fail "cannot load $BASE_URL/login"
CSRF=$(csrf_from "$PAGE")

LOGIN_REDIRECT=$(curl -s -b "$JAR" -c "$JAR" -o /dev/null -w '%{redirect_url}' \
  -X POST "$BASE_URL/login" \
  --data-urlencode "username=$USERNAME" \
  --data-urlencode "password=$CURRENT" \
  ${CSRF:+--data-urlencode "_csrf=$CSRF"})
[[ "$LOGIN_REDIRECT" != *login_error* && "$LOGIN_REDIRECT" != *csrf_error* ]] \
  || fail "sign-in with the current password failed ($LOGIN_REDIRECT)"

curl -sf -b "$JAR" -c "$JAR" "$BASE_URL/account/password" -o "$PAGE" \
  || fail "cannot load change-password page"
CSRF=$(csrf_from "$PAGE")

CHANGE_REDIRECT=$(curl -s -b "$JAR" -c "$JAR" -o "$PAGE" -w '%{redirect_url}' \
  -X POST "$BASE_URL/account/password" \
  --data-urlencode "currentPassword=$CURRENT" \
  --data-urlencode "newPassword=$NEW" \
  --data-urlencode "confirmPassword=$NEW" \
  ${CSRF:+--data-urlencode "_csrf=$CSRF"})
[[ "$CHANGE_REDIRECT" == *"/account/password"* ]] \
  || fail "password change was not accepted (no redirect; check $BASE_URL/account/password)"

# --- 4. Verify ----------------------------------------------------------------
verify_login() { # $1=password, prints redirect url
  local jar page csrf
  jar=$(mktemp); page=$(mktemp)
  curl -sf -c "$jar" "$BASE_URL/login" -o "$page" || fail "verify: cannot load login page"
  csrf=$(csrf_from "$page")
  curl -s -b "$jar" -o /dev/null -w '%{redirect_url}' -X POST "$BASE_URL/login" \
    --data-urlencode "username=$USERNAME" \
    --data-urlencode "password=$1" \
    ${csrf:+--data-urlencode "_csrf=$csrf"}
  rm -f "$jar" "$page"
}

[[ "$(verify_login "$CURRENT")" == *login_error* ]] \
  || fail "old password still works — password change did not take effect; ADMIN_PASSWORD left in place"
[[ "$(verify_login "$NEW")" != *login_error* ]] \
  || fail "new password does not work — investigate before touching Railway; old password may have been consumed"

# --- 5. Remove ADMIN_PASSWORD from Railway -----------------------------------
if railway variable delete ADMIN_PASSWORD --service "$SERVICE" >/dev/null 2>&1; then
  RAILWAY_MSG="ADMIN_PASSWORD deleted from Railway service '$SERVICE'."
else
  RAILWAY_MSG="WARNING: could not delete ADMIN_PASSWORD from Railway — remove it manually in the dashboard."
fi

# --- 6. Report -----------------------------------------------------------------
echo
echo "Password for '$USERNAME' rotated successfully on $BASE_URL"
echo "$RAILWAY_MSG"
if $DISCARD; then
  echo "New password: (discarded — sign in with your passkey)"
  echo "Recovery: set ADMIN_PASSWORD (12+ chars) and ADMIN_PASSWORD_RESET=true on the service, redeploy, then remove both."
else
  echo "New password: $NEW"
  echo "Save it now — it is not stored anywhere else."
fi
