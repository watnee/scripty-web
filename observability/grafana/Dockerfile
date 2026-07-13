FROM grafana/grafana:11.1.0

# Copy provisioning configurations
COPY observability/grafana/provisioning /etc/grafana/provisioning

# Run as root to allow writing to the persistent volume mount
USER root
