package com.machinecharades.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * These assertions mirror tools/clue-vectors.json one for one, and the
 * TypeScript suite in worker/src/validator.test.ts asserts the same vectors
 * against the server-side port.
 *
 * If one suite passes and the other fails, the client and server validators
 * have drifted — which is a scoring exploit, not a cosmetic bug. Keep them in
 * step or delete one of them.
 */
class NormaliseTest {

    @Test
    fun canonicalFoldsCaseAccentsAndPunctuation() {
        assertEquals("giraffes tall", Normalise.canonical("Giraffes, tall!"))
        assertEquals("double spaces", Normalise.canonical("  double   spaces  "))
        assertEquals("cafe", Normalise.canonical("café"))
    }

    @Test
    fun interiorHomoglyphsFoldButTrailingPunctuationDoesNot() {
        assertEquals("giraffe", Normalise.canonical("g1raffe"))
        assertEquals("giraffe", Normalise.canonical("g!raffe"))
        assertEquals("ice cold", Normalise.canonical("1ce cold"))
        // The regression that started all of this: '!' must not become 'i'.
        assertEquals("wow", Normalise.canonical("wow!!!"))
        assertEquals("tall", Normalise.canonical("tall!"))
    }

    @Test
    fun skeletonDefeatsSpacingAndRepeats() {
        assertEquals("girafe", Normalise.skeleton("giraffe"))
        assertEquals("girafe", Normalise.skeleton("g i r a f f e"))
        assertEquals("girafe", Normalise.skeleton("giraaaaffe"))
        assertEquals("girafe", Normalise.skeleton("g-i-r-a-f-f-e"))
    }

    @Test
    fun variantsOverGenerateRatherThanUnderGenerate() {
        assertTrue("giraffe" in Normalise.variants("giraffes"))
        assertTrue("tall" in Normalise.variants("tallest"))
        assertTrue("run" in Normalise.variants("running"))
        assertTrue("pony" in Normalise.variants("ponies"))
        assertTrue("spot" in Normalise.variants("spotted"))
        assertTrue("hope" in Normalise.variants("hoping"))
        assertTrue("zoo" in Normalise.variants("zoos"))
    }

    @Test
    fun variantsLeaveShortWordsAloneAndInventNoSubstrings() {
        assertEquals(setOf("ice"), Normalise.variants("ice"))
        assertTrue("ice" !in Normalise.variants("police"))
        assertTrue("ice" !in Normalise.variants("nice"))
    }
}

class ClueValidatorTest {

    private val puzzle = DailyPuzzle(
        number = 1,
        date = "2026-09-24",
        word = "giraffe",
        banned = listOf("neck", "tall", "africa", "zoo", "spots"),
        category = "animal",
        difficulty = 2,
    )

    private fun reject(clue: String, mode: ConstraintMode = ConstraintMode.NONE) =
        ClueValidator.validate(clue, puzzle, mode)

    @Test
    fun acceptsLegalClues() {
        assertNull(reject("yellow patchwork, absurd reach"))
        assertNull(reject("eats treetops"))
    }

    @Test
    fun shortBannedWordsDoNotCauseFalsePositives() {
        // "zoo" skeletonises to "zo" and must not reject ordinary words.
        assertNull(reject("the police were nice"))
    }

    @Test
    fun rejectsEmptyAndOverlongClues() {
        assertEquals(ClueRejection.Reason.EMPTY, reject("")?.reason)
        assertEquals(ClueRejection.Reason.EMPTY, reject("   ")?.reason)
        assertEquals(
            ClueRejection.Reason.TOO_LONG,
            reject("x".repeat(MAX_CLUE_CHARS + 1))?.reason,
        )
    }

    @Test
    fun rejectsInflectedBannedWordsAndNamesTheToken() {
        reject("long-necked savanna browser").let {
            assertEquals(ClueRejection.Reason.CONTAINS_BANNED_WORD, it?.reason)
            assertEquals("necked", it?.offendingToken)
        }
        reject("the tallest land mammal").let {
            assertEquals(ClueRejection.Reason.CONTAINS_BANNED_WORD, it?.reason)
            assertEquals("tallest", it?.offendingToken)
        }
        reject("spotted?").let {
            assertEquals(ClueRejection.Reason.CONTAINS_BANNED_WORD, it?.reason)
            assertEquals("spotted", it?.offendingToken)
        }
        reject("AFRICAN plains").let {
            assertEquals(ClueRejection.Reason.CONTAINS_BANNED_WORD, it?.reason)
            assertEquals("african", it?.offendingToken)
        }
        reject("zoos everywhere").let {
            assertEquals(ClueRejection.Reason.CONTAINS_BANNED_WORD, it?.reason)
            assertEquals("zoos", it?.offendingToken)
        }
    }

