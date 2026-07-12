# Scripty Workspace Guide & Rules

This file is the single source of truth for Claude Code. It contains common developer commands and project rules.

## Commands

- **Start test server (Local)**: `./start-test-server.command`
- **Restart test server**: `./restart-test-server.command`
- **Headless server (agents/routines/CI)**: `scripts/dev-server.sh {start|stop|restart|status|wait|logs}`
- **Build**: `mvn clean package` or `mvn compile`
- **Test**: `mvn verify`
- **Deploy from scratch / audit deploy setup**: `npm run deploy:doctor` (read-only) or `./scripts/bootstrap-deploy.sh all` (see docs/DEPLOY.md)
- **Run Cloudflare dev**: `npm run cf:dev`
- **Deploy Cloudflare**: `npm run cf:deploy`
- **Sync secrets**: `npm run cf:sync`
- **Provision/rotate CI Cloudflare API token**: `npm run cf:token` (no dashboard token creation)
- **Sync project rules**: `npm run rules:sync`

## Project Rules

Refer to the rules below before writing or modifying code.

### Git Commit & Push Proactivity

- When a task or implementation plan is fully implemented and successfully verified (e.g., builds compile, tests pass, server starts), the agent should proactively check `git status`, prepare a descriptive commit message, and commit and push the changes to the remote branch (`main`).
- Do not wait for the user to explicitly type "push" or ask to commit. Proactively run:
  `git status`, `git add <modified files>`, `git commit -m "..."`, and `git push`.

---

### Mobile Cursor → Railway

- Production deploys only from **`main`** (GitHub Actions → Railway + Cloudflare). Cursor Mobile cloud agents work on **`cursor/*`** branches — those are not live until they land on `main`.
- Push to `cursor/*` with no PR → CI opens PR, verifies, and ships. Existing PR → verify → auto-ship. Deploy uses skip_verify. Skip auto-ship with PR labels `hold` or `no-ship`.
- If the user asks to deploy mobile changes and auto-ship stalled: `./scripts/ship-mobile-changes.sh` then prefer `--ensure-pr` (CI ships) over `--apply` (local cherry-pick). Use `--prune` for stale remote `cursor/*` branches.
- Never claim a change is on Railway while it only exists on a `cursor/*` branch or open draft PR.
- When shipping CSS/JS/static UI fixes, bump asset / service-worker cache versions so clients pick up the new build.

---

### Persistent H2 Database Setup

- For local development and testing environments (e.g., in `application-dev.yml` or `application.properties`), avoid using in-memory databases (`jdbc:h2:mem:...`) as it causes data loss across server restarts.
- Always use persistent, file-based configurations:
  `jdbc:h2:file:./db/<project_name>;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;AUTO_SERVER=TRUE`
- Ensure that the local database directory (e.g., `/db/`) is ignored in the project's `.gitignore` file to prevent committing local data.

---

### Headless Server for Agents and Routines

- Claude Code routines, cloud agents, and CI must never use `./start-test-server.command` or `./restart-test-server.command` — those are interactive (foreground Maven, open a browser). Use the headless lifecycle script instead:
  `scripts/dev-server.sh {start|stop|restart|status|wait|logs [n]}`
- `start` runs the dev server in the background, waits for `http://localhost:$PORT/actuator/health` to report `UP` (up to `STARTUP_TIMEOUT`, default 180s), and prints `READY`/`ERROR` with log excerpts — its exit code is safe to branch on in automation.
- Server output goes to `scripty-server-<port>.log` and the PID to `scripty-server-<port>.pid` in the repo root (both gitignored). Use `scripts/dev-server.sh logs 200` to inspect output.
- Set `PORT` to run per-worktree servers side by side (default 8080); each worktree keeps its own `./db/` H2 files, so parallel agents do not share state.
- The script auto-detects Java 17 (Homebrew paths, `/usr/libexec/java_home`, `/usr/lib/jvm`); a routine only needs Java 17 and Maven installed.
- Verify changes with `curl` against `http://localhost:$PORT` (dev profile auto-logs in as admin) and `/actuator/health` — do not assume a browser is available.

---

### Layout Alignment and Third-Party CSS Guardrails

- When aligning multiple control elements (such as select checkboxes, buttons, icons, or drag handles) horizontally inside table cells or flex layouts, do not rely on standard inline block behavior if frameworks like `missing.css` are present.
- Always wrap these groups of controls in an explicit flexbox or inline-flex container:
  ```css
  .controls-wrapper {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      vertical-align: middle;
  }
  ```
