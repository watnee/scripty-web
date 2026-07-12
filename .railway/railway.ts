import { defineRailway, github, mysql, preserve, project, service, volume } from "railway/iac";

export default defineRailway(() => {
  const MySQL = mysql("MySQL");
  const mysqlVolume = volume("mysql-volume", {
    alerts: { usage: { "100": {}, "80": {}, "95": {} } },
    allowOnlineResize: true,
    region: "sfo",
    sizeMB: 50000,
  });
  const webVolume = volume("web-volume", {
    alerts: { usage: { "100": {}, "80": {}, "95": {} } },
    allowOnlineResize: true,
    region: "sfo",
    sizeMB: 50000,
  });

  const web = service("web", {
    source: github("watnee/scripty"),
    build: {
      buildEnvironment: "V3",
      builder: "DOCKERFILE",
      dockerfilePath: "Dockerfile",
      watchPatterns: [
        "src/**",
        "pom.xml",
        "Dockerfile",
        ".railway/railway.ts",
      ],
    },
    start: "java -XX:MaxRAMPercentage=75.0 -jar scripty.jar --spring.profiles.active=prod",
    healthcheck: "/health",
    healthcheckTimeout: 900,
    replicas: 1,
    volumeMounts: {
      "/app/uploads": webVolume,
    },
    env: {
      // application-prod.yml datasource (private Railway networking)
      MYSQLDATABASE: MySQL.env.MYSQLDATABASE,
      MYSQLHOST: MySQL.env.MYSQLHOST,
      MYSQLPASSWORD: MySQL.env.MYSQLPASSWORD,
      MYSQLPORT: MySQL.env.MYSQLPORT,
      MYSQLUSER: MySQL.env.MYSQLUSER,
      MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL: "true",
      MYSQL_SSL_MODE: "DISABLED",

      // Keep existing Railway values; set real values in the dashboard or via CLI.
      APP_BASE_URL: preserve(),
      JAVA_OPTS: preserve(),
      MAIL_FROM: preserve(),
      RAILPACK_JDK_VERSION: preserve(),
      RESEND_API_KEY: preserve(),

      // Observability configuration
      LOG_FORMAT: "ecs",
      METRICS_TOKEN: preserve(),
    },
  });
  const prometheusVolume = volume("prometheus-volume", {
    alerts: { usage: { "100": {}, "80": {}, "95": {} } },
    allowOnlineResize: true,
    region: "sfo",
    sizeMB: 10000,
  });

  const grafanaVolume = volume("grafana-volume", {
    alerts: { usage: { "100": {}, "80": {}, "95": {} } },
    allowOnlineResize: true,
    region: "sfo",
    sizeMB: 10000,
  });

  const prometheus = service("prometheus", {
    source: github("watnee/scripty"),
    build: {
      buildEnvironment: "V3",
      builder: "DOCKERFILE",
      dockerfilePath: "observability/prometheus/Dockerfile",
      watchPatterns: [
        "observability/prometheus/**",
        ".railway/railway.ts",
      ],
    },
    volumeMounts: {
      "/prometheus": prometheusVolume,
    },
    env: {
      METRICS_TOKEN: web.env.METRICS_TOKEN,
      SCRAPE_TARGET: "web:8080",
    },
  });

  const grafana = service("grafana", {
    source: github("watnee/scripty"),
    build: {
      buildEnvironment: "V3",
      builder: "DOCKERFILE",
      dockerfilePath: "observability/grafana/Dockerfile",
      watchPatterns: [
        "observability/grafana/**",
        ".railway/railway.ts",
      ],
    },
    volumeMounts: {
      "/var/lib/grafana": grafanaVolume,
    },
    env: {
      PROMETHEUS_URL: "http://prometheus:9090",
      // No GF_SECURITY_ADMIN_PASSWORD here: preserve() on a service that does
      // not exist yet makes `railway config apply` fail ("Unrecognized key(s)
      // in object: 'type'") — there is no existing value to preserve. Grafana
      // boots as admin/admin (private networking only, no public domain) and
      // forces a change on first login. To pin it after the service exists:
      //   railway variable set GF_SECURITY_ADMIN_PASSWORD=... --service grafana
      // then switch this back to preserve().
    },
  });

  return project("scripty", {
    resources: [
      MySQL,
      web,
      mysqlVolume,
      webVolume,
      prometheusVolume,
      grafanaVolume,
      prometheus,
      grafana,
    ],
  });
});
