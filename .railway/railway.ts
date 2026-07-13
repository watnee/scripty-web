import { defineRailway, github, mysql, preserve, project, service, volume } from "railway/iac";

export default defineRailway(() => {
  const MySQL = mysql("MySQL");
  const mysqlVolume = volume("mysql-volume", {
    alerts: { usage: { "100": {}, "80": {}, "95": {} } },
    allowOnlineResize: true,
    region: "sfo",
    sizeMB: 50000,
  });
  const web = service("web", {
    // No GitHub source on purpose: CI deploys via `railway up --ci`, and a
    // connected repo would auto-deploy every main push a second time.
    // No volume on purpose either: headshots live in MySQL (actor_headshot),
    // and a volume would force a stop-start swap (downtime) on every deploy.
    build: {
      buildEnvironment: "V3",
      builder: "DOCKERFILE",
      dockerfilePath: "Dockerfile",
      // Kept although no repo is connected: `railway config apply` cannot
      // unset watchPatterns, so omitting them leaves a permanent plan diff.
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
      EMAIL_WORKER_SECRET: preserve(),
      EMAIL_WORKER_URL: preserve(),
      JAVA_OPTS: preserve(),
      MAIL_FROM: preserve(),
      RAILPACK_JDK_VERSION: preserve(),

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
      // Railway private DNS requires the .railway.internal suffix; bare
      // service names do not resolve.
      SCRAPE_TARGET: "web.railway.internal:8080",
      RAILWAY_RUN_UID: "0",
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
      PROMETHEUS_URL: "http://prometheus.railway.internal:9090",
      RAILWAY_RUN_UID: "0",
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
      prometheusVolume,
      grafanaVolume,
      prometheus,
      grafana,
    ],
  });
});
