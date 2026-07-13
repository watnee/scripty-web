#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")"

# Prefer Java 17 (required by pom.xml).
for java_home in \
  "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
  "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"; do
  if [[ -x "${java_home}/bin/java" ]]; then
    export JAVA_HOME="${java_home}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
    break
  fi
done

if ! command -v java >/dev/null 2>&1; then
  echo "Java 17 not found. Install with: brew install openjdk@17"
  exit 1
fi

PORT="${PORT:-8080}"
URL="http://localhost:${PORT}"

echo "Starting Scripty dev server on ${URL} ..."
echo "Dev profile auto-logs in as admin. Press Ctrl+C to stop."
echo

# Open the browser once the server responds.
(
  for _ in $(seq 1 180); do
    if curl -sf "${URL}" >/dev/null 2>&1; then
      open "${URL}"
      exit 0
    fi
    sleep 1
  done
  echo "Server did not become ready within 3 minutes."
) &

# maven.test.skip: booting the server never needs test classes — skipping the
# forked test-compile phase saves seconds per start.
mvn spring-boot:run \
  -Dmaven.test.skip=true \
  -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="--server.port=${PORT}"
