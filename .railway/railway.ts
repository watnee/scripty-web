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

  return project("scripty", {
    resources: [MySQL, web, mysqlVolume, webVolume],
  });
});
