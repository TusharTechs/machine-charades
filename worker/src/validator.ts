/**
 * Server-side clue validation. A faithful port of the Kotlin
 * `core/Normalise.kt` + `core/ClueValidator.kt`.
 *
 * This is the trust boundary. The client runs the same checks for instant
 * feedback, but a modified client could submit the secret word and win every
 * day, so nothing here may be skipped on the assumption the client already did
 * it. Both implementations are held in step by tools/clue-vectors.json.
 */

export const MAX_CLUE_CHARS = 120;
export const MAX_GUESSES = 3;

export type ConstraintMode = 'NONE' | 'NO_VOWELS' | 'ONE_WORD' | 'TWENTY_CHAR_CAP';

export type RejectionReason =
  | 'EMPTY'
  | 'TOO_LONG'
  | 'CONTAINS_SECRET_WORD'
  | 'CONTAINS_BANNED_WORD'
  | 'VOWEL_USED'
  | 'MORE_THAN_ONE_WORD'
  | 'OVER_CHAR_CAP';

export interface Rejection {
  reason: RejectionReason;
  token?: string;
}

export interface Puzzle {
  n: number;
  date: string;
  word: string;
  banned: string[];
  /**
   * Tokens explicitly permitted, overriding containment. The escape hatch for
   * the residual false positives the heuristic can't reason its way out of —
   * populate it from `node tools/probe-validator.mjs`.
   */
  allow?: string[];
}

/**
 * Digit homoglyphs. Folded whenever they sit next to a letter, because a digit
 * inside or beside a word is almost never legitimate in a clue.
 */
const LEET_DIGIT: Record<string, string> = {
  '0': 'o', '1': 'i', '3': 'e', '4': 'a',
  '5': 's', '7': 't', '8': 'b', '9': 'g', '6': 'g', '2': 'z',
};

/**
 * Symbol homoglyphs. Folded ONLY when strictly interior to a word — `tall!`
 * must normalise to `tall`, not `talli`, or ordinary punctuation corrupts every
 * clue that ends in an exclamation mark.
 */
const LEET_SYMBOL: Record<string, string> = {
  '!': 'i', '|': 'i', '@': 'a', $: 's', '+': 't',
};

const ACCENTS: Record<string, string> = {};
for (const c of 'àáâãäåā') ACCENTS[c] = 'a';
for (const c of 'èéêëē') ACCENTS[c] = 'e';
for (const c of 'ìíîïī') ACCENTS[c] = 'i';
for (const c of 'òóôõöøō') ACCENTS[c] = 'o';
for (const c of 'ùúûüū') ACCENTS[c] = 'u';
Object.assign(ACCENTS, { ç: 'c', ñ: 'n', ý: 'y', ÿ: 'y', š: 's', ž: 'z', ł: 'l' });

