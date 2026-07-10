#!/usr/bin/env bash
# Enable / inspect Railway volume backup schedules for Scripty MySQL.
#
# Requires: logged-in `railway` CLI (`railway whoami`) and network access.
# Usage:
#   ./scripts/railway-mysql-backups.sh status
#   ./scripts/railway-mysql-backups.sh enable          # Daily + Weekly + Monthly
#   ./scripts/railway-mysql-backups.sh snapshot        # create a manual backup now
set -euo pipefail

PROJECT_ID="${RAILWAY_PROJECT_ID:-ac630c3e-e3ce-4518-bc62-037b0860defb}"
# mysql-volume instance in production (from `railway volume list` + GraphQL).
MYSQL_VOLUME_INSTANCE_ID="${MYSQL_VOLUME_INSTANCE_ID:-1a8a09f4-4ff0-487f-a4f3-6cf53977ca3c}"

ACTION="${1:-status}"

python3 - "$ACTION" "$PROJECT_ID" "$MYSQL_VOLUME_INSTANCE_ID" <<'PY'
import json, pathlib, sys, urllib.request, urllib.error

action, project_id, volume_instance_id = sys.argv[1:4]
cfg_path = pathlib.Path.home() / ".railway" / "config.json"
if not cfg_path.exists():
    sys.exit("error: ~/.railway/config.json not found; run `railway login`")

token = json.loads(cfg_path.read_text())["user"]["token"]

def gql(query, variables=None):
    req = urllib.request.Request(
        "https://backboard.railway.com/graphql/v2",
        data=json.dumps({"query": query, "variables": variables or {}}).encode(),
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "User-Agent": "railway-cli/4.0.0",
            "Accept": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req) as resp:
            payload = json.load(resp)
    except urllib.error.HTTPError as e:
        sys.exit(f"HTTP {e.code}: {e.read().decode()}")
    if payload.get("errors"):
        sys.exit(json.dumps(payload["errors"], indent=2))
    return payload["data"]

if action == "status":
    data = gql(
        """
        query($id: String!) {
          volumeInstanceBackupScheduleList(volumeInstanceId: $id) {
            id name cron kind retentionSeconds createdAt
          }
          volumeInstanceBackupList(volumeInstanceId: $id) {
            id name createdAt expiresAt usedMB referencedMB
          }
        }
        """,
        {"id": volume_instance_id},
    )
    print(json.dumps({"projectId": project_id, "volumeInstanceId": volume_instance_id, **data}, indent=2))
elif action == "enable":
    data = gql(
        """
        mutation($id: String!, $kinds: [VolumeInstanceBackupScheduleKind!]!) {
          volumeInstanceBackupScheduleUpdate(volumeInstanceId: $id, kinds: $kinds)
        }
        """,
        {"id": volume_instance_id, "kinds": ["DAILY", "WEEKLY", "MONTHLY"]},
    )
    print(json.dumps(data, indent=2))
    print("Enabled Daily + Weekly + Monthly schedules on MySQL volume.")
elif action == "snapshot":
    data = gql(
        """
        mutation($id: String!) {
          volumeInstanceBackupCreate(volumeInstanceId: $id) { workflowId }
        }
        """,
        {"id": volume_instance_id},
    )
    print(json.dumps(data, indent=2))
    print("Manual backup workflow started.")
else:
    sys.exit(f"usage: {sys.argv[0]} status|enable|snapshot")
PY
