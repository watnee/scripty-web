# Observability

Scripty exposes metrics, health, and build info through Spring Boot Actuator +
Micrometer, logs structured JSON in production, and tags every log line with a
request correlation id.

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

## Local Prometheus + Grafana

```bash
cd observability
cp prometheus/token.example prometheus/token   # empty/dummy is fine for dev profile
docker compose up -d
```

- Prometheus: <http://localhost:9090> (scrapes the app on `localhost:8080` every 15s)
- Grafana: <http://localhost:3000> (admin/admin, Prometheus pre-provisioned)

Good starter dashboards to import in Grafana: **4701** (JVM Micrometer) and
**17175** (Spring Boot 3 HTTP observability).
