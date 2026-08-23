#!/usr/bin/env node
/**
 * Measures the clue validator's FALSE POSITIVE rate against the real puzzle set.
 *
 * Why this exists: banned-word detection uses substring matching on a collapsed
 * "skeleton" form, which cannot tell a compound boundary from a coincidence
 * without a dictionary. Left unmeasured it silently rejects ordinary words, and
 * the player experiences that as a bug in the game rather than as a rule.
 *
 * This probe caught, in order: "light" rejecting delight/flight/slight,
 * "sting" rejecting casting/lasting/fasting, "sand" rejecting sandwich and
 * thousand, and "wind" rejecting window. Each resulting fix is documented in
 * worker/src/validator.ts.
 *
 * Run it after every new batch of puzzles:
 *   cd worker && npm run build && cd ../tools && node probe-validator.mjs
 *
 * Judge every line of output. A rejection that is a direct hit on a banned word
 * is the game working as designed. Anything else belongs in that puzzle's
 * `allow` list in seed-words.json.
 */

import { validate } from '../worker/dist/validator.js';
import { readFileSync } from 'node:fs';

const puzzles = JSON.parse(
  readFileSync(new URL('./out/puzzles.json', import.meta.url), 'utf8'),
);

/**
 * Ordinary words a clue-writer would legitimately reach for, plus a deliberate
 * set of near-collisions (delight/flight/slight, casting/lasting, sandwich/
 * thousand, window/grind) that exist purely to catch regressions.
 */
const corpus = `the a of and to in that it is was for on with as by at from but not
they this have had were are been has said would there their what so up out if about who
get which go me when make can like time no just him know take people into year your good
some could them see other than then now look only come its over think also back after use
two how our work first well way even new want because any these give day most us thing
big small large tiny huge long short high low deep wide narrow heavy light quick slow
hot cold warm cool wet dry hard soft smooth rough sharp dull bright dark loud quiet
animal creature beast bird fish insect plant tree flower leaf root seed fruit
object thing tool machine device gadget instrument vessel container
water air fire earth stone rock metal wood glass plastic cloth paper
house home room door window roof wall floor garden street city town village
move walk run jump climb fall rise sit stand sleep wake eat drink breathe
see hear smell taste touch feel think know remember forget learn teach
one two three four five many few several all none every each both
police nice ice rice price office notice practice service voice choice
sandwich island understand thousand demand command grandstand
towel power flower shower tower lower slower follower
paint point joint print grin grind mind wind kind find behind
casting lasting blasting fasting contrast
watch match catch patch batch latch
crown brown drown frown grown known shown thrown
spotlight highlight delight flight slight bright fright lighting
type stereotype prototype
glasses classes passes masses
hourly flourish nourish
crowd cloud proud aloud
scarce scarf scared scary
mill million milk mild mile
beetle beef been beer belt
tallow tally talon taxi
necklace nectar nectarine`
  .split(/\s+/)
  .filter(Boolean);

const unique = [...new Set(corpus)];

let total = 0;
let rejected = 0;
const examples = [];
const candidates = [];

for (const p of puzzles) {
  const bannedSet = new Set(p.banned);
  for (const w of unique) {
    total++;
    const r = validate(w, p);
    if (r === null) continue;
    rejected++;
    if (examples.length < 60) {
      examples.push(`${p.word.padEnd(13)} rejects "${w}" (${r.token})`);
    }
    // A rejection whose reported token is NOT itself a banned word means the
    // filter matched a substring rather than the word — a candidate false
    // positive worth a human decision.
    if (r.token !== undefined && !bannedSet.has(r.token)) {
      candidates.push([p.word, r.token]);
    }
  }
}

console.log(`checked ${total} (word, puzzle) pairs across ${puzzles.length} puzzles`);
console.log(`rejected: ${rejected}  (${((100 * rejected) / total).toFixed(2)}%)\n`);
console.log('sample rejections — judge each: legitimate block, or false positive?\n');
for (const e of examples) console.log('  ' + e);

if (candidates.length > 0) {
  console.log('\ncandidate false positives — matched a substring, not a banned word:\n');
  const byWord = new Map();
  for (const [word, token] of candidates) {
    if (!byWord.has(word)) byWord.set(word, new Set());
    byWord.get(word).add(token);
  }
  for (const [word, toks] of byWord) {
    console.log(`  ${word}: "allow": ${JSON.stringify([...toks])}`);
  }
  console.log('\nPaste any that are genuinely unrelated into seed-words.json,');
  console.log('then re-run generate-puzzles.mjs.');
} else {
  console.log('\nNo substring-only rejections. Every block was a direct banned-word hit.');
}
