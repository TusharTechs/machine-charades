# Decisions

The calls that shaped Machine Charades, why each was made, and what it cost.
Written after the fact, from the commits and the measurements, not from memory.

---

## 1. Difficulty is clue length, not banned words

**Decision.** `CHAR_ALLOWANCE` moved from 60 characters to 30, the per-character
bonus from 5 to 15, and par ships with every puzzle as a live target while you
type.

**Why.** [`tools/measure-difficulty.mjs`](../tools/measure-difficulty.mjs) plays
the game against itself with the real model and the real validator. First-guess
win rate by budget: 100% at 60 characters, 100% at 40, 90% at 25, 67% at 15.

Before that ran, I had spent real effort hardening the banned word lists
adversarially, by asking the model which words it leans on for each answer.
Running the same procedure against both ban sets moved the win rate by nothing
measurable. The bias cancels in that comparison, so the result is trustworthy
even though self-play flatters the absolute number.

**What it cost.** A week of banned-word work that is still in
`tools/seed-words.json`, unvalidated for human players and never uploaded to KV.
It was the wrong knob and keeping it would have been sunk-cost reasoning.

**Consequence.** The three-guess structure survives as framing but is honestly
decorative. The model rarely needs a second guess. Re-run the tool before making
any difficulty claim.

---

## 2. Billing fails open

**Decision.** `Plus.unlocked()` takes both the entitlement and whether a store
key is configured at all.

```kotlin
val isConfigured: Boolean get() = storeApiKey.isNotEmpty()
fun unlocked(entitled: Boolean, configured: Boolean = isConfigured): Boolean =
    entitled || !configured
```

**Why.** A daily word game must never refuse to open a puzzle because billing had
a bad day. The failure mode of gating on the entitlement alone is a screen full
of padlocks that open an empty sheet, which is both a bad experience and a store
rejection under Apple 2.1(b) and its Play equivalent.

**What it cost.** Nothing, and it paid for itself immediately. Bank verification
held up Google Play billing days before the Shipaton deadline. With no key
configured, the Android build degrades to a complete free game with no dead
paywall anywhere, so it shipped on time.

**Why it works structurally.** Every gate in the app asks this one function
rather than asking about the entitlement directly, so the two cannot drift apart.
Four call sites: the mode row, the result card's upsell, the stats screen, and
the archive.

---

## 3. The validator is implemented twice, on purpose

**Decision.** The banned-word rules exist in Kotlin in `commonMain` and again in
TypeScript in the Worker. They are kept in step by shared test vectors, not by
shared code.

**Why.** The client copy is a UX decision: instant feedback, offline, no round
trip, and the exact offending word can be highlighted as you type. It is not a
trust boundary. A modified client could otherwise submit the secret word and win
every day, so the Worker re-runs the identical check and is the authority.

**What it cost.** Two implementations to keep in sync, and a standing risk of
drift. Mitigated by [`tools/clue-vectors.json`](../tools/clue-vectors.json),
which both suites run against.

---

## 4. Substring matching is deliberately conservative

**Decision.** A banned word is only checked for *containment* inside a clue token
when its skeleton is at least 5 characters, and the remainder must be at least 3.
Each puzzle carries an `allow` list for the residual collisions.

**Why.** The threshold started at 4 and had to be raised. At 4, `sting` (banned
for BEEHIVE) rejected "casting", "lasting" and "fasting"; `sand` rejected
"sandwich" and "thousand"; `wind` rejected "window". Substring matching cannot
tell a compound boundary from a coincidence without a dictionary.

**Why it matters.** A player experiences a false rejection as a bug in the game,
not as a rule. One of these will make someone close the app.

**How it is checked.** [`tools/probe-validator.mjs`](../tools/probe-validator.mjs)
runs the real validator against the real puzzle set and prints every rejection to
be judged by hand. The false positive rate is measured rather than assumed.

---

## 5. Local notifications, not a push SDK

