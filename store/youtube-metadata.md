# YouTube upload: Machine Charades demo

## Title (58 chars)

```
Machine Charades: You Write the Clue, the Machine Guesses
```

Alternate, if you would rather the hackathon be findable in the title:

```
Machine Charades: a daily word game you play by prompting
```

## Description

```
You write the clue. A language model tries to guess the word. One new word every day.

Five obvious words are blocked, so you have to describe your way around them, and the shorter your clue, the more it scores. Everyone gets the same word, so clue length is worth comparing.

In this demo: a clue that fails (mansion, hacienda, adobe, zero points), then "fort vs tide" at twelve characters, solved on the first guess for 1,270.

HOW IT IS BUILT
Compose Multiplatform, one codebase for iOS and Android. 85% of the code is shared, with eight expect/actual pairs for the rest, including the store key. Purchases run through RevenueCat behind a single Plus object, so no other file in the app imports a RevenueCat type. Puzzles are served from a Cloudflare Worker, so the model key never ships inside the app.

HOW IT MAKES MONEY
Today's puzzle is always free. Plus sells difficulty rather than access: every constraint mode, the full archive, and your complete record. A daily game that gates the daily thing does not last a week.

DOWNLOAD
App Store: https://apps.apple.com/app/machine-charades/id6809356797
Google Play: https://play.google.com/store/apps/details?id=com.techtush.machinecharades

Built for RevenueCat Shipaton 2026.

#Shipaton #BuildInPublic #KotlinMultiplatform #ComposeMultiplatform #RevenueCat #IndieDev

Music: Colony by TrackTribe, from the YouTube Audio Library.
```

## Tags

```
machine charades, daily word game, word game, ai game, llm game, prompt engineering, prompt game, kotlin multiplatform, compose multiplatform, revenuecat, shipaton, shipaton 2026, indie dev, indie app, ios game, android game, daily puzzle, word puzzle, buildinpublic, mobile game
```

## Category

**Gaming.** It is a game, and the Shipaton Best Game judges will see it in the
right context. Science & Technology is defensible given how much of the video is
architecture, but Gaming matches the product.

## Settings that matter

- **Visibility: Public.** The rules say "publicly visible". Unlisted is a risk.
- **Made for kids: NO.** This one bites people. Marking a video made for kids
  disables links, cards, end screens and comments, which would strip the store
  links out of the description.
- **Language: English.** The rules require English or a translation.
- **Altered content disclosure: not required.** The voiceover is synthesised,
  but YouTube's disclosure rule covers realistic depictions of real people or
  events. A narrated product demo is not that.
- **Attribution.** Check Colony's licence row in the Audio Library. If it says
  attribution required, the credit line is already in the description above; if
  not, you can drop it.

## One thing to decide before you upload

The video is **1:44 and vertical**, so YouTube will classify it as a **Short**
(vertical, three minutes or under). That still satisfies the rule, since it is
publicly visible on YouTube, but judges will get the Shorts player and vertical
feed rather than a normal watch page, and the description is collapsed there.

If you would rather they land on a standard watch page with the description
open, the fix is to pad the video to 1920x1080 with a blurred backdrop behind
the phone. That is a single re-render and does not touch the edit.
