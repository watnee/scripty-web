# Cloudflare Containers deploy for Scripty (Spring Boot jar).

Config and Worker live under `cloudflare/`; the container image is the repo-root `Dockerfile` (same image Railway uses via `railway.json`).

## Prerequisites

1. Cloudflare account with **Workers Paid** (Containers requirement).
2. Docker available wherever you run `wrangler deploy` (local or CI).
3. MySQL reachable from the container. Railway private networking is **not** reachable from Cloudflare — use the MySQL **TCP Proxy** host/port (same idea as the R2 backup workflow).

## Keep Railway ↔ Cloudflare MySQL secrets in sync

Railway’s `web` service talks to MySQL on the private hostname. Cloudflare needs the **public TCP proxy** host/port plus the same credentials. Do not copy `MYSQLHOST=mysql.railway.internal` into the Worker.

```bash
chmod +x scripts/sync-railway-cloudflare.sh
./scripts/sync-railway-cloudflare.sh status          # TCP proxy + secret presence
./scripts/sync-railway-cloudflare.sh check           # drift report (local files + CF/GH names)
./scripts/sync-railway-cloudflare.sh write            # → cloudflare/.deploy-secrets
./scripts/sync-railway-cloudflare.sh write-dev        # → cloudflare/.dev.vars
./scripts/sync-railway-cloudflare.sh push-cloudflare  # wrangler secret bulk (no image rebuild)
./scripts/sync-railway-cloudflare.sh push-github      # gh secret set MYSQL* (+ SSL mode)
./scripts/sync-railway-cloudflare.sh sync             # Cloudflare + GitHub
```

Requires `railway login` (or `RAILWAY_TOKEN`). Prefers a linked project (`railway link`); falls back to the known production project id. Resolves the public TCP proxy from MySQL service vars or `railway tcp-proxy list`, and refuses private `*.railway.internal` hosts.

CI prefers this path on every Cloudflare deploy when `RAILWAY_TOKEN` is set. To refresh Worker secrets after a Railway MySQL password rotate **without** rebuilding the container image, use **Actions → Run workflow** with **sync_secrets_only**.

## One-time secrets (manual alternative)

From `cloudflare/`:

```bash
cd cloudflare
cp .dev.vars.example .dev.vars   # local only — or: ../scripts/sync-railway-cloudflare.sh write-dev
npx wrangler secret put MYSQLHOST
npx wrangler secret put MYSQLPORT
npx wrangler secret put MYSQLUSER
npx wrangler secret put MYSQLPASSWORD
npx wrangler secret put MYSQLDATABASE
# optional:
# npx wrangler secret put APP_BASE_URL
# npx wrangler secret put MYSQL_SSL_MODE
```

Or set them in the dashboard: Workers & Pages → **scripty** → Settings → Variables and Secrets.

`wrangler deploy` validates `secrets.required` before succeeding.

For a **first** deploy (Worker does not exist yet), pass secrets in one shot:

```bash
./scripts/sync-railway-cloudflare.sh write
cd cloudflare
npx wrangler deploy --secrets-file .deploy-secrets
rm -f .deploy-secrets
```

## Deploy

```bash
cd cloudflare
npx wrangler deploy
```

CI deploys this in parallel with Railway when `CLOUDFLARE_API_TOKEN` is set (see root README). MySQL secrets come from Railway via the sync script when `RAILWAY_TOKEN` is present, otherwise from GitHub `MYSQL*` secrets.

## Notes

- Uploads live on the container filesystem and are **ephemeral** (lost when the instance sleeps/stops). Prefer Railway for durable upload files, or move uploads to R2 later.
- Keep `APP_BASE_URL` pointing at the hostname you want users to hit for that environment.
- Railway and Cloudflare can share the same MySQL if Cloudflare uses the public TCP proxy. Expect two app processes against one DB (sessions/uploads are not shared).
- A Worker cron (`*/30 * * * *`) pings `/health` on the sticky container so the JVM stays warm under the 2h `sleepAfter` window and cold Spring Boot boots do not hit interactive traffic.
