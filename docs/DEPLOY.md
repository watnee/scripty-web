# Deploying Scripty from scratch (GitHub + Railway + Cloudflare + Resend)

Everything needed to take a fresh clone (or brand-new GitHub/Railway/
Cloudflare/Resend accounts) to a running production deploy, driven by one
script:

```bash
npm ci                              # wrangler + Railway TS SDK (also needed by CI's IaC apply)
npm run deploy:doctor               # read-only: what exists, what's missing, how to fix it
./scripts/bootstrap-deploy.sh all   # github → railway → secrets → resend → cloudflare (or ci) → verify
```

Every stage is idempotent — re-run any of them at any time. `doctor` never
changes anything and exits non-zero while something required is missing, so it
doubles as a setup audit in scripts.

## Prerequisites

| Tool | Needed for | Install / login |
|------|-----------|-----------------|
| Railway CLI | `railway`, `secrets`, `verify` | `railway login` |
| GitHub CLI (`gh`) | `github`, `secrets`, `ci`, doctor's repo/secret checks | `gh auth login` |
| Node 22+ | wrangler (devDependency) | `npm ci` |
| Docker (running) | `cloudflare` stage only (container image build) | skip it — the `ci` stage deploys from Actions |
| Cloudflare account | Workers **Paid** plan (Containers requirement) | `npx wrangler login` (OAuth — no token needed) |
| Resend account | `resend` stage (production email) | one API key from [resend.com/api-keys](https://resend.com/api-keys) |

## The stages

### 1. `./scripts/bootstrap-deploy.sh github`

- Creates the GitHub repo if this clone has none (`gh repo create … --source=. --push`)
  and verifies the `origin` remote resolves.
- Enables GitHub Actions if the repo has it off.
- Ensures the **`production`** environment exists (deploy jobs target it).
  Optional manual gate: Settings → Environments → production → required reviewers.
- Warns if `.railway/railway.ts` builds from a different repo than this one
  (`github("owner/name")` must match, or Railway builds someone else's code).

### 2. `./scripts/bootstrap-deploy.sh railway`

- Links the repo to a Railway project (`railway link`) or creates one
  (`railway init -n scripty`).
- Previews and (with confirmation) applies **`.railway/railway.ts`** — the
  IaC source of truth that provisions the `web` service (Dockerfile build,
  `/health` check, prod start command, no volume — headshots live in MySQL)
  and managed `MySQL` with its volume.
- Generates a Railway domain for `web` if none exists and sets
  `APP_BASE_URL` from it.
- Ensures MySQL volume snapshot schedules (Daily/Weekly/Monthly) via
  `scripts/railway-mysql-backups.sh ensure`.
- Pushes the `RAILWAY_SERVICE_ID` GitHub Actions secret.

After this stage, also set `METRICS_TOKEN` on `web` when you have it (see
[OBSERVABILITY.md](OBSERVABILITY.md)); `RESEND_API_KEY`/`MAIL_FROM` are
handled by the `resend` stage. These are declared `preserve()` in the IaC so
applies never clobber them.

### 3. `./scripts/bootstrap-deploy.sh secrets`

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

### 4. `./scripts/bootstrap-deploy.sh resend`

Production email (password recovery) uses the **Resend HTTP API** — outbound
SMTP is restricted on Railway (`app.resend-api-key` in
`application-prod.yml`):

- Finds `RESEND_API_KEY` (env → Railway web service → one-time prompt; create
  keys at [resend.com/api-keys](https://resend.com/api-keys), "Sending access"
  is enough) and validates it against the Resend API without sending anything.
- Sets `RESEND_API_KEY` + `MAIL_FROM` on the Railway `web` service **and** as
  Worker secrets, so the Cloudflare container sends the same mail.
- Reports sender-domain deliverability: `onboarding@resend.dev` works out of
  the box but **only delivers to the Resend account owner's address** — for
  real users, verify a domain at [resend.com/domains](https://resend.com/domains)
  (publish its DNS records), then re-run this stage with a `MAIL_FROM` on that
  domain.

### 5. `./scripts/bootstrap-deploy.sh cloudflare` — or `ci` without Docker

`cloudflare` is the local first Worker deploy (needs Docker running):

- Writes the Worker's MySQL secrets from Railway's **public TCP proxy**
  (Cloudflare cannot reach `*.railway.internal`).
- `wrangler deploy --secrets-file` — builds the repo-root `Dockerfile`
  (same image Railway runs) and deploys the Worker + container, passing the
  required secrets in one shot (a first deploy cannot use `wrangler secret put`
  because the Worker does not exist yet).
- Caches the printed `workers.dev` URL in `cloudflare/.worker-url`
  (gitignored) so `verify` can find it.

No Docker locally? `./scripts/bootstrap-deploy.sh ci` instead: it dispatches
the **CI/CD** workflow on `main` and watches it — Actions builds and deploys
**both** platforms (`all` picks this path automatically when Docker is
unavailable). The Railway deploy job also runs `railway config apply` first,
so IaC changes land with every push to `main`; destructive plans are refused
in CI by design and must be applied deliberately from a laptop.

### 6. `./scripts/bootstrap-deploy.sh verify`

Curls `/health` on the Railway domain and the Worker URL. The first container
cold start on Cloudflare can take a few minutes.

## Zero-downtime deploys: no GitHub source, no volume on web

Two deliberate absences on the `web` service keep deploys downtime-free:

- **No GitHub source connected** — GitHub Actions deploys with
  `railway up --ci`, and a connected repo would auto-deploy every `main`
  push a second time. If a source ever gets reconnected (e.g. via the
  dashboard), disconnect it again:

  ```bash
  railway service source disconnect --service web
  ```

- **No volume mounted** — Railway cannot overlap old/new containers when a
  volume is attached (it mounts to one container at a time), so every deploy
  becomes a stop-start swap with an "Application failed to respond" window.
  Actor headshots are stored in MySQL (`actor_headshot` table, V35 migration)
  instead of on disk; with no volume, Railway keeps the old container serving
  until the new one passes `/health`.

`prometheus` and `grafana` keep their GitHub sources — CI does not `railway
up` those; Railway builds them from the repo when their watch patterns match.

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
