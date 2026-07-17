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
