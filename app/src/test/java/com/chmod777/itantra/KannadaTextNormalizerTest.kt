package com.chmod777.itantra

import org.junit.Assert.assertEquals
import org.junit.Test

class KannadaTextNormalizerTest {

    // Expected words generated from AI4Bharat indic-numtowords 1.1.0 (kan/cardinal.py) 0-99 and
    // standalone-hundred words, composed the same unfused way as KannadaTextNormalizer.verbalize
    // (tts-research/mms-kan/kan_reference_gen.log / kan_normalizer_reference.json). Not compared
    // against the library's own convert() for 3+ digit numbers: that fuses a hundred with a
    // following ten, which is broken for X10/X20/X30/X50/X60/X70/X90 (see class doc).
    private val reference = listOf(
        0 to "ಸೊನ್ನೆ",
        1 to "ಒಂದು",
        7 to "ಏಳು",
        10 to "ಹತ್ತು",
        12 to "ಹನ್ನೆರಡು",
        19 to "ಹತ್ತೊಂಬತ್ತು",
        20 to "ಇಪ್ಪತ್ತು",
        29 to "ಇಪ್ಪತ್ತೊಂಬತ್ತು",
        45 to "ನಲವತ್ತೈದು",
        83 to "ಎಂಬತ್ತ್ಮೂರು",
        88 to "ಎಂಬತ್ತೆಂಟು",
        99 to "ತೊಂಬತ್ತೊಂಬತ್ತು",
        100 to "ನೂರು",
        101 to "ನೂರು ಒಂದು",
        110 to "ನೂರು ಹತ್ತು",
        180 to "ನೂರು ಎಂಬತ್ತು",
        250 to "ಇನ್ನೂರು ಐವತ್ತು",
        288 to "ಇನ್ನೂರು ಎಂಬತ್ತೆಂಟು",
        999 to "ಒಂಬೈನೂರು ತೊಂಬತ್ತೊಂಬತ್ತು",
        1000 to "ಒಂದು ಸಾವಿರ",
        1001 to "ಒಂದು ಸಾವಿರ ಒಂದು",
        1200 to "ಒಂದು ಸಾವಿರ ಇನ್ನೂರು",
        6000 to "ಆರು ಸಾವಿರ",
        9000 to "ಒಂಬತ್ತು ಸಾವಿರ",
        9999 to "ಒಂಬತ್ತು ಸಾವಿರ ಒಂಬೈನೂರು ತೊಂಬತ್ತೊಂಬತ್ತು",
        10000 to "ಹತ್ತು ಸಾವಿರ",
        15000 to "ಹದಿನೈದು ಸಾವಿರ",
        99999 to "ತೊಂಬತ್ತೊಂಬತ್ತು ಸಾವಿರ ಒಂಬೈನೂರು ತೊಂಬತ್ತೊಂಬತ್ತು",
        100000 to "ಒಂದು ಲಕ್ಷ",
        100001 to "ಒಂದು ಲಕ್ಷ ಒಂದು",
        250000 to "ಎರಡು ಲಕ್ಷ ಐವತ್ತು ಸಾವಿರ",
        1234567 to "ಹನ್ನೆರಡು ಲಕ್ಷ ಮೂವತ್ನಾಲ್ಕು ಸಾವಿರ ಐನೂರು ಅರವತ್ತೇಳು",
        9999999 to "ತೊಂಬತ್ತೊಂಬತ್ತು ಲಕ್ಷ ತೊಂಬತ್ತೊಂಬತ್ತು ಸಾವಿರ ಒಂಬೈನೂರು ತೊಂಬತ್ತೊಂಬತ್ತು",
        10000000 to "ಒಂದು ಕೋಟಿ",
        12345678 to "ಒಂದು ಕೋಟಿ ಇಪ್ಪತ್ತ್ಮೂರು ಲಕ್ಷ ನಲವತ್ತೈದು ಸಾವಿರ ಆರುನೂರು ಎಪ್ಪತ್ತೆಂಟು",
        123456789 to "ಹನ್ನೆರಡು ಕೋಟಿ ಮೂವತ್ನಾಲ್ಕು ಲಕ್ಷ ಐವತ್ತಾರು ಸಾವಿರ ಏಳುನೂರು ಎಂಬತ್ತೊಂಬತ್ತು",
        999999999 to "ತೊಂಬತ್ತೊಂಬತ್ತು ಕೋಟಿ ತೊಂಬತ್ತೊಂಬತ್ತು ಲಕ್ಷ ತೊಂಬತ್ತೊಂಬತ್ತು ಸಾವಿರ ಒಂಬೈನೂರು ತೊಂಬತ್ತೊಂಬತ್ತು",
        100000000 to "ಹತ್ತು ಕೋಟಿ",
        347712783 to "ಮೂವತ್ನಾಲ್ಕು ಕೋಟಿ ಎಪ್ಪತ್ತೇಳು ಲಕ್ಷ ಹನ್ನೆರಡು ಸಾವಿರ ಏಳುನೂರು ಎಂಬತ್ತ್ಮೂರು",
        161973070 to "ಹದಿನಾರು ಕೋಟಿ ಹತ್ತೊಂಬತ್ತು ಲಕ್ಷ ಎಪ್ಪತ್ತ್ಮೂರು ಸಾವಿರ ಎಪ್ಪತ್ತು",
        423938500 to "ನಲವತ್ತೆರಡು ಕೋಟಿ ಮೂವತ್ತೊಂಬತ್ತು ಲಕ್ಷ ಮೂವತ್ತೆಂಟು ಸಾವಿರ ಐನೂರು",
        698935573 to "ಅರವತ್ತೊಂಬತ್ತು ಕೋಟಿ ಎಂಬತ್ತೊಂಬತ್ತು ಲಕ್ಷ ಮೂವತ್ತೈದು ಸಾವಿರ ಐನೂರು ಎಪ್ಪತ್ತ್ಮೂರು",
        51847157 to "ಐದು ಕೋಟಿ ಹದಿನೆಂಟು ಲಕ್ಷ ನಲವತ್ತೇಳು ಸಾವಿರ ನೂರು ಐವತ್ತೇಳು",
    )

