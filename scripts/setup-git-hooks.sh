#!/bin/bash
# Install git pre-commit hook to sync rules automatically

set -euo pipefail

HOOK_FILE=".git/hooks/pre-commit"

echo "Configuring Git hooks..."

# Ensure we are in the repo root
if [ ! -d ".git" ]; then
  echo "Error: Must be run from the repository root."
  exit 1
fi

cat > "$HOOK_FILE" << 'EOF'
#!/bin/bash
# Automatically sync project rules before committing

echo "Checking/Syncing rules for Antigravity, Cursor, and Claude Code..."
node scripts/sync-rules.js

# Stage the updated rules files so they get committed
git add CLAUDE.md .cursorrules .cursor/rules/*.mdc
EOF

chmod +x "$HOOK_FILE"
echo "Git pre-commit hook installed successfully at $HOOK_FILE"
