# Machine Charades — setup

A daily word game where **you write the clue and the AI does the guessing**.
Fewest guesses and fewest characters wins. Paired weekly with one friend, whose
clue stays hidden until you've played.

Built for RevenueCat Shipaton 2026. Target: publicly live on the App Store,
Google Play and the Galaxy Store by **23 September 2026**.

---

## What's in here, and what state it's in

One repo, deliberately: the Worker and the client that calls it version
together, and there is exactly one copy of the validator.

| Path | State |
|---|---|
| `worker/` | **Verified.** 70 tests pass. Deployed and serving. |
| `tools/` | **Verified.** Generates and validates 40 days of puzzles. |
| `shared/` | **Verified.** The KMP module: validator, scoring, API client, UI. |
| `androidApp/`, `iosApp/` | **Verified.** Both run and show today's puzzle. |

The Gradle project sits at the repo root, so `./gradlew` works from here;
`worker/` and `tools/` are Node and are not Gradle modules.

✅ **The Kotlin now compiles and passes.** Ported into a generated shell and run
on 23 Aug 2026: `./gradlew :shared:allTests` is green — 27 tests on
`testAndroidHostTest` and the same 27 on `iosSimulatorArm64Test`, 54 total, zero
failures. It needed no source changes; the line-for-line port of
`worker/src/validator.ts` compiled as written.

`allTests` passing on **both** targets remains the acceptance gate — the point
is that the shared logic behaves identically on JVM and Kotlin/Native, not just
that it compiles for each. Note that a Gradle test task also reports
`BUILD SUCCESSFUL` when it finds **zero** tests, so check the counts in
`shared/build/test-results/` rather than trusting the exit code.

---

## Step 1 — generate the project shell (do NOT hand-write the Gradle files)

The KMP project layout changed in 2026: AGP 9 no longer permits the Android
*application* plugin inside a multiplatform module, so the old single
`composeApp` module is gone. Every tutorial and StackOverflow answer you'll find
predates this and will not build. **Generate the shell and trust its output over
any snippet**, including anything an AI assistant offers you.