    /**
     * The exploit that would break the leaderboard. Every evasion we know about
     * lives here so a regression is loud rather than quiet.
     */
    @Test
    fun noEvasionSmugglesTheSecretWordThrough() {
        val evasions = listOf(
            "giraffe", "GIRAFFE", "giraffe!", "Giraffes.",
            "g1raffe", "g!raffe", "g i r a f f e", "g-i-r-a-f-f-e",
            "giraaaffe", "supergiraffey", "a giraffe-like thing", "thegiraffe",
        )
        for (clue in evasions) {
            val result = reject(clue)
            assertNotNull(result, "\"$clue\" slipped through")
            assertEquals(
                ClueRejection.Reason.CONTAINS_SECRET_WORD,
                result.reason,
                "\"$clue\"",
            )
        }
    }

    /**
     * Every case here was a real false positive found by
     * tools/probe-validator.mjs against actual puzzle content. They are the
     * reason the containment rule distinguishes a prefix match from a suffix
     * match, and they must keep passing — a filter that rejects "delight"
     * because the answer is "lighthouse" reads to the player as a broken game,
     * not as a rule.
     */
    @Test
    fun substringCoincidencesAreNotTreatedAsBannedWords() {
        val lighthouse = DailyPuzzle(
            number = 2,
            date = "2026-09-25",
            word = "lighthouse",
            banned = listOf("light", "coast", "beam", "warn", "sting"),
            allow = listOf("blasting"),
        )

        // Accepted: the banned word is inside the token by coincidence.
        for (clue in listOf(
            "delight",        // suffix match, 2-char remainder
            "flight path",    // suffix match, 1-char remainder
            "a slight breeze",
            "casting call",   // "sting" inside "ca|sting" — the original bug
            "lasting",
            "blasting",       // would block on remainder, but allow overrides
        )) {
            assertNull(
                ClueValidator.validate(clue, lighthouse),
                "\"$clue\" should be accepted",
            )
        }

        // Rejected: a real derivation or a genuine compound.
        for ((clue, token) in listOf(
            "spotlight" to "spotlight",   // suffix, 4-char remainder — a real light
            "lighting" to "lighting",     // variant of a banned word
            "coastal town" to "coastal",  // prefix match, real derivation
            "stinging" to "stinging",
        )) {
            val result = ClueValidator.validate(clue, lighthouse)
            assertNotNull(result, "\"$clue\" should be rejected")
            assertEquals(ClueRejection.Reason.CONTAINS_BANNED_WORD, result.reason)
            assertEquals(token, result.offendingToken)
        }
    }

    @Test
    fun shortBannedWordsAreExemptFromContainment() {
        // "tall" and "neck" are 4 characters, below the containment floor, so
        // "tallow" and "necklace" must pass. "zoo" skeletonises to "zo".
        assertNull(reject("tallow candle"))
        assertNull(reject("a necklace"))
        assertNull(reject("zoology degree"))
    }

    @Test
    fun allowListOverridesTheVariantRule() {
        // Stripping "-y" turns "tally" into "tall", which is banned. The word is
        // unrelated, so the puzzle whitelists it.
        val withAllow = puzzle.copy(allow = listOf("tally"))
        assertNull(ClueValidator.validate("tally the votes", withAllow))
        // Without the whitelist the same clue is blocked — proving the allow
        // list is doing the work rather than the rule having gone soft.
        assertEquals(
            ClueRejection.Reason.CONTAINS_BANNED_WORD,
            ClueValidator.validate("tally the votes", puzzle)?.reason,
        )
    }

    @Test
    fun theAllowListCanNeverUnblockTheSecretWord() {
        val reckless = puzzle.copy(allow = listOf("giraffe", "giraffes"))
        assertEquals(
            ClueRejection.Reason.CONTAINS_SECRET_WORD,
            ClueValidator.validate("giraffe", reckless)?.reason,
        )
    }