const isAsciiAlnum = (c: string) =>
  (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');

/**
 * A real letter or digit — deliberately excluding symbol homoglyphs, so a run
 * of punctuation like `wow!!!` doesn't make each `!` look interior to the next.
 */
const isWordish = (c: string | undefined) =>
  c !== undefined &&
  (isAsciiAlnum(c.toLowerCase()) || ACCENTS[c.toLowerCase()] !== undefined);

/** Lowercase, fold accents and leet, punctuation to spaces, collapse runs. */
export function canonical(input: string): string {
  const chars = [...input];
  let out = '';
  for (let i = 0; i < chars.length; i++) {
    const lower = chars[i].toLowerCase();

    const accent = ACCENTS[lower];
    if (accent !== undefined) {
      out += accent;
      continue;
    }

    const digit = LEET_DIGIT[lower];
    if (digit !== undefined) {
      // Adjacent to a word on either side, so fold it.
      const adjacent = isWordish(chars[i - 1]) || isWordish(chars[i + 1]);
      out += adjacent ? digit : lower;
      continue;
    }

    const symbol = LEET_SYMBOL[lower];
    if (symbol !== undefined) {
      // Strictly interior only — trailing punctuation stays punctuation.
      const interior = isWordish(chars[i - 1]) && isWordish(chars[i + 1]);
      out += interior ? symbol : ' ';
      continue;
    }

    out += isAsciiAlnum(lower) ? lower : ' ';
  }
  return out.split(' ').filter((t) => t.length > 0).join(' ');
}

/** Canonical with separators removed and repeated letters collapsed. */
export function skeleton(input: string): string {
  const canon = canonical(input).replace(/ /g, '');
  let out = '';
  for (const c of canon) if (out.length === 0 || out[out.length - 1] !== c) out += c;
  return out;
}

export function tokens(input: string): string[] {
  const canon = canonical(input);
  return canon.length === 0 ? [] : canon.split(' ');
}

/**
 * Every plausible root of a word, as a set.
 *
 * A single-stem approach fails on the most common case there is: stripping
 * `-es` from `giraffes` yields `giraff`, which never matches `giraffe`. Rather
 * than encode ever-more-baroque rules about when to drop one letter versus two,
 * emit both candidates and match on set intersection. Over-generating is safe
 * here — a spurious variant costs a player one retype, a missed one lets the
 * secret word through.
 */
export function variants(word: string): Set<string> {
  const base = canonical(word).replace(/ /g, '');
  const out = new Set<string>();
  if (base.length === 0) return out;
  out.add(base);

  const add = (s: string) => {
    if (s.length >= 3) out.add(s);
  };
  const undouble = (s: string) => {
    const last = s[s.length - 1];
    if (s.length >= 4 && last === s[s.length - 2] && !'aeiou'.includes(last)) {
      add(s.slice(0, -1));
    }
  };

  if (base.endsWith('ies') && base.length > 4) add(base.slice(0, -3) + 'y');

  if (base.endsWith('es') && base.length > 4) {
    add(base.slice(0, -2)); // buses -> bus
    add(base.slice(0, -1)); // giraffes -> giraffe
  } else if (base.endsWith('s') && base.length > 3 && !base.endsWith('ss')) {
    add(base.slice(0, -1));
  }

  for (const [suffix, restoreE] of [['ing', true], ['ed', true]] as const) {
    if (base.endsWith(suffix) && base.length - suffix.length >= 3) {
      const b = base.slice(0, -suffix.length);
      add(b);
      undouble(b);
      if (restoreE) add(b + 'e'); // hoping -> hope
    }
  }

  for (const suffix of ['ness', 'ment', 'est', 'ers', 'er', 'ly', 'y']) {
    if (base.endsWith(suffix) && base.length - suffix.length >= 3) {
      const b = base.slice(0, -suffix.length);
      add(b);
      undouble(b); // spotty -> spot, windy -> wind
    }
  }

  return out;
}

/**
 * Minimum length a banned word must have before we check whether a clue token
 * *contains* it.
 *
 * This started at 4 and had to be raised. At 4, "sting" (banned for beehive)
 * rejected "casting", "lasting" and "fasting"; "sand" rejected "sandwich" and
 * "thousand"; "wind" rejected "window". Substring matching cannot tell a
 * compound boundary from a coincidence without a dictionary, so the rule is now
 * deliberately conservative and each puzzle carries an `allow` list for the
 * residual collisions the probe in tools/ surfaces.
 */
const MIN_CONTAINMENT_LEN = 5;

/** The remainder must look like a real compound element, not one stray letter. */
const MIN_REMAINDER_LEN = 3;

/**
 * The clue token that illegally references `forbidden`, or null.
 *
 * Order matters: per-token checks run before the whole-clue check so the UI can
 * highlight the exact word that failed. The whole-clue skeleton check is the
 * last resort, for when the player spelled the word out across several tokens
 * and there is no single culprit to point at.
 */
function forbiddenMatch(clue: string, forbidden: string): string | null {
  const fCanon = canonical(forbidden).replace(/ /g, '');
  if (fCanon.length === 0) return null;
  const fVariants = variants(forbidden);
  const fSkel = skeleton(forbidden);
  const clueTokens = tokens(clue);

  for (const token of clueTokens) {
    if (token === fCanon) return token;
    for (const v of variants(token)) {
      if (fVariants.has(v)) return token;
    }
  }

  // Short forbidden words are exempt from containment, or "ice" rejects
  // "police" and "nice".
  if (fSkel.length >= MIN_CONTAINMENT_LEN) {
    for (const token of clueTokens) {
      const tSkel = skeleton(token);
      if (!tSkel.includes(fSkel)) continue;
      if (tSkel === fSkel) return token;

      const remainder = tSkel.length - fSkel.length;
      // Prefix matches are nearly always real derivations — "africa|n",
      // "water|proof", "light|house" — so one extra letter is enough.
      // Suffix and infix matches are where the coincidences live: "de|light",
      // "f|light", "ca|sting". Those need a remainder long enough to be a
      // plausible compound element in its own right.
      const isPrefix = tSkel.startsWith(fSkel);
      if (isPrefix || remainder >= MIN_REMAINDER_LEN) return token;
    }
  }

  // Last resort, and the only reason this check exists: the player spelled the
  // word out across several tokens — "g i r a f f e". Single-token clues have
  // already had their say above, so restricting this to multi-token clues stops
  // it silently undoing the remainder rules.
  if (clueTokens.length > 1 && skeleton(clue).includes(fSkel)) {
    const joined = clueTokens.join('');
    // Only if no individual token contains it — otherwise we'd blame the whole
    // forbidden word for something one token did.
    if (!clueTokens.some((t) => skeleton(t).includes(fSkel)) && joined.length > 0) {
      return forbidden;
    }
  }

  return null;
}

export function validate(
  clue: string,
  puzzle: Puzzle,
  mode: ConstraintMode = 'NONE',
): Rejection | null {
  const trimmed = clue.trim();
  if (trimmed.length === 0) return { reason: 'EMPTY' };
  if (trimmed.length > MAX_CLUE_CHARS) return { reason: 'TOO_LONG' };

  // The secret word is never overridable by the allow list — that would be an
  // authoring mistake with no legitimate use.
  const secret = forbiddenMatch(trimmed, puzzle.word);
  if (secret !== null) return { reason: 'CONTAINS_SECRET_WORD', token: secret };

  const allowed = new Set((puzzle.allow ?? []).map((a) => canonical(a)));
  for (const banned of puzzle.banned) {
    const hit = forbiddenMatch(trimmed, banned);
    if (hit !== null && !allowed.has(canonical(hit))) {
      return { reason: 'CONTAINS_BANNED_WORD', token: hit };
    }
  }

  switch (mode) {
    case 'NO_VOWELS': {
      const canon = canonical(trimmed);
      for (const c of canon) {
        if ('aeiou'.includes(c)) return { reason: 'VOWEL_USED', token: c };
      }
      return null;
    }
    case 'ONE_WORD':
      return tokens(trimmed).length > 1 ? { reason: 'MORE_THAN_ONE_WORD' } : null;
    case 'TWENTY_CHAR_CAP':
      return trimmed.length > 20 ? { reason: 'OVER_CHAR_CAP' } : null;
    default:
      return null;
  }
}

// ---------------------------------------------------------------------------
// Scoring — mirrors core/Scoring.kt. Duplicated server-side so the leaderboard
// never trusts a client-reported score.
// ---------------------------------------------------------------------------

const GUESS_BASE = [1000, 600, 350];
export const CHAR_ALLOWANCE = 60;
const CHAR_BONUS_PER = 5;

export function score(solved: boolean, guessesUsed: number, clueChars: number): number {
  if (!solved) return 0;
  const idx = Math.min(Math.max(guessesUsed - 1, 0), GUESS_BASE.length - 1);
  const bonus = Math.max(CHAR_ALLOWANCE - clueChars, 0) * CHAR_BONUS_PER;
  return GUESS_BASE[idx] + bonus;
}
