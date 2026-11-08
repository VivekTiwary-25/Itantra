package com.chmod777.itantra

import java.text.Normalizer

/**
 * Text normalisation for the Meta MMS Tamil voice, whose 58-token character vocabulary silently
 * drops anything it does not contain. Two transformations are applied, both meaning-preserving:
 *
 * 1. Digit runs (Tamil ௦-௯ or ASCII 0-9) become Tamil cardinal words, attached in place so a
 *    classifier stays joined. Evidence for why this is required (not merely nice-to-have):
 *    the vocabulary has NO Tamil digits at all (dropped silently, confirmed against tokens.txt and
 *    a desktop probe of "௧௨" -> both codepoints reported OOV) and is even missing the ASCII digit
 *    '8' specifically (also confirmed OOV against tokens.txt and a desktop probe of a lone "8").
 *    The other ASCII digits (0,1,2,3,4,5,6,7,9) ARE present in the vocabulary, but there is no
 *    evidence they are pronounced as digits rather than as stray letters (same open question the
 *    Bengali voice had, where an in-vocabulary ASCII "12" was heard as the word "two"); verbalising
 *    all digit runs uniformly avoids relying on unverified per-digit pronunciation. Runs of up to
 *    nine digits without a leading zero are read as one number (Indian grouping: ஆயிரம்/இலட்சம்/
 *    கோடி); longer runs and runs with a leading zero (phone numbers, codes) are read digit by digit.
 * 2. Unicode NFC. The two/three-part vowel signs ொ, ோ, ௌ (U+0BCA/0BCB/0BCC) have a canonical
 *    decomposition to two codepoints each (e.g. ொ = வெ + ா, U+0BC6 + U+0BBE) that are both
 *    individually present in the vocabulary; NFC guarantees the single precomposed codepoint the
 *    tokens.txt actually uses is what reaches the model, instead of two separate vowel-sign tokens.
 *
 * Words come from AI4Bharat indic-numtowords 1.1.0 (MIT, tam/data/nums.py), first listed variant
 * only, with two Tamil-specific irregularities the Bengali normaliser does not need to model:
 * hundreds have a distinct "connecting" form when a remainder follows (நூற்று எட்டு = 108) versus
 * the standalone exact-hundred form (நூறு = 100, exceptions_dict); both are reproduced here. The
 * full stop/period is deliberately left alone: like Bengali's danda, it is not in the vocabulary and
 * is silently dropped by the model; no normalisation has been found to help with that. The aytham
 * (ஃ, U+0B83) is also not in the vocabulary and is silently dropped (confirmed with "ஃபோன்"); this
 * is left unhandled because it is rare in the message domain this app targets (short relay/location/
 * count messages) and no simple meaning-preserving substitute was evident without a listener.
 */
