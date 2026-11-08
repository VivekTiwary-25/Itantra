package com.chmod777.itantra

import java.text.Normalizer

/**
 * Text normalisation for the Meta MMS Odia (`ory`) voice, whose 76-token character vocabulary
 * silently drops anything it does not contain (`tts-research/mms-ory/MMS_ORY_CONVERSION.md`).
 * Two transformations are applied, both meaning-preserving:
 *
 * 1. Digit runs (Odia ୦-୯ or ASCII 0-9) become Odia cardinal words, attached in place so a
 *    classifier stays joined. The vocab has ASCII digits but not Odia digits (desktop evidence:
 *    "Skip unknown character" for U+0B67/0B68 dropped ୧୨ from a draft line, changing "12 boxes"
 *    into "boxes"); ASCII digits are structurally accepted but the model was never shown to
 *    pronounce a multi-digit ASCII run correctly either (same open question the Bengali voice had,
 *    where "12" came out as "two" -- this has NOT been separately confirmed for Odia by a listener,
 *    so treat the ASCII-digit branch as evidence-consistent, not evidence-proven). Runs of up to
 *    nine digits without a leading zero are read as one number (Indian grouping: ହଜାର, ଲକ୍ଷ, କୋଟି);
 *    longer runs and runs with a leading zero (phone numbers, codes) are read digit by digit, same
 *    policy as the Bengali normaliser for the same reason (a code should not be reread as a cardinal).
 * 2. Unicode NFC. U+0B5C/0B5D (ଡ଼/ଢ଼) are in Unicode's full-composition-exclusion list, so NFC of an
 *    already-precomposed input decomposes them to base + nukta (ଡ/ଢ + ଼, U+0B21/0B22 + U+0B3C), which
 *    are the vocabulary's only representation of those sounds (confirmed with Python unicodedata:
 *    NFC(U+0B5C) == U+0B21 U+0B3C). Not exercised by the current draft input, added because the
 *    vocab audit found it and it can only help, mirroring the analogous Bengali ড়/ঢ়/য় finding without
 *    reusing any Bengali-specific character or word.
 *
 * Words and the crore/lakh/thousand grouping come from AI4Bharat indic-numtowords 1.1.0 (MIT,
 * `tools/vendor/indic_numtowords/ori/data/nums.py`), first listed variant only, verified against the
 * vendored `cardinal.convert()` for 54 numbers (see OdiaTextNormalizerTest). The danda (।, U+0964) is
 * deliberately left alone: it is dropped by the vocab exactly like Bengali's, and no listening evidence
 * was collected here to justify splitting sentences on it (out of scope without a phone/listener pass).
 */
object OdiaTextNormalizer {

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
        run.map { if (it in '୦'..'୯') '0' + (it - '୦') else it }.joinToString("")

    private val DIGIT_RUN = Regex("[0-9୦-୯]+")
    private const val MAX_CARDINAL_DIGITS = 9

    // (minimum run length that reaches this scale, scale word): crore 10^7, lakh 10^5, thousand 10^3.
    // Same Indian-numbering thresholds as Bengali; word content is Odia's own (ori/data/nums.py higher_dict).
    private val SCALES = listOf(8 to "କୋଟି", 6 to "ଲକ୍ଷ", 4 to "ହଜାର")

    // direct_dict['0'..'99'], first variant only.
    private val UNITS = arrayOf(
        "ଶୁନ୍ୟ", "ଏକ", "ଦୁଇ", "ତିନି", "ଚାରି", "ପାଞ୍ଚ", "ଛଅ", "ସାତ", "ଆଠ", "ନଅ",
        "ଦଶ", "ଏଗାର", "ବାର", "ତେର", "ଚଉଦ", "ପନ୍ଦର", "ଷୋହଳ", "ସତର", "ଅଠର", "ଊଣେଇଶ",
        "କୋଡିଏ", "ଏକୋଇଶ", "ବାଇଶ", "ତେଇଶ", "ଚବିଶ", "ପଚିଶ", "ଛବିଶ", "ସତାଇଶ", "ଅଠାଇଶ", "ଅଣତିରିଶ",
        "ତିରିଶ", "ଏକତିରିଶ", "ବତିଶ", "ତେତିଶ", "ଚଉତିରିଶ", "ପଞ୍ଚତିରିଶ", "ଛତିଶ", "ସଇଁତିରିଶ", "ଅଠତିରିଶ", "ଅଣଚାଳିଶ",
        "ଚାଳିଶ", "ଏକଚାଳିଶ", "ବୟାଳିଶ", "ତେୟାଳିଶ", "ଚଉରାଳିଶ", "ପଇଁଚାଳିଶ", "ଛୟାଳିଶ", "ସତଚାଳିଶ", "ଅଠଚାଳିଶ", "ଅଣଚାଶ",
        "ପଚାଶ", "ଏକାବନ", "ବାଉନ", "ତେପନ", "ଚଉବନ", "ପଞ୍ଚାବନ", "ଛପନ", "ସତାବନ", "ଅଠାବନ", "ଅଣଷଠି",
        "ଷାଠିଏ", "ଏକଷଠି", "ବାଷଠି", "ତେଷଠି", "ଚଉଷଠି", "ପଞ୍ଚଷଠି", "ଛଅଷଠି", "ସତଷଠି", "ଅଠଷଠି", "ଅଣସ୍ତରି",
        "ସତୂରି", "ଏକସ୍ତରି", "ବାସ୍ତରି", "ତେସ୍ତରି", "ଚଉସ୍ତରି", "ପଞ୍ଚସ୍ତରି", "ଛଅସ୍ତରି", "ସତସ୍ତରି", "ଅଠସ୍ତରି", "ଅଣାଅଶୀ",
        "ଅଶୀ", "ଏକାଅଶୀ", "ବୟାଅଶୀ", "ତେୟାଅଶୀ", "ଚଉରାଅଶୀ", "ପଞ୍ଚାଅଶୀ", "ଛୟାଅଶୀ", "ସତାଅଶୀ", "ଅଠାଅଶୀ", "ଅଣାନବେ",
        "ନବେ", "ଏକାନବେ", "ବୟାନବେ", "ତେୟାନବେ", "ଚଉରାନବେ", "ପଞ୍ଚାନବେ", "ଛୟାନବେ", "ସତାନବେ", "ଅଠାନବେ", "ଅନେଶତ"
    )

    // hundreds_dict has no entries in the vendored package; cardinal.py builds them inline
    // (exceptions_dict['100'] = ଶହେ for the "1" digit, UNITS[d] + " ଶହ" otherwise).
    private val HUNDREDS = arrayOf(
        "", "ଶହେ", "ଦୁଇ ଶହ", "ତିନି ଶହ", "ଚାରି ଶହ", "ପାଞ୍ଚ ଶହ", "ଛଅ ଶହ", "ସାତ ଶହ", "ଆଠ ଶହ", "ନଅ ଶହ"
    )
}
