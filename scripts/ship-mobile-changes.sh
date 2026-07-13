#!/usr/bin/env bash
# Ship unmerged Cursor mobile (cursor/*) branches onto main so CI deploys to Railway.
#
# Usage:
#   ./scripts/ship-mobile-changes.sh              # list pending branches
#   ./scripts/ship-mobile-changes.sh --ensure-pr  # open draft PRs for pending (CI auto-ships)
#   ./scripts/ship-mobile-changes.sh --apply      # cherry-pick onto main and push
#   ./scripts/ship-mobile-changes.sh --apply cursor/fix-file-dropdown-mobile-7a12
#   ./scripts/ship-mobile-changes.sh --prune      # delete remote cursor/* fully represented on main
#
# Skips commits already on main (by SHA, commit subject, or equivalent patch-id).
# Prefer --ensure-pr (lets CI verify + auto-ship) over --apply when possible.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

APPLY=0
ENSURE_PR=0
PRUNE=0
FILTER=""
while [ $# -gt 0 ]; do
  case "$1" in
    --apply|-a) APPLY=1; shift ;;
    --ensure-pr|--pr) ENSURE_PR=1; shift ;;
    --prune) PRUNE=1; shift ;;
    -h|--help)
      sed -n '2,14p' "$0"
      exit 0
      ;;
    *)
      if [ -n "$FILTER" ]; then
        FILTER="$FILTER $1"
      else
        FILTER="$1"
      fi
      shift
      ;;
  esac
done

if [ "$APPLY" -eq 1 ] && [ "$ENSURE_PR" -eq 1 ]; then
  echo "error: use either --apply or --ensure-pr, not both" >&2
  exit 1
fi

MAIN_PATCH_IDS=""
MAIN_SUBJECTS=""

build_main_index() {
  local m
  MAIN_PATCH_IDS=""
  MAIN_SUBJECTS="$(git log origin/main -n 80 --format='%s')"
  for m in $(git log origin/main -n 40 --pretty=format:%H); do
    MAIN_PATCH_IDS="$MAIN_PATCH_IDS$(git show "$m" | git patch-id --stable | awk '{print $1}')"$'\n'
  done
}

already_on_main() {
  local commit="$1"
  local subject pid
  if git merge-base --is-ancestor "$commit" origin/main 2>/dev/null; then
    return 0
  fi
  subject="$(git log -1 --format='%s' "$commit")"
  if [ -n "$subject" ] && printf '%s\n' "$MAIN_SUBJECTS" | grep -Fxq -- "$subject"; then
    return 0
  fi
  pid="$(git show "$commit" | git patch-id --stable | awk '{print $1}')"
  [ -z "$pid" ] && return 1
  printf '%s\n' "$MAIN_PATCH_IDS" | grep -Fxq -- "$pid"
}

branch_has_pending() {
  local b="$1" c
  for c in $(git rev-list --reverse "origin/main..origin/$b" 2>/dev/null); do
    if ! already_on_main "$c"; then
      return 0
    fi
  done
  return 1
}

echo "Fetching origin…"
git fetch origin --prune

if ! git rev-parse --verify origin/main >/dev/null 2>&1; then
  echo "error: origin/main not found" >&2
  exit 1
fi

BRANCHES="$(git branch -r --list 'origin/cursor/*' | sed 's|^[[:space:]]*origin/||' | sort -u)"
build_main_index

if [ -n "$FILTER" ]; then
  SELECTED=""
  for want in $FILTER; do
    want="${want#origin/}"
    found=0
    for b in $BRANCHES; do
      if [ "$b" = "$want" ]; then
        SELECTED="$SELECTED$b "
        found=1
        break
      fi
    done
    if [ "$found" -eq 0 ]; then
      echo "error: branch not found: $want" >&2
      exit 1
    fi
  done
  BRANCHES="$SELECTED"
fi

PENDING=""
STALE=""
for b in $BRANCHES; do
  [ -z "$b" ] && continue
  if branch_has_pending "$b"; then
    PENDING="$PENDING$b "
  else
    # Has commits vs main by SHA, but all already represented — or empty.
    if [ -n "$(git rev-list "origin/main..origin/$b" 2>/dev/null | head -n1)" ] || \
       ! git merge-base --is-ancestor "origin/$b" origin/main 2>/dev/null; then
      # Still list as stale if remote tip isn't an ancestor (cherry-picked already).
      if ! git merge-base --is-ancestor "origin/$b" origin/main 2>/dev/null; then
        STALE="$STALE$b "
      fi
    fi
  fi
done

# Also treat fully-merged tips (ancestor of main) as prune candidates when --prune.
if [ "$PRUNE" -eq 1 ]; then
  for b in $BRANCHES; do
    [ -z "$b" ] && continue
    case " $PENDING " in
      *" $b "*) continue ;;
    esac
    case " $STALE " in
      *" $b "*) continue ;;
    esac
    if git merge-base --is-ancestor "origin/$b" origin/main 2>/dev/null; then
      STALE="$STALE$b "
    elif ! branch_has_pending "$b"; then
      STALE="$STALE$b "
    fi
  done
fi

if [ -z "$(echo "$PENDING" | tr -d '[:space:]')" ] && [ "$PRUNE" -ne 1 ]; then
  echo "No unmerged cursor/* patches vs origin/main."
  if [ -n "$(echo "$STALE" | tr -d '[:space:]')" ]; then
    echo ""
    echo "Stale cursor/* branches (already on main; re-run with --prune to delete):"
    for b in $STALE; do
      echo "  $b"
    done
  fi
  exit 0
