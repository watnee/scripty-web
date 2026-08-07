# Agent guardrails

What stops a Claude Code session from deploying to production, dropping the
database, or leaking a token — and, just as importantly, what does not.

Agents work in this repo in `bypassPermissions` a lot of the time, because the
alternative is approving `mvn verify` forty times a day. That mode is the one
this document is about: it is the mode where nothing asks first.

## Three layers, and only one of them holds in bypass mode

| Layer | Where | Holds in `bypassPermissions`? |
|---|---|---|
| `permissions.autoMode.hard_deny` | `~/.claude/settings.json` | **No** — it is a prompt for a classifier that only runs in auto mode |
| `permissions.deny` / `ask` | `~/.claude/settings.json` | **No** — these raise prompts, and bypass mode raises none |
| `PreToolUse` hook | `~/.claude/hooks/dangerous-command-guard.sh` | **Yes** |

A `PreToolUse` hook runs before permission rules are evaluated, and an exit
status of 2 stops the tool call outright. That is why the rules that must never
bend live in the hook, and why the settings entries are a mirror of it rather
than the other way round.

The practical consequence: **a rule that exists only in `deny` or `hard_deny` is
not a guardrail, it is a preference.** If you add something to those lists,
add it to the hook too, or it evaporates the moment someone runs in bypass mode.

## Files

- `scripts/dangerous-command-guard.sh` — the hook. This is source; it guards
  nothing until installed.
- `scripts/test-dangerous-command-guard.sh` — 82 cases, both directions.
- `docs/claude-settings.reference.json` — a reference `~/.claude/settings.json`
  whose `deny` and `hard_deny` mirror the hook.

## Installing

```bash
cp scripts/dangerous-command-guard.sh ~/.claude/hooks/dangerous-command-guard.sh
chmod +x ~/.claude/hooks/dangerous-command-guard.sh
```

The hook must be registered in `~/.claude/settings.json`. The matcher is a
regex, and it has to include MCP tools:

```json
"hooks": {
  "PreToolUse": [
    {
      "matcher": "Bash|Edit|Write|NotebookEdit|mcp__.*",
      "hooks": [
        { "type": "command", "command": "\"$HOME\"/.claude/hooks/dangerous-command-guard.sh", "timeout": 10 }
      ]
    }
  ]
}
```

Without `mcp__.*` the hook is never invoked for MCP tool calls, and the
Cloudflare connector can delete an R2 bucket or query production D1 without
passing a single check. That one alternation is load-bearing.

Then verify against the installed copy, not just the repo one:

```bash
scripts/test-dangerous-command-guard.sh ~/.claude/hooks/dangerous-command-guard.sh
```

## What it stops

Production data (`railway connect`, a mysql client pointed at `rlwy.net`,
`flyway clean|repair`, `wrangler d1 execute --remote`), infrastructure
(`railway up`, `wrangler deploy`, Cloudflare resource deletes,
`bootstrap-deploy.sh` in any stage but `doctor`), the GitHub Actions workflows
that ship to Railway and Cloudflare, secrets (setting, printing, or uploading
them), shared git history (force-push, refspec deletes), and its own
installation directory.

It also covers MCP equivalents of the same acts — any `*_delete` tool, and
`d1_database_query` — because a connector reaches production without ever
touching a shell.

## Two design rules worth keeping

**Fail closed.** Missing `jq`, an unparseable payload, a tool call in an
unrecognised shape — all blocked, not waved through. A guard that disappears
when its dependencies do is worse than no guard, because the session still
believes it is guarded.

**False positives are the real failure mode.** A guard that gets in the way of
`mvn verify` gets switched off, and a switched-off guard stops nothing. Half of
`test-dangerous-command-guard.sh` asserts that everyday commands still pass.
Two lessons are already encoded there:

- This repo keeps worktrees under `.claude/worktrees/`, so a self-protection
  rule matching `.claude/` followed by anything blocks `chmod`, `cp` and `rm`
  against every absolute path in a checkout.
- The self-protection rule matches on *location*, not filename. A file named
  `dangerous-command-guard.sh` in `scripts/` is source to be edited freely.

## What it is not

Not a sandbox. It matches command strings, so a model that writes a script and
runs it under another name walks straight past — as does anything reaching the
network through a language runtime rather than a CLI. It stops the direct,
plausible mistake, which is the failure mode `bypassPermissions` actually has.
Treat it as a seatbelt, not a cage.

## Turning it off

Export `CLAUDE_GUARD_OFF=1` before launching `claude`. A command cannot set it:
hooks inherit Claude Code's environment, not the environment of the command
being inspected.

## Changing it

The guard blocks agents from editing `~/.claude/hooks` and `~/.claude/settings*.json`,
by design — otherwise a session in bypass mode can disarm the thing inspecting
it and the next command goes through unexamined. So an agent can propose a
change to `scripts/dangerous-command-guard.sh` and run the tests against it,
but installing it is a human step. That asymmetry is the point.