    @Test
    fun constraintModesApply() {
        assertNull(reject("brwn ptchwrk", ConstraintMode.NO_VOWELS))
        assertEquals(
            ClueRejection.Reason.VOWEL_USED,
            reject("brown patchwork", ConstraintMode.NO_VOWELS)?.reason,
        )

        assertNull(reject("treetops", ConstraintMode.ONE_WORD))
        assertEquals(
            ClueRejection.Reason.MORE_THAN_ONE_WORD,
            reject("eats treetops", ConstraintMode.ONE_WORD)?.reason,
        )

        assertNull(reject("patchwork reacher", ConstraintMode.TWENTY_CHAR_CAP))
        assertEquals(
            ClueRejection.Reason.OVER_CHAR_CAP,
            reject("yellow patchwork reach", ConstraintMode.TWENTY_CHAR_CAP)?.reason,
        )
    }
}

class ScoringTest {

    @Test
    fun matchesTheSharedVectors() {
        assertEquals(1200, Scoring.score(solved = true, guessesUsed = 1, clueChars = 20))
        assertEquals(1025, Scoring.score(solved = true, guessesUsed = 1, clueChars = 55))
        assertEquals(800, Scoring.score(solved = true, guessesUsed = 2, clueChars = 20))
        assertEquals(1000, Scoring.score(solved = true, guessesUsed = 1, clueChars = 60))
        assertEquals(1000, Scoring.score(solved = true, guessesUsed = 1, clueChars = 90))
        assertEquals(500, Scoring.score(solved = true, guessesUsed = 3, clueChars = 30))
        assertEquals(0, Scoring.score(solved = false, guessesUsed = 3, clueChars = 12))
    }

    /**
     * The ordering IS the game design. Brevity must matter, but never enough to
     * let a terse third-try beat a verbose first-try — otherwise players
     * optimise for short clues instead of good ones.
     */
    @Test
    fun preservesTheIntendedOrdering() {
        val shortFirst = Scoring.score(true, 1, 20)
        val longFirst = Scoring.score(true, 1, 55)
        val shortSecond = Scoring.score(true, 2, 20)
        val shortThird = Scoring.score(true, 3, 12)

        assertTrue(shortFirst > longFirst, "brevity should matter")
        assertTrue(longFirst > shortSecond, "a long first-try beats a short second-try")
        assertTrue(shortSecond > shortThird, "guess count dominates")
    }

    @Test
    fun ratingIsSlowAndFloored() {
        val up = Scoring.updateRating(Scoring.START_RATING, myScore = 1200, fieldMeanScore = 600)
        val down = Scoring.updateRating(Scoring.START_RATING, myScore = 0, fieldMeanScore = 600)

        assertTrue(up > Scoring.START_RATING, "beating the field should raise the rating")
        assertTrue(down < Scoring.START_RATING, "a zero should lower it")
        assertTrue(up - Scoring.START_RATING <= 24, "one day must not swing the rating wildly")
        assertTrue(Scoring.updateRating(100, 0, 600) >= 100, "rating is floored")
    }

    @Test
    fun modesUnlockInOrder() {
        assertEquals(listOf(ConstraintMode.NONE), Scoring.unlockedModes(1000))
        assertTrue(ConstraintMode.TWENTY_CHAR_CAP in Scoring.unlockedModes(1100))
        assertTrue(ConstraintMode.ONE_WORD in Scoring.unlockedModes(1250))
        assertEquals(4, Scoring.unlockedModes(1400).size)
    }

    @Test
    fun shareStringIsSpoilerFreeAndReadsLeftToRight() {
        val result = RoundResult(
            puzzleNumber = 142,
            clue = "yellow patchwork, absurd reach",
            guesses = listOf(
                MachineGuess("zebra", correct = false),
                MachineGuess("giraffe", correct = true),
            ),
            solved = true,
        )
        val partner = PartnerRound(142, solved = true, guessesUsed = 1, clueChars = 22)
        val share = Scoring.shareString(result, partner)

        assertTrue(share.startsWith("MACHINE CHARADES #142"))
        assertTrue("🟥🟩🟨" in share, "one square per attempt, unused marked yellow")
        assertTrue("2 guesses" in share)
        assertTrue("30 chars" in share)
        assertTrue("vs 1 guess · 22 chars" in share)
        // The whole point: nothing in the string reveals the word or the clue.
        assertTrue("giraffe" !in share.lowercase(), "must not leak the answer")
        assertTrue("patchwork" !in share.lowercase(), "must not leak the clue")
    }
}
