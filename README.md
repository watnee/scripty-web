# Scripty

## CI/CD

GitHub Actions runs Maven verify on every pull request and on `main`. After verify succeeds on `main` (or a manual **Run workflow**), the app deploys to Railway and the job **waits until `/health` passes** before succeeding.

```text
PR opened/updated  →  Verify (Maven)
Push to main       →  Verify (Maven)  →  Deploy to Railway (wait for health)
Actions → Run workflow  →  Verify (Maven)  →  Deploy to Railway (wait for health)
```

### One-time setup

1. **Add secrets** — repo **Settings → Secrets and variables → Actions**:

   | Secret | Where to get it |
   |--------|-----------------|
   | `RAILWAY_TOKEN` | Railway → Project → Settings → Tokens (production environment) |
   | `RAILWAY_SERVICE_ID` | Railway → your web service → Settings → copy service ID |

2. **Turn off Railway auto-deploy** for this service (Settings → Source / GitHub) so pushes are not deployed twice — once by Railway and once by Actions.

3. **Optional:** approve the `production` environment the first time Actions asks (Settings → Environments). That gate is intentional for deploys.

### Reading a run

- **Verify (Maven)** — always runs; PRs stop here.
- **Deploy to Railway** — only on `main` (push or **Run workflow** from `main`); fails fast if secrets are missing; uses `railway up --ci` so a red Actions job means the Railway release failed (build or healthcheck), not just “upload queued.”
- Open the job’s **Summary** tab for a short status table and next-step hints.

### Deploy config

- `railway.json` — Railpack builder, start command, `/health` healthcheck, restart policy
- `railpack.json` — Java 17 + start command for Railpack
- `.railwayignore` — keeps `railway up` uploads small (skips `target/`, local DBs, logs, etc.)
