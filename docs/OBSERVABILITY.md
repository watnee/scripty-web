# Observability

Scripty exposes metrics, health, and build info through Spring Boot Actuator +
Micrometer, logs structured JSON in production, and tags every log line with a
request correlation id.

## Quick start

```bash
npm run obs:open     # open the production Grafana dashboards (creates the Railway domain on first run)
npm run obs:up       # local Prometheus + Grafana via Docker (no manual setup steps)
npm run obs:doctor   # read-only health check of the whole pipeline: services, domain, scrape endpoint, credentials
npm run obs:down     # stop the local stack
```

All commands are idempotent (`scripts/observability.sh`). `obs:open` prints
the Grafana URL; on the very first visit log in as `admin/admin` — Grafana
forces a password change, and the new password persists on the `grafana-volume`
across redeploys.

## Endpoints

| Endpoint | Access | Purpose |
|---|---|---|
| `/health` | public | Static liveness check (Railway `healthcheckPath`) |
| `/actuator/health` | public | Real health: DB, disk, liveness/readiness probes. Details only for authenticated admins. |
| `/actuator/health/liveness`, `/actuator/health/readiness` | public | Kubernetes-style probes |
| `/actuator/prometheus` | `Authorization: Bearer $METRICS_TOKEN` | Prometheus scrape endpoint |
| `/actuator/metrics`, `/actuator/info` | `ROLE_ADMIN` session | Ad-hoc inspection; `/actuator/info` includes Maven build version + timestamp |

In the **dev profile** everything is open (the dev security chain permits all).

## Environment variables (Railway)

| Variable | Effect |
|---|---|
| `METRICS_TOKEN` | Enables `/actuator/prometheus` for scrapers. Unset → endpoint stays closed (403). |
| `LOG_FORMAT` | Prod log format. Defaults to `ecs` (JSON). Set to empty for plain text. |

## Logs

Production logs are ECS JSON on stdout, so Railway's log search can filter by
field. Every request gets a `request_id` MDC field, taken from Cloudflare's
`CF-Ray` header when present (so app logs correlate with Cloudflare logs) and
echoed back to clients as `X-Request-Id`.

Example Railway log queries: `@log.level:ERROR`, `@request_id:<cf-ray>`.

## Metrics

Micrometer publishes JVM, HikariCP, Tomcat, and `http_server_requests` metrics
(with latency histograms) tagged `application="scripty"`.

To scrape from Grafana Cloud or any hosted Prometheus, point it at
`https://<app-domain>/actuator/prometheus` with bearer token `$METRICS_TOKEN`.

### Verification

`npm run obs:doctor` checks all of this in one shot: the Railway services
exist, Grafana has a domain and rejects `admin/admin`, `METRICS_TOKEN` is set,
and the scrape endpoint rejects tokenless requests and returns 200 with the token.

To verify the metrics endpoint by hand:

```bash
# Verify it blocks requests without authorization (302 redirect to /login; no metrics served)
curl -I https://web-production-ce5bc3.up.railway.app/actuator/prometheus

# Verify it responds with metrics when authorization header is set (should return HTTP 200 and Prometheus metrics)
curl -H "Authorization: Bearer <METRICS_TOKEN>" https://web-production-ce5bc3.up.railway.app/actuator/prometheus
```

You can find the active `METRICS_TOKEN` configured in your Railway dashboard variables or by running:
```bash
railway variables
```

## Local Prometheus + Grafana

```bash
npm run obs:up     # creates prometheus/token if missing, starts the stack, waits for health
npm run obs:down   # stop
```

- Prometheus: <http://localhost:9090> (scrapes the app on `localhost:8080` every 15s)
- Grafana: <http://localhost:3000> (admin/admin, Prometheus pre-provisioned)

The token file starts empty, which is fine for the dev profile; put a real
`METRICS_TOKEN` in `observability/prometheus/token` to scrape a prod-like
instance.

Good starter dashboards to import in Grafana: **4701** (JVM Micrometer) and
**17175** (Spring Boot 3 HTTP observability).