**Decision.** `AlarmManager` plus a `BroadcastReceiver` on Android,
`UNUserNotificationCenter` on iOS. No push service, no backend, no certificate,
and no androidx dependency.

**Why.** There is nothing to say that the phone does not already know: a new
puzzle every day at the same time. A backend, a third-party SDK and a push
certificate would all be machinery in service of a message the clock can deliver.
It also works offline, costs nothing to run, and keeps the platform layer to one
more thin pair rather than a native dependency on both sides.

There was a hard constraint too. The androidx versions in the current catalog
demand `compileSdk 37`, which AGP 9.1 cannot target. Platform APIs cover
everything at `minSdk 24`, so the feature costs the binary nothing.

**What it cost.** An entire Shipaton prize category. The Keep Them Coming Back
award names OneSignal implementation quality specifically. This was a deliberate
trade, not an oversight.

**Second-order decision.** Permission is requested only when reminders are turned
on, and the soft ask fires only after a player's first win, never on launch. iOS
gives you exactly one chance to ask.

---

## 6. Firebase over REST, not through the SDK

**Decision.** Anonymous auth is one HTTP request against the REST endpoint.

**Why.** Pulling in the Firebase Android SDK would have forced an
`expect`/`actual` around the whole auth surface, and dragged an advertising ID
and analytics into a binary that has no use for either.

**What it cost.** Manual token refresh handling, which is a small amount of code
in one place rather than a dependency in two.

---

## 7. The UI never branches on platform

**Decision.** One fixed dark palette, one bundled typeface, identical composition
on every target.

**Why.** Deferring to Material You on Android and iOS conventions on iOS would
mean the game looks different depending on the phone, and a game scored on
character counts should not have its numbers rendered in a proportional system
font that varies by device. JetBrains Mono is bundled and applied to every
number, label and heading. Prose stays on the system font so body text remains
readable.

**What it cost.** The app does not feel native to either platform in the
conventional sense. That was the intent: it should feel like itself.

---

## 8. Puzzles are generated offline

**Decision.** The schedule is generated by
[`tools/generate-puzzles.mjs`](../tools/generate-puzzles.mjs) and uploaded to
Workers KV. The only live model call is the guess.

**Why.** Every player on a given day reads the same pre-made document, so a
normal session costs nothing. Guesses are additionally cached by a hash of the
normalised clue, and players converge hard on the same phrasings, so the cache
runs at a high hit rate within days: about 20ms on a hit against about 700ms on
a miss.

**Privacy consequence.** The clue text is never stored. It is SHA-256'd into a
cache key, sent to the model in flight, and dropped. Only the hash and the
model's answer survive, for 14 days.

---

## 9. Nothing lets a player read ahead

**Decision.** The puzzle number is resolved from a date index server side and
never taken from the request.

**Why.** Today's puzzle is played through `/puzzle/today`, where one round a day
is enforced. The archive endpoint serves only puzzles whose scheduled date has
already passed. A dev override exists to force a puzzle number, gated behind a
flag that no deployed Worker sets.

**How it is checked.** The test asserts that five different smuggling shapes
(`?n=8`, `?number=8`, `?n=8&n=7`, `?N=8`, `?n=%38`) all still resolve to today
with the flag unset.

---

## 10. Two App Store rejections, and what each taught

**3.1.2, subscription terms.** The paywall did not link Terms of Use and a
privacy policy. Apple requires both on the purchase screen itself, not only in
the listing. Fixed by putting the links in the paywall.

**2.1(b), in-app purchases not locatable.** This one was self-inflicted. My
review note told the reviewer to tap any padlocked mode, but that row only
renders during the writing phase of a round, so they went looking somewhere the
control did not exist.

The fix was to keep a route into Plus on screen after the round is played. A
tester had independently described the same gap in different words: "the next
screen just says come back tomorrow." A paying reviewer and a free tester hit the
same hole from opposite directions.

**The lesson worth keeping.** Write App Review notes against the actual render
conditions of the control you are describing, not against your memory of the UI.
