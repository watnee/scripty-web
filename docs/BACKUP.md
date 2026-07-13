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

You can run the restore script using npm or directly. The script automatically detects your database coordinates if you are logged in to the `railway` CLI (via `railway login`) or if you have a local `cloudflare/.dev.vars` file.

```bash
# Auto-detect coordinates and restore the latest backup (defaults bucket to scripty-db-backups)
npm run db:restore

# Or restore a specific backup
npm run db:restore -- scripty-YYYYMMDD-HHMMSS.sql.gz
```

*Note: If auto-detecting the `latest` backup, the script will also attempt to resolve your `CLOUDFLARE_ACCOUNT_ID` automatically from the Cloudflare API if `CLOUDFLARE_API_TOKEN` is exported in the environment.*

The script will validate the backup integrity (gzip structure + SQL headers), prompt for confirmation, restore the data, and print a verification report with table row counts.

#### Restore via GitHub Actions (CI / Cloud)

For safety and ease of use, you can trigger a restore directly from the GitHub Actions tab:

1. Go to **Actions** -> **Restore database** -> **Run workflow**.
2. Input the backup filename (default: `latest`).
3. Input the target database name (default: `scripty`).
4. Check the **CONFIRM RESTORE** box.
5. Click **Run workflow**.

Warning: Restoring will overwrite all existing data in the target database. Make sure to stop application services or put them in offline mode during the import to prevent dirty writes.

### Manual dump / backup (local)

Like the restore process, the backup script automatically detects database coordinates via the `railway` CLI or local `.dev.vars`. You can run it via npm:

```bash
# Auto-detect credentials and dump/upload to R2 (defaults bucket to scripty-db-backups)
npm run db:backup
```

Requires `mysqldump`, `gzip`, `gunzip`, `openssl`, and Node/`npx wrangler` (ensure you have run `npx wrangler login` or set `CLOUDFLARE_API_TOKEN`).

### Verification (weekly)

Workflow: [`.github/workflows/verify-backup.yml`](../.github/workflows/verify-backup.yml)  
Script: [`scripts/verify-backup.sh`](../scripts/verify-backup.sh)

Backups that are written but never read are a silent-failure risk, so the newest
R2 dump is verified weekly — without touching any database:

- a `scripty-*.sql.gz` object exists and is **fresh** (< 26h old, `MAX_AGE_HOURS`)
- its size is plausible: ≥ 1 KiB (`BACKUP_MIN_BYTES`) and ≥ 70% of the previous
  dump (`MIN_SIZE_RATIO`)
- it downloads and passes `gunzip -t`
- the full decompressed SQL contains `CREATE TABLE`s, `INSERT`s, and the key
  application tables (`user project actor person block`)

Runs twice, independently:

- **GitHub Actions** — Mondays 09:00 UTC (2h after that morning's backup), plus
  manual **Run workflow**. Needs only the existing `CLOUDFLARE_*`/`R2_BUCKET`
  secrets. A red run means the backup layer needs attention.
- **Claude Code routine** — same schedule; also checks Railway volume snapshots
  (`./scripts/railway-mysql-backups.sh status`) and files/updates a GitHub issue
  labeled `backup` on failure. See [docs/ROUTINES.md](ROUTINES.md).

Local run: `npm run db:verify` (needs `CLOUDFLARE_API_TOKEN` for the R2 listing).

## Secrets backup (Railway variables → encrypted snapshot in R2)

Workflow: [`.github/workflows/backup-secrets.yml`](../.github/workflows/backup-secrets.yml)  
Script: [`scripts/backup-secrets.sh`](../scripts/backup-secrets.sh)

Database backups don't cover configuration: Railway variables (`EMAIL_WORKER_URL`,
`EMAIL_WORKER_SECRET`, `MAIL_FROM`, `METRICS_TOKEN`, MySQL credentials, …) exist only on Railway.
GitHub Actions secrets and Cloudflare Worker secrets are **write-only** and
cannot be exported — but both are re-pushed *from Railway* by
`./scripts/bootstrap-deploy.sh secrets` and `npm run cf:sync`, so snapshotting
Railway covers everything recoverable.

```bash
npm run secrets:backup    # read Railway vars (web + MySQL) + local .dev.vars,
                          # encrypt (AES-256, PBKDF2), upload to r2://scripty-db-backups/secrets/
npm run secrets:list      # list snapshots in R2 (needs CLOUDFLARE_API_TOKEN)
npm run secrets:restore                       # decrypt latest to stdout
npm run secrets:restore -- <file> -- --keys   # key names only, no values
```

### Automated (GitHub Actions)

- **Schedule:** weekly, Sundays 07:30 UTC, plus manual **Run workflow**.
- Reuses the existing `RAILWAY_TOKEN`, `CLOUDFLARE_API_TOKEN`,
  `CLOUDFLARE_ACCOUNT_ID`, and `R2_BUCKET` GitHub secrets.
- **One-time setup:** generate a strong passphrase, store it in your password
  manager, then add it as the `SECRETS_BACKUP_PASSPHRASE` GitHub secret:

  ```bash
  PASS="$(openssl rand -base64 30)" \
    && echo "Store in password manager: $PASS" \
    && gh secret set SECRETS_BACKUP_PASSPHRASE --body "$PASS"
  ```

  Then run **Actions → Backup secrets → Run workflow** once to verify.

### Notes

- **Passphrase:** prompted (or `SECRETS_BACKUP_PASSPHRASE`). Keep it in your
  password manager — without it a snapshot is unreadable, by design.
- Plaintext never touches disk: variables are piped straight into
  `openssl enc`; restore decrypts to stdout after validating the payload.
- Local runs require `railway login` and wrangler auth; CI uses `RAILWAY_TOKEN`.
- The bucket's 30-day lifecycle rule applies to `secrets/` too — weekly runs
  keep ~4 snapshots around at any time.
- After rotating a secret, trigger the workflow (or `npm run secrets:backup`)
  so the latest snapshot holds the new values.
- **Root credentials are out of scope on purpose:** the GitHub, Railway,
  and Cloudflare account logins + 2FA recovery codes belong in a
  password manager. With those, every secret here can be re-minted even
  without a snapshot.

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
- [ ] Account logins + 2FA recovery codes (GitHub, Railway, Cloudflare) in a password manager
- [ ] `SECRETS_BACKUP_PASSPHRASE` GitHub secret set and its passphrase stored in the password manager
- [ ] Manual **Backup secrets** workflow succeeded once

