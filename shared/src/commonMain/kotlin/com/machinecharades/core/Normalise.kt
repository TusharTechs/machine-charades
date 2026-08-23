package com.machinecharades.core

/**
 * Text normalisation used by banned-word detection.
 *
 * The job here is adversarial: players WILL try "g1raffe", "g i r a f f e",
 * "giraffe." and "giraffes" to smuggle the secret word past the filter. Every
 * check runs against a canonical form, plus a more aggressive "skeleton" form
 * that also collapses repeats and strips separators entirely.
 *
 * This file is mirrored by worker/src/validator.ts. The two are held in step by
 * tools/clue-vectors.json — if you change a rule here, change it there and
 * re-run both suites. Drift between them is a scoring exploit, not a cosmetic
 * bug: the server is the only thing standing between a modified client and a
 * perfect score every day.
 */
object Normalise {

    /**
     * Digit homoglyphs. Folded whenever they sit next to a letter, because a
     * digit inside or beside a word is almost never legitimate in a clue.
     */
    private val leetDigit: Map<Char, Char> = mapOf(
        '0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a',
        '5' to 's', '7' to 't', '8' to 'b', '9' to 'g', '6' to 'g', '2' to 'z',
    )

    /**
     * Symbol homoglyphs. Folded ONLY when strictly interior to a word — "tall!"
     * must normalise to "tall", not "talli", or ordinary punctuation corrupts
     * every clue that ends in an exclamation mark.
     */
    private val leetSymbol: Map<Char, Char> = mapOf(
        '!' to 'i', '|' to 'i', '@' to 'a', '$' to 's', '+' to 't',
    )

    /**
     * Latin-1 / common-accent folding. Kotlin/Common has no Normalizer, so we
     * fold the characters that actually turn up in English-language play.
     */
    private val accents: Map<Char, Char> = buildMap {
        "àáâãäåā".forEach { put(it, 'a') }
        "èéêëē".forEach { put(it, 'e') }
        "ìíîïī".forEach { put(it, 'i') }
        "òóôõöøō".forEach { put(it, 'o') }
        "ùúûüū".forEach { put(it, 'u') }
        put('ç', 'c'); put('ñ', 'n'); put('ý', 'y'); put('ÿ', 'y')
        put('š', 's'); put('ž', 'z'); put('ł', 'l')
    }

    private const val VOWELS = "aeiou"

    /**
     * A real letter or digit — deliberately excluding symbol homoglyphs, so a
     * run of punctuation like "wow!!!" doesn't make each '!' look interior to
     * the next one.
     */
    private fun isWordish(c: Char?): Boolean {
        if (c == null) return false
        val lower = c.lowercaseChar()
        return lower.isAsciiAlnum() || accents.containsKey(lower)
    }

    /**
     * Canonical form: lowercase, accent-folded, leet-folded, punctuation
     * replaced by single spaces, whitespace collapsed. Word boundaries survive.
     */
    fun canonical(input: String): String {
        val sb = StringBuilder(input.length)
        for (i in input.indices) {
            val lower = input[i].lowercaseChar()
            val prev = if (i > 0) input[i - 1] else null
            val next = if (i < input.length - 1) input[i + 1] else null

            val accent = accents[lower]
            if (accent != null) {
                sb.append(accent)
                continue
            }

            val digit = leetDigit[lower]
            if (digit != null) {
                // Adjacent to a word on either side, so fold it.
                sb.append(if (isWordish(prev) || isWordish(next)) digit else lower)
                continue
            }

            val symbol = leetSymbol[lower]
            if (symbol != null) {
                // Strictly interior only — trailing punctuation stays punctuation.
                sb.append(if (isWordish(prev) && isWordish(next)) symbol else ' ')
                continue
            }

            sb.append(if (lower.isAsciiAlnum()) lower else ' ')
        }
        return sb.toString().split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * Skeleton form: canonical, then all separators removed and runs of the
     * same letter collapsed to one. Defeats "g i r a f f e" and "giraaaffe".
     *
     * Collapsing repeats is lossy on purpose — it means "bookkeeper" and
     * "bokeper" share a skeleton. That is the right trade for a filter: a false
     * positive costs the player one retype, a false negative breaks the puzzle.
     */
    fun skeleton(input: String): String {
        val canon = canonical(input).replace(" ", "")
        if (canon.isEmpty()) return ""
        val sb = StringBuilder(canon.length)
        for (c in canon) if (sb.isEmpty() || sb.last() != c) sb.append(c)
        return sb.toString()
    }

    /** Canonical tokens, in order. */
    fun tokens(input: String): List<String> =
        canonical(input).split(' ').filter { it.isNotEmpty() }

    /**
     * Every plausible root of a word, as a set.
     *
     * A single-stem approach fails on the most common case there is: stripping
     * "-es" from "giraffes" yields "giraff", which never matches "giraffe".
     * Rather than encode ever-more-baroque rules about when to drop one letter
     * versus two, emit both candidates and match on set intersection.
     * Over-generating is safe here — a spurious variant costs a player one
     * retype, a missed one lets the secret word through.
     */
    fun variants(word: String): Set<String> {
        val base = canonical(word).replace(" ", "")
        if (base.isEmpty()) return emptySet()

        val out = mutableSetOf(base)
        fun add(s: String) { if (s.length >= 3) out.add(s) }
        fun undouble(s: String) {
            if (s.length >= 4 && s.last() == s[s.length - 2] && s.last() !in VOWELS) {
                add(s.dropLast(1))
            }
        }

        if (base.endsWith("ies") && base.length > 4) add(base.dropLast(3) + "y")

        if (base.endsWith("es") && base.length > 4) {
            add(base.dropLast(2))  // buses -> bus
            add(base.dropLast(1))  // giraffes -> giraffe
        } else if (base.endsWith("s") && base.length > 3 && !base.endsWith("ss")) {
            add(base.dropLast(1))
        }

        for (suffix in listOf("ing", "ed")) {
            if (base.endsWith(suffix) && base.length - suffix.length >= 3) {
                val b = base.dropLast(suffix.length)
                add(b)
                undouble(b)
                add(b + "e")       // hoping -> hope
            }
        }

        for (suffix in listOf("ness", "ment", "est", "ers", "er", "ly", "y")) {
            if (base.endsWith(suffix) && base.length - suffix.length >= 3) {
                val b = base.dropLast(suffix.length)
                add(b)
                undouble(b)  // spotty -> spot, windy -> wind
            }
        }

        return out
    }
}

/**
 * Kotlin/Common's Char.isLetterOrDigit() behaves subtly differently across
 * targets over the full Unicode range. We only ever want ASCII letters and
 * digits to survive normalisation, so be explicit.
 */
private fun Char.isAsciiAlnum(): Boolean =
    (this in 'a'..'z') || (this in '0'..'9')
