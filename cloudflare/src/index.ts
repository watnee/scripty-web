/**
 * Scripty Cloudflare entrypoint.
 *
 * Proxies every request to a Spring Boot container (Durable Object + Containers).
 * Container process env (MySQL, mail, APP_BASE_URL, …) comes from Worker secrets
 * listed in wrangler.jsonc → secrets.required (plus optional secrets you set).
 */
import { Container, getContainer } from "@cloudflare/containers";

export interface Env {
  SCRIPTY_CONTAINER: DurableObjectNamespace<ScriptyContainer>;
  MYSQLHOST: string;
  MYSQLPORT: string;
  MYSQLUSER: string;
  MYSQLPASSWORD: string;
  MYSQLDATABASE: string;
  MYSQL_SSL_MODE?: string;
  MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL?: string;
  APP_BASE_URL?: string;
  // Preferred mail transport (Resend HTTP API); the MAIL_* SMTP settings
  // below are the legacy fallback and are ignored when this is set.
  RESEND_API_KEY?: string;
  MAIL_ENABLED?: string;
  MAIL_HOST?: string;
  MAIL_PORT?: string;
  MAIL_USERNAME?: string;
  MAIL_PASSWORD?: string;
  MAIL_FROM?: string;
  MAIL_SMTP_AUTH?: string;
  MAIL_SMTP_STARTTLS?: string;
  JAVA_OPTS?: string;
}

function stringEnv(value: string | undefined, fallback?: string): string | undefined {
  if (typeof value === "string" && value.length > 0) {
    return value;
  }
  return fallback;
}

function buildContainerEnv(env: Env): Record<string, string> {
  const vars: Record<string, string> = {
    PORT: "8080",
    SPRING_PROFILES_ACTIVE: "prod",
    JAVA_OPTS: stringEnv(env.JAVA_OPTS, "-XX:MaxRAMPercentage=75.0")!,
    MYSQLHOST: stringEnv(env.MYSQLHOST)!,
    MYSQLPORT: stringEnv(env.MYSQLPORT, "3306")!,
    MYSQLUSER: stringEnv(env.MYSQLUSER)!,
    MYSQLPASSWORD: stringEnv(env.MYSQLPASSWORD)!,
    MYSQLDATABASE: stringEnv(env.MYSQLDATABASE)!,
    MYSQL_SSL_MODE: stringEnv(env.MYSQL_SSL_MODE, "PREFERRED")!,
    MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL: stringEnv(
      env.MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL,
      "true",
    )!,
  };

  const optional: (keyof Env)[] = [
    "APP_BASE_URL",
    "RESEND_API_KEY",
    "MAIL_ENABLED",
    "MAIL_HOST",
    "MAIL_PORT",
    "MAIL_USERNAME",
    "MAIL_PASSWORD",
    "MAIL_FROM",
    "MAIL_SMTP_AUTH",
    "MAIL_SMTP_STARTTLS",
  ];
  for (const key of optional) {
    const value = stringEnv(env[key] as string | undefined);
    if (value !== undefined) {
      vars[key] = value;
    }
  }
  return vars;
}

export class ScriptyContainer extends Container<Env> {
  defaultPort = 8080;
  // Keep the JVM warm between requests; cold Spring Boot boots are slow
  // and look like sync/offline failures to clients on an otherwise stable network.
  sleepAfter = "2h";
  enableInternet = true;

  constructor(ctx: DurableObjectState, env: Env) {
    // DurableObjectState storage generic differs slightly across workers-types revisions.
    super(ctx as ConstructorParameters<typeof Container>[0], env);
    this.envVars = buildContainerEnv(env);
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    // Single sticky instance keeps sessions + uploads directory coherent.
    return getContainer(env.SCRIPTY_CONTAINER, "scripty").fetch(request);
  },

  async scheduled(
    _controller: ScheduledController,
    env: Env,
    ctx: ExecutionContext,
  ): Promise<void> {
    // Keep the sticky JVM warm; resets Container sleepAfter.
    ctx.waitUntil(
      getContainer(env.SCRIPTY_CONTAINER, "scripty").fetch(
        new Request("http://container/health"),
      ),
    );
  },
};
