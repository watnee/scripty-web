FROM prom/prometheus:v2.53.0

# Copy template and entrypoint
COPY prometheus.yml.template /etc/prometheus/prometheus.yml.template
COPY entrypoint.sh /usr/local/bin/entrypoint.sh

# Switch to root to change ownership and permissions, then back to nobody
USER root
RUN chmod +x /usr/local/bin/entrypoint.sh
USER nobody

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
