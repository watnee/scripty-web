const fs = require('fs');
const path = require('path');

const isCheckMode = process.argv.includes('--check');
let driftFound = false;

const repoRoot = path.join(__dirname, '..');
const agentsMdPath = path.join(repoRoot, '.agents', 'AGENTS.md');
const claudeMdPath = path.join(repoRoot, 'CLAUDE.md');
const cursorRulesPath = path.join(repoRoot, '.cursorrules');
const cursorRulesDir = path.join(repoRoot, '.cursor', 'rules');
const claudeRulesDir = path.join(repoRoot, '.claude', 'rules');

// Known filename mappings for existing or specialized MDC files
const filenameMap = {
  'Git Commit & Push Proactivity': 'git-commit-push-proactivity.mdc',
  'Mobile Cursor → Railway': 'mobile-railway-deploy.mdc',
  'Persistent H2 Database Setup': 'persistent-h2-database-setup.mdc',
  'Layout Alignment and Third-Party CSS Guardrails': 'layout-alignment-css-guardrails.mdc'
};

function getFilename(title) {
  if (filenameMap[title]) return filenameMap[title];
  return title
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '') + '.mdc';
}

// Known filename mappings for Claude Code Markdown rules files
const claudeFilenameMap = {
  'Git Commit & Push Proactivity': 'git-commit-push-proactivity.md',
  'Mobile Cursor → Railway': 'mobile-railway-deploy.md',
  'Persistent H2 Database Setup': 'persistent-h2-database-setup.md',
  'Layout Alignment and Third-Party CSS Guardrails': 'layout-alignment-css-guardrails.md'
};

function getClaudeFilename(title) {
  if (claudeFilenameMap[title]) return claudeFilenameMap[title];
  return title
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '') + '.md';
}

// Standard file paths/globs for scoped Claude Code rules
const claudePathsMap = {
  'Git Commit & Push Proactivity': null, // unconditional
  'Mobile Cursor → Railway': null, // unconditional
  'Persistent H2 Database Setup': ['**/application*.yml', '**/application*.properties', '**/pom.xml'],
  'Layout Alignment and Third-Party CSS Guardrails': ['**/*.css', '**/*.html']
};

function getClaudePaths(title) {
  return claudePathsMap[title] || null;
}

if (!fs.existsSync(agentsMdPath)) {
  console.error(`Error: .agents/AGENTS.md not found at ${agentsMdPath}`);
  process.exit(1);
}

