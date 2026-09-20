# Devpost: Additional info (judges and organizers)

## Attachments

| Field | Answer | File |
|---|---|---|
| 1024 x 1024 uncropped app icon | **Yes** | `store/devpost/app-icon-1024.png` |
| Screenshot without device frames | **Yes** | `store/devpost/screenshot-1179x2556.png` (1179 x 2556) |

## Straight answers

| Field | Answer |
|---|---|
| First version released on a store between 1 Aug and 30 Sep 2026 | **Yes.** First public release was on the App Store in September 2026. |
| Employee of RevenueCat or a Shipaton sponsor | **No** |
| What type of app did you build | **iOS (iPhone and iPad)**, **Mac**, and **Android**. The iOS build runs on Apple Silicon Macs from the Mac App Store and was verified there: the reading-width cap keeps the layout correct in a resizable window. |
| iOS or Mac App Store URL | `https://apps.apple.com/app/machine-charades/id6809356797` (one field covers both) |
| Google Play URL | `https://play.google.com/store/apps/details?id=com.techtush.machinecharades` |
| Samsung Galaxy Store URL | *leave blank* |
| Next Gen repo / student email / consent | *leave blank, not a Next Gen entry* |
| RevenueCat project ID | **Get this from the dashboard: Project Settings, Project ID.** It is not the app ID `appac80744dfd` and not the public key `appl_...`. |
| Promo code | Your generated App Store promo code. The paywall and the real purchase sheet both appear inside the first 90 seconds of the demo video, so judges can see the premium flow without redeeming it. |
| RevenueCat Growth Fund | **Yes** |

---

## For the HAMM Award

```
Machine Charades sells difficulty, not access.

Today's puzzle is free and always will be. A daily game that gates the daily
thing does not survive a week, because the habit is the product and a paywall
in front of it kills the habit before it forms. So the free tier is a complete
game: the word, the blocked list, three guesses, the score, the streak.

Plus is for players who have already formed the habit and want it harder. It
unlocks the three constraint modes (no vowels, one word, twenty characters),
the full archive of every past puzzle, and the complete record: solve rate,
shortest clue, clue-length average. Every one of those is worthless to someone
who has played once and valuable to someone on a thirty day streak, which is
exactly the person worth charging.

Pricing is reasoned rather than copied. 9.99 USD a year and 2.99 USD a month in
the United States; 299 INR a year and 49 INR a month in India, set from
purchasing power rather than converted at the spot rate. The annual plan leads
the paywall and carries a one week trial, because the product is a daily habit
and an annual term matches the thing being sold.

The whole integration sits behind a single Plus object in shared Kotlin, using
purchases-kmp. No other file in the app imports a RevenueCat type. That
abstraction paid for itself: our Indian payment provider held up bank
verification for Google Play, so Plus.unlocked() takes both the entitlement and
whether a store key is configured at all. With no key, the app degrades to a
complete free game with no dead paywall anywhere, rather than a wall of
padlocks that open an empty sheet. A blocked payment provider became a
footnote instead of a launch blocker, and it is also what kept the Android
build from being rejected under Play's equivalent of Apple 2.1(b).
```

## For the Ship Kotlin Everywhere Award

```
This is not "Kotlin for the logic, native for the UI". Every screen in the app
is shared Compose Multiplatform.

  shared/src/commonMain     2,970 lines   game, UI, networking, billing, storage
  shared/src/androidMain      297 lines
  shared/src/iosMain          165 lines
  androidApp + iosApp          57 lines   two thin hosts

That is 85% shared, and the platform layer is eight expect/actual pairs:
platform identity, HTTP client (OkHttp / Darwin), key-value storage
(SharedPreferences / NSUserDefaults), the share sheet, sound, the daily
reminder, and the store API key.

Two decisions are worth judging:

Subscriptions are shared too. purchases-kmp puts entitlements, offerings and
the purchase flow in commonMain rather than being written twice against
BillingClient and StoreKit. The single Plus object that wraps it is what let
the app ship on Android with no store key configured and still be a complete
game.

The daily reminder is a thin expect/actual pair rather than a dependency. It
uses AlarmManager plus a BroadcastReceiver on Android and
UNUserNotificationCenter on iOS, with no androidx dependency at all, because
the versions in the current catalog demand compileSdk 37 which AGP 9.1 cannot
target. The platform APIs cover it at minSdk 24, so the feature costs the
binary nothing. Choosing this over a push SDK forfeited an entire prize
category, and it was still the right call for the product.

The repository is public and documents the reasoning rather than just the
result, including the measurement tooling, the validator probe, and a written
account of two App Store rejections and what caused them:
https://github.com/TusharTechs/machine-charades
```

