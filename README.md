# Scripty

## CI/CD

GitHub Actions runs Maven verify on every pull request and on `main`. After verify succeeds on `main` (or a manual **Run workflow**), the app deploys to **Railway and Cloudflare in parallel**. Each deploy job waits for its platform to finish before succeeding.

```text
PR opened/updated  →  Verify (Maven)
Push to main       →  Verify (Maven)  →  Deploy Railway ∥ Deploy Cloudflare
Actions → Run workflow  →  Verify (Maven)  →  Deploy Railway ∥ Deploy Cloudflare
```

### One-time setup

1. **Add secrets** — repo **Settings → Secrets and variables → Actions**:

   | Secret | Where to get it |
   |--------|-----------------|
   | `RAILWAY_TOKEN` | Railway → Project → Settings → Tokens (production environment) |
   | `RAILWAY_SERVICE_ID` | Railway → your web service → Settings → copy service ID |
   | `CLOUDFLARE_API_TOKEN` | Cloudflare API token with Workers Scripts Edit + Containers |
   | `CLOUDFLARE_ACCOUNT_ID` | (optional) Cloudflare account ID if the token can see multiple accounts |
   | `MYSQLHOST` / `MYSQLPORT` / `MYSQLUSER` / `MYSQLPASSWORD` / `MYSQLDATABASE` | (fallback) Railway MySQL **TCP proxy** host/port + credentials — only needed if `RAILWAY_TOKEN` is missing; CI prefers syncing these from Railway automatically |

2. **Turn off Railway auto-deploy** for this service (Settings → Source / GitHub) so pushes are not deployed twice — once by Railway and once by Actions.

3. **Cloudflare Worker secrets** — keep aligned with Railway via:

   ```bash
   ./scripts/sync-railway-cloudflare.sh sync
   ```

   Details: [docs/CLOUDFLARE.md](docs/CLOUDFLARE.md). CI also rewrites Worker MySQL secrets from Railway’s TCP proxy on each Cloudflare deploy when `RAILWAY_TOKEN` is set.

4. **Optional:** approve the `production` environment the first time Actions asks (Settings → Environments). That gate is intentional for deploys.

### Reading a run

- **Verify (Maven)** — always runs; PRs stop here.
- **Deploy to Railway** / **Deploy to Cloudflare** — both run after verify on `main` (push or **Run workflow**); they do not block each other. Either can fail independently.
- Railway: fails fast if secrets are missing; uses `railway up --ci` so a red job means the Railway release failed (build or healthcheck).
- Cloudflare: uses `wrangler deploy` (builds the root `Dockerfile` + Worker under `cloudflare/`).
- Open the job’s **Summary** tab for a short status table and next-step hints.

### Deploy config

- `railway.json` — Dockerfile builder, start command, `/health` healthcheck, restart policy (Config as Code; used by `railway up`)
- `.railwayignore` — keeps `railway up` uploads small (skips `target/`, local DBs, logs, etc.)
- `.railway/railway.ts` — Infrastructure as Code (web + MySQL + uploads volume). Preview with `railway config plan`; apply only after `railway link` and migrating off `railway.json` (a service cannot be managed by both)
- `Dockerfile` + `cloudflare/` — Cloudflare Containers Worker that proxies to the Spring Boot image; details in [docs/CLOUDFLARE.md](docs/CLOUDFLARE.md)
- `scripts/sync-railway-cloudflare.sh` — copies Railway MySQL **TCP proxy** credentials into Cloudflare Worker / GitHub Actions secrets so both platforms stay aligned

## Database backups

Production MySQL is backed up with Railway volume snapshots (Daily / Weekly / Monthly) plus a daily `mysqldump` to Cloudflare R2 (`scripty-db-backups`). Setup, secrets, and restore steps: [docs/BACKUP.md](docs/BACKUP.md).
