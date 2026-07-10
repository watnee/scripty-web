import {
  defineRailway,
  github,
  group,
  mysql,
  preserve,
  project,
  service,
  volume,
} from "railway/iac";

/**
 * Scripty — Spring Boot 3.4 / Java 17 on Railway.
 *
 * Deploy-time overrides still live in railway.json + railpack.json until you
 * migrate fully to IaC (a service cannot be managed by both). To migrate:
 *   1. Link a Railway project: `railway link`
 *   2. Confirm this file matches production intent
 *   3. Remove railway.json (and clear any custom config-file path in Settings)
 *   4. `railway config plan` && `railway config apply`
 */
export default defineRailway(() => {
  const db = mysql("MySQL");

  const uploads = volume("uploads", {
    sizeMB: 1024,
  });

  const web = service("web", {
    source: github("watnee/scripty", { branch: "main" }),
    // Railpack builds from pom.xml (Java 17 via railpack.json / RAILPACK_JDK_VERSION).
    start:
      "java -XX:MaxRAMPercentage=75.0 -jar target/scripty.jar --server.port=$PORT --spring.profiles.active=prod",
    healthcheck: "/health",
    healthcheckTimeout: 300,
    volumeMounts: {
      "/app/uploads": uploads,
    },
    env: {
      JAVA_OPTS: "-XX:MaxRAMPercentage=75.0",
      RAILPACK_JDK_VERSION: "17",

      // application-prod.yml datasource
      MYSQLHOST: db.env.MYSQLHOST,
      MYSQLPORT: db.env.MYSQLPORT,
      MYSQLUSER: db.env.MYSQLUSER,
      MYSQLPASSWORD: db.env.MYSQLPASSWORD,
      MYSQLDATABASE: db.env.MYSQLDATABASE,
      // Private networking typically has no TLS.
      MYSQL_SSL_MODE: "DISABLED",
      MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL: "true",

      // Keep existing Railway values; set real values in the dashboard or via CLI.
      APP_BASE_URL: preserve(),
      MAIL_ENABLED: preserve(),
      MAIL_HOST: preserve(),
      MAIL_PORT: preserve(),
      MAIL_USERNAME: preserve(),
      MAIL_PASSWORD: preserve(),
      MAIL_FROM: preserve(),
      MAIL_SMTP_AUTH: preserve(),
      MAIL_SMTP_STARTTLS: preserve(),
    },
  });

  return project("scripty", {
    resources: [group("Scripty", [web, db, uploads])],
  });
});
