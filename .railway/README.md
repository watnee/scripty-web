# Railway configuration

This project defines its Railway infrastructure in code.

```txt
.railway/railway.ts
```

Scripty is a Spring Boot (Java 17 / Maven) app. The generated config wires:

- `web` — GitHub `watnee/scripty`, Dockerfile image layout (`scripty.jar`), `/health` check, prod start command
- `MySQL` — managed MySQL with JDBC vars for `application-prod.yml` (volume backups: Daily / Weekly / Monthly via `./scripts/railway-mysql-backups.sh ensure`)
- `uploads` — volume mounted at `/app/uploads`

**Coexistence note:** Deploy-time settings still live in root `railway.json` (used by GitHub Actions `railway up`). A service cannot be managed by both Config-as-Code and IaC. Keep `railway.json` until you intentionally migrate (see docs below).

Cloudflare Containers use the same root `Dockerfile`. Keep MySQL secrets aligned with:

```bash
./scripts/sync-railway-cloudflare.sh sync    # Cloudflare + GitHub MYSQL*
./scripts/sync-railway-cloudflare.sh check   # drift report
```

Use this file to describe the Railway project you want: services, databases, buckets, custom domains, replicas, groups, and environment variables.

## Common commands

Create the configuration files:

```bash
railway config init
```

Import an existing Railway project into code:

```bash
railway config pull
```

Preview what Railway would change:

```bash
railway config plan
```

Apply the planned changes:

```bash
railway config apply
```

## Notes

- `railway config plan` is safe and does not change Railway.
- `railway config apply` previews changes and asks before applying unless you pass `--yes`.
- Destructive changes in non-interactive or agent sessions require `railway config apply --confirm-destructive` after reviewing the plan.
- Services already managed by `railway.json` / `railway.toml` must be migrated before `.railway/railway.ts` can manage them.
- Use `replicas` for scaling; advanced placement can still specify region names.
- Use `group("Name", [resources])` to keep large projects organized on the Railway canvas.
- Secrets imported from Railway are rendered as `preserve()` so existing values are retained without writing secret values to source. Use `railway config pull --omit-preserved-variables` for a smaller import.
