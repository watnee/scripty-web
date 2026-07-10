# Cloudflare Containers deploy for Scripty (Spring Boot jar).

Config and Worker live under `cloudflare/`; the container image is the repo-root `Dockerfile`.

## Prerequisites

1. Cloudflare account with **Workers Paid** (Containers requirement).
2. Docker available wherever you run `wrangler deploy` (local or CI).
3. MySQL reachable from the container (public host/port, or Hyperdrive later). Railway private networking is **not** reachable from Cloudflare — use the MySQL **TCP Proxy** host/port, same idea as the R2 backup workflow.

## One-time secrets

From `cloudflare/`:

```bash
cd cloudflare
cp .dev.vars.example .dev.vars   # local only
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

## Deploy

```bash
cd cloudflare
npx wrangler deploy
```

CI deploys this in parallel with Railway when `CLOUDFLARE_API_TOKEN` is set (see root README).

## Notes

- Uploads live on the container filesystem and are **ephemeral** (lost when the instance sleeps/stops). Prefer Railway for durable upload files, or move uploads to R2 later.
- Keep `APP_BASE_URL` pointing at the hostname you want users to hit for that environment.
- Railway and Cloudflare can share the same MySQL if Cloudflare uses the public TCP proxy. Expect two app processes against one DB (sessions/uploads are not shared).
