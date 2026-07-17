/**
 * Scripty Cloudflare entrypoint.
 *
 * Proxies every request to a Spring Boot container (Durable Object + Containers).
 * Container process env (MySQL, mail, APP_BASE_URL, …) comes from Worker secrets
 * listed in wrangler.jsonc → secrets.required (plus optional secrets you set).
 */
import { Container, getContainer } from "@cloudflare/containers";

/**
 * Cloudflare Email Sending binding (send_email in wrangler.jsonc).
 * Minimal structural type so we do not depend on a specific
 * @cloudflare/workers-types revision shipping the binding type.
 */
interface EmailAttachment {
  content: string | ArrayBuffer | ArrayBufferView;
  filename: string;
  type?: string;
  disposition?: "attachment" | "inline";
  contentId?: string;
}

interface EmailSendBinding {
  send(message: {
    from: { email: string; name?: string };
    to: string;
    subject: string;
    html?: string;
    text?: string;
    attachments?: EmailAttachment[];
  }): Promise<{ messageId?: string }>;
}

export interface Env {
  SCRIPTY_CONTAINER: DurableObjectNamespace<ScriptyContainer>;
  EMAIL: EmailSendBinding;
  // Bearer secret for POST /internal/email (wrangler secret put EMAIL_PROXY_SECRET).
  EMAIL_PROXY_SECRET?: string;
  MYSQLHOST: string;
  MYSQLPORT: string;
  MYSQLUSER: string;
  MYSQLPASSWORD: string;
  MYSQLDATABASE: string;
  MYSQL_SSL_MODE?: string;
  MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL?: string;
  APP_BASE_URL?: string;
  // Preferred mail transport: this Worker's /internal/email endpoint
  // (Cloudflare Email Sending). The MAIL_* SMTP settings below are the
  // legacy fallback and are ignored when these are set.
  EMAIL_WORKER_URL?: string;
  EMAIL_WORKER_SECRET?: string;
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
    "EMAIL_WORKER_URL",
    "EMAIL_WORKER_SECRET",
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

/** Constant-time bearer-token check (hash both sides to equalise lengths). */
async function authorizedForEmail(request: Request, secret: string): Promise<boolean> {
  const header = request.headers.get("authorization") ?? "";
  const token = header.replace(/^Bearer\s+/i, "");
  const encoder = new TextEncoder();
  const [a, b] = await Promise.all([
    crypto.subtle.digest("SHA-256", encoder.encode(token)),
    crypto.subtle.digest("SHA-256", encoder.encode(secret)),
  ]);
  const av = new Uint8Array(a);
  const bv = new Uint8Array(b);
  let diff = 0;
  for (let i = 0; i < av.length; i++) {
    diff |= av[i] ^ bv[i];
  }
  return diff === 0;
}

/** "Scripty <noreply@solfege.app>" → { name: "Scripty", email: "noreply@solfege.app" } */
function parseFrom(from: string): { email: string; name?: string } {
  const match = from.match(/^\s*(?:"?([^"<]*?)"?\s*)?<([^>]+)>\s*$/);
  if (match) {
    const name = (match[1] ?? "").trim();
    return name ? { email: match[2].trim(), name } : { email: match[2].trim() };
  }
  return { email: from.trim() };
}

function base64ToArrayBuffer(base64: string): ArrayBuffer {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

/**
 * Normalise the JSON attachment payload from the Railway app into the shape the
 * EMAIL binding expects. Base64-encoded content is decoded to an ArrayBuffer so
 * binary files (e.g. a screenplay PDF) survive the JSON hop intact.
 */
function parseAttachments(
  raw:
    | Array<{
        filename?: string;
        type?: string;
        encoding?: string;
        content?: string;
        disposition?: "attachment" | "inline";
        contentId?: string;
      }>
    | undefined,
): EmailAttachment[] | undefined {
  if (!raw || raw.length === 0) {
    return undefined;
  }
  return raw.map((a) => {
    if (!a.filename || !a.content) {
      throw new Error("each attachment requires filename and content");
    }
    const content =
      a.encoding === "base64" ? base64ToArrayBuffer(a.content) : a.content;
    return {
      content,
      filename: a.filename,
      type: a.type,
      disposition: a.disposition ?? "attachment",
      ...(a.contentId ? { contentId: a.contentId } : {}),
    };
  });
}

/**
 * POST /internal/email — send transactional email via the EMAIL binding.
 * Lets the Railway deployment (and anything else holding EMAIL_PROXY_SECRET)
 * use Cloudflare Email Sending without a Cloudflare API token.
 * Body: { from: "Name <addr>" | addr, to, subject, html?, text?,
 *         attachments?: [{ filename, type?, encoding?, content, disposition? }] }
 */
async function handleEmailSend(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") {
    return Response.json({ error: "method not allowed" }, { status: 405 });
  }
  if (!env.EMAIL_PROXY_SECRET || !env.EMAIL) {
    return Response.json({ error: "email sending not configured" }, { status: 503 });
  }
  if (!(await authorizedForEmail(request, env.EMAIL_PROXY_SECRET))) {
    return Response.json({ error: "unauthorized" }, { status: 401 });
  }

  let body: {
    from?: string;
    to?: string;
    subject?: string;
    html?: string;
    text?: string;
    attachments?: Array<{
      filename?: string;
      type?: string;
      encoding?: string;
      content?: string;
      disposition?: "attachment" | "inline";
      contentId?: string;
    }>;
  };
  try {
    body = await request.json();
  } catch {
    return Response.json({ error: "invalid JSON body" }, { status: 400 });
  }
  const { from, to, subject, html, text } = body;
  if (!from || !to || !subject || !(html || text)) {
    return Response.json(
      { error: "required: from, to, subject and html or text" },
      { status: 400 },
    );
  }

  let attachments: EmailAttachment[] | undefined;
  try {
    attachments = parseAttachments(body.attachments);
  } catch (e) {
    return Response.json(
      { error: e instanceof Error ? e.message : "invalid attachment" },
      { status: 400 },
    );
  }

  // Plain-text fallback improves spam scores when the caller only has HTML.
  const textBody =
    text ??
    html!
      .replace(/<[^>]+>/g, " ")
      .replace(/&nbsp;/g, " ")
      .replace(/&amp;/g, "&")
      .replace(/&lt;/g, "<")
      .replace(/&gt;/g, ">")
      .replace(/&quot;/g, '"')
      .replace(/&#39;/g, "'")
      .replace(/\s+/g, " ")
      .trim();

  try {
    const result = await env.EMAIL.send({
      from: parseFrom(from),
      to,
      subject,
      html,
      text: textBody,
      ...(attachments && attachments.length > 0 ? { attachments } : {}),
    });
    return Response.json({ messageId: result?.messageId ?? null });
  } catch (e) {
    return Response.json(
      { error: e instanceof Error ? e.message : "send failed" },
      { status: 502 },
    );
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (new URL(request.url).pathname === "/internal/email") {
      return handleEmailSend(request, env);
    }
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
