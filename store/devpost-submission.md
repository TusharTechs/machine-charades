# Devpost submission: Machine Charades

## Project name (16 / 60)

```
Machine Charades
```

## Elevator pitch (164 / 200)

```
You write the clue. A language model has to guess the word. Five obvious words are blocked, and the shorter your clue, the higher you score. One new word every day.
```

---

## Project story (paste the block below into "About the project")

## Inspiration

Every AI app is the same shape. You type something, it answers, you leave.
Prompting is a skill almost nobody practises deliberately, and one that nobody
can put a number on.

Charades is already a game about describing your way around a constraint. Point
it at a language model and the roles invert: the machine becomes the thing you
are trying to beat, and your prompt becomes the move. That gave me something a
chat box never can, which is a score.

## What it does

One word a day, the same word for everyone.

Five obvious words are blocked. For **SANDCASTLE** that is *sand, beach, build,
castle, bucket*. You write a clue that goes around them, and a language model
gets three guesses.

Scoring is brevity. Par is 30 characters, and every character you come in under
par is worth 15 points. `fort vs tide` is twelve characters, lands on the first
guess, and scores **1,270**. A clue that says too much gets you *mansion,
hacienda, adobe* and zero.

Everyone plays the same word, so clue length is worth comparing. There is a
streak, an archive, and a full record of how short your clues really are.

**Today's puzzle is always free.** Plus sells difficulty rather than access: the
constraint modes (no vowels, one word, twenty characters), the full archive, and
your complete statistics.

## How we built it

**Compose Multiplatform, and genuinely shared.** Not "Kotlin for the logic,
native for the UI". Every screen is shared Kotlin.

```
shared/src/commonMain     2,970 lines    game, UI, networking, billing, storage
shared/src/androidMain      297 lines
shared/src/iosMain          165 lines
androidApp + iosApp          57 lines    two thin hosts
```

That is **85% shared**, with **eight expect/actual pairs** for the rest:
platform identity, HTTP client, storage, share sheet, sound, daily reminders,
and the store API key.

**RevenueCat behind one object.** Entitlements, offerings and the purchase flow
run through `purchases-kmp` in `commonMain`, wrapped in a single `Plus` object.
No other file in the app imports a RevenueCat type, which is what made the next
part possible.

**A Cloudflare Worker serves the puzzles.** They are generated offline and
uploaded to KV, so a normal session costs nothing and the model key never ships
inside the app. Guesses are cached by a hash of the normalised clue, and players
converge hard on the same phrasings, so a cache hit returns in about 20ms
instead of about 700ms.

## Challenges we ran into

**I was tuning the wrong knob.** The banned word lists felt like the difficulty
control, so I hardened them adversarially by asking the model which words it
leans on. Then I measured it with self play against the real model, and the
first-guess win rate did not move at all. What did move it was length: 100% at
60 characters, 100% at 40, 90% at 25, and 67% at 15. Par went from 60 characters
to 30 on the strength of that one graph, and the three-guess framing turned out
to be decorative rather than a real tension mechanic.

**Substring matching cannot tell a compound from a coincidence.** Blocking
*sting* for BEEHIVE also rejected "casting", "lasting" and "fasting". *Sand*
rejected "sandwich" and "thousand". *Wind* rejected "window". A player
experiences a false rejection as a bug in the game, not as a rule, so the
minimum containment length went to five characters with a three-character
remainder, and every puzzle carries an allow list for the leftovers. The false
positive rate is now measured by a probe rather than assumed.

**Two App Store rejections, and the second one was my own fault.** The first was
3.1.2, a missing Terms of Use link on the paywall. The second was 2.1(b),
reviewers could not locate the in-app purchases. My review note had told them to
tap any padlocked mode, but that row only renders during the writing phase, so
they went looking in the wrong place. The fix was to keep a way into Plus on
screen after the round is played.

**Play billing is still blocked on bank verification.** Rather than ship an
Android build full of padlocks that open an empty sheet, `Plus.unlocked()` takes
both the entitlement and whether a store key is configured. With no key the app
degrades to a complete free game with no dead paywall anywhere, which is also
exactly what avoided a Play rejection.

