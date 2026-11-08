package com.chmod777.itantra

import java.text.Normalizer

/**
 * Text normalisation for the Meta MMS Kannada voice, whose character vocabulary silently drops
 * anything it does not contain (desktop probe: `tts-research/mms-kan/evidence/raw/timings.json`,
 * 10/10 sentences). Two transformations are applied, both meaning-preserving:
 *
 * 1. Digit runs (Kannada ೦-೯ or ASCII 0-9) become Kannada cardinal words, attached in place so a
 *    classifier stays joined. MMS drops Kannada digits outright (vocab has no ೦-೯; ASCII digits
 *    ARE in the vocab but their pronunciation is unverified by a listener, so neither may reach
 *    the model unconverted). Runs of up to nine digits without a leading zero are read as one
 *    number (Indian grouping: ಸಾವಿರ/ಲಕ್ಷ/ಕೋಟಿ); longer runs and runs with a leading zero (phone
 *    numbers, codes) are read digit by digit. This mirrors `BengaliTextNormalizer`'s structure,
 *    not its data.
 * 2. Unicode NFC (defensive; no precomposed-form gap was found in the Kannada vocabulary the way
 *    Bengali had one, but normalising keeps behaviour consistent with the other MMS voices).
 *
 * Word data provenance and a correction: AI4Bharat indic-numtowords 1.1.0 (MIT, kan/cardinal.py)
 * supplies the 0-99 words (`direct_dict`) and the standalone hundred words 100/200/.../900. Its
 * algorithm for *fusing* a hundred with a following multiple of ten (e.g. "310" -> one continuous
 * word) is NOT used here: it is measurably broken for 7 of every 9 such combinations (any X10,
 * X20, X30, X50, X60, X70, X90 with X in 1-9; verified by calling `cardinal.convert` for all of
 * 100-999 -- see `tts-research/mms-kan/kan_units0_999.json` -- e.g. convert(110) and convert(870)
 * both return a truncated "ನೂ"/"ಎಂಟುನೂ" instead of a complete word; X40 and X80 happen to fall
 * inside the code's numeric-range checks and are not truncated, which is why the break is uneven).
 * This normaliser therefore composes a hundreds group as the standalone hundred word plus the
 * separate 1-99 word ("ಇನ್ನೂರು ಎಂಬತ್ತೆಂಟು", unfused, rather than attempting "ಇನ್ನೂರೆಂಬತ್ತೆಂಟು"),
 * and likewise joins the ಸಾವಿರ/ಲಕ್ಷ/ಕೋಟಿ scale words as separate tokens rather than using the
 * library's genitive-inflected forms ("ಸಾವಿರದ"/"ಲಕ್ಷದ"), which exist only to support that same
 * fusion. A native listener should confirm the unfused reading is acceptable; it was chosen over
 * the fused one only because the fused one is demonstrably broken, not because it was judged more
 * natural. Separately, `direct_dict["83"]` is itself a data bug in the vendored package (it is a
 * verbatim duplicate of `direct_dict["82"]`, "ಎಂಬತ್ತೆರಡು"); index 83 below is corrected to
 * "ಎಂಬತ್ತ್ಮೂರು" by the same stem+ಮೂರು pattern the package uses for 23/33/43/63/73/93.
 */
object KannadaTextNormalizer {

    fun normalize(text: String): String {
        val expanded = DIGIT_RUN.replace(text) { verbalize(toAsciiDigits(it.value)) }
        return Normalizer.normalize(expanded, Normalizer.Form.NFC)
    }

    internal fun verbalize(digits: String): String {
        if (digits.length > MAX_CARDINAL_DIGITS || (digits.length > 1 && digits[0] == '0')) {
            return digits.map { UNITS[it - '0'] }.joinToString(" ")
        }
        var rest = digits.trimStart('0')
        if (rest.isEmpty()) return UNITS[0]
        val words = ArrayList<String>()
        for ((minLen, scale) in SCALES) {
            if (rest.length >= minLen) {
                val head = rest.substring(0, rest.length - (minLen - 1))
                words += UNITS[head.toInt()]
                words += scale
                rest = rest.substring(head.length).trimStart('0')
            }
        }
        if (rest.length == 3) {
            words += HUNDREDS[rest[0] - '0']
            rest = rest.substring(1).trimStart('0')
        }
        if (rest.isNotEmpty()) words += UNITS[rest.toInt()]
        return words.joinToString(" ")
    }

    private fun toAsciiDigits(run: String): String =
        run.map { if (it in '೦'..'೯') '0' + (it - '೦') else it }.joinToString("")

    private val DIGIT_RUN = Regex("[0-9೦-೯]+")
    private const val MAX_CARDINAL_DIGITS = 9

    // (minimum run length that reaches this scale, scale word): crore 10^7, lakh 10^5, thousand 10^3.
    // Nominative forms (ಕೋಟಿ/ಲಕ್ಷ/ಸಾವಿರ), not the library's genitive ಲಕ್ಷದ/ಸಾವಿರದ -- see class doc.
    private val SCALES = listOf(8 to "ಕೋಟಿ", 6 to "ಲಕ್ಷ", 4 to "ಸಾವಿರ")

