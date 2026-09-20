# Machine Charades: demo video edit list

Built from the three recordings of 20 Sep 2026. Target 1:52 against the 2:00
ceiling.

Sources:
- **C1** = RecordIt-1789902185.mp4 (3:33): App Store install, a failed round, failed purchases
- **C2** = RecordIt-1789902689.mp4 (2:40): intro, the winning round, stats, paywall
- **C3** = VIDEO-2026-09-20-16-53-14.mp4 (1:01): Android, banned-word rejection, a failed round

---

## Cuts to use

| # | Source | In – Out | Dur | What it shows |
|---|--------|----------|-----|---------------|
| 1 | C2 | 0:12 – 0:22 | 10s | Intro screen, the four-beat explainer |
| 2 | C2 | 1:02 – 1:12 | 10s | SANDCASTLE, blocked pills, mode row, empty field |
| 3 | C2 | 1:12 – 1:16 | 4s  | Typing `fort vs tide`, counter falling 420 → 270, Send |
| 4 | C2 | 1:16 – 1:18 | 2s  | Thinking dots |
| 5 | C2 | 1:32 – 1:40 | 8s  | **IT HEARD: SANDCASTLE. 1 guess, 12 chars, 1270 points** |
| 6 | C2 | 1:42 – 1:48 | 6s  | Your record, with the Plus upsell line |
| 7 | C3 | 0:21 – 0:24 | 3s  | Android: "'sand' is blocked today. Try another angle." |
| 8 | C3 | 0:12 – 0:16 | 4s  | Android running the same puzzle (cross-platform proof) |
| 9 | C2 | 2:18 – 2:22 | 4s  | Paywall, three ticked perks, ₹299 / ₹49 |
| 10 |  | RE-SHOOT | 8s | **Successful purchase + padlocks opening** |
| 11 |  | to record | 10s | Terminal: `node tools/measure-difficulty.mjs` |
| 12 |  | to record | 6s  | `Plus.kt` scrolling in the editor |
| 13 | C2 | 0:00 – 0:04 | 4s  | App Store listing |

**Skip C2 1:18 – 1:32.** The "Keep the streak going?" dialog fires over the win
there. Cut from the thinking dots straight to the clean result screen.

**Do not use C1 at all** except as a fallback. It contains the exposed email,
the competitor search results, and a round that fails.

---

## Must not ship

- **Email on screen.** `tusharaggarwal274@gmail.com` in C1 at 0:48, and in the
  `Account:` line of every In-App Purchase sheet in both C1 and C2. Crop the
  sheet's lower third or blur that line.
- **Competitor apps.** C1 at 0:12, App Store search results. Third-party
  trademarks are an explicit disqualifier.

---

## Still to record

**1. The successful purchase, essential.** Every attempt in all three clips
failed: "That didn't go through. You have not been charged," and Restore says
"No purchase found on this account." The archive stays padlocked throughout.
Most likely the **Double-Click to Subscribe** never completed; it needs a
physical double-press of the side button. Retry, watch for that prompt, and
record paywall → sheet → confirm → padlocks gone in the archive. This is the
single shot the HAMM category is judged on.

**2. The measurement terminal, high value, optional.** Ten seconds of
`measure-difficulty.mjs` printing real win rates. Nothing else in the video
carries this much credibility per second.

**3. Plus.kt on screen, easy, optional.** Six seconds scrolling the file that
wraps RevenueCat.

---

## Voiceover

Corrected to rupees, to match what is on screen, and with the Play claim left
out until the listing is live.

**A, over cut 1 (11s)**
> Every AI app is the same shape. You type, it answers, you leave. Nobody
> practises prompting, and nobody can score it. Machine Charades turns that
> into a game.

**B, over cuts 2 to 5 (30s)**
> One word a day. Five obvious words are blocked, so you have to go around them.
> You write the clue, and a language model gets three guesses.
>
> *(beat, let the guess land)*
>
> The shorter your clue, the more it scores. Twelve characters here, solved on
> the first guess. Par is not a difficulty setting, it is a character budget.

**C, over cut 11 (14s)**
> That budget came from measurement. Self-play against the real model: at sixty
> characters it wins every time. At fifteen, a third of clues beat it. Hardening
> the banned word lists changed nothing at all. Length is the entire difficulty
> curve.

**D, over cuts 6, 9, 10 (26s)**
> Today's puzzle is always free. A daily game that gates the daily thing dies in
> a week. So Plus sells difficulty instead: every constraint mode, the full
> archive, your whole record.
>
> RevenueCat drives all of it. Offerings, the purchase, and one entitlement the
> whole app reads. Two hundred and ninety-nine rupees a year, priced for the
> market rather than converted into it, with a week's trial.

**E, over cuts 7, 8, 12 (19s)**
> It is Compose Multiplatform. Eighty-five percent of the code is shared, with
> eight expect-actual pairs for the rest, including the store key. That means
> one Plus object wraps RevenueCat and no other file in the app imports a
> RevenueCat type. Puzzles come from a Cloudflare Worker, so the model key never
> ships inside the app.

**F, over cut 13 (8s)**
> Live on the App Store. One word a day. See how short you can go.

---

## Production

- No music, or YouTube Audio Library only. Copyrighted audio is a disqualifier.
- Say "a language model", not the vendor name.
- Burn in captions. Judges watch muted.
- Upload **Public**, not unlisted. The rule says "publicly visible."
