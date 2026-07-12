# Claude Code routines

Scheduled Claude Code agents that keep an eye on the project. This file is the
source of truth for their prompts and schedules — if a routine is ever deleted
or recreated, copy the prompt from here.

Design rule: all pass/fail logic lives in deterministic repo scripts; the
routine only invokes them, interprets the result, and reports (GitHub issues
via `gh`). Success is quiet; failure is an issue.

| Routine | Schedule (UTC) | Script | CI fallback |
|---|---|---|---|
| Backup verification | `0 9 * * 1` (Mon 09:00) | `npm run db:verify` | [`verify-backup.yml`](../.github/workflows/verify-backup.yml) |
| Dependency & CVE report | `0 9 * * 3` (Wed 09:00) | `npm run deps:report` | — |

## Routine 1: Backup verification

Cron: `0 9 * * 1` (Monday 09:00 UTC — 2h after that morning's 07:00 UTC backup).

Prompt:

> You are running in the Scripty repo. Verify the off-platform MySQL backups in Cloudflare R2.
>
> 1. Run `npm run db:verify` (wrapper for `scripts/verify-backup.sh`; uses local wrangler OAuth or CLOUDFLARE_API_TOKEN). Do not write your own R2 or MySQL logic — the script is the source of truth.
> 2. Also run `./scripts/railway-mysql-backups.sh status` to check the on-platform Railway snapshot layer (exits non-zero if unhealthy).
> 3. If BOTH pass: finish quietly. Your final message should be a one-line summary (newest dump name, size, age).
> 4. If either fails: first check whether an open GitHub issue labeled `backup` already covers it (`gh issue list --label backup --state open`). If yes, add a comment with today's script output. If no, create one titled "Backup verification failed YYYY-MM-DD" containing the full script output, your diagnosis of the likely cause (e.g. the backup-db.yml workflow failed — check `gh run list --workflow backup-db.yml --limit 3`), and a link to docs/BACKUP.md.
> 5. Safe remediation you MAY attempt: re-trigger the backup with `gh workflow run backup-db.yml`, wait for completion with `gh run watch`, and re-run `npm run db:verify`. Note the outcome in the issue. Never run restore-mysql.sh, never modify the database, never delete R2 objects.
> 6. Rules: never use ./start-test-server.command or ./restart-test-server.command; you do not need a dev server for this task.

## Routine 2: Dependency & CVE report

Cron: `0 9 * * 3` (Wednesday 09:00 UTC — separated from the Monday backup check).

Prompt:

> You are running in the Scripty repo (Spring Boot pom.xml + package.json).
>
> 1. Run `npm run deps:report` (wrapper for `scripts/dependency-report.sh`; it auto-detects Java 17). It prints a markdown report; exit code 1 means high/critical npm vulnerabilities exist.
> 2. Find the rolling report issue: `gh issue list --label dependencies --state open --search "Weekly dependency report"`. If it exists, replace its body with the new report via `gh issue edit --body-file`; otherwise create it titled "Weekly dependency report" with label `dependencies`.
> 3. If the report contains NEW high/critical vulnerabilities compared to the previous issue body, also add a comment starting with "New high/critical:" listing them — comments trigger notifications, body edits do not.
> 4. Do NOT open PRs, do NOT modify pom.xml or package.json, do NOT run `npm audit fix` — this routine is report-only. The report's remediation section lists the exact commands.
> 5. Finish with a one-line summary: counts of outdated Maven deps, outdated npm deps, and vulns by severity.
> 6. Rules: never use ./start-test-server.command or ./restart-test-server.command; you do not need a dev server for this task.
