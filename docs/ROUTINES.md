# Claude Code routines

Scheduled Claude Code agents that keep an eye on the project. This file is the
source of truth for their prompts and schedules — if a routine is ever deleted
or recreated, copy the prompt from here.

Design rule: all pass/fail logic lives in deterministic repo scripts; the
routine only invokes them, interprets the result, and reports (GitHub issues
via `gh`). Success is quiet; failure is an issue.

The routines are Claude Code scheduled tasks on the owner's Mac (task IDs
`weekly-backup-verification` and `weekly-dependency-report`; cron below is
US-Eastern local time). They run on next app launch if the Mac was asleep.

| Routine | Schedule (local) | Script | CI fallback |
|---|---|---|---|
| Backup verification | `0 5 * * 1` (Mon ~05:00, ≈09:00 UTC) | `npm run db:verify` | [`verify-backup.yml`](../.github/workflows/verify-backup.yml) |
| Dependency & CVE report | `0 5 * * 3` (Wed ~05:00) | `npm run deps:report` | — |

## Routine 1: Backup verification

Prompt:

> You are running in the Scripty repo at /Users/clintwatnee/Desktop/scripty (GitHub: watnee/scripty). Verify the off-platform MySQL backups in Cloudflare R2. The canonical copy of these instructions lives in docs/ROUTINES.md.
>
> 1. Run `npm run db:verify` (wrapper for scripts/verify-backup.sh). Do not write your own R2 or MySQL logic — the script is the source of truth. It needs CLOUDFLARE_API_TOKEN + CLOUDFLARE_ACCOUNT_ID for the R2 listing.
> 2. If it fails ONLY because Cloudflare credentials are missing locally, verify through CI instead: `gh workflow run verify-backup.yml`, wait with `gh run watch $(gh run list --workflow verify-backup.yml --limit 1 --json databaseId --jq '.[0].databaseId')`, then read the result with `gh run view --workflow verify-backup.yml`. A green run counts as a pass; a red run is a failure (fetch its logs with `gh run view <id> --log-failed`).
> 3. Also run `./scripts/railway-mysql-backups.sh status` to check the on-platform Railway snapshot layer (exits non-zero if unhealthy). If it needs Railway credentials that are unavailable, note that in your summary rather than failing.
> 4. If everything passes: finish quietly. Your final message should be a one-line summary (newest dump name, size, age — from the script's JSON summary line or the CI run summary).
> 5. If verification fails: check whether an open GitHub issue labeled `backup` already covers it (`gh issue list --label backup --state open`). If yes, add a comment with today's output. If no, create one titled "Backup verification failed YYYY-MM-DD" containing the full output, your diagnosis of the likely cause (e.g. the backup-db.yml workflow failed — check `gh run list --workflow backup-db.yml --limit 3`), and a link to docs/BACKUP.md.
> 6. Safe remediation you MAY attempt: re-trigger the backup with `gh workflow run backup-db.yml`, wait for completion, and re-verify (step 1 or 2). Note the outcome in the issue. Never run restore-mysql.sh, never modify the database, never delete R2 objects.
> 7. Rules: never use ./start-test-server.command or ./restart-test-server.command; you do not need a dev server for this task. Do not commit or push anything.

## Routine 2: Dependency & CVE report

Prompt:

> You are running in the Scripty repo at /Users/clintwatnee/Desktop/scripty (GitHub: watnee/scripty; Spring Boot pom.xml + package.json). Produce the weekly dependency & vulnerability report. The canonical copy of these instructions lives in docs/ROUTINES.md.
>
> 1. Run `npm run deps:report` (wrapper for scripts/dependency-report.sh; it auto-detects Java 17). It prints a markdown report to stdout; exit code 1 means high/critical npm vulnerabilities exist. Save the report to a temp file for step 2.
> 2. Find the rolling report issue: `gh issue list --label dependencies --state open --search "Weekly dependency report"`. If it exists, replace its body with the new report via `gh issue edit <number> --body-file <file>`; otherwise create it: `gh issue create --title "Weekly dependency report" --label dependencies --body-file <file>`.
> 3. Before overwriting, read the previous issue body (`gh issue view <number> --json body`). If the new report contains high/critical npm vulnerabilities or Dependabot alerts that were NOT in the previous body, also add a comment starting with "New high/critical:" listing them — comments trigger notifications, body edits do not.
> 4. Do NOT open PRs, do NOT modify pom.xml or package.json, do NOT run `npm audit fix` — this routine is report-only. The report's remediation section lists the exact commands.
> 5. Finish with a one-line summary: counts of outdated Maven deps, outdated npm deps, and vulns by severity.
> 6. Rules: never use ./start-test-server.command or ./restart-test-server.command; you do not need a dev server for this task. Do not commit or push anything.
