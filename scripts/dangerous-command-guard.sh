#!/bin/bash
#
# dangerous-command-guard.sh — PreToolUse hook, registered in ~/.claude/settings.json.
#
# permissions.autoMode.hard_deny is a prompt for a classifier: it only holds
# while auto mode is running it. bypassPermissions runs no classifier and skips
# the prompts that allow/deny rules would raise. A PreToolUse hook still runs —
# and an exit status of 2 stops the tool call *before* permission rules are
# evaluated, so it holds in every mode. The rules that must never bend live
# here rather than in hard_deny alone.
#
# Deliberately blunt. It matches the whole command string, so `grep "railway up"`
# is blocked too. A false positive costs one command typed by hand; a missed
# `railway up` costs a production deploy.
#
# This guard is not a sandbox. A determined model can still write a script and
# run it under a name this file has never heard of. It stops the direct,
# plausible mistake — which is the failure mode bypassPermissions actually has.
#
# Two properties this file tries hard to keep:
#
#   1. It fails closed. Anything it cannot parse — missing jq, unreadable
#      payload, a tool call in a shape it does not recognise — is blocked, not
#      waved through. A guard that disappears when its dependencies do is worse
#      than no guard, because the session still believes it is guarded.
#   2. It survives the session it is guarding. The self-protection rules cover
#      the *directories*, not just the filenames, because `rm -rf ~/.claude/hooks`
#      never has to say the word "guard".

set -u

# ------------------------------------------------------------------ plumbing

block() {
  printf 'Blocked by dangerous-command-guard: %s

This rule holds in every permission mode, including bypassPermissions, and is
not something to work around. Say what you wanted to run and why, and let the
user run it themselves.
' "$1" >&2
  exit 2
}

# The user opts out for a whole session by exporting this before `claude`.
# A command can't set it: hooks inherit Claude Code's environment, not the
# environment of the command being inspected. Checked first, so that the opt-out
# covers file writes too — the block messages below promise that it does.
[ "${CLAUDE_GUARD_OFF:-}" = "1" ] && exit 0

# Fail closed on a missing interpreter. /usr/bin/jq ships with recent macOS, but
# a PATH-installed jq is the common case elsewhere and this hook has no business
# guessing wrong in silence.
JQ=""
for candidate in /usr/bin/jq /opt/homebrew/bin/jq /usr/local/bin/jq; do
  [ -x "$candidate" ] && { JQ="$candidate"; break; }
done
[ -n "$JQ" ] || JQ=$(command -v jq 2>/dev/null || true)
[ -n "$JQ" ] || block 'jq is not installed, so this hook cannot read the tool call it is meant to inspect. Install jq (brew install jq) or relaunch with CLAUDE_GUARD_OFF=1.'

input=$(cat)
tool=$(printf '%s' "$input" | "$JQ" -r '.tool_name // empty' 2>/dev/null) ||
  block 'the tool call could not be parsed, so it cannot be checked.'
[ -n "$tool" ] || block 'the tool call carried no tool name, so it cannot be checked.'

# ---------------------------------------------------------------- file writes

# The guard has to survive the session it is guarding. In bypassPermissions a
# model can otherwise edit the hook, or the settings file that registers it,
# and the next command goes through unexamined.
#
# Paths are normalised before matching: /Users/me/.claude/../.claude/hooks/x is
# the same file as /Users/me/.claude/hooks/x, and a prefix test on the raw
# string does not know that.
normalise() {
  local p="$1" dir base
  case "$p" in /*) ;; *) p="$PWD/$p" ;; esac
  dir=$(dirname -- "$p")
  base=$(basename -- "$p")
  # -P resolves symlinks and .. against the real filesystem; if the parent does
  # not exist yet, fall back to the lexical path rather than giving up.
  if dir=$(cd -P -- "$dir" 2>/dev/null && pwd -P); then
    printf '%s/%s' "${dir%/}" "$base"
  else
    printf '%s' "$p"
  fi
}

protected_path() {
  case "$1" in
    # This hook, its neighbours, and the settings files that register them.
    "$HOME"/.claude/hooks|"$HOME"/.claude/hooks/*) return 0 ;;
    "$HOME"/.claude/settings.json|"$HOME"/.claude/settings.local.json) return 0 ;;
    # A project can register hooks of its own; they deserve the same footing.
    */.claude/hooks|*/.claude/hooks/*) return 0 ;;
    */.claude/settings.json|*/.claude/settings.local.json) return 0 ;;
  esac
  return 1
}

