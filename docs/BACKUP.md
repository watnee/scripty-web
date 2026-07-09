# Database backups

Scripty production data lives in **Railway MySQL**. Screenplay **Snapshot History** (`project_version` JSON) is user-facing version history inside that database — it is **not** a substitute for infrastructure backups. If the database is lost, snapshots are lost with it.

This project uses two layers:

1. **Railway volume backups** — same-project restore of the MySQL volume
2. **Daily `mysqldump` to Cloudflare R2** — off-platform copies via GitHub Actions

## Railway volume backups

Railway can snapshot the volume attached to the MySQL service.

### Enable (one-time, dashboard)

1. Open the Railway project → **MySQL** service → **Backups** tab.
2. Enable **Daily** (kept ~6 days) and **Weekly** (kept ~1 month).
3. Optionally trigger a **manual** backup after enabling.

Schedules are configured in the Railway UI only (not in `railway.json`).

### Restore a volume backup

1. MySQL service → **Backups** → pick a dated backup → **Restore**.
2. Review the staged change on the project canvas (a new volume is mounted; the previous volume is unmounted but retained).
3. Click **Deploy** to apply.

Notes from Railway:

- Restores only into the **same project and environment**.
- Restoring removes newer backups after the chosen point; older backups remain.
- Wiping a volume deletes its backups.

## Off-platform dumps (Cloudflare R2)

Workflow: [`.github/workflows/backup-db.yml`](../.github/workflows/backup-db.yml)  
Script: [`scripts/backup-mysql.sh`](../scripts/backup-mysql.sh)

- **Schedule:** daily at 07:00 UTC (`0 7 * * *`), plus manual **Run workflow**
- **Format:** `scripty-YYYYMMDD-HHMMSS.sql.gz`
- **Retention:** prefer an R2 **lifecycle rule** to expire objects after 30 days

### One-time setup

1. **Create an R2 bucket** (e.g. `scripty-db-backups`) in Cloudflare → R2.
2. Create an R2 API token with Object Read & Write on that bucket.
3. On the Railway MySQL service, note the **TCP Proxy** public host and port (needed for Actions to reach the DB).
4. Add GitHub Actions secrets (**Settings → Secrets and variables → Actions**):

   | Secret | Purpose |
   |--------|---------|
   | `MYSQL_BACKUP_HOST` | Railway MySQL TCP proxy host |
   | `MYSQL_BACKUP_PORT` | Railway MySQL TCP proxy port |
   | `MYSQL_BACKUP_USER` | MySQL user |
   | `MYSQL_BACKUP_PASSWORD` | MySQL password |
   | `MYSQL_BACKUP_DATABASE` | Database name |
   | `R2_ACCOUNT_ID` | Cloudflare account ID |
   | `R2_ACCESS_KEY_ID` | R2 API access key ID |
   | `R2_SECRET_ACCESS_KEY` | R2 API secret access key |
   | `R2_BUCKET` | Bucket name (e.g. `scripty-db-backups`) |

5. Run **Actions → Backup database → Run workflow** once to verify upload.
6. (Recommended) Set an R2 lifecycle rule: expire objects after **30 days**.

### Download a dump from R2

```bash
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
export AWS_ENDPOINT_URL="https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com"

aws s3 ls "s3://${R2_BUCKET}/"
aws s3 cp "s3://${R2_BUCKET}/scripty-YYYYMMDD-HHMMSS.sql.gz" .
```

### Restore a dump into MySQL

Prefer restoring into a **new** empty database (or a staging MySQL) and verifying before pointing production at it.

```bash
gunzip -c scripty-YYYYMMDD-HHMMSS.sql.gz | mysql \
  -h "$MYSQL_BACKUP_HOST" \
  -P "$MYSQL_BACKUP_PORT" \
  -u "$MYSQL_BACKUP_USER" \
  -p"$MYSQL_BACKUP_PASSWORD" \
  "$MYSQL_BACKUP_DATABASE"
```

Warning: restoring over a live production database replaces all data. Stop the app (or take the service offline) during restore to avoid writes mid-import.

### Manual dump (local)

With the same env vars as the workflow:

```bash
./scripts/backup-mysql.sh
```

Requires `mysqldump`, `gzip`, and AWS CLI v2 (or compatible) configured for R2 via `AWS_ENDPOINT_URL`.

## Snapshot History retention (app)

Auto-saves in Snapshot History are pruned per screenplay edition: the newest **30** auto-saves are kept. Manually named snapshots and “Before restore …” entries are never pruned by this policy.

## Checklist

- [ ] Railway MySQL: Daily + Weekly volume backups enabled
- [ ] R2 bucket + API token created
- [ ] R2 30-day lifecycle rule (recommended)
- [ ] All `MYSQL_BACKUP_*` and `R2_*` GitHub secrets set
- [ ] Manual **Backup database** workflow succeeded once
- [ ] You know how to restore from Railway and from an R2 dump
