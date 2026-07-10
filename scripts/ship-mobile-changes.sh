#!/usr/bin/env bash
# Ship unmerged Cursor mobile (cursor/*) branches onto main so CI deploys to Railway.
#
# Usage:
#   ./scripts/ship-mobile-changes.sh           # list pending branches
#   ./scripts/ship-mobile-changes.sh --apply   # cherry-pick onto main and push
#   ./scripts/ship-mobile-changes.sh --apply cursor/fix-file-dropdown-mobile-7a12
#
# Skips commits already on main (by SHA or equivalent patch-id from a prior cherry-pick).

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

APPLY=0
FILTER=""
while [ $# -gt 0 ]; do
  case "$1" in
    --apply|-a) APPLY=1; shift ;;
    -h|--help)
      sed -n '2,10p' "$0"
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

is_patch_on_main() {
  local commit="$1"
  local pid mpid m
  pid="$(git show "$commit" | git patch-id --stable | awk '{print $1}')"
  [ -z "$pid" ] && return 1
  for m in $(git log origin/main -n 100 --pretty=format:%H); do
    mpid="$(git show "$m" | git patch-id --stable | awk '{print $1}')"
    if [ -n "$mpid" ] && [ "$mpid" = "$pid" ]; then
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
for b in $BRANCHES; do
  [ -z "$b" ] && continue
  need=0
  for c in $(git rev-list --reverse "origin/main..origin/$b" 2>/dev/null); do
    if git merge-base --is-ancestor "$c" origin/main 2>/dev/null; then
      continue
    fi
    if is_patch_on_main "$c"; then
      echo "  note: already on main (same patch): $(git log -1 --oneline "$c")"
      continue
    fi
    need=1
    break
  done
  if [ "$need" -eq 1 ]; then
    PENDING="$PENDING$b "
  fi
done

if [ -z "$(echo "$PENDING" | tr -d '[:space:]')" ]; then
  echo "No unmerged cursor/* patches vs origin/main."
  exit 0
fi

echo ""
echo "Pending Cursor mobile branches (need shipping to main):"
echo ""
for b in $PENDING; do
  echo "  $b"
  for c in $(git rev-list --reverse "origin/main..origin/$b"); do
    if git merge-base --is-ancestor "$c" origin/main 2>/dev/null || is_patch_on_main "$c"; then
      continue
    fi
    echo "    $(git log -1 --oneline "$c")"
  done
  echo ""
done

if [ "$APPLY" -ne 1 ]; then
  echo "Dry run only. Re-run with --apply to cherry-pick onto main and push."
  echo "CI will then deploy Railway + Cloudflare."
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
    if git merge-base --is-ancestor "$c" HEAD 2>/dev/null || is_patch_on_main "$c"; then
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
