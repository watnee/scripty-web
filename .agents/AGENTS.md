# Project Rules

This file defines rules and guidelines specific to the Scripty workspace.

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