1. Open IntelliJ IDEA or Android Studio → `File | New | Project | Kotlin Multiplatform`
   (or use https://kmp.jetbrains.com).
2. Select **Android + iOS**, with **shared UI** (Compose Multiplatform).
3. Name it `MachineCharades`, package `com.machinecharades`.
4. Let it sync **before** you add anything. If the empty project doesn't run on
   both an Android emulator and the iOS simulator, stop and fix that first.

Toolchain, verified 23 Aug 2026:

- **JDK 17** — use a JetBrains Runtime build. Set it as the **Gradle JVM**
  (Settings | Build Tools | Gradle). A Homebrew JDK 26 on `PATH` will not work:
  Gradle 9.5 does not support Java 26.
- **Xcode 26.4** — required by Kotlin 2.4.x. Command Line Tools alone are *not*
  enough; without the full Xcode there is no `simctl` and no iOS simulator.
- **Gradle 9.5.0** — pin this in `gradle-wrapper.properties`, *not* 9.7.1;
  9.5.0 is what AGP 9.3 was validated against
- **Apple Silicon Mac** — Compose Multiplatform 1.11 removed the `iosX64`
  target, so an Intel Mac can no longer build for the simulator

### Xcode: installing it is not enough (two extra steps)

Installing Xcode from the App Store leaves `xcode-select` still pointing at the
Command Line Tools, so there is no simulator SDK and Kotlin/Native cannot link
an iOS test binary. The failure surfaces as an opaque Gradle error:

```
Execution failed for task ':shared:linkDebugTestIosSimulatorArm64'
Caused by: KonanExternalToolFailure: The /usr/bin/xcrun command returned
non-zero exit code: 72.
```

Nothing in that message mentions Xcode. Two things fix it:

1. **Select the iOS platform during Xcode's first-launch dialog.** Tick
   **iOS** (~8.5 GB) — that is the `iphonesimulator` SDK. Skip watchOS, tvOS and
   visionOS (~15 GB saved); this project targets neither. The Predictive Code
   Completion Model (~2 GB) is Swift autocomplete you will not use, since all
   real code is Kotlin. Any of these can be added later under
   Xcode | Settings | Components.

2. **Point the toolchain at Xcode:**

   ```
   sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
   ```

   You may also need `sudo xcodebuild -license accept`. Verify with:

   ```
   xcrun --sdk iphonesimulator --show-sdk-path
   ```

   A path means you are done. `SDK "iphonesimulator" cannot be located` means
   step 1 or 2 is incomplete — fix that rather than debugging Gradle.

Xcode 26.6 works as well as the 26.4 named above; the Kotlin 2.4.x floor is what
matters, not the exact version.

### AGP is capped by your IDE, not by the project

AGP 9.3.1 is current, but **IntelliJ IDEA 2026.2.1 (build 262.9437.185) refuses
to sync anything above AGP 9.1.0**:

> The project is using an incompatible version (AGP 9.3.1) of the Android Gradle
> plugin. Latest supported version is AGP 9.1.0

This is an IDE-side gate only — the Gradle build itself succeeds on 9.3.1 from
the CLI. IntelliJ's Android support trails Android Studio by a couple of AGP
minors as a rule. 2026.2.1 was the latest stable on 23 Aug 2026, so there is no
IntelliJ update that lifts this today.

**Verified working pair: AGP 9.1.0 + Gradle 9.5.0.** AGP 9.1.0 drives the AGP-9
KMP shell completely — iOS targets, Compose resource sync, device-test builders
— and `:androidApp:assembleDebug` produces a debug APK. Note AGP 9.1.0's *own*
floor is Gradle 9.3.1; 9.5.0 sits above it and is what the wrapper pins.

If you want AGP 9.3.x, install Android Studio alongside IntelliJ and point both
at the same directory. Not required to ship — decide it when you actually want
Compose Preview or Logcat, not on a schedule.

### On a corporate network (Netskope, and likely any TLS-intercepting proxy)

Three separate failures, all the same root cause. Symptoms look like Gradle bugs
and are not.

1. **`PKIX path building failed` on every repository.** The proxy re-signs all
   TLS with a tenant CA. macOS trusts it, so `curl` works and Java does not.
   Import the **tenant-specific** CA — for GoKwik that is
   `CN=ca.gokwik.goskope.com`, *not* the generic `eproxy.caadmin.netskope.com`,
   which is the wrong cert and will not help:

   ```
   security find-certificate -a -c "ca.gokwik.goskope.com" -p \
     /Library/Keychains/System.keychain > netskope-ca.pem
   keytool -importcert -trustcacerts -alias corp-ca -file netskope-ca.pem \
     -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit
   ```

   Import into the JBR you set as the Gradle JVM — patching a different JDK does
   nothing.

2. **`Could not install Gradle distribution … Read timed out`.**
   `services.gradle.org` 307-redirects to GitHub release assets, and
   `release-assets.githubusercontent.com` is blocked outright. A failed download
   lands as a few-hundred-byte *HTML error page* named `.zip`, which is
   confusing — check the file size before believing it.
   Proper fix is an IT allowlist. Until then, pin a reachable mirror **with the
   official checksum** so tampering fails the build:

   ```
   distributionUrl=https\://mirrors.huaweicloud.com/gradle/gradle-9.5.0-bin.zip
   distributionSha256Sum=553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746
   validateDistributionUrl=false
   ```

   Revert to `services.gradle.org` once the allowlist lands — the mirror will
   confuse any teammate whose network reaches GitHub fine.

3. **"Failed to download compatibility.json" in Preflight Checks.**
   `raw.githubusercontent.com` is blocked too. Cosmetic: the preflight just
   can't verify versions online. Ignore it.

Maven Central, Google Maven, the Gradle plugin portal, `api.foojay.io` and the
Kotlin/Native toolchain CDN are all reachable once the CA is imported. Only
GitHub asset hosts are blocked.

4. **Every device trust store needs the CA too, and each one differently.**
   The proxy re-signs TLS for the emulator and simulator as well, and neither
   inherits the Mac's trust. Symptoms differ and neither names the cause:

   - Android: `SSLHandshakeException: Trust anchor for certification path not
     found` — but the *visible* error was an IPv6 `ConnectException`, because
     OkHttp reports the last route it tried and hides the rest in `suppressed`.
     Fixed in-repo by `androidApp/src/debug/res/xml/network_security_config.xml`,
     which trusts the CA alongside the system anchors. Being in `src/debug` it
     cannot reach a release build.
   - iOS simulator: `NSURLErrorDomain Code=-1200`, which does at least print
     the chain. Fix per simulator:

     ```
     xcrun simctl keychain <udid> add-root-cert netskope-root.pem   # the ROOT
     xcrun simctl keychain <udid> add-cert       netskope-ca.pem    # intermediate
     ```

   **Get the chain level right — this wasted time twice.** Netskope issues a
   three-level chain and each level has a different job:

   | Cert | Role | Use for |
   |---|---|---|
   | `*.fra2.goskope.com` | self-signed **root** | `add-root-cert`, trust anchors |
   | `ca.gokwik.goskope.com` | tenant **intermediate** | `add-cert`, JDK truststore |
   | `eproxy.caadmin.netskope.com` | generic, unrelated | nothing — it is a red herring |

   `add-root-cert` on the intermediate fails with `NSOSStatusErrorDomain -50`,
   because it is not self-signed and so cannot be an anchor. Extract them with
   `security find-certificate -a -c "<CN>" -p /Library/Keychains/System.keychain`.

5. **Android also needs IPv4 preferred.** The emulator resolves AAAA records but
   has no IPv6 route at all (`ping6` 100% loss, IPv4 9ms), so a client that
   tries AAAA first fails on a working network. `HttpClientFactory.android.kt`
   sorts IPv4 first rather than dropping IPv6, so an IPv6-only network still
   works.

6. **`INTERNET` permission is missing from the wizard's manifest.** Without it
   requests fail at the platform layer, not in Ktor.

Then copy `shared/src/` from this bundle over the generated `shared/src/`.

## Step 2 — dependencies

Versions verified against Maven Central metadata and official release notes on
23 Aug 2026. Do not let an assistant substitute different numbers; this is the
single most hallucination-prone surface in the project.

```toml
[versions]
kotlin               = "2.4.10"   # 2.4.20 is RC only
composeMultiplatform = "1.11.1"   # 1.12.0 is RC only
agp                  = "9.1.0"    # 9.3.1 is current; IntelliJ caps at 9.1.0 — see Step 1
compileSdk           = "36"       # mandatory for new Play apps from 31 Aug 2026
targetSdk            = "36"
minSdk               = "24"
coroutines           = "1.11.0"
serialization        = "1.11.0"
datetime             = "0.8.0"
ktor                 = "3.5.2"
lifecycle            = "2.11.0"   # org.jetbrains.androidx.lifecycle
navigation           = "2.9.2"    # org.jetbrains.androidx.navigation
datastore            = "1.2.1"
koin                 = "4.2.2"
revenuecat           = "3.5.1"    # purchases-kmp-core + purchases-kmp-ui
onesignalAndroid     = "5.9.9"
layersAndroid        = "3.2.12"
gitliveFirebase      = "2.6.0"
compottie            = "2.2.2"
jindong              = "1.1.0"
```

Notes that will save you a day each:

- **The Compose compiler plugin version must equal the Kotlin version** exactly.
- **`iosArm64()` and `iosSimulatorArm64()` only.** Don't declare `iosX64`.
- **iOS deployment target 15.0.** CMP 1.11 raised the floor to 14.0.
- **RevenueCat needs `isStatic = true`** on the iOS framework binary, plus an
  `ExperimentalForeignApi` opt-in in the iOS source sets. As of 3.x the native
  iOS SDK is bundled, so you do **not** need CocoaPods.
- **Navigation is 2.9.2.** Do not copy "2.9.7" out of CMP's release notes — that
  is the upstream Jetpack numbering line, a different series.
- **DataStore: `datastore-core` + `datastore-preferences-core`**, not
  `datastore-preferences`. Preferences only; Proto is not multiplatform, and the
  instance must be created per-platform via `expect`/`actual`.
- **OneSignal has no KMP wrapper.** `expect`/`actual`: Maven on Android,
  `OneSignal-XCFramework` 5.5.6 via SPM on iOS.
- **Layers ships an Android artifact but I could not find an iOS one.** Email
  them before planning the Growth Loop entry around iOS evidence.
- **GitLive Firebase's `firebase-messaging` is ~1% API coverage.** Use it for
  Firestore/Auth; do FCM per-platform. On iOS you must link the real Firebase
  SDK yourself in Xcode — it is not a transitive dependency.
- **Pin CMP and purchases-kmp after week 3** and stop upgrading. There is a
  known issue class where a CMP bump renders RevenueCat paywalls empty on iOS
  (purchases-kmp #287, still open on paper against much older versions).
  **Smoke-test a real paywall on a physical iOS device in week two**, before you
  build pricing around it.

## Step 3 — the Worker (LLM proxy)

The app must never ship an API key; mobile binaries are trivially extractable.
The key lives in Worker Secrets and the client gets an endpoint.

```bash
cd worker
npm install
npm test                       # 46 tests, all should pass

npx wrangler kv namespace create CACHE
npx wrangler kv namespace create PUZZLES
# paste both ids into wrangler.toml, and set FIREBASE_PROJECT_ID

npx wrangler secret put GEMINI_API_KEY
npx wrangler deploy
```

Free tier is 100,000 requests/day, which is enormous for a launch-week game —
and every cache hit costs nothing at all. (That figure is *Cloudflare's* Worker
limit. Gemini is billed separately — see below.)

### Gemini: the model list lies, and read the status code

Verified against a live key on 24 Aug 2026.

**`/v1beta/models` is not a list of models you can call.** Two models in a row
appeared in that listing and returned 404 on `generateContent`:

```
models/gemini-2.5-flash-lite  → 404 "no longer available to new users",
                                    use models/gemini-3.5-flash-lite
models/gemini-2.5-flash       → 404 "no longer available to new users",
                                    use models/gemini-3.6-flash
```

The whole 2.5 line is retired for keys created after its cutoff, while still
being advertised. Only an actual `generateContent` call tells you the truth, so
confirm a model with one before pinning it.

**The status code is the diagnosis** — this is the fastest way through:

| Code | Means | Fix |
|---|---|---|
| 404 | Model retired or misspelled | Use the replacement named in the message |
| 429 | Model is fine; account has no funds | Add credits |
| 403 | Key invalid or API not enabled | Check the key / enable the API |

**A Gemini key needs prepay credits.** An unfunded key returns
`RESOURCE_EXHAUSTED`: *"Your prepayment credits are depleted."* This is
account-wide, not per-model — every model returns it, so no substitution avoids
it. Top up at https://ai.studio/projects. The amount is small: a judging call is
about $0.0002, `maxOutputTokens` is 8, thinking is disabled, and identical clues
are served from KV.

🔴 **Deploy the `verifyPlayer()` fix before adding credits.** An unfunded key
fails safe — the worst case is 429s. A funded key behind a forgeable auth check
is the surprise-bill scenario this Worker exists to prevent. Order matters.

✅ **`verifyPlayer()` is fixed** (23 Aug 2026). It now verifies the RS256
signature against Google's published keys in `src/auth.ts`, pins the algorithm,
and checks `iss` / `aud` / `exp` / `iat` against the Firebase project. 12 tests
in `src/auth.test.ts` cover it, including the exact `alg=none` token the old
placeholder accepted.

**Verified in production** on 24 Aug 2026 with a real Firebase anonymous token:
signature checked against Google's JWKS, `aud`/`iss` matched, clue re-validated,
model called, result cached. `FIREBASE_PROJECT_ID = "machine-charades"`.

⚠️ **It fails closed, so `FIREBASE_PROJECT_ID` is load-bearing.** If it is unset
or wrong, every token is rejected and `/guess` returns 401 to everyone. That is
the correct trade — a deploy that forgot the project id must reject everything
rather than accept everything — but it does mean a mismatch looks exactly like a
broken endpoint.

**Debugging 401s.** The response deliberately never says why (the reason is a
probing oracle). The reason is logged instead — run `npx wrangler tail` and look
for `auth_rejected TokenError: <reason>`: `bad_audience` is the wrong project,
`expired` means the token is over an hour old, `unknown_key` is a JWKS problem.

**Getting a real token without building the client**, useful for testing the
Worker on its own. Add a Web app in the Firebase console (an Android key is
restricted to a package name and SHA-1, so it will not work from curl), take the
Web API key, then:

```bash
ID_TOKEN=$(curl -sS -X POST \
  "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$FB_WEB_KEY" \
  -H "content-type: application/json" -d '{"returnSecureToken":true}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['idToken'])")

curl -sS -X POST https://<worker>.workers.dev/guess \
  -H "content-type: application/json" \
  -H "authorization: Bearer $ID_TOKEN" \
  -d '{"puzzleNumber":1,"clue":"eats treetops","attempt":1}'
```

`ALLOW_UNVERIFIED=1` with `wrangler dev` still works for offline poking, but the
token route above tests the real path. Never set that variable on a deploy.

## Step 4 — puzzles

```bash
cd tools
node generate-puzzles.mjs --start 2026-09-24 --days 40
npx wrangler kv bulk put --binding PUZZLES out/kv-puzzles.json --remote
```

40 hand-authored puzzles ship in `seed-words.json`, so the game is playable on
day one with no API key and no cold-start problem. `--extend` will generate more
with Gemini once you have a key.

**After every new batch, run the probe:**

```bash
cd worker && npm run build
cd ../tools && node probe-validator.mjs
```

This measures the validator's **false-positive** rate against real puzzle
content, and it is not optional. Banned-word detection uses substring matching
on a collapsed form, which cannot distinguish a compound boundary from a
coincidence without a dictionary. Unmeasured, it silently rejects ordinary words
and the player experiences that as a broken game rather than as a rule.

What the probe has already caught:

| Symptom | Cause | Fix |
|---|---|---|
| `lighthouse` rejected *delight, flight, slight* | "light" as a coincidental suffix | Suffix matches now need a ≥3-char remainder; prefix matches still need only 1 |
| `beehive` rejected *casting, lasting, fasting* | "sting" inside "ca‑sting" | Same rule |
| `sandcastle` rejected *sandwich, thousand* | "sand" is only 4 chars | Containment now requires ≥5 chars |
| `windmill` rejected *window* | "wind" is only 4 chars | Same |
| `giraffe` rejected *tally* | "-y" stripping makes "tall" | Per-puzzle `allow` list |

Current rate: **0.50%**, and every remaining rejection is a direct hit on a
banned word. Judge each line the probe prints — a rejection whose token *is* a
banned word is the game working; anything else belongs in that puzzle's `allow`
list. The probe prints paste-ready `allow` entries.

## Step 5 — start the Play clock immediately

This is the binding constraint on the whole project, and it has nothing to do
with the game. A new personal Google Play account cannot publish to production
until it has run a closed test with **12 testers opted in continuously for 14
days**, followed by a production-access review of up to seven days.

**Upload a thin, non-crashing AAB to a closed testing track as soon as the
skeleton runs.** It does not need to be the finished game — it needs to exist so
the 14 days elapse while you build. Pushing new builds to the same track does
not reset the window; only the tester count does.

- Recruit **18** testers, not 12. Google emails "more testing is required" if
  anyone uninstalls, and any dip below 12 should be assumed to reset the clock.
- Twelfth tester opted in by **31 August**. Absolute backstop: **2 September**.
- Check the opted-in count in Console daily.

---

## Architecture, in one paragraph

Puzzles are batch-generated offline and served as one document per day, so a
normal session costs one read and zero model calls until the player submits a
clue. The client validates the clue locally for instant feedback; the Worker
re-validates it because that is the trust boundary — a patched client would
otherwise submit the secret word and win every day. The Worker caches on
`hash(day + normalised clue)`, and since players converge hard on the same
phrasings that runs at a high hit rate within days. A cache hit returns in about
20 ms instead of 700, which makes the game *feel* better, not just cheaper. The
model call is deterministic (`temperature: 0`, thinking off), which is what makes
the cache correct rather than merely cheap, and what makes the daily leaderboard
fair. Never put that call on a blocking path: fire it, play the reveal animation
over it, and fall back deterministically at the 3-second timeout.

## The two files that must never drift

`shared/.../core/ClueValidator.kt` and `worker/src/validator.ts` implement the
same algorithm and are held in step by `tools/clue-vectors.json`. If one suite
passes and the other fails, the client and server have diverged — which is a
scoring exploit, not a cosmetic bug. Change a rule in one, change it in the
other, run both.

## Still to build

- The reveal animation — this is the Design Award entry, and the whole reason
  the latency is an asset rather than a cost
- Pair layer: weekly pairing, withheld-clue reveal, pair record
- RevenueCat paywall (pair pass, lifetime tier) and the rewarded-ad hint
- OneSignal Journeys — the "your partner played" push
- Layers SDK, verifiably streaming events before judging
- Share-card renderer
- Report / block / terms-gate / contact-info set — about 1.5 days, and Apple
  treats it as non-negotiable even though this design keeps free text between
  friends only
