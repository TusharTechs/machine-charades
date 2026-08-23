#!/usr/bin/env node
/**
 * Builds the daily puzzle schedule and the KV bulk-upload file.
 *
 * Why offline: generating puzzles ahead of time is what makes a normal session
 * cost nothing. Every player on a given day reads the same pre-made document,
 * so the only live model call is the guess itself — and most of those hit the
 * Worker's cache. It also removes the cold-start problem entirely: day one has
 * a real puzzle without needing any player-authored content.
 *
 * Usage:
 *   node generate-puzzles.mjs --start 2026-09-24 --days 40
 *   node generate-puzzles.mjs --start 2026-09-24 --days 90 --extend   # needs GEMINI_API_KEY
 *
 * Then:
 *   npx wrangler kv bulk put --binding PUZZLES out/kv-puzzles.json --remote
 */

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const OUT = join(here, 'out');

// ---------------------------------------------------------------------------
// args
// ---------------------------------------------------------------------------

const args = process.argv.slice(2);
const arg = (name, fallback) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
};
const flag = (name) => args.includes(`--${name}`);

const startDate = arg('start', new Date().toISOString().slice(0, 10));
const days = Number(arg('days', '40'));
const shouldExtend = flag('extend');

if (!/^\d{4}-\d{2}-\d{2}$/.test(startDate)) {
  console.error('--start must be an ISO date, e.g. 2026-09-24');
  process.exit(1);
}

// ---------------------------------------------------------------------------
// validation — the same rules the game enforces, applied to the content itself
// ---------------------------------------------------------------------------

/**
 * Minimal re-implementation of the canonical/skeleton pair from
 * worker/src/validator.ts. Kept deliberately small: this script only needs to
 * catch authoring mistakes, not adversarial players.
 */
const canonical = (s) =>
  [...s.toLowerCase()]
    .map((c) => ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ? c : ' '))
    .join('')
    .split(' ')
    .filter(Boolean)
    .join(' ');

const problems = [];
const notes = [];

function checkPuzzle(p, index) {
  const where = `#${index + 1} "${p.word}"`;
  const word = canonical(p.word).replace(/ /g, '');

  if (word.length < 4) problems.push(`${where}: too short to clue well`);
  if (!Array.isArray(p.banned) || p.banned.length < 3) {
    problems.push(`${where}: needs at least 3 banned words`);
  }

  let compoundParts = 0;

  for (const b of p.banned ?? []) {
    const banned = canonical(b).replace(/ /g, '');
    if (banned.length === 0) {
      problems.push(`${where}: empty banned entry`);
      continue;
    }
    if (banned === word) {
      problems.push(`${where}: banned word "${b}" IS the secret word`);
      continue;
    }
    // The genuinely broken direction. Banning "catapult" when the word is "cat"
    // is nonsense: the secret-word check already rejects anything containing
    // "cat", so the entry does nothing except confuse whoever reads the puzzle.
    if (banned.includes(word)) {
      problems.push(
        `${where}: banned word "${b}" contains the secret word — redundant`,
      );
    }
    // The reverse is not a bug, it's the design. For a compound like
    // "waterfall", blocking "water" and "fall" IS the puzzle — those are the
    // two routes every player reaches for first. Worth surfacing so the author
    // can see how constrained a puzzle has become, but never an error.
    if (word.includes(banned) && banned.length > 3) compoundParts++;
  }

  if (compoundParts > 0) {
    notes.push(
      `${where}: ${compoundParts} banned word(s) are parts of the compound — ` +
        `intended, but this is a hard puzzle`,
    );
  }

  const seen = new Set();
  for (const b of p.banned ?? []) {
    const key = canonical(b);
    if (seen.has(key)) problems.push(`${where}: duplicate banned word "${b}"`);
    seen.add(key);
  }
}

// ---------------------------------------------------------------------------
// optional LLM extension
// ---------------------------------------------------------------------------