    @Test
    fun cardinalsMatchComposedIndicNumToWords() {
        for ((n, words) in reference) {
            assertEquals("n=$n", words, KannadaTextNormalizer.verbalize(n.toString()))
        }
    }

    @Test
    fun kannadaAndAsciiDigitsBothBecomeWordsAttachedToClassifier() {
        assertEquals(
            "ಔಷಧಿಗಾಗಿ ಹನ್ನೆರಡು ಪೆಟ್ಟಿಗೆಗಳನ್ನು ತನ್ನಿ",
            KannadaTextNormalizer.normalize("ಔಷಧಿಗಾಗಿ ೧೨ ಪೆಟ್ಟಿಗೆಗಳನ್ನು ತನ್ನಿ")
        )
        assertEquals(
            "ಔಷಧಿಗಾಗಿ ಹನ್ನೆರಡು ಪೆಟ್ಟಿಗೆಗಳನ್ನು ತನ್ನಿ",
            KannadaTextNormalizer.normalize("ಔಷಧಿಗಾಗಿ 12 ಪೆಟ್ಟಿಗೆಗಳನ್ನು ತನ್ನಿ")
        )
        assertEquals("ಇನ್ನೂರು ಐವತ್ತು ಜನ", KannadaTextNormalizer.normalize("೨೫೦ ಜನ"))
    }

    @Test
    fun leadingZeroAndLongRunsAreReadDigitByDigit() {
        assertEquals("ಸೊನ್ನೆ ಒಂದು ಏಳು", KannadaTextNormalizer.normalize("೦೧೭"))
        assertEquals(
            "ಒಂದು ಎರಡು ಮೂರು ನಾಲ್ಕು ಐದು ಆರು ಏಳು ಎಂಟು ಒಂಬತ್ತು ಸೊನ್ನೆ",
            KannadaTextNormalizer.verbalize("1234567890")
        )
        assertEquals("ಸೊನ್ನೆ", KannadaTextNormalizer.normalize("೦"))
    }

    @Test
    fun textWithoutDigitsIsUnchangedApartFromNfc() {
        val s = "ನದಿ ನೀರು ವೇಗವಾಗಿ ಏರುತ್ತಿದೆ. ಸುರಕ್ಷಿತ ಸ್ಥಳಕ್ಕೆ ಹೋಗಿ."
        assertEquals(java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC), KannadaTextNormalizer.normalize(s))
    }
}
