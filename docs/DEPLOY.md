# Deploying Scripty from scratch (Railway + Cloudflare)

Everything needed to take a fresh clone (or a brand-new Railway/Cloudflare
account) to a running production deploy on **both** platforms, driven by one
script:

```bash
npm ci                              # wrangler et al. (devDependencies)
npm run deploy:doctor               # read-only: what exists, what's missing, how to fix it
./scripts/bootstrap-deploy.sh all   # railway → secrets → cloudflare → verify
```

Every stage is idempotent — re-run any of them at any time. `doctor` never
changes anything and exits non-zero while something required is missing, so it
doubles as a setup audit in scripts.

## Prerequisites

| Tool | Needed for | Install / login |
|------|-----------|-----------------|
| Railway CLI | `railway`, `secrets`, `verify` | `railway login` |
| GitHub CLI (`gh`) | `secrets`, doctor's secret checks | `gh auth login` |
| Node 22+ | wrangler (devDependency) | `npm ci` |
| Docker (running) | `cloudflare` stage only (container image build) | CI can do this deploy instead |
| Cloudflare account | Workers **Paid** plan (Containers requirement) | `npx wrangler login` (OAuth — no token needed) |

## The stages

### 1. `./scripts/bootstrap-deploy.sh railway`

- Links the repo to a Railway project (`railway link`) or creates one
  (`railway init -n scripty`).
- Previews and (with confirmation) applies **`.railway/railway.ts`** — the
  IaC source of truth that provisions the `web` service (Dockerfile build,
  `/health` check, prod start command), managed `MySQL`, and both volumes.
- Generates a Railway domain for `web` if none exists and sets
  `APP_BASE_URL` from it.
- Ensures MySQL volume snapshot schedules (Daily/Weekly/Monthly) via
  `scripts/railway-mysql-backups.sh ensure`.
- Pushes the `RAILWAY_SERVICE_ID` GitHub Actions secret.

After this stage, also set the app's remaining env vars on `web` when you
have them: `RESEND_API_KEY`, `MAIL_FROM`, `METRICS_TOKEN` (see
[OBSERVABILITY.md](OBSERVABILITY.md)). They are declared `preserve()` in the
IaC so applies never clobber them.

### 2. `./scripts/bootstrap-deploy.sh secrets`

Pushes the GitHub Actions secrets CI deploys with:

- `RAILWAY_SERVICE_ID` — resolved automatically from the linked project.
- `RAILWAY_TOKEN` — the **only** credential no CLI can mint. The script
  prompts once (Railway dashboard → Project → Settings → Tokens, scope
  `production`) and pushes it; the value is never written to disk.
- `CLOUDFLARE_API_TOKEN` + `CLOUDFLARE_ACCOUNT_ID` — minted by
  `scripts/cf-token.sh setup` via the Cloudflare API (no dashboard token
  creation; see [CLOUDFLARE.md](CLOUDFLARE.md) for bootstrap credentials).
- `MYSQL*` fallback secrets — synced from Railway's MySQL TCP proxy so
  Cloudflare deploys work even without `RAILWAY_TOKEN`.

Optional extras:

- `npm run cf:token:seed` — seeds `CLOUDFLARE_BOOTSTRAP_TOKEN` so CI can
  mint its own short-lived deploy tokens forever (trade-off documented in
  [CLOUDFLARE.md](CLOUDFLARE.md)).
- R2 backup secrets for the nightly `mysqldump` workflow — see
  [BACKUP.md](BACKUP.md).

### 3. `./scripts/bootstrap-deploy.sh cloudflare`

First Worker deploy (needs Docker running locally):

- Writes the Worker's MySQL secrets from Railway's **public TCP proxy**
  (Cloudflare cannot reach `*.railway.internal`).
- `wrangler deploy --secrets-file` — builds the repo-root `Dockerfile`
  (same image Railway runs) and deploys the Worker + container, passing the
  required secrets in one shot (a first deploy cannot use `wrangler secret put`
  because the Worker does not exist yet).
- Caches the printed `workers.dev` URL in `cloudflare/.worker-url`
  (gitignored) so `verify` can find it.

No Docker locally? Skip this stage and let CI do it: push to `main` or run
**Actions → CI/CD → Run workflow** once the secrets stage is done.

### 4. `./scripts/bootstrap-deploy.sh verify`

Curls `/health` on the Railway domain and the Worker URL. The first container
cold start on Cloudflare can take a few minutes.

## One manual dashboard step

If the Railway service was ever connected to GitHub, **turn off Railway
auto-deploy** (service → Settings → Source) so pushes are not deployed twice —
once by Railway and once by GitHub Actions. Fresh IaC-created projects don't
have this problem.

## After bootstrap: steady state

- **All production deploys run from `main`** via GitHub Actions (Railway and
  Cloudflare in parallel) — see the root [README](../README.md).
- MySQL password rotated? `npm run cf:sync` (or **Run workflow** with
  `sync_secrets_only`) re-aligns Worker + GitHub secrets without a rebuild.
- Cloudflare CI token dead? `npm run cf:token:rotate` — or nothing at all if
  `CLOUDFLARE_BOOTSTRAP_TOKEN` is seeded (CI self-provisions).
- Drift check any time: `npm run deploy:doctor` and `npm run cf:sync:check`.

## Related docs

- [CLOUDFLARE.md](CLOUDFLARE.md) — Worker/Containers details, token
  provisioning, secret sync internals
- [BACKUP.md](BACKUP.md) — volume snapshots + nightly R2 `mysqldump`
- [OBSERVABILITY.md](OBSERVABILITY.md) — metrics endpoint, Grafana/Prometheus