async function extendWithModel(existing, target) {
  const key = process.env.GEMINI_API_KEY;
  if (!key) {
    console.error('--extend needs GEMINI_API_KEY in the environment.');
    process.exit(1);
  }
  const needed = target - existing.length;
  if (needed <= 0) return [];

  const avoid = existing.map((p) => p.word).join(', ');
  const prompt = `Generate ${needed} puzzles for a word game.

In this game a human writes one short clue and an AI tries to guess a secret
word. Each puzzle blocks the five most obvious routes to the word, which is
what makes it hard and interesting.

Rules:
- Secret words: concrete, common English nouns, 5-12 letters. No proper nouns,
  no brands, no abstractions.
- For each, list exactly 5 banned words: the category it belongs to, its single
  most defining feature, and its three nearest synonyms or associations.
- A banned word must never contain the secret word, and the secret word must
  never contain a banned word.
- Rate difficulty 1 (easy to clue around) to 5 (very hard).
- Do not reuse any of these words: ${avoid}

Return ONLY a JSON array, no markdown fence:
[{"word":"giraffe","banned":["neck","tall","africa","zoo","spots"],"cat":"animal","diff":2}]`;

  const res = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${key}`,
    {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        contents: [{ role: 'user', parts: [{ text: prompt }] }],
        generationConfig: { temperature: 0.9, responseMimeType: 'application/json' },
      }),
    },
  );
  if (!res.ok) {
    console.error(`Model call failed: HTTP ${res.status}`);
    process.exit(1);
  }
  const data = await res.json();
  const text = data.candidates?.[0]?.content?.parts?.[0]?.text ?? '[]';
  try {
    return JSON.parse(text);
  } catch {
    console.error('Model returned unparseable JSON. Raw output:\n', text);
    process.exit(1);
  }
}

// ---------------------------------------------------------------------------
// build
// ---------------------------------------------------------------------------

const seed = JSON.parse(readFileSync(join(here, 'seed-words.json'), 'utf8'));
let pool = [...seed.words];

if (shouldExtend && pool.length < days) {
  const extra = await extendWithModel(pool, days);
  console.log(`Model contributed ${extra.length} new puzzles.`);
  pool = pool.concat(extra);
}

if (pool.length < days) {
  console.warn(
    `\n⚠  Only ${pool.length} words available for ${days} days. ` +
      `The schedule will stop at ${pool.length}.\n` +
      `   Add more to seed-words.json, or re-run with --extend.\n`,
  );
}

pool.forEach(checkPuzzle);
if (problems.length > 0) {
  console.error('\n✗ Content problems — fix these before shipping:\n');
  for (const p of problems) console.error(`  - ${p}`);
  process.exit(1);
}
if (notes.length > 0) {
  console.log(`\n  ${notes.length} compound-word puzzle(s) noted (run --verbose to list)`);
  if (flag('verbose')) for (const n of notes) console.log(`    · ${n}`);
}

const puzzles = [];
const start = new Date(`${startDate}T00:00:00Z`);
const count = Math.min(days, pool.length);

for (let i = 0; i < count; i++) {
  const date = new Date(start);
  date.setUTCDate(start.getUTCDate() + i);
  const src = pool[i];
  puzzles.push({
    n: i + 1,
    date: date.toISOString().slice(0, 10),
    word: canonical(src.word).replace(/ /g, ''),
    banned: src.banned.map((b) => canonical(b).replace(/ /g, '')),
    ...(src.allow?.length
      ? { allow: src.allow.map((a) => canonical(a).replace(/ /g, '')) }
      : {}),
    cat: src.cat ?? null,
    diff: src.diff ?? 3,
  });
}

mkdirSync(OUT, { recursive: true });

// One document per day, which is what the client reads.
writeFileSync(join(OUT, 'puzzles.json'), JSON.stringify(puzzles, null, 2));

// Wrangler bulk format: [{key, value}] with value as a string.
const kv = puzzles.map((p) => ({
  key: `puzzle:${p.n}`,
  value: JSON.stringify(p),
}));
// A date index, so the client can resolve "today" without knowing the number.
kv.push({
  key: 'index:by-date',
  value: JSON.stringify(
    Object.fromEntries(puzzles.map((p) => [p.date, p.n])),
  ),
});
writeFileSync(join(OUT, 'kv-puzzles.json'), JSON.stringify(kv, null, 2));

const byDiff = puzzles.reduce((acc, p) => {
  acc[p.diff] = (acc[p.diff] ?? 0) + 1;
  return acc;
}, {});

console.log(`\n✓ ${puzzles.length} puzzles, ${puzzles[0].date} → ${puzzles.at(-1).date}`);
console.log(`  difficulty spread: ${JSON.stringify(byDiff)}`);
console.log(`  out/puzzles.json      (the schedule, for review)`);
console.log(`  out/kv-puzzles.json   (upload with wrangler kv bulk put)\n`);
console.log('  Next:  npx wrangler kv bulk put --binding PUZZLES \\');
console.log('           tools/out/kv-puzzles.json --remote\n');
