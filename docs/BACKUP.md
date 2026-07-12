# Database backups

Scripty production data lives in **Railway MySQL**. Screenplay **Snapshot History** (`project_version` JSON) is user-facing version history inside that database — it is **not** a substitute for infrastructure backups. If the database is lost, snapshots are lost with it.

This project uses two layers:

1. **Railway volume backups** — same-project restore of the MySQL volume (Daily / Weekly / Monthly)
2. **Daily `mysqldump` to Cloudflare R2** — off-platform copies via GitHub Actions

## Railway volume backups

Railway snapshots the volume attached to the MySQL service (`mysql-volume` at `/var/lib/mysql`).

| Schedule | Cadence | Retention |
|----------|---------|-----------|
| Daily | every 24h | ~6 days |
| Weekly | every 7 days | ~1 month |
| Monthly | every 30 days | ~3 months |

### Enable / inspect (CLI)

```bash
chmod +x scripts/railway-mysql-backups.sh
./scripts/railway-mysql-backups.sh status    # schedules + backups (exits 1 if unhealthy)
./scripts/railway-mysql-backups.sh ensure    # enable schedules + snapshot if none exist
./scripts/railway-mysql-backups.sh enable    # Daily + Weekly + Monthly
./scripts/railway-mysql-backups.sh snapshot  # manual backup now
```

The helper auto-resolves the `mysql-volume` instance from the linked Railway project (override with `MYSQL_VOLUME_INSTANCE_ID` / `RAILWAY_ENVIRONMENT_ID` if needed). Requires `railway login`. Schedules can also be set in the dashboard: **MySQL** → **Backups** → **Edit Schedule**.

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
- **Checks:** non-empty size floor, `gunzip -t`, SQL content sniff, SHA-256 in the Actions summary
- **Upload:** `wrangler r2 object put` (uses `CLOUDFLARE_API_TOKEN`)
- **Retention:** prefer an R2 **lifecycle rule** to expire objects after 30 days

### One-time setup

1. **Create an R2 bucket** (e.g. `scripty-db-backups`) in Cloudflare → R2.
2. Ensure `CLOUDFLARE_API_TOKEN` can edit R2 objects (Account → Workers R2 Storage → Edit, or Object Read & Write on that bucket).
3. On the Railway MySQL service, note the **TCP Proxy** public host and port (needed for Actions to reach the DB).
4. Add GitHub Actions secrets (**Settings → Secrets and variables → Actions**):

   | Secret | Purpose |
   |--------|---------|
   | `MYSQLHOST` | Railway MySQL TCP proxy host |
   | `MYSQLPORT` | Railway MySQL TCP proxy port |
   | `MYSQLUSER` | MySQL user |
   | `MYSQLPASSWORD` | MySQL password |
   | `MYSQLDATABASE` | Database name |
   | `CLOUDFLARE_API_TOKEN` | Token with Workers + **R2 edit** |
   | `CLOUDFLARE_ACCOUNT_ID` | Cloudflare account ID |
   | `R2_BUCKET` | Bucket name (e.g. `scripty-db-backups`) |

   The `MYSQL*` / `CLOUDFLARE_*` secrets are the same ones used by the Cloudflare deploy job.

5. Run **Actions → Backup database → Run workflow** once to verify upload.
6. (Recommended) Set an R2 lifecycle rule: expire objects after **30 days**.

### Automated Recovery / Restore from R2

You can restore a database backup directly from Cloudflare R2 into your target MySQL database using the automated script.

#### Restore via script (Local / CLI)

Run the restore script with the backup filename as the first argument. If you omit the filename (or specify `latest`), it will automatically query the Cloudflare API to detect and download the most recent backup file:

```bash
# Set coordinates (falls back to MYSQLHOST etc. if MYSQL_BACKUP_* are not defined)
export R2_BUCKET=scripty-db-backups
export CLOUDFLARE_API_TOKEN=...
export CLOUDFLARE_ACCOUNT_ID=...

# Auto-detect and restore the latest backup
./scripts/restore-mysql.sh

# Or restore a specific backup
./scripts/restore-mysql.sh scripty-YYYYMMDD-HHMMSS.sql.gz
```

The script will validate the backup integrity (gzip structure + SQL headers), prompt for confirmation, restore the data, and print a verification report with table row counts.

#### Restore via GitHub Actions (CI / Cloud)

For safety and ease of use, you can trigger a restore directly from the GitHub Actions tab:

1. Go to **Actions** -> **Restore database** -> **Run workflow**.
2. Input the backup filename (default: `latest`).
3. Input the target database name (default: `scripty`).
4. Check the **CONFIRM RESTORE** box.
5. Click **Run workflow**.

Warning: Restoring will overwrite all existing data in the target database. Make sure to stop application services or put them in offline mode during the import to prevent dirty writes.

### Manual dump (local)

With the same env vars as the workflow (or after `npx wrangler login`):

```bash
export R2_BUCKET=scripty-db-backups
# MYSQLHOST/PORT/USER/PASSWORD/DATABASE = Railway TCP proxy values
./scripts/backup-mysql.sh
```

Requires `mysqldump`, `gzip`, `gunzip`, `openssl`, and Node/`npx wrangler`.

## Snapshot History retention (app)

Auto-saves in Snapshot History are pruned per screenplay edition: the newest **30** auto-saves are kept. Manually named snapshots and “Before restore …” entries are never pruned by this policy.

## Checklist

- [x] Railway MySQL: Daily + Weekly + Monthly volume backups enabled
- [x] R2 bucket created (`scripty-db-backups`)
- [x] R2 30-day lifecycle rule (`expire-30d`)
- [x] `CLOUDFLARE_API_TOKEN` includes R2 edit
- [x] `MYSQL*` / `CLOUDFLARE_*` / `R2_BUCKET` GitHub secrets set
- [x] Manual **Backup database** workflow succeeded once
- [x] You know how to restore from Railway and from an R2 dump using scripts/workflows