fi

# Branches whose tip isn't pending but isn't an ancestor of main either (already cherry-picked).
if [ -z "$(echo "$STALE" | tr -d '[:space:]')" ]; then
  for b in $BRANCHES; do
    [ -z "$b" ] && continue
    case " $PENDING " in
      *" $b "*) continue ;;
    esac
    if ! branch_has_pending "$b"; then
      STALE="$STALE$b "
    fi
  done
fi

if [ -n "$(echo "$PENDING" | tr -d '[:space:]')" ]; then
  echo ""
  echo "Pending Cursor mobile branches (need shipping to main):"
  echo ""
  for b in $PENDING; do
    echo "  $b"
    for c in $(git rev-list --reverse "origin/main..origin/$b"); do
      if already_on_main "$c"; then
        continue
      fi
      echo "    $(git log -1 --oneline "$c")"
    done
    if command -v gh >/dev/null 2>&1; then
      pr="$(gh pr list --head "$b" --base main --state open --json number,url,isDraft --jq '.[0] | select(. != null) | "#\(.number) \(.url)" + (if .isDraft then " (draft)" else "" end)' 2>/dev/null || true)"
      if [ -n "$pr" ]; then
        echo "    PR: $pr"
      else
        echo "    PR: (none — CI opens one on push, or use --ensure-pr)"
      fi
    fi
    echo ""
  done
fi

if [ -n "$(echo "$STALE" | tr -d '[:space:]')" ] && [ "$APPLY" -ne 1 ] && [ "$ENSURE_PR" -ne 1 ]; then
  echo "Stale cursor/* (already on main; use --prune to delete):"
  for b in $STALE; do
    echo "  $b"
  done
  echo ""
fi

if [ "$PRUNE" -eq 1 ]; then
  if [ -z "$(echo "$STALE" | tr -d '[:space:]')" ]; then
    # Rebuild stale: any cursor/* with no pending commits
    STALE=""
    for b in $BRANCHES; do
      [ -z "$b" ] && continue
      case " $PENDING " in
        *" $b "*) continue ;;
      esac
      STALE="$STALE$b "
    done
  fi
  if [ -z "$(echo "$STALE" | tr -d '[:space:]')" ]; then
    echo "Nothing to prune."
  else
    echo "Pruning stale remote cursor/* branches:"
    for b in $STALE; do
      echo "  delete origin/$b"
      git push origin --delete "$b"
    done
  fi
  if [ "$APPLY" -ne 1 ] && [ "$ENSURE_PR" -ne 1 ]; then
    exit 0
  fi
fi

if [ "$APPLY" -ne 1 ] && [ "$ENSURE_PR" -ne 1 ]; then
  echo "Dry run only."
  echo "  --ensure-pr   open draft PRs (CI verifies + auto-ships to Railway)"
  echo "  --apply       cherry-pick onto main and push"
  echo "  --prune       delete remote cursor/* already on main"
  exit 0
fi

if [ "$ENSURE_PR" -eq 1 ]; then
  if ! command -v gh >/dev/null 2>&1; then
    echo "error: gh CLI required for --ensure-pr" >&2
    exit 1
  fi
  OPENED=0
  for b in $PENDING; do
    existing="$(gh pr list --head "$b" --base main --state open --json number --jq '.[0].number // empty' 2>/dev/null || true)"
    if [ -n "$existing" ]; then
      echo "  $b → PR #$existing (already open)"
      continue
    fi
    subject="$(git log -1 --format='%s' "origin/$b")"
    [ -n "$subject" ] || subject="Ship $b"
    url="$(gh pr create \
      --base main \
      --head "$b" \
      --draft \
      --title "$subject" \
      --body "$(printf '%s\n' \
        "Opened by \`ship-mobile-changes.sh --ensure-pr\` for \`$b\`." \
        "" \
        "After Maven verify, CI auto-ships to \`main\` → Railway + Cloudflare." \
        "Add label \`hold\` or \`no-ship\` to skip.")")"
    echo "  $b → $url"
    OPENED=$((OPENED + 1))
  done
  echo "Done. Opened $OPENED PR(s). Watch Actions: https://github.com/watnee/scripty/actions"
  exit 0
fi

current="$(git branch --show-current)"
if [ "$current" != "main" ]; then
  echo "Checking out main…"
  git checkout main
fi
git pull --ff-only origin main

SHIPPED=0
FAILED=0
for b in $PENDING; do
  echo "=== Shipping $b ==="
  for c in $(git rev-list --reverse "origin/main..origin/$b"); do
    subject="$(git log -1 --format='%s' "$c")"
    if already_on_main "$c"; then
      echo "  skip (already on main): $subject"
      continue
    fi
    echo "  cherry-pick $c — $subject"
    if git cherry-pick "$c"; then
      SHIPPED=$((SHIPPED + 1))
    else
      echo "  conflict on $c — aborting this commit; resolve manually." >&2
      git cherry-pick --abort 2>/dev/null || true
      FAILED=$((FAILED + 1))
      break
    fi
  done
done

if [ "$SHIPPED" -eq 0 ]; then
  echo "Nothing new committed."
  if [ "$FAILED" -gt 0 ]; then
    exit 1
  fi
  exit 0
fi

echo "Pushing main ($SHIPPED commit(s))…"
git push origin main
echo "Done. Watch Actions: https://github.com/watnee/scripty/actions"
if [ "$FAILED" -gt 0 ]; then
  echo "warning: $FAILED branch(es) had conflicts and were skipped." >&2
  exit 1
fi