## For the Best Game Award

```
Gameplay. Most word games ask you to find the answer. This one shows you the
answer and makes you explain it to a machine. One word a day, the same word for
everyone. Five obvious words are blocked, so you have to describe your way
around them, and a language model gets three guesses.

The interesting part came out of measurement rather than design. I hardened the
banned word lists adversarially, by asking the model which words it leans on,
and the first-guess win rate did not move at all. What moved it was clue length:
100% at 60 characters, 100% at 40, 90% at 25, and 67% at 15. So the game is not
"can you make it guess". It is how few characters you can do it in. Par is 30
characters and every character under par is worth 15 points. "fort vs tide" is
twelve characters, lands first guess, and scores 1,270.

Art direction is deliberately austere. A dark palette, one accent green, and
JetBrains Mono bundled and used throughout, because a game about counting
characters should be set in a typeface where every character is the same width.
There are no illustrations, no mascot, and no confetti. The blocked words are
pills, the clue field shows the brevity bonus falling as you type, and the
result is a number.

The monetisation fits the genre exactly. Daily games live or die on the habit,
so the daily puzzle is permanently free. Plus sells difficulty instead: the
constraint modes, the archive, and the full record. That is the right fit for a
daily puzzle, where the engaged player wants a harder version of the thing they
already do, not access to the thing itself.
```

## For the RevenueCat Design Award

```
Worth looking at, in order:

The typeface. JetBrains Mono is bundled and applied to every number, label and
heading in the app. It is not decoration: the game is scored on character
count, and a monospace face is the only honest way to render a character
budget. Prose stays on the system font so body text remains readable.

The clue field. Par is shown live as you type and the brevity bonus counts down
in real time, so the thing you are optimising is visible while you optimise it,
not revealed on the results screen after the round is over.

The paywall. No stock illustration, no urgency, no countdown. Three perks with
drawn tick marks rather than emoji, the annual plan leading, and one honest
line: "Today's puzzle is always free. Plus is for the days you want it harder."
The tick marks are drawn with two line segments on a Canvas rather than shipped
as an emoji, because an emoji renders differently on every platform and a green
square answers the wrong question. A tick answers "do I get this". A square
does not.

The first-run explainer. A tester said "I didn't quite understand what I am
typing and what the machine tries to guess", which is the game failing before
it starts. The fix was not more words, it was one worked example in four beats:
here is a word, here are the blocked words, here is a clue that goes around
them, here is the machine getting it. It is shown once, and it is skippable on
the same tap that starts the game.

Restraint. There is no confetti, no mascot, no animation on the score. The
result is a large number on a dark card. For a thirty second daily ritual, the
fastest interface is the most respectful one.
```

## For the Gaming Influencer Award (Mr Lewis Blogs Gaming)

```
Category entered: Gaming.

Machine Charades is a genuinely novel game mechanic rather than a reskin of an
existing one. Reverse charades against a language model has not been built as a
daily, and the hook demonstrates itself in about fifteen seconds of footage:
you type a short clue, the machine answers, and either it lands or it does not.
That makes it unusually easy to cover. A single round is a complete story with
a win or a loss and a number at the end, which is the shape a short-form gaming
clip wants.

It is also inherently shareable in the way daily games are. Everyone gets the
same word on the same day, so clue lengths are directly comparable, and the
share card carries your character count rather than a spoiler.

For a gaming audience specifically, the appeal is that the difficulty is real
and measurable. I can show the graph: the model wins 100% of the time at 60
characters and only 67% at 15. There is a skill ceiling, and it is a strange
one, because the skill is compression.
```