case "$tool" in
  Edit|Write|NotebookEdit)
    path=$(printf '%s' "$input" | "$JQ" -r '.tool_input.file_path // .tool_input.notebook_path // empty')
    [ -n "$path" ] || exit 0
    if protected_path "$(normalise "$path")"; then
      block "writing to $path would disarm the guard that is reading this command. Ask the user to make the change, or to relaunch with CLAUDE_GUARD_OFF=1."
    fi
    exit 0
    ;;
  Bash) ;;
  mcp__*)
    # MCP servers reach the same production surfaces the CLIs do, without ever
    # going through a shell. The Cloudflare server can drop a D1 database or an
    # R2 bucket in one call, and none of the Bash rules below would ever see it.
    case "$tool" in
      *_delete|*_delete_*)
        block "the MCP tool $tool deletes a hosted resource. Do it from the Cloudflare dashboard, deliberately." ;;
      *d1_database_query*)
        block "the MCP tool $tool runs SQL against a hosted D1 database, which is the production data." ;;
    esac
    exit 0
    ;;
  *) exit 0 ;;
esac

cmd=$(printf '%s' "$input" | "$JQ" -r '.tool_input.command // empty')
[ -n "$cmd" ] || exit 0

# One line, single-spaced, so a wrapped or multi-line command matches the same
# patterns a one-liner does.
cmd=$(printf '%s' "$cmd" | tr '\n\t' '  ' | tr -s ' ')

match() { printf '%s' "$cmd" | grep -Eiq "$1"; }

# `git` and `gh` take global options before the subcommand, so `git -C /repo
# push --force` has to match the same rule `git push --force` does.
GIT='(^|[^-[:alnum:]])git( +-[^ ]+( +[^-][^ ]*)?)* +'

# --------------------------------------------------------------- exemptions

# The read-only stage of the deploy bootstrapper is the one safe entry point.
match 'bootstrap-deploy\.sh[^&|;]* doctor( |$)' && exit 0

# ------------------------------------------------------- the production data

match '(^|[^-[:alnum:]])railway +connect' &&
  block 'railway connect opens a shell on the production database.'

match '(^|[^-[:alnum:]])railway +(run|ssh)\b[^&|;]*\b(mysql|mysqldump|mariadb|flyway)' &&
  block 'this runs a database client against the production environment.'

match '(^|[^-[:alnum:]])railway +(tcp-proxy|proxy)\b' &&
  block 'a tcp proxy to Railway exposes the production database to every local tool.'

match 'restore-mysql\.sh' &&
  block 'restore-mysql.sh overwrites a database from a backup.'

match '(mysql|mysqldump|mariadb)\b[^&|;]*(rlwy\.net|railway\.(app|internal)|proxy\.rlwy)' &&
  block 'this points a database client at the production host.'

match 'flyway[:.-][^&|;]*(clean|repair)|flyway +(clean|repair)' &&
  block 'flyway clean drops the schema and flyway repair rewrites the checksum history. A production repair has to be done deliberately, by hand.'

match 'flyway[^&|;]*(rlwy\.net|railway\.(app|internal))' &&
  block 'this aims Flyway at production rather than the local ./db files.'

# D1 is production data too, and `wrangler d1 execute --remote` is its psql.
match '(^|[^-[:alnum:]])wrangler +d1 +(execute|migrations)\b[^&|;]*--remote' &&
  block 'this runs SQL against the hosted D1 database rather than the local one.'

# --------------------------------------------------------- the infrastructure

match '(^|[^-[:alnum:]])railway +(up|redeploy|down|delete|init|link|unlink|domain|add|volume)\b' &&
  block 'this mutates the Railway project.'

match '(^|[^-[:alnum:]])railway +(config +apply|variables? +(set|delete)|environment +(new|delete))' &&
  block 'this rewrites Railway configuration or environment variables.'

match '(^|[^-[:alnum:]])wrangler +(deploy|publish|delete)\b' &&
  block 'this publishes to or deletes from Cloudflare.'

# The resource subcommands delete buckets, namespaces and databases outright.
match '(^|[^-[:alnum:]])wrangler +(r2|kv|d1|queues|hyperdrive|vectorize|pages)\b[^&|;]*\b(delete|destroy)\b' &&
  block 'this deletes a Cloudflare resource and the data inside it.'

match 'bootstrap-deploy\.sh' &&
  block 'bootstrap-deploy.sh mutates deploy infrastructure in every stage except doctor.'

# Production ships from main through GitHub Actions, so the workflow API is a
# deploy button with a different label.
match '(^|[^-[:alnum:]])gh +(workflow +(run|enable|disable)|run +(rerun|cancel))\b' &&
  block 'this drives the GitHub Actions workflows that deploy to Railway and Cloudflare.'