// 1. Read and parse .agents/AGENTS.md
const content = fs.readFileSync(agentsMdPath, 'utf8');
const sections = [];
const parts = content.split(/\n### /);

for (let i = 1; i < parts.length; i++) {
  const part = parts[i];
  const firstNewLine = part.indexOf('\n');
  const rawTitle = firstNewLine === -1 ? part : part.substring(0, firstNewLine);
  const rawBody = firstNewLine === -1 ? '' : part.substring(firstNewLine + 1);

  const title = rawTitle.trim();
  let body = rawBody.trim();
  
  if (body.endsWith('---')) {
    body = body.substring(0, body.length - 3).trim();
  }
  
  sections.push({ title, body });
}

const rulesSectionContent = sections.map(s => `### ${s.title}\n\n${s.body}`).join('\n\n---\n\n');

// 2. Generate expected content
const expectedClaudeMd = `# Scripty Workspace Guide & Rules

This file is the single source of truth for Claude Code. It contains common developer commands and project rules.

## Commands

- **Start test server (Local)**: \`./start-test-server.command\`
- **Restart test server**: \`./restart-test-server.command\`
- **Headless server (agents/routines/CI)**: \`scripts/dev-server.sh {start|stop|restart|status|wait|logs}\`
- **Build**: \`mvn clean package\` or \`mvn compile\`
- **Test**: \`mvn verify\`
- **Run Cloudflare dev**: \`npm run cf:dev\`
- **Deploy Cloudflare**: \`npm run cf:deploy\`
- **Sync secrets**: \`npm run cf:sync\`
- **Provision/rotate CI Cloudflare API token**: \`npm run cf:token\` (no dashboard token creation)
- **Sync project rules**: \`npm run rules:sync\`

## Project Rules

Refer to the rules below before writing or modifying code.

${rulesSectionContent}
`;

const expectedCursorRules = `# Scripty Project Rules

This file contains the unified project rules for Cursor.

${rulesSectionContent}
`;

function checkOrWrite(filePath, expectedContent) {
  const normalizedExpected = expectedContent.trim().replace(/\r\n/g, '\n');
  if (isCheckMode) {
    if (!fs.existsSync(filePath)) {
      console.error(`[DRIFT] File does not exist: ${path.relative(repoRoot, filePath)}`);
      driftFound = true;
      return;
    }
    const current = fs.readFileSync(filePath, 'utf8').trim().replace(/\r\n/g, '\n');
    if (current !== normalizedExpected) {
      console.error(`[DRIFT] Content drift detected in: ${path.relative(repoRoot, filePath)}`);
      driftFound = true;
    }
  } else {
    const dir = path.dirname(filePath);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    fs.writeFileSync(filePath, expectedContent, 'utf8');
    console.log(`[SYNC] Wrote ${path.relative(repoRoot, filePath)}`);
  }
}

// Check or write CLAUDE.md
checkOrWrite(claudeMdPath, expectedClaudeMd);

// Check or write .cursorrules
checkOrWrite(cursorRulesPath, expectedCursorRules);

// Check, write or prune .cursor/rules/*.mdc files
const activeFilenames = new Set();

for (const { title, body } of sections) {
  const filename = getFilename(title);
  activeFilenames.add(filename);
  const mdcPath = path.join(cursorRulesDir, filename);

  let frontmatter = `description: Rule for ${title}\nalwaysApply: true\nsyncSource: agents`;
  if (fs.existsSync(mdcPath)) {
    const existingFile = fs.readFileSync(mdcPath, 'utf8');
    const match = existingFile.match(/^---([\s\S]*?)---\n?([\s\S]*)$/);
    if (match) {
      let parsedFrontmatter = match[1].trim();
      if (!parsedFrontmatter.includes('syncSource:')) {
        parsedFrontmatter += '\nsyncSource: agents';
      }
      frontmatter = parsedFrontmatter;
    }
  }

  const expectedMdcContent = `---
${frontmatter}
---

# ${title}

${body}
`;

  checkOrWrite(mdcPath, expectedMdcContent);
}

// Check, write or prune .claude/rules/*.md files
const activeClaudeFilenames = new Set();

for (const { title, body } of sections) {
  const filename = getClaudeFilename(title);
  activeClaudeFilenames.add(filename);
  const mdPath = path.join(claudeRulesDir, filename);

  const paths = getClaudePaths(title);
  let frontmatter = '';
  if (paths) {
    frontmatter = `paths:\n${paths.map(p => `  - "${p}"`).join('\n')}\nsyncSource: agents`;
  } else {
    frontmatter = `syncSource: agents`;
  }

  if (fs.existsSync(mdPath)) {
    const existingFile = fs.readFileSync(mdPath, 'utf8');
    const match = existingFile.match(/^---([\s\S]*?)---\n?([\s\S]*)$/);
    if (match) {
      let parsedFrontmatter = match[1].trim();
      if (!parsedFrontmatter.includes('syncSource:')) {
        parsedFrontmatter += '\nsyncSource: agents';
      }
      frontmatter = parsedFrontmatter;
    }
  }

  const expectedMdContent = `---
${frontmatter}
---

# ${title}

${body}
`;

  checkOrWrite(mdPath, expectedMdContent);
}

// Prune obsolete generated MDC files
if (fs.existsSync(cursorRulesDir)) {
  const files = fs.readdirSync(cursorRulesDir);
  for (const file of files) {
    if (file.endsWith('.mdc') && !activeFilenames.has(file)) {
      const filePath = path.join(cursorRulesDir, file);
      const content = fs.readFileSync(filePath, 'utf8');
      if (content.includes('syncSource: agents') || Object.values(filenameMap).includes(file)) {
        if (isCheckMode) {
          console.error(`[DRIFT] Obsolete rules file should be deleted: ${path.relative(repoRoot, filePath)}`);
          driftFound = true;
        } else {
          fs.unlinkSync(filePath);
          console.log(`[SYNC] Deleted obsolete rules file: ${path.relative(repoRoot, filePath)}`);
        }
      }
    }
  }
}

// Prune obsolete generated Claude MD files
if (fs.existsSync(claudeRulesDir)) {
  const files = fs.readdirSync(claudeRulesDir);
  for (const file of files) {
    if (file.endsWith('.md') && !activeClaudeFilenames.has(file)) {
      const filePath = path.join(claudeRulesDir, file);
      const content = fs.readFileSync(filePath, 'utf8');
      if (content.includes('syncSource: agents') || Object.values(claudeFilenameMap).includes(file)) {
        if (isCheckMode) {
          console.error(`[DRIFT] Obsolete rules file should be deleted: ${path.relative(repoRoot, filePath)}`);
          driftFound = true;
        } else {
          fs.unlinkSync(filePath);
          console.log(`[SYNC] Deleted obsolete rules file: ${path.relative(repoRoot, filePath)}`);
        }
      }
    }
  }
}

if (isCheckMode) {
  if (driftFound) {
    console.error('\n[DRIFT] Verification failed. Rule files are out of sync.');
    console.error('Run "npm run rules:sync" to synchronize rules.');
    process.exit(1);
  } else {
    console.log('[CHECK] Verification passed. All rule files are synchronized.');
  }
}
