#!/usr/bin/env node
/**
 * Chooses banned words by asking the model what it actually leans on.
 *
 * The hand-authored lists ban what a person imagines is obvious. Measured
 * against the real guesser, that left a 100% first-guess win rate: five human
 * intuitions are not enough to constrain a model that has read everything.
 *
 * So: draw a sample of clues for each word, count the content terms that recur,
 * and ban the ones the model keeps returning to. Frequency-derived bans are also
 * *fair* bans — by construction they are the obvious routes, so a player reading
 * the list thinks "yes, those" rather than "why that?".
 *
 *   node harden-puzzles.mjs [--samples 8] [--target 8] [--words 40] [--dry]
 */
import { readFileSync, writeFileSync } from 'node:fs';

const KEY = readFileSync('../worker/.dev.vars', 'utf8')
  .match(/GEMINI_API_KEY\s*=\s*"?([^"\s]+)"?/)?.[1];
if (!KEY) { console.error('no GEMINI_API_KEY in worker/.dev.vars'); process.exit(1); }

const MODEL = 'gemini-3.5-flash-lite';
const arg = (n, d) => { const i = process.argv.indexOf(`--${n}`); return i >= 0 ? process.argv[i+1] : d; };
const SAMPLES = Number(arg('samples', '8'));
const TARGET  = Number(arg('target', '8'));
const DRY     = process.argv.includes('--dry');

// Banning a function word would reject almost every clue ever written, and the
// player would experience that as the game being broken.
const STOP = new Set(`a an the and or but of in on at to for with from by as is are was were be been
it its this that these those you your they them their he she his her we our us i my me
something someone thing things very often used use uses using can could would should
what which who whom when where why how all any both each few more most other some such
no nor not only own same so than too s t don now d ll m o re ve y one two three
has have had having make makes made keep keeps kept get gets got take takes taken
give gives given come comes came goes going went find finds found know knows knew
right left through around above below under over into onto after before while during
often always never sometimes usually really quite rather much many little
called known named sort kind type form part piece bit lot
large small great good long short high low big`.split(/\s+/));

/**
 * Detection matches banned words as substrings of a collapsed skeleton, so a
 * short ban is a trap: "has" would reject "chase", "wall" would reject
 * "swallow". tools/probe-validator.mjs exists because of exactly this. Five
 * characters is the length below which collisions stop being rare.
 */
const MIN_BAN_LENGTH = 5;

// seed-words.json is a document: { _comment, _rules, words: [...] }.
const doc = JSON.parse(readFileSync('./seed-words.json', 'utf8'));
const words = doc.words;
const chosen = words.slice(0, Number(arg('words', String(words.length))));

const ask = async (prompt) => {
  const res = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${KEY}`,
    { method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ contents: [{ parts: [{ text: prompt }] }] }) });
  const d = await res.json();
  return (d.candidates?.[0]?.content?.parts?.[0]?.text ?? '').trim();
};

const canonical = (s) => s.toLowerCase().replace(/[^a-z0-9 ]/g, ' ').split(/\s+/).filter(Boolean);

// --- pass one: sample clues for every word ---------------------------------
// Gathered before scoring because a term only earns a ban by being specific to
// one word. That cannot be known from one word's clues alone.
const samples = new Map();   // word -> Map(term -> clues containing it)

for (const entry of chosen) {
  const { word } = entry;
  const raw = await ask([
    `Write ${SAMPLES} different one-line clues for the word "${word}".`,
    `Each should make another AI guess "${word}".`,
    `Do not use the word "${word}" itself.`,
    'Vary your approach — different angles, not rephrasings of one idea.',
    'Reply with one clue per line, nothing else.',
  ].join('\n'));

  const counts = new Map();
  for (const line of raw.split('\n')) {
    // Once per clue: a term repeated inside one sentence is a tic, not reliance.
    for (const t of new Set(canonical(line))) {
      if (t.length < MIN_BAN_LENGTH || STOP.has(t)) continue;
      if (t.includes(word) || word.includes(t)) continue;
      counts.set(t, (counts.get(t) ?? 0) + 1);
    }
  }
  samples.set(word, counts);
  process.stdout.write('.');
}
process.stdout.write('\n\n');

// --- pass two: score by specificity ----------------------------------------
// "acacia" appears for one word; "famous" appears for a dozen. Frequency alone
// bans the model's verbal tics, which are not routes to the answer and make the
// blocked list read as arbitrary. Dividing by document frequency keeps only the
// terms that actually lead somewhere.
const documentFreq = new Map();
for (const counts of samples.values()) {
  for (const t of counts.keys()) documentFreq.set(t, (documentFreq.get(t) ?? 0) + 1);
}

for (const entry of chosen) {
  const { word } = entry;
  const existing = new Set(entry.banned.map((b) => b.toLowerCase()));
  const counts = samples.get(word);

  const added = [...counts.entries()]
    .filter(([t, n]) => n >= 2 && !existing.has(t))
    .map(([t, n]) => [t, n / documentFreq.get(t)])
    .filter(([, score]) => score >= 1.5)      // recurring here, uncommon elsewhere
    .sort((a, b) => b[1] - a[1])
    .slice(0, Math.max(0, TARGET - entry.banned.length))
    .map(([t]) => t);

  entry.banned = [...entry.banned, ...added];
  console.log(`${word.padEnd(13)} +${String(added.length).padStart(2)}  ${added.join(', ') || '—'}`);
}

if (DRY) console.log('\n--dry: seed-words.json not written');
else {
  writeFileSync('./seed-words.json', JSON.stringify(doc, null, 2) + '\n');
  console.log('\nseed-words.json updated');
}
