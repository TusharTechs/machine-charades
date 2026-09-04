#!/usr/bin/env node
/**
 * Measures how often the machine wins.
 *
 * The design claim is that a player has three guesses and real tension. The
 * observed behaviour is that the model solves nearly everything on the first
 * attempt, which makes the three-guess structure decorative. This puts a number
 * on it so a change to the puzzles can be judged rather than felt.
 *
 * Self-play: the model writes a clue under the puzzle's constraints, the clue is
 * checked by the real validator, and a separate call tries to guess it. Using
 * one model for both roles flatters the result — it writes clues it can read —
 * so treat the absolute number as a ceiling. What matters is the same procedure
 * run against two ban sets, where the bias cancels.
 *
 *   node measure-difficulty.mjs [--puzzles 12] [--bans path.json]
 */
import { validate } from '../worker/dist/validator.js';
import { readFileSync } from 'node:fs';

const KEY = readFileSync('../worker/.dev.vars', 'utf8')
  .match(/GEMINI_API_KEY\s*=\s*"?([^"\s]+)"?/)?.[1];
if (!KEY) { console.error('no GEMINI_API_KEY in worker/.dev.vars'); process.exit(1); }

// The Worker's model, so this measures the real guesser.
const MODEL = 'gemini-3.5-flash-lite';
const arg = (n, d) => { const i = process.argv.indexOf(`--${n}`); return i >= 0 ? process.argv[i+1] : d; };

async function ask(prompt) {
  const res = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${KEY}`,
    { method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ contents: [{ parts: [{ text: prompt }] }] }) },
  );
  const data = await res.json();
  return (data.candidates?.[0]?.content?.parts?.[0]?.text ?? '').trim();
}

/** The Worker's guesser prompt, so this measures the real thing. */
const guessPrompt = (clue, previous) => [
  'You are playing a word-guessing game. A human has written a clue for a',
  'secret word. Guess the word.', '',
  `Clue: "${clue}"`,
  previous.length ? `You already guessed these and were wrong: ${previous.join(', ')}. Do not repeat them.` : '',
  '', 'Reply with your single best guess: one common English noun, lowercase,',
  'no punctuation, no explanation. Just the word.',
].filter(Boolean).join('\n');

const cluePrompt = (p, budget) => [
  `You are playing a word game. The secret word is "${p.word}".`,
  'Write a clue that would make another AI guess it.',
  `You may NOT use these words or any variation of them: ${p.banned.join(', ')}.`,
  `You may not use the word "${p.word}" itself.`,
  `Your clue must be at most ${budget} characters. This is a hard limit.`,
  'Reply with the clue only, no quotes, no explanation.',
].join('\n');

/** Character budgets to sweep. Scoring's brevity bonus runs out at 60. */
const BUDGETS = (arg('budgets', '60,40,25,15')).split(',').map(Number);

const puzzles = JSON.parse(readFileSync(arg('bans', './out/puzzles.json'), 'utf8'))
  .slice(0, Number(arg('puzzles', '12')));

const results = [];

for (const budget of BUDGETS) {
  let solvedFirst = 0, tooLong = 0, played = 0;
  for (const p of puzzles) {
    let clue = null;
    for (let i = 0; i < 3 && clue === null; i++) {
      const candidate = (await ask(cluePrompt(p, budget))).replace(/^["']|["']$/g, '').trim();
      // Over budget is not an invalid clue, it is a clue the player could not
      // have submitted. Counting it as a loss would measure the model's
      // instruction-following rather than the game.
      if (candidate.length > budget) { tooLong++; continue; }
      if (validate(candidate, p, 'NONE') === null) clue = candidate;
    }
    if (clue === null) continue;
    played++;
    const g = (await ask(guessPrompt(clue, []))).toLowerCase().replace(/[^a-z]/g, '');
    if (g === p.word) solvedFirst++;
  }
  results.push({ budget, solvedFirst, played, tooLong });
  process.stdout.write('.');
}
process.stdout.write('\n');

console.log('\nbudget  first-guess wins   clues written');
console.log('─'.repeat(48));
for (const r of results) {
  const pct = r.played ? Math.round(r.solvedFirst / r.played * 100) : 0;
  const bar = '█'.repeat(Math.round(pct / 5)).padEnd(20);
  console.log(`${String(r.budget).padStart(4)}c  ${bar} ${String(pct).padStart(3)}%   ${r.played}/${puzzles.length}`);
}
