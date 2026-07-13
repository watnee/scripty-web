#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")"

PORT="${PORT:-8080}"

if pids=$(lsof -ti :"${PORT}" 2>/dev/null); then
  echo "Stopping existing server on port ${PORT} ..."
  kill ${pids} 2>/dev/null || true
  sleep 2
  if pids=$(lsof -ti :"${PORT}" 2>/dev/null); then
    kill -9 ${pids} 2>/dev/null || true
    sleep 1
  fi
else
  echo "No server running on port ${PORT}."
fi

exec "$(dirname "$0")/start-test-server.command"
