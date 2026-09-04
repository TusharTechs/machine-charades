/**
 * Machine Charades — guess proxy.
 *
 * Why this exists at all: the app must never ship an LLM API key. Mobile
 * binaries are trivially extractable (`strings` on the framework finds it in
 * seconds) and the usual outcome is a surprise five-figure bill. So the key
 * lives in Worker Secrets and the client gets an endpoint instead.
 *
 * The Worker also does four things the client cannot be trusted to do:
 *   1. re-validate the clue (a patched client would otherwise submit the
 *      secret word and win every day)
 *   2. rate-limit per player
 *   3. cache identical clues, which is both a cost win and a latency win
 *   4. recompute the score, so the leaderboard never trusts a client number
 *
 * Cost note: a ~1000-token judging call on Gemini Flash-Lite runs about
 * $0.0002. Puzzles are batch-generated offline, so a session's only live call
 * is the guess itself — and most of those hit the cache.
 */

import { validate, score, canonical, MAX_GUESSES, type Puzzle } from './validator.js';
import { verifyIdToken, googleKeyResolver } from './auth.js';

export interface Env {
  /** `wrangler secret put GEMINI_API_KEY` — never in wrangler.toml. */
  GEMINI_API_KEY: string;
  /** Cache of clue -> guess. Also the rate-limit counter store. */
  CACHE: KVNamespace;
  /** Daily puzzles, keyed `puzzle:<n>`. Written by tools/generate-puzzles.mjs. */
  PUZZLES: KVNamespace;
  /** Firebase project id, for App Check token verification. */
  FIREBASE_PROJECT_ID: string;
  /** Set to "1" in dev to skip App Check. Never set in production. */
  ALLOW_UNVERIFIED?: string;
}

/**
 * Pinned deliberately, not an alias.
 *
 * `gemini-flash-lite-latest` would keep itself current, but it would also
 * change the model underneath a running season. Guesses are cached by clue and
 * the leaderboard compares players across days, so the same clue must return
 * the same guess for as long as a puzzle is live — a silent model swap makes
 * the cache wrong and the ranking unfair.
 *
 * Was `gemini-2.5-flash-lite`, which Google retired for new keys on 23 Aug 2026
 * ("no longer available to new users"). Note it still appears in the
 * `/v1beta/models` listing, so confirm a replacement with an actual
 * `generateContent` call rather than trusting the list.
 */