**A tester said: "I didn't quite understand what I am typing and what the
machine tries to guess."** That is the game failing before it starts. The answer
was not more words, it was one worked example on first run: here is a word, here
is what you may not say, here is a clue, here is the machine getting it.

## Accomplishments that we're proud of

- **A design decision that came from a measurement rather than a feeling.** The
  difficulty curve is entirely clue length, and I can show the numbers.
- **Billing that fails open.** A build with no store key is a complete game, not
  a wall of locks. The whole app asks one function whether something is
  unlocked, so the two can never drift apart.
- **85% shared with no UI compromise**, and a platform layer small enough to
  print in a table.
- **Nothing lets a player read ahead.** The puzzle number is resolved from a
  date index server side and never taken from the request. The test asserts that
  five different smuggling shapes all still resolve to today.
- **Live on the App Store inside the submission window**, after two rejections.

## What we learned

**Measure before you design.** I spent real effort hardening ban lists that
moved the difficulty by zero. Thirty minutes of self play would have told me
that on day one.

**Fail open on billing.** Treating "no store configured" as "everything
unlocked" rather than "everything locked" turned a blocked payment provider from
a launch blocker into a footnote.

**Write App Review notes against the actual render conditions.** I described a
control that only exists in one phase of the game and cost myself a review
cycle.

**Ask for permission after the win, not on launch.** The notification prompt
fires only once someone has solved a puzzle, because asking a stranger who has
not played yet burns the single chance iOS gives you.

## What's next for Machine Charades

- **Play subscriptions**, once the bank verification clears. The mapping is
  already built on the RevenueCat side; only the key is missing.
- **More puzzles.** The current schedule runs to 12 October 2026 and needs
  extending well past that.
- **Fix a duplicate-guess bug.** The model occasionally repeats a guess it has
  already made, despite being told not to. It needs a stricter prompt or a
  server-side reject-and-retry.
- **A head-to-head mode**, where two players clue the same word and compare
  character counts directly rather than through par.
- **Localised puzzle sets**, because the banned word lists are the interesting
  part and they do not translate.

---

## Built with (24 tags)

```
kotlin, kotlin-multiplatform, compose-multiplatform, revenuecat, cloudflare-workers,
cloudflare-kv, ktor, gemini, storekit, google-play-billing, swift, xcode,
android-studio, gradle, typescript, node.js, vitest, coroutines,
kotlinx-serialization, material-design, ios, android, javascript, jetbrains-mono
```

## Try it out links

```
https://apps.apple.com/app/machine-charades/id6809356797
https://play.google.com/store/apps/details?id=com.techtush.machinecharades
https://github.com/TusharTechs/machine-charades
https://youtube.com/shorts/CRBlALFE3kw
```

## Video demo link

```
https://youtube.com/shorts/CRBlALFE3kw
```

---

## Gallery captions (all under 140 characters)

Upload in this order. Devpost uses the first image as the card image if no
thumbnail is set, so the hero goes first.

| File | Caption |
|---|---|
| `01-hero.png` | You write the clue, a language model guesses the word. Twelve characters, solved on the first guess, 1,270 points. |
| `02-the-miss.png` | A clue that says too much. The machine answers mansion, hacienda, adobe, and the round scores zero. |
| `03-blocked.png` | Five obvious words are blocked each day. The validator catches variations and spelled-out attempts, then runs again server side. |
| `04-revenuecat.png` | RevenueCat drives offerings, the purchase and one entitlement, all behind a single Plus object in shared Kotlin. |
| `05-archive.png` | Plus sells difficulty rather than access: the full archive, every constraint mode, and your complete record. |
| `06-cross-platform.png` | One codebase, two stores. 2,970 lines shared against 519 lines of platform code and eight expect/actual pairs. |
| `07-measurement.png` | Self play against the real model: hardening banned words moved the win rate by nothing, clue length moved everything. |
