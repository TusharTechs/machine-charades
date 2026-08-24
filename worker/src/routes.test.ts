/**
 * Tests for request routing and the puzzle endpoint.
 *
 * The load-bearing assertion is `cannotRequestAnArbitraryPuzzle`: the puzzle
 * number is resolved from the date index server-side and is never read from the
 * request. If that ever changes, a player can read the whole season ahead and
 * the daily game stops being a game.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import worker, { utcDay, handleToday, type Env } from './index.js';

const TODAY = utcDay();

const PUZZLE = {
  n: 7,
  date: TODAY,
  word: 'giraffe',
  banned: ['neck', 'tall'],
  allow: ['tally'],
  cat: 'animal',
  diff: 2,
};

const FUTURE = { ...PUZZLE, n: 8, word: 'lighthouse', date: '2099-01-01' };

/** Minimal KV stub — only `get` is exercised by these paths. */
function kv(entries: Record<string, string>): KVNamespace {
  return {
    get: async (key: string) => entries[key] ?? null,
    put: async () => undefined,
  } as unknown as KVNamespace;
}

function env(overrides: Partial<Env> = {}): Env {
  return {
    GEMINI_API_KEY: 'unused',
    FIREBASE_PROJECT_ID: 'machine-charades',
    ALLOW_UNVERIFIED: '1',
    CACHE: kv({}),
    PUZZLES: kv({
      'index:by-date': JSON.stringify({ [TODAY]: 7, '2099-01-01': 8 }),
      'puzzle:7': JSON.stringify(PUZZLE),
      'puzzle:8': JSON.stringify(FUTURE),
    }),
    ...overrides,
  };
}

const get = (path: string, e: Env = env()) =>
  worker.fetch(new Request(`https://w.dev${path}`), e);

test("serves today's puzzle with the fields the client needs", async () => {
  const res = await get('/puzzle/today');
  assert.equal(res.status, 200);
  const body = (await res.json()) as typeof PUZZLE;
  assert.equal(body.word, 'giraffe');
  // banned and allow must be present, or the local validator cannot agree
  // with the server's re-check and the player sees inconsistent rejections.
  assert.deepEqual(body.banned, ['neck', 'tall']);
  assert.deepEqual(body.allow, ['tally']);
  assert.equal(body.n, 7);
});

test('cannot request an arbitrary puzzle, so the season stays unread', async () => {
  // The property that matters in PRODUCTION: with the dev flag off, no query
  // shape may name a puzzle other than today's. Calling handleToday directly
  // keeps this focused on the resolution logic rather than on auth.
  const prod = env({ ALLOW_UNVERIFIED: undefined });
  for (const q of ['?n=8', '?number=8', '?n=8&n=7', '?N=8', '?n=%38']) {
    const res = await handleToday(prod, new URL(`https://w.dev/puzzle/today${q}`));
    assert.equal(res.status, 200, q);
    assert.equal(((await res.json()) as { n: number }).n, 7, `${q} escaped today`);
  }
});

test('requires a verified player', async () => {
  const res = await get('/puzzle/today', env({ ALLOW_UNVERIFIED: undefined }));
  assert.equal(res.status, 401);
  assert.deepEqual(await res.json(), { error: 'unauthenticated' });
});

test('unauthenticated callers learn nothing about which routes exist', async () => {
  const anon = env({ ALLOW_UNVERIFIED: undefined });
  for (const path of ['/puzzle/today', '/guess', '/nope']) {
    assert.equal((await get(path, anon)).status, 401, path);
  }
});

test('rejects the wrong method per route', async () => {
  const post = await worker.fetch(
    new Request('https://w.dev/puzzle/today', { method: 'POST' }),
    env(),
  );
  assert.equal(post.status, 405);
  // /guess stays POST-only — this is the 405 a browser GET produces.
  assert.equal((await get('/guess')).status, 405);
});

test('unknown routes are 404 for a verified player', async () => {
  assert.equal((await get('/nope')).status, 404);
});

test('says so when the schedule has run out, rather than erroring vaguely', async () => {
  const e = env({
    PUZZLES: kv({ 'index:by-date': JSON.stringify({ '2099-01-01': 8 }) }),
  });
  const res = await get('/puzzle/today', e);
  assert.equal(res.status, 404);
  assert.equal(((await res.json()) as { error: string }).error, 'no_puzzle_today');
});

test('reports a missing index distinctly from a missing puzzle', async () => {
  const res = await get('/puzzle/today', env({ PUZZLES: kv({}) }));
  assert.equal(res.status, 503);
  assert.equal(((await res.json()) as { error: string }).error, 'no_index');
});

test('is edge-cacheable but only briefly', async () => {
  const res = await get('/puzzle/today');
  assert.match(res.headers.get('cache-control') ?? '', /max-age=300/);
});

test('the dev puzzle override is inert without ALLOW_UNVERIFIED', async () => {
  // This is the override that must never work in production. A deployed Worker
  // has no ALLOW_UNVERIFIED, so ?n= must be ignored rather than honoured.
  const prod = env({ ALLOW_UNVERIFIED: undefined });
  // Unauthenticated first — proves it cannot even be reached.
  assert.equal((await get('/puzzle/today?n=8', prod)).status, 401);

  // Now authenticated by a real subject, with the dev flag still off.
  const res = await worker.fetch(
    new Request('https://w.dev/puzzle/today?n=8', {
      headers: { authorization: 'Bearer ' + 'x'.repeat(20) },
    }),
    prod,
  );
  assert.equal(res.status, 401, 'a bogus token must not pass either');
});

test('the dev override serves a chosen puzzle when explicitly enabled', async () => {
  const res = await get('/puzzle/today?n=8');
  assert.equal(res.status, 200);
  assert.equal(((await res.json()) as { word: string }).word, 'lighthouse');
});

test('the dev override rejects non-numeric input', async () => {
  const res = await get('/puzzle/today?n=../../etc/passwd');
  // Falls through to the date index, which has today -> 7.
  assert.equal(((await res.json()) as { n: number }).n, 7);
});
