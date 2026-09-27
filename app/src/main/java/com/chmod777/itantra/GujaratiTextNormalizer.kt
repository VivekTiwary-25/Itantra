package com.chmod777.itantra

import java.text.Normalizer

/**
 * Text normalisation for the Meta MMS Gujarati voice, whose character vocabulary silently drops
 * anything it does not contain. The Gujarati checkpoint's 60-token vocabulary has NO digit
 * characters at all — not Gujarati ૦-૯ nor ASCII 0-9 (verified against `vocab.txt`; unlike the
 * Bengali checkpoint, which does keep ASCII digits). A desktop check confirmed both native digits
 * (દવા માટે ૧૨ બોક્સ -> the ૧૨ is dropped, "12 boxes" becomes "boxes") and ASCII digits
 * (`12`) are silently skipped by sherpa's character frontend, changing the message's meaning.
 *
 * One transformation is applied, meaning-preserving:
 *
 * Digit runs (Gujarati ૦-૯ or ASCII 0-9) become Gujarati cardinal words, attached in place so a
 * classifier/unit stays joined. Runs of up to nine digits without a leading zero are read as one
 * number (Indian grouping: હજાર/thousand, લાખ/lakh, કરોડ/crore); longer runs and runs with a
 * leading zero (phone numbers, codes) are read digit by digit — same policy as
 * `BengaliTextNormalizer`, independently justified here by the same category of evidence
 * (native digits vanish, meaning changes), not copied from Bengali's data.
 *
 * Words and grouping come from AI4Bharat indic-numtowords 1.1.0 (MIT, guj/data/nums.py), the sole
 * listed variant (Gujarati has no `variations_dict` entries in that package). All words in UNITS,
 * HUNDREDS and the SCALES words were checked to use only characters present in the MMS Gujarati
 * `vocab.txt` (60 tokens: letters, matras, virama, avagraha-less anusvara/visarga, apostrophe,
 * hyphen, space — no candra vowel signs). No candra-vowel or punctuation normalisation is added
 * here: those are separate, unquantified frontend gaps (see MMS_GUJ_CONVERSION.md) that were not
 * observed in any number path and would need their own evidence.
 */
object GujaratiTextNormalizer {

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
        run.map { if (it in '૦'..'૯') '0' + (it - '૦') else it }.joinToString("")

    private val DIGIT_RUN = Regex("[0-9૦-૯]+")
    private const val MAX_CARDINAL_DIGITS = 9

    // (minimum run length that reaches this scale, scale word): crore 10^7, lakh 10^5, thousand 10^3.
    private val SCALES = listOf(8 to "કરોડ", 6 to "લાખ", 4 to "હજાર")

    private val UNITS = arrayOf(
        "શૂન્ય", "એક", "બે", "ત્રણ", "ચાર", "પાંચ", "છ", "સાત", "આઠ", "નવ",
        "દસ", "અગિયાર", "બાર", "તેર", "ચૌદ", "પંદર", "સોળ", "સત્તર", "અઢાર", "ઓગણીસ",
        "વીસ", "એકવીસ", "બાવીસ", "ત્રેવીસ", "ચોવીસ", "પચ્ચીસ", "છવ્વીસ", "સત્તાવીસ", "અઠ્ઠાવીસ", "ઓગણત્રીસ",
        "ત્રીસ", "એકત્રીસ", "બત્રીસ", "તેત્રીસ", "ચોત્રીસ", "પાંત્રીસ", "છત્રીસ", "સાડત્રીસ", "આડત્રીસ", "ઓગણચાલીસ",
        "ચાલીસ", "એકતાલીસ", "બેંતાલીસ", "તેતાલીસ", "ચુંમાલીસ", "પિસ્તાલીસ", "છેતાલીસ", "સુડતાલીસ", "અડતાલીસ", "ઓગણપચાસ",
        "પચાસ", "એકાવન", "બાવન", "ત્રેપન", "ચોપન", "પંચાવન", "છપ્પન", "સત્તાવન", "અઠ્ઠાવન", "ઓગણસાંઠ",
        "સાંઠ", "એકસઠ", "બાંસઠ", "ત્રેસઠ", "ચોસઠ", "પાંસઠ", "છાંસઠ", "સડસઠ", "અડસઠ", "ઓગણસિત્તેર",
        "સિત્તેર", "એકોતેર", "બોંતેર", "તોંતેર", "ચુંમોતેર", "પંચોતેર", "છોતેર", "સત્યોતેર", "અઠ્યોતેર", "ઓગણએંસી",
        "એંસી", "એક્યાસી", "બ્યાંસી", "ત્યાંસી", "ચોર્યાસી", "પંચ્યાસી", "છ્યાંસી", "સત્યાસી", "અઠ્યાસી", "નેવ્યાસી",
        "નેવું", "એકાણું", "બાણું", "ત્રાણું", "ચોરાણું", "પંચાણું", "છન્નું", "સત્તાણું", "અઠ્ઠાણું", "નવ્વાણું"
    )
    private val HUNDREDS = arrayOf(
        "", "એક સો", "બસો", "ત્રણ સો", "ચાર સો", "પાંચ સો", "છ સો", "સાત સો", "આઠ સો", "નવ સો"
    )
}
