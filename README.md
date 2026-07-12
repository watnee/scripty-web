# Scripty

## CI/CD

GitHub Actions runs Maven verify on every pull request and on `main`. After verify succeeds on `main` (or a manual **Run workflow**), the app deploys to **Railway and Cloudflare in parallel**. Each deploy job waits for its platform to finish before succeeding.

```text
Push to cursor/* (no PR)       →  Ensure PR + Verify + Ship → Deploy Railway ∥ Cloudflare
Push to cursor/* (PR open)     →  (no-op) pull_request sync → Verify → Ship → Deploy
PR opened/updated              →  Verify (Maven)
PR from cursor/* (mobile)      →  Verify → Ship to main → Deploy Railway ∥ Cloudflare (skip re-verify)
Push to main                   →  Verify (Maven)  →  Deploy Railway ∥ Deploy Cloudflare
Actions → Run workflow         →  Verify (Maven)  →  Deploy Railway ∥ Deploy Cloudflare
```

### Cursor Mobile → Railway

Cursor Mobile / cloud agents push to `cursor/*` branches. Those do **not** deploy until they are on `main`.

- **Automatic:** a push with no PR opens one, verifies, and ships; an existing PR verifies then auto-ships (unless labeled `hold` or `no-ship`). Deploy skips a second Maven run.
- **Manual:** list or ship pending branches:

  ```bash
  ./scripts/ship-mobile-changes.sh              # dry-run list (+ PR links)
  ./scripts/ship-mobile-changes.sh --ensure-pr  # open draft PRs; CI verifies + auto-ships
  ./scripts/ship-mobile-changes.sh --apply      # cherry-pick onto main and push
  ./scripts/ship-mobile-changes.sh --prune      # delete remote cursor/* already on main
  ```

### One-time setup

**Fast path:** the whole from-scratch setup (GitHub repo + Actions environment,
Railway project + IaC apply, secrets, first deploy on both platforms) is
scripted — see [docs/DEPLOY.md](docs/DEPLOY.md):

```bash
npm ci
npm run deploy:doctor               # read-only audit: what's missing + fix commands
./scripts/bootstrap-deploy.sh all   # github → railway → secrets → resend → cloudflare (or ci) → verify
```

Manual reference:

1. **Add secrets** — repo **Settings → Secrets and variables → Actions**:

   | Secret | Where to get it |
   |--------|-----------------|
   | `RAILWAY_TOKEN` | Railway → Project → Settings → Tokens (production environment) |
   | `RAILWAY_SERVICE_ID` | Railway → your web service → Settings → copy service ID |
   | `CLOUDFLARE_API_TOKEN` | **Automated:** `npm run cf:token` mints a scoped token via the Cloudflare API and pushes it here — no dashboard token creation ([docs/CLOUDFLARE.md](docs/CLOUDFLARE.md)) |
   | `CLOUDFLARE_ACCOUNT_ID` | set automatically by `npm run cf:token` |
   | `CLOUDFLARE_BOOTSTRAP_TOKEN` | (optional) `npm run cf:token:seed` — lets deploys self-provision: if the deploy token is ever missing/dead, CI mints a short-lived one on the spot |
   | `MYSQLHOST` / `MYSQLPORT` / `MYSQLUSER` / `MYSQLPASSWORD` / `MYSQLDATABASE` | (fallback) Railway MySQL **TCP proxy** host/port + credentials — only needed if `RAILWAY_TOKEN` is missing; CI prefers syncing these from Railway automatically |
   | `MYSQL_SSL_MODE` / `MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL` | (optional fallback) kept in sync by `./scripts/sync-railway-cloudflare.sh push-github` |

2. **Turn off Railway auto-deploy** for this service (Settings → Source / GitHub) so pushes are not deployed twice — once by Railway and once by Actions.

3. **Cloudflare Worker secrets** — keep aligned with Railway via:

   ```bash
   ./scripts/sync-railway-cloudflare.sh sync    # Cloudflare Worker + GitHub MYSQL* fallback
   ./scripts/sync-railway-cloudflare.sh check   # drift report
   ```

   Details: [docs/CLOUDFLARE.md](docs/CLOUDFLARE.md). CI rewrites Worker MySQL secrets from Railway’s TCP proxy on each Cloudflare deploy when `RAILWAY_TOKEN` is set. For a secrets-only refresh (no Maven / no image rebuild), use **Actions → Run workflow** with **sync_secrets_only**.

4. **Optional:** approve the `production` environment the first time Actions asks (Settings → Environments). That gate is intentional for deploys.

### Reading a run

- **Verify (Maven)** — always runs; PRs stop here.
- **Deploy to Railway** / **Deploy to Cloudflare** — both run after verify on `main` (push or **Run workflow**); they do not block each other. Either can fail independently.
- Railway: fails fast if secrets are missing; uses `railway up --ci` so a red job means the Railway release failed (build or healthcheck).
- Cloudflare: uses `wrangler deploy` (builds the root `Dockerfile` + Worker under `cloudflare/`).
- Open the job’s **Summary** tab for a short status table and next-step hints.

### Deploy config

- `.railway/railway.ts` — Infrastructure as Code, the source of truth for Railway (web service with Dockerfile build + `/health` healthcheck, MySQL, volumes, env vars). Preview with `railway config plan`, apply with `railway config apply`
- `.railwayignore` — keeps `railway up` uploads small (skips `target/`, local DBs, logs, etc.)
- `scripts/bootstrap-deploy.sh` — from-scratch bootstrap + setup audit (`doctor`/`github`/`railway`/`secrets`/`resend`/`cloudflare`/`ci`/`verify`); runbook in [docs/DEPLOY.md](docs/DEPLOY.md)
- `Dockerfile` + `cloudflare/` — Cloudflare Containers Worker that proxies to the Spring Boot image; details in [docs/CLOUDFLARE.md](docs/CLOUDFLARE.md)
- `scripts/sync-railway-cloudflare.sh` — copies Railway MySQL **TCP proxy** credentials into Cloudflare Worker / GitHub Actions secrets so both platforms stay aligned
- `scripts/cf-token.sh` — mints/rotates the scoped Cloudflare API token CI deploys with and pushes it to GitHub secrets (no manual token creation in the dashboard)

## Database backups

Production MySQL is backed up with Railway volume snapshots (Daily / Weekly / Monthly) plus a daily `mysqldump` to Cloudflare R2 (`scripty-db-backups`). Setup, secrets, and restore steps: [docs/BACKUP.md](docs/BACKUP.md).
