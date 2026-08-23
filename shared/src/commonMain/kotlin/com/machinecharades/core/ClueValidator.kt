package com.machinecharades.core

/**
 * Decides whether a clue is legal, before it ever reaches the network.
 *
 * Running this client-side is a UX decision (instant feedback, no round trip)
 * but it is NOT a trust boundary — the Worker re-runs the identical check
 * server-side, because a modified client could otherwise submit the secret word
 * and win every day. Keep the two implementations in step; the shared test
 * vectors in CoreTest exist for exactly that reason.
 */
object ClueValidator {

    private const val VOWELS = "aeiou"

    /**
     * Minimum length a banned word must have before we check whether a clue
     * token *contains* it.
     *
     * This started at 4 and had to be raised. At 4, "sting" (banned for
     * beehive) rejected "casting", "lasting" and "fasting"; "sand" rejected
     * "sandwich" and "thousand"; "wind" rejected "window". Substring matching
     * cannot tell a compound boundary from a coincidence without a dictionary,
     * so the rule is deliberately conservative and each puzzle carries an
     * `allow` list for the residual collisions.
     */
    private const val MIN_CONTAINMENT_LEN = 5

    /** The remainder must look like a compound element, not one stray letter. */
    private const val MIN_REMAINDER_LEN = 3

    fun validate(
        clue: String,
        puzzle: DailyPuzzle,
        mode: ConstraintMode = ConstraintMode.NONE,
    ): ClueRejection? {
        val trimmed = clue.trim()
        if (trimmed.isEmpty()) return ClueRejection(ClueRejection.Reason.EMPTY)
        if (trimmed.length > MAX_CLUE_CHARS) {
            return ClueRejection(ClueRejection.Reason.TOO_LONG)
        }

        // Secret word first, so its message wins over a banned-word overlap.
        // Never overridable by the allow list — that would be an authoring
        // mistake with no legitimate use.
        forbiddenMatch(trimmed, puzzle.word)?.let {
            return ClueRejection(ClueRejection.Reason.CONTAINS_SECRET_WORD, it)
        }

        val allowed = puzzle.allow.map { Normalise.canonical(it) }.toSet()
        for (banned in puzzle.banned) {
            val hit = forbiddenMatch(trimmed, banned) ?: continue
            if (Normalise.canonical(hit) in allowed) continue
            return ClueRejection(ClueRejection.Reason.CONTAINS_BANNED_WORD, hit)
        }

        return when (mode) {
            ConstraintMode.NONE -> null

            ConstraintMode.NO_VOWELS -> {
                val canon = Normalise.canonical(trimmed)
                val hit = canon.firstOrNull { it in VOWELS }
                if (hit != null) {
                    ClueRejection(ClueRejection.Reason.VOWEL_USED, hit.toString())
                } else null
            }

            ConstraintMode.ONE_WORD ->
                if (Normalise.tokens(trimmed).size > 1) {
                    ClueRejection(ClueRejection.Reason.MORE_THAN_ONE_WORD)
                } else null

            ConstraintMode.TWENTY_CHAR_CAP ->
                if (trimmed.length > 20) {
                    ClueRejection(ClueRejection.Reason.OVER_CHAR_CAP)
                } else null
        }
    }

    /**
     * Returns the clue token that illegally references [forbidden], or null.
     *
     * Four escalating checks. Each exists because a player will try it:
     *  1. token equality on the canonical form   — "Giraffe."
     *  2. variant-set intersection               — "giraffes", "spotted", "tallest"
     *  3. skeleton containment per token         — "supergiraffe", "g1raffe"
     *  4. skeleton containment of the whole clue — "g i r a f f e"
     *
     * Order matters. Per-token checks run first so the UI can highlight the
     * exact word that failed. Check 4 is the last resort and returns the
     * forbidden word itself, because when a player spells it out across several
     * tokens there is no single culprit to point at.
     */
    private fun forbiddenMatch(clue: String, forbidden: String): String? {
        val fCanon = Normalise.canonical(forbidden).replace(" ", "")
        if (fCanon.isEmpty()) return null
        val fVariants = Normalise.variants(forbidden)
        val fSkel = Normalise.skeleton(forbidden)

        val clueTokens = Normalise.tokens(clue)

        for (token in clueTokens) {
            if (token == fCanon) return token
            if (Normalise.variants(token).any { it in fVariants }) return token
        }

        // Short forbidden words are exempt from containment: "ice" would
        // otherwise reject "police" and "nice".
        if (fSkel.length >= MIN_CONTAINMENT_LEN) {
            for (token in clueTokens) {
                val tSkel = Normalise.skeleton(token)
                if (!tSkel.contains(fSkel)) continue
                if (tSkel == fSkel) return token

                val remainder = tSkel.length - fSkel.length
                // Prefix matches are nearly always real derivations —
                // "africa|n", "water|proof", "light|house" — so one extra
                // letter is enough. Suffix and infix matches are where the
                // coincidences live: "de|light", "f|light", "ca|sting". Those
                // need a remainder long enough to be a plausible compound
                // element in its own right.
                if (tSkel.startsWith(fSkel) || remainder >= MIN_REMAINDER_LEN) {
                    return token
                }
            }
        }

        // Last resort, and the only reason this check exists: the player
        // spelled the word out across several tokens — "g i r a f f e".
        // Single-token clues have already had their say above, so restricting
        // this to multi-token clues stops it silently undoing the rules above.
        if (clueTokens.size > 1 &&
            Normalise.skeleton(clue).contains(fSkel) &&
            clueTokens.none { Normalise.skeleton(it).contains(fSkel) }
        ) {
            return forbidden
        }

        return null
    }
}
