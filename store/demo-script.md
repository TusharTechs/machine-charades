# Machine Charades: Shipaton demo video

**Hard limits:** under 2:00 (judges stop watching at two minutes). Real device
footage. Public on YouTube or Vimeo. No copyrighted music, no third-party
trademarks. English.

**Target runtime: 1:52.** Leaves eight seconds of margin.

Written to serve the four categories we are actually placed for: Best Game
(gameplay, art direction, monetisation fit), HAMM (smartest use of RevenueCat
to drive revenue), Ship Kotlin Everywhere (cross-platform quality), Design.

---

## A. Cold open: the pain point
**0:00 – 0:11 (11s)**

**On screen:** App already open on the intro screen. No logo card, no title
slate. The first frame is the product.

> Every AI app is the same shape. You type, it answers, you leave. Nobody
> practises prompting, and nobody can score it. Machine Charades turns that
> into a game.

*Note: a title card here costs three seconds and wins nothing. Judges are
watching dozens of these; open on motion.*

---

## B. The loop, played for real
**0:11 – 0:43 (32s)**

**On screen:** one complete round, played live and solved. Today's word appears,
the five blocked pills, you type the clue with the par counter ticking down,
then the guesses land one at a time. Let the winning guess breathe for a full
second before cutting.

> One word a day. Five obvious words are blocked, so you have to go around them.
> You write the clue, and a language model gets three guesses.
>
> *(beat, let a guess land)*
>
> The shorter your clue, the more it scores. Par is not a difficulty setting,
> it is a character budget. Everyone gets the same word, so a clue length is
> worth comparing.

*This is the single most important segment. Do not rush it and do not narrate
over the moment the machine gets it right.*

---

## C. The measurement
**0:43 – 0:57 (14s)**

**On screen:** the terminal running `tools/measure-difficulty.mjs`, or a clean
four-line chart of the win rates. Two seconds of real output beats a stock
graphic.

> That number came from measurement. Self-play against the real model: at sixty
> characters it wins every time. At fifteen, a third of clues beat it. Hardening
> the banned word lists changed nothing at all. Length is the entire difficulty
> curve.

*Almost no entry can say anything like this. It is the strongest fourteen
seconds in the video for the Game and Design categories both.*

---

## D. RevenueCat
**0:57 – 1:23 (26s)**

**On screen, in this order:** finish a round → the "Want it harder? See Plus"
line → paywall with the three ticked perks → tap the annual plan → **the real
StoreKit sheet** → dismiss → the constraint modes unlocked, padlocks gone.

The StoreKit sheet is the shot that matters. It is the proof a purchase works.

> Today's puzzle is always free. A daily game that gates the daily thing dies in
> a week. So Plus sells difficulty instead: every constraint mode, the full
> archive, your whole record.
>
> RevenueCat drives all of it. Offerings, the purchase, and one entitlement the
> whole app reads. Nine ninety-nine a year or two ninety-nine a month, priced
> down for India, with a week's trial on the annual plan.

---

## E. Under the hood
**1:23 – 1:42 (19s)**

**On screen:** iPhone and Android phone side by side running the same round, for
three seconds. Then `Plus.kt` on screen, scrolling slowly.

> It is Compose Multiplatform. Eighty-five percent of the code is shared, with
> eight expect-actual pairs for the rest, including the store key. That means
> one Plus object wraps RevenueCat and no other file in the app imports a
> RevenueCat type. Puzzles come from a Cloudflare Worker, so the model key never
> ships inside the app.

*The side-by-side shot is what the Kotlin category is judged on. Three seconds
of two phones playing the same round says more than any claim about sharing.*

---

## F. Close
**1:42 – 1:52 (10s)**

**On screen:** the App Store listing, then the Play listing, then back to the
game on a fresh word.

> Live on the App Store and on Google Play. One word a day. See how short you
> can go.

---

## Production notes

**Recording the purchase.** You already own Plus on your Apple ID, so the
StoreKit sheet will not show a fresh purchase. Sign a **sandbox tester account**
into Settings → Developer → Sandbox Apple Account first, then record. Without
this you will only be able to film the paywall, not the transaction, and the
transaction is the shot the HAMM judges want.

**Device, not simulator.** The rule says footage of the app "functioning on the
device for which it was built." Plug the iPhone in and use QuickTime → New Movie
Recording → select the iPhone as the source. Cleaner than the on-device recorder
and no control-centre artefacts.

**Music.** Use none, or a track from the YouTube Audio Library. Copyrighted
music is an explicit disqualifier, and a voiceover over silence reads as
confident rather than cheap.

**Naming the model.** Say "a language model" rather than the vendor name. Vendor
names are third-party trademarks and the rule is written broadly. It costs
nothing to avoid.

**Captions.** Burn them in. Judges watch a lot of these muted.

**Upload as Public.** The rule says "publicly visible." Unlisted is a needless
risk.
