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
| `TRACING_ENABLED` | Turns on OTLP trace export. Defaults to `false` — see [Tracing](#tracing). |
| `TRACING_SAMPLE_RATE` | Fraction of requests traced. Defaults to `0.1`. |
| `OTLP_TRACING_ENDPOINT` | Where spans are shipped, e.g. the Grafana Cloud Tempo OTLP URL. |
| `MANAGEMENT_OTLP_TRACING_HEADERS_AUTHORIZATION` | Auth header for the OTLP endpoint (`Basic <base64 instanceID:token>` for Grafana Cloud). |

## Logs

Production logs are ECS JSON on stdout, so Railway's log search can filter by
field. Every request gets a `request_id` MDC field, taken from Cloudflare's
`CF-Ray` header when present (so app logs correlate with Cloudflare logs) and
echoed back to clients as `X-Request-Id`.

Example Railway log queries: `@log.level:ERROR`, `@request_id:<cf-ray>`.

## Metrics

Micrometer publishes JVM, HikariCP, Tomcat, and `http_server_requests` metrics
(with latency histograms) tagged `application="scripty"`.

### Domain metrics

Infrastructure metrics tell you the JVM is healthy; these tell you the product
works. All of them carry only bounded tags — never a project id, username, or
email address, because Prometheus keeps one time series per tag combination and
an unbounded tag would grow the registry without limit.

| Metric | Tags | What it answers |
|---|---|---|
| `scripty_export_seconds` | `format`, `outcome` | Can users get scripts out, and how slowly? Timer with histogram, so p95/p99 per format. |
| `scripty_import_seconds` | `format`, `outcome` | Are uploads being accepted? Counts a returned `ImportOutcome(success=false)` as a failure, not just thrown exceptions. |
| `scripty_email_sent_total` | `transport`, `outcome` | Are password resets and invitations actually going out? `transport` separates the Cloudflare Worker from SMTP; `disabled` means no transport is configured. |
| `scripty_auth_events_total` | `event` | Sign-in success/failure ratio. |
| `scripty_errors_unhandled_total` | `exception`, `status` | Which exception types are reaching users. |

Exports and imports are instrumented by `ExportMetricsAspect`, which matches
service beans by name (`*ExportServiceImpl`, `*ImportServiceImpl`). A new
exporter is therefore instrumented the moment it is added — there is no
annotation to remember.

## Tracing

Micrometer Tracing with an OTLP exporter, **off by default**: with tracing on
and no reachable collector the exporter retries against `localhost:4318` and
floods the logs. Turn it on by setting all of `TRACING_ENABLED=true`,
`OTLP_TRACING_ENDPOINT`, and the authorization header.

For Grafana Cloud, take the OTLP endpoint and instance ID from the Tempo
"Details" page, then on Railway:

```bash
railway variables --set TRACING_ENABLED=true \
  --set OTLP_TRACING_ENDPOINT=https://tempo-prod-XX-prod-YY.grafana.net/otlp/v1/traces \
  --set "MANAGEMENT_OTLP_TRACING_HEADERS_AUTHORIZATION=Basic $(printf '%s:%s' "$INSTANCE_ID" "$TOKEN" | base64)"
```

Sampling defaults to 10% (`TRACING_SAMPLE_RATE`). Trace and span ids land in the
MDC automatically, so every ECS log line carries `trace_id` alongside
`request_id` — a slow trace in Tempo links straight to its Railway log lines.

## Alerting

`observability/prometheus/alerts.yml` holds the rules: app down, 5xx rate, p99
latency, Hikari pool exhaustion, heap pressure, low disk, and the domain rules
(exports failing, email failing, unhandled-error spike, login-failure spike).

One file serves both environments — the local Prometheus loads it via
`rule_files`, so a threshold can be tried at <http://localhost:9090/alerts>
before it is trusted to page anyone. Load it into Grafana Cloud with
[`mimirtool`](https://grafana.com/docs/mimir/latest/manage/tools/mimirtool/):

```bash
mimirtool rules load observability/prometheus/alerts.yml \
  --address=https://prometheus-prod-XX-prod-YY.grafana.net \
  --id=<instance id> --key=<access policy token>
```

Then in Grafana Cloud → **Alerting → Contact points**, add where alerts should
go and a notification policy routing `severity=critical` there. Rules only fire
once Grafana Cloud is scraping `/actuator/prometheus`
(Connections → Add new connection → Metrics endpoint, with the bearer
`$METRICS_TOKEN`).

Thresholds are starting points sized for modest traffic. Tune them against a few
weeks of real baseline rather than trusting them blind.

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
