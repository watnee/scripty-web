#!/bin/sh
set -e

# Replace placeholders with environment variables
sed -e "s|\${METRICS_TOKEN}|${METRICS_TOKEN}|g" \
    -e "s|\${SCRAPE_TARGET}|${SCRAPE_TARGET}|g" \
    /etc/prometheus/prometheus.yml.template > /tmp/prometheus.yml

exec /bin/prometheus \
  --config.file=/tmp/prometheus.yml \
  --storage.tsdb.path=/prometheus \
  --web.console.libraries=/usr/share/prometheus/console_libraries \
  --web.console.templates=/usr/share/prometheus/consoles \
  "$@"
