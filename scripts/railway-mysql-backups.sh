#!/usr/bin/env bash
# Enable / inspect / ensure Railway volume backup schedules for Scripty MySQL.
#
# Requires: logged-in `railway` CLI (`railway whoami`) and network access.
# Usage:
#   ./scripts/railway-mysql-backups.sh status     # schedules + backups (exit 1 if unhealthy)
#   ./scripts/railway-mysql-backups.sh enable     # Daily + Weekly + Monthly
#   ./scripts/railway-mysql-backups.sh snapshot   # create a manual backup now
#   ./scripts/railway-mysql-backups.sh ensure     # enable schedules + snapshot if none exist
#
# Optional env overrides:
#   RAILWAY_PROJECT_ID, RAILWAY_ENVIRONMENT_ID, MYSQL_VOLUME_INSTANCE_ID
#   MYSQL_VOLUME_NAME (default: mysql-volume)
set -euo pipefail

ACTION="${1:-status}"
PROJECT_ID="${RAILWAY_PROJECT_ID:-}"
ENVIRONMENT_ID="${RAILWAY_ENVIRONMENT_ID:-}"
VOLUME_INSTANCE_ID="${MYSQL_VOLUME_INSTANCE_ID:-}"
VOLUME_NAME="${MYSQL_VOLUME_NAME:-mysql-volume}"

python3 - "$ACTION" "$PROJECT_ID" "$ENVIRONMENT_ID" "$VOLUME_INSTANCE_ID" "$VOLUME_NAME" <<'PY'
import json, pathlib, sys, urllib.request, urllib.error

action, project_id, environment_id, volume_instance_id, volume_name = sys.argv[1:6]
cfg_path = pathlib.Path.home() / ".railway" / "config.json"
if not cfg_path.exists():
    sys.exit("error: ~/.railway/config.json not found; run `railway login`")

cfg = json.loads(cfg_path.read_text())
token = cfg["user"]["token"]

# Prefer linked project/environment for this workspace when not overridden.
if not project_id or not environment_id:
    cwd = str(pathlib.Path.cwd().resolve())
    linked = (cfg.get("projects") or {}).get(cwd) or {}
    # Also try case-normalized path keys used by some Railway CLI versions.
    if not linked:
        for path, meta in (cfg.get("projects") or {}).items():
            if pathlib.Path(path).resolve() == pathlib.Path.cwd().resolve():
                linked = meta
                break
    project_id = project_id or linked.get("project") or ""
    environment_id = environment_id or linked.get("environment") or ""

if not project_id:
    # Scripty production fallback (safe default for this repo).
    project_id = "ac630c3e-e3ce-4518-bc62-037b0860defb"

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

def resolve_volume_instance_id():
    if volume_instance_id:
        return volume_instance_id, None

    data = gql(
        """
        query($id: String!) {
          project(id: $id) {
            volumes {
              edges {
                node {
                  id
                  name
                  volumeInstances {
                    edges {
                      node {
                        id
                        environmentId
                        mountPath
                      }
                    }
                  }
                }
              }
            }
          }
        }
        """,
        {"id": project_id},
    )
    volumes = data["project"]["volumes"]["edges"]
    matches = []
    for edge in volumes:
        node = edge["node"]
        if node["name"] != volume_name:
            continue
        for inst_edge in node["volumeInstances"]["edges"]:
            inst = inst_edge["node"]
            if environment_id and inst["environmentId"] != environment_id:
                continue
            matches.append(
                {
                    "volumeId": node["id"],
                    "volumeName": node["name"],
                    "volumeInstanceId": inst["id"],
                    "environmentId": inst["environmentId"],
                    "mountPath": inst["mountPath"],
                }
            )

    if not matches:
        names = sorted({e["node"]["name"] for e in volumes})
        sys.exit(
            f"error: no volume instance for name={volume_name!r} "
            f"environment={environment_id or '(any)'} in project {project_id}. "
            f"Available volumes: {', '.join(names) or '(none)'}"
        )
    if len(matches) > 1 and not environment_id:
        sys.exit(
            "error: multiple volume instances matched; set RAILWAY_ENVIRONMENT_ID "
            "or MYSQL_VOLUME_INSTANCE_ID"
        )
    return matches[0]["volumeInstanceId"], matches[0]

def backup_state(vid):
    return gql(
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
        {"id": vid},
    )

def enable_schedules(vid):
    return gql(
        """
        mutation($id: String!, $kinds: [VolumeInstanceBackupScheduleKind!]!) {
          volumeInstanceBackupScheduleUpdate(volumeInstanceId: $id, kinds: $kinds)
        }
        """,
        {"id": vid, "kinds": ["DAILY", "WEEKLY", "MONTHLY"]},
    )

def create_snapshot(vid):
    return gql(
        """
        mutation($id: String!) {
          volumeInstanceBackupCreate(volumeInstanceId: $id) { workflowId }
        }
        """,
        {"id": vid},
    )

def print_status(vid, meta, state, *, check=False):
    schedules = state["volumeInstanceBackupScheduleList"] or []
    backups = state["volumeInstanceBackupList"] or []
    kinds = {s["kind"] for s in schedules}
    expected = {"DAILY", "WEEKLY", "MONTHLY"}
    missing = sorted(expected - kinds)
    healthy = not missing and len(backups) > 0

    payload = {
        "projectId": project_id,
        "environmentId": (meta or {}).get("environmentId") or environment_id or None,
        "volumeName": volume_name,
        "volumeInstanceId": vid,
        "healthy": healthy,
        "missingSchedules": missing,
        "scheduleCount": len(schedules),
        "backupCount": len(backups),
        "volumeInstanceBackupScheduleList": schedules,
        "volumeInstanceBackupList": backups,
    }
    if meta:
        payload["volumeId"] = meta.get("volumeId")
        payload["mountPath"] = meta.get("mountPath")
    print(json.dumps(payload, indent=2))

    if check and not healthy:
        reasons = []
        if missing:
            reasons.append(f"missing schedules: {', '.join(missing)}")
        if not backups:
            reasons.append("no backups yet")
        sys.exit("unhealthy: " + "; ".join(reasons))

vid, meta = resolve_volume_instance_id()

if action == "status":
    print_status(vid, meta, backup_state(vid), check=True)
elif action == "enable":
    data = enable_schedules(vid)
    print(json.dumps({"volumeInstanceId": vid, **data}, indent=2))
    print("Enabled Daily + Weekly + Monthly schedules on MySQL volume.")
elif action == "snapshot":
    data = create_snapshot(vid)
    print(json.dumps({"volumeInstanceId": vid, **data}, indent=2))
    print("Manual backup workflow started.")
elif action == "ensure":
    state = backup_state(vid)
    schedules = state["volumeInstanceBackupScheduleList"] or []
    backups = state["volumeInstanceBackupList"] or []
    kinds = {s["kind"] for s in schedules}
    expected = {"DAILY", "WEEKLY", "MONTHLY"}
    actions = []
    if expected - kinds:
        enable_schedules(vid)
        actions.append("enabled Daily/Weekly/Monthly schedules")
    if not backups:
        snap = create_snapshot(vid)
        actions.append(f"started manual snapshot workflow {snap['volumeInstanceBackupCreate']['workflowId']}")
    if not actions:
        actions.append("already healthy (schedules + at least one backup)")
    state = backup_state(vid)
    print(json.dumps({"volumeInstanceId": vid, "actions": actions}, indent=2))
    print_status(vid, meta, state, check=True)
else:
    sys.exit(f"usage: {sys.argv[0]} status|enable|snapshot|ensure")
PY
