#!/usr/bin/env node
/**
 * Static guards for conventions that have already caused defects.
 *
 * Kept as a standalone script rather than ESLint rules: the project has no
 * ESLint config or TypeScript parser installed, and these checks are
 * plain-text patterns that need neither.
 *
 * Run with `npm run check:conventions` (also part of `npm run verify`).
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC = join(fileURLToPath(new URL('../src', import.meta.url)));

const RULES = [
  {
    name: 'no-duplicate-api-prefix',
    // The axios instance sets baseURL '/api'; repeating it yields /api/api/… → 404.
    pattern: /(?:api\s*\.\s*(?:get|post|put|patch|delete)\s*(?:<[^>]*>)?\s*\(\s*|['"`])\/api\//,
    message:
      'Path repeats the /api prefix already set by the axios baseURL — drop the leading /api.',
    // lib/api.ts owns the baseURL; lib/files.ts explains the rule in a comment.
    exempt: (file) => file === 'lib/api.ts' || file === 'lib/files.ts',
  },
  {
    name: 'no-hardcoded-content-type',
    // A forced Content-Type makes axios serialise FormData as JSON, or strips the
    // multipart boundary; either way uploads fail. Let axios choose per request.
    pattern: /['"]Content-Type['"]\s*:/i,
    message: 'Do not set Content-Type by hand — axios picks it from the request body.',
    exempt: () => false,
  },
  {
    name: 'no-emoji-in-source',
    // Pictographic characters instead of the project's Lucide icon set.
    pattern: /[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{2B00}-\u{2BFF}\u{FE0F}]/u,
    message: 'Use a Lucide icon component instead of a literal emoji.',
    exempt: () => false,
  },
];

function* walk(dir) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      yield* walk(full);
    } else if (/\.(ts|tsx)$/.test(entry)) {
      yield full;
    }
  }
}

let failures = 0;

for (const file of walk(SRC)) {
  const rel = relative(SRC, file);
  const lines = readFileSync(file, 'utf8').split('\n');

  for (const rule of RULES) {
    if (rule.exempt(rel)) continue;
    lines.forEach((line, i) => {
      if (rule.pattern.test(line)) {
        console.error(`${rel}:${i + 1}  [${rule.name}] ${rule.message}`);
        console.error(`    ${line.trim()}`);
        failures += 1;
      }
    });
  }
}

if (failures > 0) {
  console.error(`\n${failures} convention violation(s) found.`);
  process.exit(1);
}
console.log('Conventions OK.');
