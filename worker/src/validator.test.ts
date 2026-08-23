/**
 * Runs tools/clue-vectors.json against the TypeScript validator.
 *
 * The same vectors are asserted by the Kotlin suite in
 * shared/src/commonTest/.../CoreTest.kt. If one suite passes and the other
 * fails, the client and server have drifted — which is a scoring exploit, not
 * a cosmetic bug.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { validate, score, canonical, skeleton, variants } from './validator.js';
import type { ConstraintMode, Puzzle } from './validator.js';

interface Vector {
  clue: string;
  mode?: ConstraintMode;
  expect: string | null;
  token?: string;
  why: string;
}

interface ScoreVector {
  solved: boolean;
  guesses: number;
  chars: number;
  score: number;
  why: string;
}

const fixture = JSON.parse(
  readFileSync(new URL('../../tools/clue-vectors.json', import.meta.url), 'utf8'),
) as {
  puzzle: Puzzle;
  cases: Vector[];
  scoring: ScoreVector[];
  fpPuzzle: Puzzle;
  fpCases: Vector[];
};

function assertVector(v: Vector, puzzle: Puzzle) {
  const result = validate(v.clue, puzzle, v.mode ?? 'NONE');
  if (v.expect === null) {
    assert.equal(result, null, `expected accept, got ${JSON.stringify(result)}`);
  } else {
    assert.notEqual(result, null, 'expected a rejection, got accept');
    assert.equal(result!.reason, v.expect);
    if (v.token !== undefined) assert.equal(result!.token, v.token);
  }
}

test('normalisation primitives', () => {
  assert.equal(canonical('Giraffes, tall!'), 'giraffes tall');
  assert.equal(canonical('g1raffe'), 'giraffe');
  assert.equal(canonical('  double   spaces  '), 'double spaces');
  assert.equal(canonical('café'), 'cafe');
  assert.equal(canonical('g!raffe'), 'giraffe', 'interior symbol folds');
  assert.equal(canonical('wow!!!'), 'wow', 'trailing symbols do not fold');
  assert.equal(canonical('1ce cold'), 'ice cold', 'leading digit folds');

  assert.equal(skeleton('g i r a f f e'), 'girafe');
  assert.equal(skeleton('giraaaaffe'), 'girafe');
  assert.equal(skeleton('giraffe'), 'girafe');

  // Variants must over-generate rather than under-generate.
  assert.ok(variants('giraffes').has('giraffe'), 'giraffes -> giraffe');
  assert.ok(variants('tallest').has('tall'), 'tallest -> tall');
  assert.ok(variants('running').has('run'), 'running -> run');
  assert.ok(variants('ponies').has('pony'), 'ponies -> pony');
  assert.ok(variants('spotted').has('spot'), 'spotted -> spot');
  assert.ok(variants('hoping').has('hope'), 'hoping -> hope');
  assert.ok(variants('zoos').has('zoo'), 'zoos -> zoo');
  assert.deepEqual([...variants('ice')], ['ice'], 'short words are left alone');
  assert.ok(!variants('police').has('ice'), 'no spurious substring variants');
});

for (const v of fixture.cases) {
  test(`${v.expect ?? 'ACCEPT'} :: "${v.clue}" (${v.why})`, () =>
    assertVector(v, fixture.puzzle));
}

// Every one of these was a real false positive found by tools/probe-validator.mjs
// against actual puzzle content. They are the reason the containment rule
// distinguishes prefix from suffix matches.
for (const v of fixture.fpCases) {
  test(`[fp] ${v.expect ?? 'ACCEPT'} :: "${v.clue}" (${v.why})`, () =>
    assertVector(v, fixture.fpPuzzle));
}

for (const s of fixture.scoring) {
  test(`score ${s.score} :: ${s.why}`, () => {
    assert.equal(score(s.solved, s.guesses, s.chars), s.score);
  });
}

test('scoring preserves the intended ordering', () => {
  const shortFirst = score(true, 1, 20);
  const longFirst = score(true, 1, 55);
  const shortSecond = score(true, 2, 20);
  const longThird = score(true, 3, 80);

  assert.ok(shortFirst > longFirst, 'brevity should matter');
  assert.ok(
    longFirst > shortSecond,
    'a long first-try must still beat a short second-try',
  );
  assert.ok(shortSecond > longThird, 'guess count dominates');
  assert.equal(score(false, 1, 5), 0, 'unsolved is always zero');
});

test('a modified client cannot smuggle the secret word', () => {
  // Every evasion we know about, asserted in one place so a regression here is
  // loud. This is the exploit that would break the leaderboard.
  const evasions = [
    'giraffe',
    'GIRAFFE',
    'giraffe!',
    'g1raffe',
    'g!raffe',
    'g i r a f f e',
    'g-i-r-a-f-f-e',
    'giraaaffe',
    'a giraffe-like thing',
    'thegiraffe',
  ];
  for (const clue of evasions) {
    const result = validate(clue, fixture.puzzle);
    assert.notEqual(result, null, `"${clue}" slipped through`);
    assert.equal(result!.reason, 'CONTAINS_SECRET_WORD', `"${clue}"`);
  }
});