object TamilTextNormalizer {

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
            val hundredsDigit = rest[0] - '0'
            val remainder = rest.substring(1).trimStart('0')
            words += if (remainder.isEmpty()) HUNDREDS_EXACT[hundredsDigit] else HUNDREDS_CONNECTOR[hundredsDigit]
            rest = remainder
        }
        if (rest.isNotEmpty()) words += UNITS[rest.toInt()]
        return words.joinToString(" ")
    }

    private fun toAsciiDigits(run: String): String =
        run.map { if (it in '௦'..'௯') '0' + (it - '௦') else it }.joinToString("")

    private val DIGIT_RUN = Regex("[0-9௦-௯]+")
    private const val MAX_CARDINAL_DIGITS = 9

    // (minimum run length that reaches this scale, scale word): crore 10^7, lakh 10^5, thousand 10^3.
    private val SCALES = listOf(8 to "கோடி", 6 to "இலட்சம்", 4 to "ஆயிரம்")

    // direct_dict '0'..'99', first variant each.
    private val UNITS = arrayOf(
        "பூஜ்ஜியம்", "ஒன்று", "இரண்டு", "மூன்று", "நான்கு", "ஐந்து", "ஆறு", "ஏழு", "எட்டு", "ஒன்பது",
        "பத்து", "பதினொன்று", "பன்னிரண்டு", "பதிமூன்று", "பதினான்கு", "பதினைந்து", "பதினாறு", "பதினேழு", "பதினெட்டு", "பத்தொன்பது",
        "இருபது", "இருபத்து ஒன்று", "இருபத்து இரண்டு", "இருபத்து மூன்று", "இருபத்து நான்கு", "இருபத்து ஐந்து", "இருபத்து ஆறு", "இருபத்து ஏழு", "இருபத்து எட்டு", "இருபத்து ஒன்பது",
        "முப்பது", "முப்பத்து ஒன்று", "முப்பத்து இரண்டு", "முப்பத்து மூன்று", "முப்பத்து நான்கு", "முப்பத்து ஐந்து", "முப்பத்து ஆறு", "முப்பத்து ஏழு", "முப்பத்து எட்டு", "முப்பத்து ஒன்பது",
        "நாற்பது", "நாற்பத்து ஒன்று", "நாற்பத்து இரண்டு", "நாற்பத்து மூன்று", "நாற்பத்து நான்கு", "நாற்பத்து ஐந்து", "நாற்பத்து ஆறு", "நாற்பத்து ஏழு", "நாற்பத்து எட்டு", "நாற்பத்து ஒன்பது",
        "ஐம்பது", "ஐம்பத்து ஒன்று", "ஐம்பத்து இரண்டு", "ஐம்பத்து மூன்று", "ஐம்பத்து நான்கு", "ஐம்பத்து ஐந்து", "ஐம்பத்து ஆறு", "ஐம்பத்து ஏழு", "ஐம்பத்து எட்டு", "ஐம்பத்து ஒன்பது",
        "அறுபது", "அறுபத்து ஒன்று", "அறுபத்து இரண்டு", "அறுபத்து மூன்று", "அறுபத்து நான்கு", "அறுபத்து ஐந்து", "அறுபத்து ஆறு", "அறுபத்து ஏழு", "அறுபத்து எட்டு", "அறுபத்து ஒன்பது",
        "எழுபது", "எழுபத்து ஒன்று", "எழுபத்து இரண்டு", "எழுபத்து மூன்று", "எழுபத்து நான்கு", "எழுபத்து ஐந்து", "எழுபத்து ஆறு", "எழுபத்து ஏழு", "எழுபத்து எட்டு", "எழுபத்து ஒன்பது",
        "எண்பது", "எண்பத்து ஓன்று", "எண்பத்து இரண்டு", "எண்பத்து மூன்று", "எண்பத்து நான்கு", "எண்பத்து ஐந்து", "எண்பத்து ஆறு", "எண்பத்து ஏழு", "எண்பத்து எட்டு", "எண்பத்து ஓன்பது",
        "தொண்ணூறு", "தொண்ணூற்றொன்று", "தொண்ணூற்றிரெண்டு", "தொண்ணூற்று மூன்று", "தொண்ணூற்று நான்கு", "தொண்ணூற்றைந்து", "தொண்ணூற்றாறு", "தொண்ணூற்றேழு", "தொண்ணூரெட்டு", "தொண்ணூற்றொன்பது"
    )

    // hundreds_dict['1'..'9'], first variant each: connecting form used when a remainder follows.
    private val HUNDREDS_CONNECTOR = arrayOf(
        "", "நூற்று", "இருநூற்று", "முந்நூற்று", "நானூற்று", "ஐநூற்று", "அறுநூற்று", "எழுநூற்று", "எண்ணூற்று", "தொள்ளாயிரத்து"
    )

    // exceptions_dict['100','200',...]: standalone form used for an exact multiple of 100.
    private val HUNDREDS_EXACT = arrayOf(
        "", "நூறு", "இருநூறு", "முந்நூறு", "நானூறு", "ஐநூறு", "அறுநூறு", "எழுநூறு", "எண்ணூறு", "தொள்ளாயிரம்"
    )
}
