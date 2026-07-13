#!/usr/bin/env bash
# Weekly dependency & vulnerability report for pom.xml and package.json.
#
# Prints a markdown report to stdout. Individual sections degrade to an
# "(unavailable)" note if their tool fails — this is a report, not a gate —
# but the script exits 1 if `npm audit` finds high or critical vulnerabilities
# so automation can branch on it.
#
# Requirements: node/npm. Maven section needs Java 17+ (auto-detected like
# scripts/dev-server.sh). Dependabot section needs an authenticated `gh` CLI.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${REPO_ROOT}"

find_java17() {
  # Honor a preset JAVA_HOME if it is already Java 17+.
  local candidates=()
  [[ -n "${JAVA_HOME:-}" ]] && candidates+=("${JAVA_HOME}")
  candidates+=(
    "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
    "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
  )
  if [[ "$(uname)" == "Darwin" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
    local mac_home
    if mac_home=$(/usr/libexec/java_home -v 17 2>/dev/null); then
      candidates+=("${mac_home}")
    fi
  fi
  local jvm_dir
  for jvm_dir in /usr/lib/jvm/*17* /usr/lib/jvm/java-17*; do
    [[ -d "${jvm_dir}" ]] && candidates+=("${jvm_dir}")
  done

  local home
  for home in "${candidates[@]}"; do
    if [[ -x "${home}/bin/java" ]]; then
      local version
      version=$("${home}/bin/java" -version 2>&1 | awk -F'"' '/version/ {split($2, v, "."); print v[1]}')
      if [[ "${version:-0}" -ge 17 ]]; then
        echo "${home}"
        return 0
      fi
    fi
  done
  return 1
}

WORKDIR="${TMPDIR:-/tmp}/scripty-deps-report-$$"
mkdir -p "${WORKDIR}"
trap 'rm -rf "$WORKDIR"' EXIT

HIGH_CRITICAL=0

echo "# Scripty dependency report"
echo
echo "Generated: $(date -u '+%Y-%m-%d %H:%M UTC')"

# --- Maven -------------------------------------------------------------------
echo
echo "## Maven (pom.xml)"
echo

MVN_OK=0
if command -v mvn >/dev/null 2>&1; then
  if JAVA17_HOME=$(find_java17); then
    export JAVA_HOME="${JAVA17_HOME}"
    # Separate output files: each goal truncates versions.outputFile.
    # processDependencyManagement=false skips the (huge, unused) Spring Boot
    # BOM-managed list; outputLineWidth keeps each update on a single line.
    if mvn -B -q versions:display-dependency-updates \
          -DprocessDependencyManagement=false \
          -Dversions.outputLineWidth=200 \
          -Dversions.outputFile="${WORKDIR}/mvn-deps.txt" >"${WORKDIR}/mvn-stderr.log" 2>&1 \
       && mvn -B -q versions:display-plugin-updates \
          -Dversions.outputLineWidth=200 \
          -Dversions.outputFile="${WORKDIR}/mvn-plugins.txt" >>"${WORKDIR}/mvn-stderr.log" 2>&1; then
      MVN_OK=1
      cat "${WORKDIR}/mvn-deps.txt" "${WORKDIR}/mvn-plugins.txt" > "${WORKDIR}/mvn-updates.txt"
    fi
  else
    echo "_(unavailable: Java 17+ not found)_"
  fi
else
  echo "_(unavailable: mvn not installed)_"
fi

if [[ "${MVN_OK}" -eq 1 ]]; then
  # versions:* -Dversions.outputFile lines look like:
  #   org.example:artifact ............. 1.2.3 -> 1.3.0
  MVN_ROWS=$(sed -nE 's/^[[:space:]]*([[:alnum:]._-]+:[[:alnum:]._-]+)[[:space:].]+([^ ]+) -> ([^ ]+)$/| \1 | \2 | \3 |/p' \
      "${WORKDIR}/mvn-updates.txt" | sort -u)
  if [[ -n "${MVN_ROWS}" ]]; then
    MVN_COUNT=$(wc -l <<<"${MVN_ROWS}" | tr -d ' ')
    echo "${MVN_COUNT} dependency/plugin update(s) available:"
    echo
    echo "| artifact | current | latest |"
    echo "|---|---|---|"
    echo "${MVN_ROWS}"
  else
    MVN_COUNT=0
    echo "All Maven dependencies and plugins are up to date."
  fi
elif [[ -f "${WORKDIR}/mvn-stderr.log" ]]; then
  MVN_COUNT="?"
  echo "_(unavailable: mvn versions goals failed; last lines below)_"
  echo
  echo '```'
  tail -n 5 "${WORKDIR}/mvn-stderr.log"
  echo '```'
else
  MVN_COUNT="?"
fi

# --- npm outdated --------------------------------------------------------------
echo
echo "## npm outdated (package.json)"
echo

npm outdated --json >"${WORKDIR}/npm-outdated.json" 2>/dev/null || true
NPM_OUTDATED_SUMMARY=$(python3 - "${WORKDIR}/npm-outdated.json" <<'PY' || echo "_(unavailable: could not parse npm outdated output)_"
import json, sys
try:
    with open(sys.argv[1]) as f:
        raw = f.read().strip()
    data = json.loads(raw) if raw else {}
except Exception:
    sys.exit(1)
if not data:
    print("All npm dependencies are up to date.")
else:
    print(f"{len(data)} outdated package(s):")
    print()
    print("| package | current | wanted | latest |")
    print("|---|---|---|---|")
    for name, info in sorted(data.items()):
        print(f"| {name} | {info.get('current', '?')} | {info.get('wanted', '?')} | {info.get('latest', '?')} |")
PY
)
echo "${NPM_OUTDATED_SUMMARY}"
NPM_OUTDATED_COUNT=$(python3 -c "
import json
try:
    raw = open('${WORKDIR}/npm-outdated.json').read().strip()
    print(len(json.loads(raw)) if raw else 0)
except Exception:
    print('?')
")

# --- npm audit -----------------------------------------------------------------
echo
echo "## npm audit"
echo

npm audit --json >"${WORKDIR}/npm-audit.json" 2>/dev/null || true
AUDIT_RENDERED=$(python3 - "${WORKDIR}/npm-audit.json" <<'PY' || true
import json, sys
try:
    with open(sys.argv[1]) as f:
        data = json.load(f)
except Exception:
    print("_(unavailable: could not parse npm audit output)_")
    sys.exit(0)

meta = data.get("metadata", {}).get("vulnerabilities", {})
total = sum(meta.get(sev, 0) for sev in ("info", "low", "moderate", "high", "critical"))
high_critical = meta.get("high", 0) + meta.get("critical", 0)
print(f"HIGH_CRITICAL_COUNT={high_critical}", file=sys.stderr)

if total == 0:
    print("No known vulnerabilities.")
    sys.exit(0)

counts = ", ".join(f"{sev}: {meta.get(sev, 0)}" for sev in ("critical", "high", "moderate", "low", "info") if meta.get(sev, 0))
print(f"**{total} vulnerability(ies)** ({counts})")
print()
print("| package | severity | fix | advisory |")
print("|---|---|---|---|")
for name, vuln in sorted(data.get("vulnerabilities", {}).items()):
    sev = vuln.get("severity", "?")
    fix = vuln.get("fixAvailable")
    if fix is True:
        fix_txt = "`npm audit fix`"
    elif isinstance(fix, dict):
        major = " (semver-major)" if fix.get("isSemVerMajor") else ""
        fix_txt = f"{fix.get('name', name)}@{fix.get('version', '?')}{major}"
    else:
        fix_txt = "none"
    urls = [v.get("url") for v in vuln.get("via", []) if isinstance(v, dict) and v.get("url")]
    print(f"| {name} | {sev} | {fix_txt} | {' '.join(urls) or '-'} |")
PY
)
# The python helper reports the high/critical count on stderr; recompute here.
HIGH_CRITICAL=$(python3 -c "
import json
try:
    data = json.load(open('${WORKDIR}/npm-audit.json'))
    meta = data.get('metadata', {}).get('vulnerabilities', {})
    print(meta.get('high', 0) + meta.get('critical', 0))
except Exception:
    print(0)
")
echo "${AUDIT_RENDERED}"

# --- GitHub Dependabot alerts (best-effort) --------------------------------------
echo
echo "## GitHub Dependabot alerts"
echo

if command -v gh >/dev/null 2>&1; then
  DEPENDABOT=$(gh api 'repos/{owner}/{repo}/dependabot/alerts?state=open' --paginate \
      --jq '.[] | "- **\(.security_advisory.severity)** \(.dependency.package.name): \(.security_advisory.summary) (\(.html_url))"' \
      2>/dev/null) || DEPENDABOT="__UNAVAILABLE__"
  if [[ "${DEPENDABOT}" == "__UNAVAILABLE__" ]]; then
    echo "_(unavailable: gh api failed — Dependabot alerts may be disabled for this repo)_"
  elif [[ -z "${DEPENDABOT}" ]]; then
    echo "No open Dependabot alerts."
  else
    echo "${DEPENDABOT}"
  fi
else
  echo "_(unavailable: gh CLI not installed)_"
fi

# --- Footer ----------------------------------------------------------------------
echo
echo "## Remediation"
echo
# shellcheck disable=SC2016  # backticks below are markdown, not command substitution
echo '- npm vulnerabilities: `npm audit fix` (review `(semver-major)` fixes before applying: `npm audit fix --force`)'
# shellcheck disable=SC2016
echo '- npm updates: `npm update <package>` or bump versions in package.json'
# shellcheck disable=SC2016
echo '- Maven updates: bump the version in pom.xml (or the corresponding `<properties>` entry), then run `mvn verify`'
echo
echo "---"
echo "Summary: Maven updates: ${MVN_COUNT:-?} | npm outdated: ${NPM_OUTDATED_COUNT:-?} | npm high/critical vulns: ${HIGH_CRITICAL}"

if [[ "${HIGH_CRITICAL}" =~ ^[0-9]+$ ]] && [[ "${HIGH_CRITICAL}" -gt 0 ]]; then
  echo "error: ${HIGH_CRITICAL} high/critical npm vulnerability(ies) found" >&2
  exit 1
fi
