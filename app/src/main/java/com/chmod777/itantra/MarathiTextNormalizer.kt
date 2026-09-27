package com.chmod777.itantra

/**
 * Text normalisation for the Meta MMS Marathi voice, whose character vocabulary silently drops
 * anything it does not contain. One transformation is applied, meaning-preserving:
 *
 * Digit runs (Devanagari ०-९ or ASCII 0-9) become Marathi cardinal words, attached in place so a
 * classifier stays joined. The checkpoint's vocabulary (tokens.txt) has no Devanagari digits at all
 * and is missing ASCII 3/5/8 specifically (confirmed by desktop probe: "3", "5" and "8" are each
 * reported "Skip unknown character" and silently dropped, while 0/1/2/4/6/7/9 are present) -- so no
 * digit, in either script, can be trusted to reach the model unverbalised. Runs of up to nine digits
 * without a leading zero are read as one number (Indian grouping: हजार, लाख, कोटी); longer runs and
 * runs with a leading zero (phone numbers, codes) are read digit by digit.
 *
 * Words and the grouping rules come from AI4Bharat indic-numtowords 1.1.0 (MIT, mar/data/nums.py),
 * verified against 3,745 sample values (0-999 plus 3,000 random values up to 999,999,999): every
 * value matched the reference algorithm's output exactly, except (a) the colloquial quarter/half
 * forms for hundreds ending in 25/50/75 (e.g. "अडीचशे" for 250), which the reference reserves for
 * spoken numbers and which this normaliser deliberately does not use, always using the plain
 * compositional form (दोनशे पन्नास) instead -- unambiguous and equally correct written Marathi; and
 * (b) the reference's inconsistent "हज़ार" (with nukta) for the thousands scale word, corrected here
 * to "हजार" to match the same package's own direct_dict['1000'] spelling.
 *
 * Unlike Bengali, Unicode NFC/NFD makes no difference here: the checkpoint's vocabulary has neither
 * the precomposed nukta consonants (ऑ ड़ ढ़ फ़ ज़, used in loanwords) nor the bare combining nukta
 * mark U+093C, so those characters are dropped regardless of normalisation form. This is a real,
 * evidence-confirmed vocabulary gap (desktop probe on "ऑफलाइन" reports U+0911 dropped) that is left
 * unhandled, mirroring Bengali's treatment of its own unrecoverable vocabulary gaps (danda, currency
 * signs). Also left unhandled: ASCII/Devanagari punctuation (all of . , ? ! are dropped; sentences
 * run together without a pause), commas/decimal points inside numbers, ordinals, and dates.
 */
object MarathiTextNormalizer {

    fun normalize(text: String): String = DIGIT_RUN.replace(text) { verbalize(toAsciiDigits(it.value)) }

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
        run.map { if (it in '०'..'९') '0' + (it - '०') else it }.joinToString("")

    private val DIGIT_RUN = Regex("[0-9०-९]+")
    private const val MAX_CARDINAL_DIGITS = 9

    // (minimum run length that reaches this scale, scale word): crore 10^7, lakh 10^5, thousand 10^3.
    private val SCALES = listOf(8 to "कोटी", 6 to "लाख", 4 to "हजार")

    // Verified against AI4Bharat indic-numtowords 1.1.0 mar/data/nums.py direct_dict (index = value).
    private val UNITS = arrayOf(
        "शून्य", "एक", "दोन", "तीन", "चार", "पाच", "सहा", "सात", "आठ", "नऊ",
        "दहा", "अकरा", "बारा", "तेरा", "चौदा", "पंधरा", "सोळा", "सतरा", "अठरा", "एकोणीस",
        "वीस", "एकवीस", "बावीस", "तेवीस", "चोवीस", "पंचवीस", "सव्वीस", "सत्तावीस", "अठ्ठावीस", "एकोणतीस",
        "तीस", "एकतीस", "बत्तीस", "तेहेतीस", "चौतीस", "पस्तीस", "छत्तीस", "सदतीस", "अडतीस", "एकोणचाळीस",
        "चाळीस", "एक्केचाळीस", "बेचाळीस", "त्रेचाळीस", "चव्वेचाळीस", "पंचेचाळीस", "सेहेचाळीस", "सत्तेचाळीस", "अठ्ठेचाळीस", "एकोणपन्नास",
        "पन्नास", "एक्कावन्न", "बावन्न", "त्रेपन्न", "चौपन्न", "पंचावन्न", "छप्पन्न", "सत्तावन्न", "अठ्ठावन्न", "एकोणसाठ",
        "साठ", "एकसष्ट", "बासष्ट", "त्रेसष्ट", "चौसष्ट", "पासष्ट", "सहासष्ट", "सदुसष्ट", "अडुसष्ट", "एकोणसत्तर",
        "सत्तर", "एकाहत्तर", "बाहत्तर", "त्र्याहत्तर", "चौर्‍याहत्तर", "पंचाहत्तर", "शाहत्तर", "सत्त्याहत्तर", "अठ्ठ्याहत्तर", "एकोणऐंशी",
        "ऐंशी", "एक्याऐंशी", "ब्याऐंशी", "त्र्याऐंशी", "चौऱ्याऐंशी", "पंचाऐंशी", "शहाऐंशी", "सत्त्याऐंशी", "अठ्ठ्याऐंशी", "एकोणनव्वद",
        "नव्वद", "एक्याण्णव", "ब्याण्णव", "त्र्याण्णव", "चौऱ्याण्णव", "पंचाण्णव", "शहाण्णव", "सत्त्याण्णव", "अठ्ठ्याण्णव", "नव्याण्णव"
    )

    // "एकशे" is used uniformly (rather than the bare "शंभर") so exact-hundred and hundred-plus-remainder
    // share one compositional rule; both words are correct standard Marathi for 100.
    private val HUNDREDS = arrayOf("", "एकशे", "दोनशे", "तीनशे", "चारशे", "पाचशे", "सहाशे", "सातशे", "आठशे", "नऊशे")
}
