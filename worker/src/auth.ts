/**
 * Firebase ID token verification.
 *
 * This replaces a placeholder that decoded the JWT payload and trusted it. That
 * placeholder accepted a token anyone could type by hand — `{"alg":"none"}`
 * plus a base64 payload plus a literal "x" for the signature — which made the
 * per-player rate limit meaningless, because minting players was free.
 *
 * What is checked here, and why each one matters:
 *   - the RS256 signature, against Google's published public keys. Without it
 *     nothing else on this list means anything, since the claims are attacker
 *     controlled.
 *   - `alg` is pinned to RS256 before use. Accepting the header's own choice is
 *     the classic JWT bug: "none" bypasses signing entirely, and "HS256"
 *     invites the confused-deputy attack where the RSA *public* key is used as
 *     an HMAC secret.
 *   - `iss` and `aud`, both bound to the Firebase project. A valid token from
 *     someone else's Firebase project must not authenticate against ours.
 *   - `exp` and `iat`, so a leaked token stops working.
 *
 * Deliberately fails closed: any malformed input, unknown key, or unset project
 * id throws, and the caller turns that into a 401.
 */

/** Google's public keys for Firebase Auth ID tokens, in JWK form. */
const JWKS_URL =
  'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';

/** Tolerance for clock drift between Google, Cloudflare and the device. */
const CLOCK_SKEW_S = 60;

/** Bounds on how long we trust a cached JWKS, whatever Cache-Control says. */
const JWKS_MIN_TTL_S = 300;
const JWKS_MAX_TTL_S = 86_400;

export class TokenError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'TokenError';
  }
}

/** Resolves a `kid` to a verification key, or null if the key is unknown. */
export type KeyResolver = (kid: string) => Promise<CryptoKey | null>;

interface JwtHeader {
  alg?: unknown;
  kid?: unknown;
}

interface FirebaseClaims {
  iss?: unknown;
  aud?: unknown;
  sub?: unknown;
  user_id?: unknown;
  exp?: unknown;
  iat?: unknown;
}

function b64urlToBytes(input: string): Uint8Array {
  const padded = input.replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(padded + '='.repeat((4 - (padded.length % 4)) % 4));
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

function b64urlToJson<T>(input: string, what: string): T {
  try {
    return JSON.parse(new TextDecoder().decode(b64urlToBytes(input))) as T;
  } catch {
    throw new TokenError(`malformed_${what}`);
  }
}

/**
 * Verifies a Firebase Auth ID token and returns its subject (the player id).
 *
 * `nowSeconds` is injectable so the expiry tests do not depend on wall time.
 */
export async function verifyIdToken(
  token: string,
  projectId: string,
  getKey: KeyResolver,
  nowSeconds: number = Math.floor(Date.now() / 1000),
): Promise<string> {
  // Fail closed on unset config. A deploy that forgot FIREBASE_PROJECT_ID must
  // reject every token, never accept every token.
  if (!projectId || projectId === 'REPLACE_ME') {
    throw new TokenError('project_id_not_configured');
  }

  const parts = token.split('.');
  if (parts.length !== 3) throw new TokenError('malformed_token');
  const [headerB64, payloadB64, signatureB64] = parts as [string, string, string];
  if (headerB64 === '' || payloadB64 === '' || signatureB64 === '') {
    throw new TokenError('malformed_token');
  }

  const header = b64urlToJson<JwtHeader>(headerB64, 'header');
  // Pin the algorithm rather than trusting the header's choice.
  if (header.alg !== 'RS256') throw new TokenError('unsupported_alg');
  if (typeof header.kid !== 'string' || header.kid === '') {
    throw new TokenError('missing_kid');
  }

  const key = await getKey(header.kid);
  if (key === null) throw new TokenError('unknown_key');

  const signed = new TextEncoder().encode(`${headerB64}.${payloadB64}`);
  const signature = b64urlToBytes(signatureB64);
  const ok = await crypto.subtle.verify(
    'RSASSA-PKCS1-v1_5',
    key,
    signature as unknown as BufferSource,
    signed as unknown as BufferSource,
  );
  if (!ok) throw new TokenError('bad_signature');

  // Only now are the claims worth reading.
  const claims = b64urlToJson<FirebaseClaims>(payloadB64, 'payload');

  if (claims.aud !== projectId) throw new TokenError('bad_audience');
  if (claims.iss !== `https://securetoken.google.com/${projectId}`) {
    throw new TokenError('bad_issuer');
  }

  if (typeof claims.exp !== 'number' || claims.exp + CLOCK_SKEW_S <= nowSeconds) {
    throw new TokenError('expired');
  }
  if (typeof claims.iat !== 'number' || claims.iat - CLOCK_SKEW_S > nowSeconds) {
    throw new TokenError('issued_in_future');
  }

  const sub = typeof claims.sub === 'string' && claims.sub !== '' ? claims.sub : null;
  if (sub === null) throw new TokenError('missing_subject');
  return sub;
}

interface CachedJwks {
  keys: Map<string, CryptoKey>;
  expiresAt: number;
}

/** Isolate-local cache. Survives across requests on a warm isolate. */
let jwksCache: CachedJwks | null = null;

function parseMaxAge(cacheControl: string | null): number {
  const match = /max-age\s*=\s*(\d+)/i.exec(cacheControl ?? '');
  const raw = match ? Number(match[1]) : JWKS_MIN_TTL_S;
  return Math.min(Math.max(raw, JWKS_MIN_TTL_S), JWKS_MAX_TTL_S);
}

/**
 * Fetches Google's signing keys, caching them for as long as Google says.
 *
 * Google rotates these roughly daily. A `kid` miss re-fetches once, so a
 * rotation costs one extra request rather than a wave of 401s.
 */
export function googleKeyResolver(
  fetchImpl: typeof fetch = fetch,
  nowMs: () => number = Date.now,
): KeyResolver {
  async function load(): Promise<CachedJwks> {
    const res = await fetchImpl(JWKS_URL);
    if (!res.ok) throw new TokenError(`jwks_http_${res.status}`);
    const body = (await res.json()) as { keys?: (JsonWebKey & { kid?: string })[] };
    const keys = new Map<string, CryptoKey>();
    for (const jwk of body.keys ?? []) {
      if (typeof jwk.kid !== 'string') continue;
      keys.set(
        jwk.kid,
        await crypto.subtle.importKey(
          'jwk',
          jwk,
          { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
          false,
          ['verify'],
        ),
      );
    }
    if (keys.size === 0) throw new TokenError('jwks_empty');
    return {
      keys,
      expiresAt: nowMs() + parseMaxAge(res.headers.get('cache-control')) * 1000,
    };
  }

  return async (kid: string) => {
    if (jwksCache === null || jwksCache.expiresAt <= nowMs()) {
      jwksCache = await load();
    } else if (!jwksCache.keys.has(kid)) {
      // Unknown kid on a still-valid cache: Google probably just rotated.
      jwksCache = await load();
    }
    return jwksCache.keys.get(kid) ?? null;
  };
}

/** Test seam — drops the isolate-local JWKS cache. */
export function resetJwksCacheForTests(): void {
  jwksCache = null;
}