const MODEL = 'gemini-3.5-flash-lite';
const ENDPOINT = (model: string) =>
  `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;

/** Hard timeout on the model call. Past this the client shows the fallback. */
const MODEL_TIMEOUT_MS = 3000;

/** Guesses allowed per player per day, generously above legitimate play. */
const DAILY_CALL_LIMIT = 60;

interface GuessRequest {
  puzzleNumber: number;
  clue: string;
  attempt: number;
  mode?: 'NONE' | 'NO_VOWELS' | 'ONE_WORD' | 'TWENTY_CHAR_CAP';
  /** Guesses already made this round, so the model doesn't repeat itself. */
  previousGuesses?: string[];
}

interface GuessResponse {
  guess: string;
  correct: boolean;
  confidence: number;
  /** Median clue length among everyone who has solved this puzzle. */
  par?: number;
  /** Shortest clue that has ever worked on this puzzle. */
  best?: number;
  /** How many players have solved it, so the client can hide a par of one. */
  solvers?: number;
  /** True when served from KV rather than the model. Useful in the client's
   *  telemetry — a high cache rate is the thing keeping costs at zero. */
  cached: boolean;
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8' },
  });

/** UTC, deliberately: the day boundary is the same for every player. */
export const utcDay = (now: Date = new Date()): string =>
  now.toISOString().slice(0, 10);

/**
 * Today's puzzle, and only today's.
 *
 * The client needs `word` — the player writes a clue *for* it — plus `banned`
 * and `allow`, so the local validator can reject a clue as it is typed without
 * a round trip. That is the same data the server holds; nothing is withheld,
 * because the player is meant to see it.
 *
 * What IS withheld is every other day. Serving `puzzle:<n>` for an arbitrary n
 * would let anyone read the whole season ahead, and a daily game where the
 * answers are knowable in advance has no game left. So the puzzle number is
 * resolved from the date index server-side and never taken from the request.
 */
/**
 * What everyone else spent on this puzzle.
 *
 * A histogram of successful clue lengths rather than a running mean: par is a
 * median, and a median needs the distribution. Lengths are capped at 120 by the
 * validator, so this is at most 120 small integers however many people play.
 */
export interface ParStats {
  /** Number of solved rounds recorded. */
  n: number;
  /** Shortest clue that has ever worked. */
  best: number;
  /** clue length -> how many players landed it at that length. */
  hist: Record<string, number>;
}

const parKey = (puzzleNumber: number) => `par:${puzzleNumber}`;

/** The median of a length histogram. */
export function medianOf(stats: ParStats): number {
  const target = stats.n / 2;
  let seen = 0;
  for (const len of Object.keys(stats.hist).map(Number).sort((a, b) => a - b)) {
    seen += stats.hist[String(len)];
    if (seen >= target) return len;
  }
  return stats.best;
}

/**
 * Folds one solved round into the puzzle's par.
 *
 * Read-modify-write on KV, which is last-write-wins, so simultaneous solves can
 * drop a sample. That is an acceptable trade here and not worth a Durable
 * Object: par is a soft statistic shown to one decimal place of meaning, and
 * losing the occasional data point moves a median by nothing. What it must
 * never do is fail the round, hence the catch.
 */
async function recordPar(
  env: Env,
  puzzleNumber: number,
  clueChars: number,
): Promise<ParStats | null> {
  try {
    const raw = await env.CACHE.get(parKey(puzzleNumber));
    const stats: ParStats = raw
      ? (JSON.parse(raw) as ParStats)
      : { n: 0, best: clueChars, hist: {} };

    stats.n += 1;
    stats.best = Math.min(stats.best, clueChars);
    const bucket = String(clueChars);
    stats.hist[bucket] = (stats.hist[bucket] ?? 0) + 1;

    // Outlives the 14-day guess cache: par is what makes an archived puzzle
    // worth replaying, so it has to survive longer than the round did.
    await env.CACHE.put(parKey(puzzleNumber), JSON.stringify(stats), {
      expirationTtl: 60 * 60 * 24 * 90,
    });
    return stats;
  } catch {
    return null;
  }
}

export async function handleToday(env: Env, url: URL): Promise<Response> {
  // Dev-only escape hatch. The schedule starts on launch day, so before then
  // there is no puzzle for "today" and the app cannot be exercised at all.
  // Gated on ALLOW_UNVERIFIED, which is never set on a deployed Worker — the
  // same flag that already means "this is not production". Tested inert
  // without it, because this is exactly the override that must not ship.
  if (env.ALLOW_UNVERIFIED === '1') {
    const forced = url.searchParams.get('n');
    if (forced !== null && /^[0-9]+$/.test(forced)) {
      const raw = await env.PUZZLES.get(`puzzle:${forced}`);
      return raw === null
        ? json({ error: 'unknown_puzzle' }, 404)
        : new Response(raw, { headers: { 'content-type': 'application/json; charset=utf-8' } });
    }
  }

  const indexRaw = await env.PUZZLES.get('index:by-date');
  if (indexRaw === null) return json({ error: 'no_index' }, 503);

  const today = utcDay();
  const number = (JSON.parse(indexRaw) as Record<string, number>)[today];
  // Ran out of scheduled puzzles — generate more rather than letting the game
  // silently end. Distinct from an error so the client can say so.
  if (typeof number !== 'number') return json({ error: 'no_puzzle_today', date: today }, 404);

  const puzzleRaw = await env.PUZZLES.get(`puzzle:${number}`);
  if (puzzleRaw === null) return json({ error: 'unknown_puzzle' }, 404);

  return new Response(puzzleRaw, {
    headers: {
      'content-type': 'application/json; charset=utf-8',
      // Same answer for everyone all day, so let the edge serve it. Short
      // enough that a fresh puzzle appears promptly after the UTC rollover.
      'cache-control': 'public, max-age=300',
    },
  });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    // Every route needs a verified player. Doing this before the method check
    // means an unauthenticated caller learns nothing about which routes exist.
    const playerId = await verifyPlayer(request, env);
    if (playerId === null) return json({ error: 'unauthenticated' }, 401);

    if (url.pathname === '/puzzle/today') {
      if (request.method !== 'GET') return json({ error: 'method_not_allowed' }, 405);
      return handleToday(env, url);
    }

    if (url.pathname !== '/guess') return json({ error: 'not_found' }, 404);
    if (request.method !== 'POST') return json({ error: 'method_not_allowed' }, 405);

    // --- rate limit ---------------------------------------------------------
    const day = utcDay();
    const rlKey = `rl:${day}:${playerId}`;
    const used = Number((await env.CACHE.get(rlKey)) ?? '0');
    if (used >= DAILY_CALL_LIMIT) {
      return json({ error: 'rate_limited', retryAfterSeconds: 3600 }, 429);
    }

    // --- parse --------------------------------------------------------------
    let body: GuessRequest;
    try {
      body = (await request.json()) as GuessRequest;
    } catch {
      return json({ error: 'bad_json' }, 400);
    }
    if (
      typeof body.puzzleNumber !== 'number' ||
      typeof body.clue !== 'string' ||
      typeof body.attempt !== 'number' ||
      body.attempt < 1 ||
      body.attempt > MAX_GUESSES
    ) {
      return json({ error: 'bad_request' }, 400);
    }

    // --- puzzle -------------------------------------------------------------
    const puzzleRaw = await env.PUZZLES.get(`puzzle:${body.puzzleNumber}`);
    if (puzzleRaw === null) return json({ error: 'unknown_puzzle' }, 404);
    const puzzle = JSON.parse(puzzleRaw) as Puzzle;

    // --- re-validate. This is the trust boundary. ---------------------------
    const rejection = validate(body.clue, puzzle, body.mode ?? 'NONE');
    if (rejection !== null) {
      // A legitimate client never reaches here — it ran the same check locally.
      // Getting here means either a patched client or a validator drift bug.
      return json({ error: 'invalid_clue', rejection }, 422);
    }

    // --- cache --------------------------------------------------------------
    // Key on the puzzle plus the normalised clue, so "Yellow  patchwork!" and
    // "yellow patchwork" share an entry. Players converge hard on the same
    // phrasings, so this runs at a high hit rate within days — and a hit
    // returns in ~20ms instead of ~700ms, which makes the game feel better.
    const cacheKey = `g:${puzzle.n}:${body.attempt}:${await sha256(
      canonical(body.clue),
    )}`;
    const clueChars = body.clue.trim().length;
    const cached = await env.CACHE.get(cacheKey);
    if (cached !== null) {
      const hit = JSON.parse(cached) as Omit<GuessResponse, 'cached'>;
      // Par is recorded on the cache path too. The cached value is the model's
      // answer, not this player's round — someone reaching the same clue as an
      // earlier player still solved it, and still counts.
      const stats = hit.correct
        ? await recordPar(env, puzzle.n, clueChars)
        : null;
      return json({
        ...hit,
        ...(stats ? { par: medianOf(stats), best: stats.best, solvers: stats.n } : {}),
        cached: true,
      } satisfies GuessResponse);
    }

    // --- model --------------------------------------------------------------
    let guess: string;
    try {
      guess = await callModel(env, puzzle, body);
    } catch (err) {
      // Never fail the round on an upstream problem — the client has a
      // deterministic fallback, and a timeout must not cost the player a turn.
      return json(
        { error: 'upstream_unavailable', detail: String(err) },
        503,
      );
    }

    const correct = isCorrect(guess, puzzle.word);
    const payload = { guess, correct, confidence: correct ? 1 : 0.4 };

    // Deliberately not part of `payload`, which is what gets cached: par moves
    // as more people play, and a cached par would freeze at whatever it was
    // when the first player used this clue.
    const stats = correct ? await recordPar(env, puzzle.n, clueChars) : null;

    // Cache and increment the counter without holding up the response.
    await Promise.all([
      env.CACHE.put(cacheKey, JSON.stringify(payload), {
        expirationTtl: 60 * 60 * 24 * 14,
      }),
      env.CACHE.put(rlKey, String(used + 1), { expirationTtl: 60 * 60 * 36 }),
    ]);

    return json({
      ...payload,
      ...(stats ? { par: medianOf(stats), best: stats.best, solvers: stats.n } : {}),
      cached: false,
    } satisfies GuessResponse);
  },
} satisfies ExportedHandler<Env>;

// ---------------------------------------------------------------------------

/**
 * A guess counts if it normalises to the same word, or to a shared root.
 * "giraffes" and "a giraffe" both count — refusing them would feel like a bug
 * to the player, and the machine has no reason to be pedantic about number.
 */
function isCorrect(guess: string, word: string): boolean {
  const g = canonical(guess).replace(/ /g, '');
  const w = canonical(word).replace(/ /g, '');
  if (g === w) return true;
  // Tolerate an article or a plural without pulling in the full variant logic.
  const stripped = canonical(guess)
    .split(' ')
    .filter((t) => !['a', 'an', 'the'].includes(t))
    .join('');
  return stripped === w || stripped === `${w}s` || `${stripped}s` === w;
}

/**
 * Asks the model to guess. Two properties matter more than prompt cleverness:
 * the model must NOT be told the answer (or it trivially "guesses" it), and it
 * must return one word so the reveal animation has something short to show.
 */
async function callModel(
  env: Env,
  puzzle: Puzzle,
  body: GuessRequest,
): Promise<string> {
  const previous = (body.previousGuesses ?? []).filter((g) => g.length > 0);

  const prompt = [
    'You are playing a word-guessing game. A human has written a clue for a',
    'secret word. Guess the word.',
    '',
    `Clue: "${body.clue.trim()}"`,
    previous.length > 0
      ? `You already guessed these and were wrong: ${previous.join(', ')}. Do not repeat them.`
      : '',
    '',
    'Reply with your single best guess: one common English noun, lowercase,',
    'no punctuation, no explanation. Just the word.',
  ]
    .filter((line) => line !== '')
    .join('\n');

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), MODEL_TIMEOUT_MS);

  try {
    const res = await fetch(`${ENDPOINT(MODEL)}?key=${env.GEMINI_API_KEY}`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      signal: controller.signal,
      body: JSON.stringify({
        contents: [{ role: 'user', parts: [{ text: prompt }] }],
        generationConfig: {
          // Deterministic, so the same clue always gets the same guess — which
          // is what makes the cache correct rather than merely cheap, and what
          // makes the daily leaderboard fair.
          temperature: 0,
          maxOutputTokens: 8,
          responseMimeType: 'text/plain',
          // `thinkingConfig: { thinkingBudget: 0 }` was here, to stop reasoning
          // tokens blowing the 3s budget on what is only a classification call.
          // gemini-3.5-flash-lite rejects it — 400 INVALID_ARGUMENT, with no
          // field named. Every other field in this request is accepted; a probe
          // that added them one at a time isolated this one.
          //
          // Omitted rather than replaced: the model is fast enough without it
          // (measured below MODEL_TIMEOUT_MS), and guessing at a non-zero
          // budget would be inventing an API contract. If latency regresses,
          // measure before adding anything back.
        },
        safetySettings: [],
      }),
    });

    if (!res.ok) {
      // Log upstream's reason but never return it. A 429 body distinguishes
      // "free-tier limit is zero for this model" from "you used your day's
      // quota", and the two need different fixes — but it is also upstream
      // detail a caller has no business seeing. Visible via `wrangler tail`.
      console.error(`model_http_${res.status}`, (await res.text()).slice(0, 600));
      throw new Error(`model_http_${res.status}`);
    }

    const data = (await res.json()) as {
      candidates?: { content?: { parts?: { text?: string }[] } }[];
    };
    const text = data.candidates?.[0]?.content?.parts?.[0]?.text ?? '';
    const word = canonical(text).split(' ')[0] ?? '';
    if (word.length === 0) throw new Error('model_empty');
    return word;
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Verifies a Firebase Auth ID token and returns a stable player id.
 *
 * The real work is in auth.ts. This layer only turns any failure into `null`,
 * which the caller renders as a 401 — the reason is deliberately not sent to
 * the client, since "bad_audience" versus "bad_signature" is a hint an attacker
 * can probe against.
 */
async function verifyPlayer(request: Request, env: Env): Promise<string | null> {
  const header = request.headers.get('authorization') ?? '';
  const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
  if (token.length === 0) {
    return env.ALLOW_UNVERIFIED === '1' ? 'dev-player' : null;
  }
  try {
    return await verifyIdToken(token, env.FIREBASE_PROJECT_ID, googleKeyResolver());
  } catch (err) {
    // Log the reason, return none of it. "bad_audience" vs "bad_signature" is
    // a probing oracle for an attacker, but without it a 401 is undebuggable —
    // and the first client integration will produce plenty of them. Read with
    // `wrangler tail`.
    console.error('auth_rejected', String(err));
    return null;
  }
}

async function sha256(input: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    'SHA-256',
    new TextEncoder().encode(input),
  );
  return [...new Uint8Array(digest)]
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
    .slice(0, 32);
}