## For the Build in Public Award

```
Building in public forced the two changes that most improved the game.

The first came from a tester who said: "I didn't quite understand what I am
typing and what the machine tries to guess." I had built and played the game
for weeks and could no longer see that the premise was not obvious. That one
sentence produced the first-run explainer, which is now the first thing anyone
sees.

The second was accountability during two App Store rejections. Posting about a
rejection publicly, including the one that was entirely my own fault (my review
note pointed reviewers at a control that only renders during one phase of the
game), made me diagnose it properly and write down the cause rather than just
resubmit and hope.

[ADD YOUR POST LINKS HERE]
```

---

## Leave blank

| Field | Why |
|---|---|
| Grand Prize growth description | Judged on traction and growth since launch. The app launched days ago with no meaningful download numbers, and a thin answer here is worse than none. |
| RevenueCat Peace Prize | Not a social impact app. Claiming otherwise would be transparent. |
| Catvertising | No ads, deliberately. |
| Most Viral App / Noise | No Noise account. |
| Best App for Galaxy | Not published on the Galaxy Store. |
| Idea to Income / Replit | Not built on Replit. |
| Keep Them Coming Back / OneSignal | Deliberately forfeited. Local notifications were chosen over a push SDK because the phone already knows a new puzzle appears daily, so a backend, a third-party SDK and a push certificate would all have been machinery in service of a message the clock can deliver. The category names OneSignal implementation quality specifically, so this was a real trade, not an oversight. |
| The Growth Loop / Layers | Layers SDK not installed. |
| Funnel Vision / Stripe | No web-to-app funnel. |

---

## Additional notes for the judges

```
THE PAIN POINT

Prompting has quietly become a skill people use every day and nobody practises.

Hundreds of millions of us now type instructions to a model as part of ordinary
work. But there is no way to get better at it deliberately, and no way to know
whether you already are. Every AI product is the same shape: a box, an answer,
and you leave. There is no feedback, no score, no second attempt, and nothing
to compare yourself against. It is the only widely-used skill I can think of
with no practice format at all.

Machine Charades is an attempt at one. It takes the thing everyone already does
and gives it the three things a skill needs: a constraint, a score, and someone
else's number to compare with. You write a prompt. A model succeeds or fails on
it. You find out immediately, and you find out how efficiently you did it. Then
tomorrow you get another one.

WHAT I DID NOT EXPECT

The plan was a game about whether you could make the machine guess. I built it,
and then I measured it, and the machine won essentially every time. At sixty
characters it was right on the first attempt in one hundred percent of cases.

I spent real effort on the wrong fix. I hardened the banned word lists
adversarially, asking the model which words it leans on and blocking those.
The win rate did not move at all. Not slightly: not at all.

What moved it was length. 100% at sixty characters, 90% at twenty-five, 67% at
fifteen. The game was never about whether the machine could understand you. It
was about how little you could give it. So I threw away the ban-list work and
rebuilt the scoring around compression, and the game became good.

That is the part I would want a judge to take away. Not that I had a clever
idea, but that I had a wrong one, measured it honestly, and let the measurement
win. The character budget in the shipped app exists because of a graph, not
because of a hunch.

WHAT I CHOSE WHEN IT GOT AWKWARD

Two moments where the easy path and the right one diverged.

My Indian payment provider held up bank verification, which blocked Google Play
billing entirely, days before this deadline. The easy thing was to ship Android
with the paywall in place and the purchases dead, and hope no judge tapped a
padlock. Instead I made the entitlement check ask whether a store is configured
at all, so the Android build degrades to a complete free game with no locks
anywhere. Nobody sees a door they cannot open.

And the daily puzzle is free permanently, not as a trial. A daily game that
gates the daily thing does not deserve the habit it is asking for. Plus sells a
harder version to people who already love it. If this app never makes money,
it will still be a complete game that anyone can play every day for nothing.

[OPTIONAL: if there is a personal reason you started this, add it here in your
own words. Two or three honest sentences from you will land better than
anything written for you.]
```
