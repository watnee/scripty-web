# Feature flags

Flags turn behaviour on and off from a Railway service variable, with no code
change. They resolve **once at startup**, so flipping one restarts the service —
there is no live kill-switch. Everything about a flag lives in one place:
[`FeatureFlag`](../src/main/java/com/scripty/config/FeatureFlag.java).

## Current flags

| Flag | Railway variable | Default | Controls |
|------|-----------------|---------|----------|
| `passkeys` | `FEATURE_PASSKEYS` | `true` | Passkey (WebAuthn) sign-in. Also needs a usable `APP_BASE_URL` — without one, passkeys stay off whatever the flag says. |
| `service-worker` | `FEATURE_SERVICE_WORKER` | `true` | Service worker registration for static assets and public offline shells. |
| `api-invitations` | `FEATURE_API_INVITATIONS` | `false` | Managing invitations over the REST API (`/api/project/{id}/invitations`). See below before turning this on. |

### `api-invitations`

Off by default, unlike the others, because turning it on widens who can make
the server send mail. The web forms have always been able to; the difference is
that a form is paced by a person and an endpoint is paced by whatever is calling
it. Before enabling, know what it does and does not open up.

What it enables: listing, sending and revoking invitations — both collaborators
and view-only readers — for anyone who can already edit the screenplay.

What it does not enable, deliberately:

- **Accepting an invitation.** There is no REST endpoint for it. Accepting
  creates a user account without authentication, and the person doing it has no
  session by definition, so it stays a browser route reached from the email
  link. Turning this flag on does not add an unauthenticated write path to
  `/api`.
- **Reading a screenplay by view token.** Also stays a browser route. The API
  never returns a token or an invite URL, so it cannot hand out a long-lived
  read-anything-in-this-screenplay credential.
- **PDF attachments.** The web form lets a sender attach a rendered PDF of the
  whole screenplay; the API does not offer the option and always sends without
  one. Rendering a screenplay per invitation is real CPU and a large outbound
  attachment, which is fine when a person chooses it each time and is not fine
  when a loop does.

Sending is rate limited per user (20 per hour) by
[`InvitationRateLimiter`](../src/main/java/com/scripty/security/InvitationRateLimiter.java).
That limiter is in-memory and per-instance: it is a brake on one runaway
session, not a distributed quota. A restart clears it and a second instance
keeps its own count.

One behaviour to preserve if this code is ever changed: sending to an address
that already has an account answers exactly like sending to one that does not.
The service returns null in that case so as not to reveal who is registered, and
the endpoint must not turn that into a distinguishable response — returning a
409 would be the obvious instinct and would undo the defence from the outside.

## Flipping a flag on Railway

Set the variable on the service (dashboard → Variables, or the CLI below).
Railway restarts the service to apply it; that restart *is* the deploy.

```bash
railway variables --set FEATURE_SERVICE_WORKER=false
```

Values must be `true` or `false` (case and surrounding whitespace are ignored).
Anything else fails the boot with a message naming the variable, rather than
silently reading as `false` — a flag quietly stuck off is much harder to notice
than a deploy that refuses. Railway keeps the previous deployment running when
the new one fails its healthcheck, so a typo costs a failed deploy, not an
outage.

Unset the variable to return to the default in the table above. The resolved
flags are logged at every startup, so Railway's logs always show what the
running instance actually has:

```
INFO com.scripty.config.FeatureFlags : Feature flags: {passkeys=true, service-worker=true}
```

## Adding a flag

1. Add a constant to `FeatureFlag` with a kebab-case key and a default. The
   property (`app.features.<key>`) and variable (`FEATURE_<KEY>`) derive from it.
2. Declare it in `application.yml` under `app.features`, pointing at the
   variable: `my-thing: ${FEATURE_MY_THING:false}`.
3. Read it by injecting `FeatureFlags` and calling
   `featureFlags.isEnabled(FeatureFlag.MY_THING)`.

New flags should default to `false` — an unset variable then means "off", so the
flag is opt-in until it's proven in production and you delete it.

Flags are for behaviour that is on or off. Settings carrying a *value*
(`APP_BASE_URL`, `MAIL_ENABLED` and the mail transport config, `APP_ASSET_VERSION`)
stay ordinary configuration and are not flags.

## Testing with flags

`FeatureFlags` takes a fixed map, so tests never touch the environment:

```java
FeatureFlags flags = new FeatureFlags(Map.of(FeatureFlag.SERVICE_WORKER, false));
```

Unlisted flags fall back to their declared default.