match '(^|[^-[:alnum:]])gh +(release +(create|delete|edit)|repo +(delete|archive|rename)|api +[^&|;]*-X +(POST|PUT|PATCH|DELETE))' &&
  block 'this mutates the GitHub repository or publishes a release.'

# -------------------------------------------------------------- the secrets

match '(^|[^-[:alnum:]])gh +(secret|variable) +(set|delete)|(^|[^-[:alnum:]])gh +auth +token' &&
  block 'this sets, deletes, or prints a repository secret.'

match '(^|[^-[:alnum:]])wrangler +secret\b[^&|;]*\b(put|delete|bulk)\b' &&
  block 'this writes or removes a Cloudflare Worker secret.'

match 'cf-token\.sh|backup-secrets\.sh|rotate-admin-password\.sh' &&
  block 'this script handles production credentials.'

match 'security +find-(generic|internet)-password[^&|;]* -w' &&
  block 'this prints a password out of the keychain in clear text.'

# Reading a credential file is the same act whichever program does the reading.
match '(^| |\|)(cat|bat|less|more|head|tail|open|grep|egrep|rg|awk|sed|strings|xxd|od|base64|cp|scp|rsync)( +-[^ ]+)* [^|;&]*(\.env\b|\.env\.|\.credentials\.json|\.netrc|id_rsa|id_ed25519|\.pem\b|\.p12\b|\.keystore\b)' &&
  block 'this reads or copies a credential file. Read what you need from the code that consumes it instead.'

# And sending one somewhere is worse than reading it.
match '(curl|wget|nc|ncat)\b[^&|;]*(@|--upload-file|-T )[^ &|;]*(\.env|\.pem|id_rsa|id_ed25519|\.netrc|credentials)' &&
  block 'this uploads a credential file to a remote host.'

match '(printenv|env)( |$)[^&|;]*\| *(curl|wget|nc|ncat)\b' &&
  block 'this pipes the environment, secrets included, into a network client.'

# --------------------------------------------------------- the shared history

match "${GIT}push[^&|;]*( --force\\b| --force-with-lease| -f\\b| --mirror| --delete\\b| --prune\\b)" &&
  block 'this force-pushes or deletes on the remote, rewriting history other checkouts depend on.'

match "${GIT}push[^&|;]* \\+[A-Za-z_]" &&
  block 'a + refspec is a force push in disguise.'

match "${GIT}push[^&|;]* :[A-Za-z_]" &&
  block 'a : refspec deletes the remote branch.'

match "${GIT}tag +-d[^&|;]*|${GIT}push[^&|;]* --tags[^&|;]* --force" &&
  block 'this deletes or rewrites a tag other checkouts have already fetched.'

# ---------------------------------------------------------------- the guard

# Broader than the filename: `rm -rf ~/.claude/hooks` never says "guard", and
# `mv ~/.claude ~/.claude.bak` disarms everything at once.
#
# Matched on location, not on filename. A file called dangerous-command-guard.sh
# somewhere else in a repo is source to be edited freely — it guards nothing
# until it is installed under .claude/hooks, and matching the bare name would
# block ordinary work on the very copy under review.
#
# The last alternative deliberately stops at `.claude` with nothing after it.
# This repo keeps its worktrees under .claude/worktrees, so matching
# `.claude/` followed by anything would block `chmod`, `cp` and `rm` against
# every absolute path in a checkout — a guard nobody can work alongside is a
# guard that gets switched off.
if printf '%s' "$cmd" | grep -Eq '\.claude/(settings(\.local)?\.json|hooks)|(^| )[^ ]*[/~]\.claude( |$)'; then
  # A redirect into the file counts as a write; `2>&1` on a read does not.
  if match '(^| )(rm|rmdir|mv|cp|ln|chmod|chown|chflags|truncate|shred|tee|install)\b' ||
     match 'sed +-i|perl +-[^ ]*i|python3? +-c|node +-e|ruby +-e' ||
     printf '%s' "$cmd" | grep -Eq '(^|[^0-9&])>>?[^&]'; then
    block 'this rewrites, moves, or removes the guard and the settings that register it. Ask the user to make the change.'
  fi
fi

# ------------------------------------------------------------------- general

match '(curl|wget)[^|]*\| *(sudo +)?(ba|z|d)?sh\b' &&
  block 'piping a download straight into a shell runs code nobody has read.'

# permissions.ask holds sudo behind a prompt. bypassPermissions raises no
# prompt, so the honest equivalent of "ask me first" is "not from in here".
match '(^| )sudo( +-[^ ]+)* +[^ ]' &&
  block 'sudo is held behind a prompt in every other mode, and bypassPermissions raises no prompt. Run it by hand.'

exit 0
