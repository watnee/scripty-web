# Scripty

## CI/CD

GitHub Actions runs Maven verify on pull requests and on pushes to `main`. Successful pushes to `main` deploy to Railway via the Railway CLI.

**GitHub Actions secrets** (Settings → Secrets and variables → Actions):

| Secret | Source |
|--------|--------|
| `RAILWAY_TOKEN` | Railway project → Settings → Tokens (production environment) |
| `RAILWAY_SERVICE_ID` | Railway service ID for the Scripty web service |

Disable Railway’s automatic GitHub deploys for this service so pushes are not deployed twice.
