package com.chmod777.itantra

import java.text.Normalizer

/**
 * Text normalisation for the Meta MMS Telugu voice, whose character vocabulary silently drops
 * anything it does not contain (sherpa-onnx's character frontend logs "Skip unknown character"
 * and continues). Two transformations are applied, both meaning-preserving:
 *
 * 1. Digit runs (Telugu ౦-౯ or ASCII 0-9) become Telugu cardinal words. The MMS `tel` vocabulary
 *    (`tts-research/mms-tel/vocab.txt`) has no Telugu digits at all and only the single ASCII
 *    digit '6' (an artefact of the training data, not a usable digit path), so both scripts must
 *    be verbalised before reaching the model. Runs of up to nine digits without a leading zero are
 *    read as one number using Indian grouping (వేల/లక్షల/కోట్ల); longer runs and runs with a
 *    leading zero (phone numbers, codes) are read digit by digit, mirroring the Bengali normaliser.
 * 2. Unicode NFC, a harmless safety default (Telugu has no known precomposed/decomposed vocabulary
 *    gap analogous to Bengali's nukta letters, but normalising costs nothing).
 *
 * The cardinal algorithm is a direct port of AI4Bharat indic-numtowords 1.1.0 (MIT,
 * `indic_numtowords/tel/cardinal.py`), first listed variant only, verified against the running
 * Python package for 100+ numbers (see TeluguTextNormalizerTest). Unlike Bengali, Telugu's scale
 * words change shape depending on what follows: an exact multiple takes the standalone plural
 * (ఆరు వేలు = "six thousand"), a multiple with a remainder takes the genitive stem (ఆరు వేల
 * ఐదు = "six thousand five"), and a multiplier of exactly one uses yet another shape per scale
 * (ఒక కోటి "one crore", but ఒక లక్షా "one lakh [and]", ఒక వెయ్యి "one thousand" — ported
 * verbatim from the Python `cardinal.py` branches, not reconstructed from a general rule).
 *
 * The ASCII period (only sentence terminator in the drafted input; Telugu does not use the
 * Devanagari-style danda) and ZWNJ U+200C (seen after a virama in a compound place name) are
 * deliberately left alone: both are already silently dropped by the MMS frontend with no
 * pronunciation effect (ZWNJ is a rendering hint, not a phoneme), matching the Bengali
 * normaliser's decision not to touch punctuation the model already discards.
 */
object TeluguTextNormalizer {

    fun normalize(text: String): String {
        val expanded = DIGIT_RUN.replace(text) { verbalize(toAsciiDigits(it.value)) }
        return Normalizer.normalize(expanded, Normalizer.Form.NFC)
    }

    internal fun verbalize(digits: String): String {
        if (digits.length > MAX_CARDINAL_DIGITS || (digits.length > 1 && digits[0] == '0')) {
            return digits.map { UNITS[it - '0'] }.joinToString(" ")
        }
        val trimmed = digits.trimStart('0')
        if (trimmed.isEmpty()) return UNITS[0]
        return cardinal(trimmed)
    }

    /** Port of indic_numtowords tel/cardinal.py `convert()`, first variant only, digits 1-9 long, no leading zero. */
    private fun cardinal(start: String): String {
        var numStr = start
        var n = numStr.length
        var acc = ""
        fun append(s: String) {
            acc = if (acc.isEmpty()) s else "$acc $s"
        }
        fun consumeAndMaybeSuffix(headLen: Int) {
            numStr = numStr.substring(headLen).trimStart('0')
            n = numStr.length
            if (n == 0) acc += PLURAL_SUFFIX
        }

        if (n == 8 || n == 9) {
            if (numStr == "10000000") {
                append(CRORE_EXACT); numStr = ""; n = 0
            } else {
                val head = numStr.substring(0, n - 7)
                if (head == "1") append("ఒక కోటి") else append("${wordsUpTo99(head.toInt())} కోట్ల")
                consumeAndMaybeSuffix(head.length)
            }
        }
        if (n == 6 || n == 7) {
            if (numStr == "100000") {
                append(LAKH_EXACT); numStr = ""; n = 0
            } else {
                val head = numStr.substring(0, n - 5)
                if (head == "1") append("ఒక లక్షా") else append("${wordsUpTo99(head.toInt())} లక్షల")
                consumeAndMaybeSuffix(head.length)
            }
        }
        if (n == 4 || n == 5) {
            if (numStr == "1000") {
                append(THOUSAND_EXACT); numStr = ""; n = 0
            } else {
                val head = numStr.substring(0, n - 3)
                if (head == "1") append("ఒక వెయ్యి") else append("${wordsUpTo99(head.toInt())} వేల")
                consumeAndMaybeSuffix(head.length)
            }
        }
        if (n == 3) {
            when (numStr) {
                "100" -> { append(HUNDRED_EXACT); numStr = ""; n = 0 }
                "200" -> { append(TWO_HUNDRED_EXACT); numStr = ""; n = 0 }
                else -> {
                    val headDigit = numStr[0]
                    if (headDigit == '1') append("నూట") else append("${UNITS[headDigit - '0']} వందల")
                    consumeAndMaybeSuffix(1)
                }
            }
        }
        if (n == 1 || n == 2) {
            append(wordsUpTo99(numStr.toInt()))
        }
        return acc
    }

    /** direct_dict[0..99], first variant. */
    private fun wordsUpTo99(value: Int): String = when {
        value < 10 -> UNITS[value]
        value < 20 -> TEENS[value - 10]
        value % 10 == 0 -> TENS_PREFIX[value / 10 - 2]
        else -> "${TENS_PREFIX[value / 10 - 2]} ${UNITS[value % 10]}"
    }

    private fun toAsciiDigits(run: String): String =
        run.map { if (it in '౦'..'౯') '0' + (it - '౦') else it }.joinToString("")

    private val DIGIT_RUN = Regex("[0-9౦-౯]+")
    private const val MAX_CARDINAL_DIGITS = 9
    private const val PLURAL_SUFFIX = "ు"

    private const val HUNDRED_EXACT = "వంద"
    private const val TWO_HUNDRED_EXACT = "రెండు వందలు"
    private const val THOUSAND_EXACT = "వెయ్యి"
    private const val LAKH_EXACT = "ఒక లక్ష"
    private const val CRORE_EXACT = "ఒక కోటి"

    private val UNITS = arrayOf(
        "సున్నా", "ఒకటి", "రెండు", "మూడు", "నాలుగు", "ఐదు", "ఆరు", "ఏడు", "ఎనిమిది", "తొమ్మిది"
    )
    private val TEENS = arrayOf(
        "పది", "పదకొండు", "పన్నెండు", "పదమూడు", "పద్నాలుగు",
        "పదిహేను", "పదహారు", "పదిహేడు", "పద్దెనిమిది", "పందొమ్మిది"
    )
    private val TENS_PREFIX = arrayOf(
        "ఇరవై", "ముప్పై", "నలభై", "యాభై", "అరవై", "డెబ్బై", "ఎనభై", "తొంభై"
    )
}
