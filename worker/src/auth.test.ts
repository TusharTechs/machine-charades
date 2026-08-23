/**
 * Tests for Firebase ID token verification.
 *
 * These sign real RS256 tokens with a throwaway keypair generated per run, so
 * nothing here depends on network access or on a fixed test vector expiring.
 *
 * The case that matters most is `rejectsTheHandCraftedNoneToken` — that exact
 * string was accepted by the previous placeholder implementation.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  verifyIdToken,
  googleKeyResolver,
  resetJwksCacheForTests,
  TokenError,
  type KeyResolver,
} from './auth.js';

const PROJECT = 'machine-charades-test';
const KID = 'test-key-1';
const NOW = 1_800_000_000;

const b64url = (bytes: Uint8Array): string =>
  Buffer.from(bytes).toString('base64url');
const encodeSegment = (value: unknown): string =>
  b64url(new TextEncoder().encode(JSON.stringify(value)));

const keyPair = (await crypto.subtle.generateKey(
  { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
  true,
  ['sign', 'verify'],
)) as CryptoKeyPair;

const publicJwk = await crypto.subtle.exportKey('jwk', keyPair.publicKey);
const verifyKey = await crypto.subtle.importKey(
  'jwk',
  publicJwk,
  { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
  false,
  ['verify'],
);

/** Resolves only KID, so an unknown-kid token has somewhere to fail. */
const resolver: KeyResolver = async (kid) => (kid === KID ? verifyKey : null);

interface ClaimOverrides {
  iss?: unknown;
  aud?: unknown;
  sub?: unknown;
  exp?: unknown;
  iat?: unknown;
}

async function makeToken(
  overrides: ClaimOverrides = {},
  header: Record<string, unknown> = {},
): Promise<string> {
  const claims = {
    iss: `https://securetoken.google.com/${PROJECT}`,
    aud: PROJECT,
    sub: 'player-abc',
    iat: NOW - 60,
    exp: NOW + 3600,
    ...overrides,
  };
  const h = encodeSegment({ alg: 'RS256', kid: KID, typ: 'JWT', ...header });
  const p = encodeSegment(claims);
  const sig = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    keyPair.privateKey,
    new TextEncoder().encode(`${h}.${p}`),
  );
  return `${h}.${p}.${b64url(new Uint8Array(sig))}`;
}

const rejects = async (token: string, projectId = PROJECT): Promise<string> => {
  try {
    await verifyIdToken(token, projectId, resolver, NOW);
  } catch (err) {
    assert.ok(err instanceof TokenError, `expected TokenError, got ${String(err)}`);
    return err.message;
  }
  assert.fail('token was accepted but should have been rejected');
};

test('accepts a correctly signed token and returns the subject', async () => {
  assert.equal(await verifyIdToken(await makeToken(), PROJECT, resolver, NOW), 'player-abc');
});

test('rejects the hand-crafted alg=none token the placeholder accepted', async () => {
  // Verbatim the token used to demonstrate the hole against the live Worker.
  const forged =
    'eyJhbGciOiJub25lIn0.eyJzdWIiOiJ0ZXN0LXBsYXllciJ9.x';
  assert.equal(await rejects(forged), 'unsupported_alg');
});

test('rejects a token whose signature does not match the payload', async () => {
  // Keep a valid signature, swap in a payload claiming a different player.
  const [h, , s] = (await makeToken()).split('.') as [string, string, string];
  const tampered = encodeSegment({
    iss: `https://securetoken.google.com/${PROJECT}`,
    aud: PROJECT,
    sub: 'someone-else',
    iat: NOW - 60,
    exp: NOW + 3600,
  });
  assert.equal(await rejects(`${h}.${tampered}.${s}`), 'bad_signature');
});

test('rejects HS256, so the public key cannot be used as an HMAC secret', async () => {
  assert.equal(await rejects(await makeToken({}, { alg: 'HS256' })), 'unsupported_alg');
});

test('rejects a token signed by an unknown key', async () => {
  assert.equal(await rejects(await makeToken({}, { kid: 'rotated-away' })), 'unknown_key');
});

test('rejects a valid token issued for another Firebase project', async () => {
  assert.equal(await rejects(await makeToken({ aud: 'someone-elses-project' })), 'bad_audience');
  assert.equal(
    await rejects(await makeToken({ iss: 'https://securetoken.google.com/evil' })),
    'bad_issuer',
  );
});

test('rejects expired and future-dated tokens', async () => {
  assert.equal(await rejects(await makeToken({ exp: NOW - 3600 })), 'expired');
  assert.equal(await rejects(await makeToken({ iat: NOW + 3600 })), 'issued_in_future');
});

test('allows a token expiring within the clock-skew window', async () => {
  // 30s past expiry is inside the 60s tolerance — devices drift.
  assert.equal(await verifyIdToken(await makeToken({ exp: NOW - 30 }), PROJECT, resolver, NOW), 'player-abc');
});

test('rejects a token with no usable subject', async () => {
  assert.equal(await rejects(await makeToken({ sub: '' })), 'missing_subject');
  assert.equal(await rejects(await makeToken({ sub: 42 })), 'missing_subject');
});

test('fails closed when FIREBASE_PROJECT_ID is unset', async () => {
  // A deploy that forgot the project id must reject everything, not everything.
  assert.equal(await rejects(await makeToken(), 'REPLACE_ME'), 'project_id_not_configured');
  assert.equal(await rejects(await makeToken(), ''), 'project_id_not_configured');
});

test('rejects structurally malformed tokens', async () => {
  for (const bad of ['', 'not-a-jwt', 'a.b', 'a.b.c.d', '.b.c', 'a..c']) {
    await rejects(bad);
  }
});

test('googleKeyResolver caches, and re-fetches when a kid is unknown', async () => {
  resetJwksCacheForTests();
  let calls = 0;
  const fakeFetch = (async () => {
    calls++;
    return new Response(JSON.stringify({ keys: [{ ...publicJwk, kid: KID }] }), {
      headers: { 'cache-control': 'public, max-age=3600' },
    });
  }) as unknown as typeof fetch;

  const resolve = googleKeyResolver(fakeFetch, () => NOW * 1000);
  assert.ok(await resolve(KID));
  assert.ok(await resolve(KID));
  assert.equal(calls, 1, 'second lookup should hit the cache');

  assert.equal(await resolve('unseen-kid'), null);
  assert.equal(calls, 2, 'an unknown kid should trigger exactly one re-fetch');
  resetJwksCacheForTests();
});
