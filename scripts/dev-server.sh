#!/bin/bash
# Headless dev-server lifecycle for agents, routines, and CI.
#
# Unlike start-test-server.command (interactive: foreground mvn, opens a
# browser), this script runs the Spring Boot dev server in the background,
# never opens a browser, and reports readiness via /actuator/health — safe
# to call from Claude Code routines, cloud agents, and scripts.
#
# Usage: scripts/dev-server.sh {start|stop|restart|status|logs|wait}
#   PORT (default 8080) selects the port; each worktree can pick its own.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${PORT:-8080}"
BASE_URL="http://localhost:${PORT}"
HEALTH_URL="${BASE_URL}/actuator/health"
PID_FILE="${REPO_ROOT}/scripty-server-${PORT}.pid"
LOG_FILE="${REPO_ROOT}/scripty-server-${PORT}.log"
STARTUP_TIMEOUT="${STARTUP_TIMEOUT:-180}"

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

setup_java() {
  local home
  if ! home=$(find_java17); then
    echo "ERROR: Java 17+ not found. Install with: brew install openjdk@17 (macOS) or apt-get install openjdk-17-jdk (Linux)." >&2
    exit 1
  fi
  export JAVA_HOME="${home}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
}

is_healthy() {
  curl -sf --max-time 3 "${HEALTH_URL}" 2>/dev/null | grep -q '"status":"UP"'
}

port_pids() {
  lsof -ti :"${PORT}" 2>/dev/null || true
}

wait_ready() {
  SECONDS=0
  while (( SECONDS < STARTUP_TIMEOUT )); do
    if is_healthy; then
      echo "READY: ${BASE_URL} (health UP after ${SECONDS}s)"
      return 0
    fi
    if [[ -f "${PID_FILE}" ]] && ! kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
      echo "ERROR: server process exited during startup. Last log lines:" >&2
      tail -n 40 "${LOG_FILE}" >&2 || true
      return 1
    fi
    sleep 0.5
  done
  echo "ERROR: server not healthy after ${STARTUP_TIMEOUT}s. Last log lines:" >&2
  tail -n 40 "${LOG_FILE}" >&2 || true
  return 1
}

cmd_start() {
  if is_healthy; then
    echo "ALREADY RUNNING: ${BASE_URL} is healthy."
    return 0
  fi
  if [[ -n "$(port_pids)" ]]; then
    echo "ERROR: port ${PORT} is occupied but not healthy. Run 'stop' or 'restart' first." >&2
    exit 1
  fi
  setup_java
  echo "Starting Scripty dev server on ${BASE_URL} (log: ${LOG_FILE}) ..."
  cd "${REPO_ROOT}"
  # maven.test.skip: spring-boot:run forks the test-compile phase, but booting
  # the server never needs test classes — skipping them saves seconds per start.
  nohup mvn spring-boot:run \
    -Dmaven.test.skip=true \
    -Dspring-boot.run.profiles=dev \
    -Dspring-boot.run.arguments="--server.port=${PORT}" \
    >"${LOG_FILE}" 2>&1 &
  echo $! >"${PID_FILE}"
  wait_ready
}

cmd_stop() {
  local pids=""
  [[ -f "${PID_FILE}" ]] && pids="$(cat "${PID_FILE}")"
  pids="${pids} $(port_pids)"
  pids="$(echo "${pids}" | tr ' ' '\n' | grep -E '^[0-9]+$' | sort -u | tr '\n' ' ')"
  if [[ -z "${pids// /}" ]]; then
    echo "NOT RUNNING: nothing to stop on port ${PORT}."
    rm -f "${PID_FILE}"
    return 0
  fi
  echo "Stopping server on port ${PORT} (pids: ${pids}) ..."
  kill ${pids} 2>/dev/null || true
  local waited=0
  while (( waited < 15 )); do
    [[ -z "$(port_pids)" ]] && break
    sleep 1
    (( waited += 1 ))
  done
  if [[ -n "$(port_pids)" ]]; then
    kill -9 $(port_pids) 2>/dev/null || true
    sleep 1
  fi
  rm -f "${PID_FILE}"
  echo "STOPPED."
}

cmd_status() {
  if is_healthy; then
    echo "RUNNING: ${BASE_URL} health is UP."
  elif [[ -n "$(port_pids)" ]]; then
    echo "UNHEALTHY: port ${PORT} occupied but ${HEALTH_URL} not UP."
    exit 1
  else
    echo "STOPPED: nothing listening on port ${PORT}."
    exit 1
  fi
}

case "${1:-}" in
  start)   cmd_start ;;
  stop)    cmd_stop ;;
  restart) cmd_stop; cmd_start ;;
  status)  cmd_status ;;
  wait)    wait_ready ;;
  logs)    tail -n "${2:-100}" "${LOG_FILE}" ;;
  *)
    echo "Usage: $0 {start|stop|restart|status|wait|logs [n]}" >&2
    echo "Env: PORT (default 8080), STARTUP_TIMEOUT (default 180s)" >&2
    exit 2
    ;;
esac
