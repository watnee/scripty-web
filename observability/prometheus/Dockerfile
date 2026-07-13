FROM prom/prometheus:v2.53.0

# Copy template and entrypoint
COPY observability/prometheus/prometheus.yml.template /etc/prometheus/prometheus.yml.template
COPY observability/prometheus/entrypoint.sh /usr/local/bin/entrypoint.sh

# Switch to root to change ownership and permissions
USER root
RUN chmod +x /usr/local/bin/entrypoint.sh

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