    // AI4Bharat indic-numtowords 1.1.0, kan/cardinal.py direct_dict, first listed variant, 0-99.
    // Index 83 corrected from the package's verbatim duplicate of 82 -- see class doc.
    private val UNITS = arrayOf(
        "ಸೊನ್ನೆ", "ಒಂದು", "ಎರಡು", "ಮೂರು", "ನಾಲ್ಕು", "ಐದು", "ಆರು", "ಏಳು", "ಎಂಟು", "ಒಂಬತ್ತು",
        "ಹತ್ತು", "ಹನ್ನೊಂದು", "ಹನ್ನೆರಡು", "ಹದಿಮೂರು", "ಹದಿನಾಲ್ಕು", "ಹದಿನೈದು", "ಹದಿನಾರು", "ಹದಿನೇಳು", "ಹದಿನೆಂಟು", "ಹತ್ತೊಂಬತ್ತು",
        "ಇಪ್ಪತ್ತು", "ಇಪ್ಪತ್ತೊಂದು", "ಇಪ್ಪತ್ತೆರಡು", "ಇಪ್ಪತ್ತ್ಮೂರು", "ಇಪ್ಪತ್ನಾಲ್ಕು", "ಇಪ್ಪತ್ತೈದು", "ಇಪ್ಪತ್ತಾರು", "ಇಪ್ಪತ್ತೇಳು", "ಇಪ್ಪತ್ತೆಂಟು", "ಇಪ್ಪತ್ತೊಂಬತ್ತು",
        "ಮೂವತ್ತು", "ಮೂವತ್ತೊಂದು", "ಮೂವತ್ತೆರಡು", "ಮೂವತ್ತ್ಮೂರು", "ಮೂವತ್ನಾಲ್ಕು", "ಮೂವತ್ತೈದು", "ಮೂವತ್ತಾರು", "ಮೂವತ್ತೇಳು", "ಮೂವತ್ತೆಂಟು", "ಮೂವತ್ತೊಂಬತ್ತು",
        "ನಲವತ್ತು", "ನಲವತ್ತೊಂದು", "ನಲವತ್ತೆರಡು", "ನಲವತ್ತ್ಮೂರು", "ನಲವತ್ನಾಲ್ಕು", "ನಲವತ್ತೈದು", "ನಲವತ್ತಾರು", "ನಲವತ್ತೇಳು", "ನಲವತ್ತೆಂಟು", "ನಲವತ್ತೊಂಬತ್ತು",
        "ಐವತ್ತು", "ಐವತ್ತೊಂದು", "ಐವತ್ತೆರಡು", "ಐವತ್ತ್ಮೂರು", "ಐವತ್ತ್ನಾಲ್ಕು", "ಐವತ್ತೈದು", "ಐವತ್ತಾರು", "ಐವತ್ತೇಳು", "ಐವತ್ತೆಂಟು", "ಐವತ್ತೊಂಬತ್ತು",
        "ಅರವತ್ತು", "ಅರವತ್ತೊಂದು", "ಅರವತ್ತೆರಡು", "ಅರವತ್ಮೂರು", "ಅರವತ್ನಾಲ್ಕು", "ಅರವತ್ತೈದು", "ಅರವತ್ತಾರು", "ಅರವತ್ತೇಳು", "ಅರವತ್ತೆಂಟು", "ಅರವತ್ತೊಂಬತ್ತು",
        "ಎಪ್ಪತ್ತು", "ಎಪ್ಪತ್ತೊಂದು", "ಎಪ್ಪತ್ತೆರಡು", "ಎಪ್ಪತ್ತ್ಮೂರು", "ಎಪ್ಪತ್ತ್ನಾಲ್ಕು", "ಎಪ್ಪತ್ತೈದು", "ಎಪ್ಪತ್ತಾರು", "ಎಪ್ಪತ್ತೇಳು", "ಎಪ್ಪತ್ತೆಂಟು", "ಎಪ್ಪತ್ತೊಂಬತ್ತು",
        "ಎಂಬತ್ತು", "ಎಂಬತ್ತೊಂದು", "ಎಂಬತ್ತೆರಡು", "ಎಂಬತ್ತ್ಮೂರು", "ಎಂಬತ್ತ್ನಾಲ್ಕು", "ಎಂಬತ್ತೈದು", "ಎಂಬತ್ತಾರು", "ಎಂಬತ್ತೇಳು", "ಎಂಬತ್ತೆಂಟು", "ಎಂಬತ್ತೊಂಬತ್ತು",
        "ತೊಂಬತ್ತು", "ತೊಂಬತ್ತೊಂದು", "ತೊಂಬತ್ತೆರಡು", "ತೊಂಬತ್ತ್ಮೂರು", "ತೊಂಬತ್ತ್ನಾಲ್ಕು", "ತೊಂಬತ್ತೈದು", "ತೊಂಬತ್ತಾರು", "ತೊಂಬತ್ತೇಳು", "ತೊಂಬತ್ತೆಂಟು", "ತೊಂಬತ್ತೊಂಬತ್ತು"
    )

    // AI4Bharat indic-numtowords 1.1.0, kan/cardinal.py: convert(100), convert(200), ..., convert(900)
    // (the standalone forms, not the broken fused-with-tens forms -- see class doc). Index 0 unused.
    private val HUNDREDS = arrayOf(
        "", "ನೂರು", "ಇನ್ನೂರು", "ಮುನ್ನೂರು", "ನಾನ್ನೂರು", "ಐನೂರು", "ಆರುನೂರು", "ಏಳುನೂರು", "ಎಂಟುನೂರು", "ಒಂಬೈನೂರು"
    )
}
