package com.chmod777.itantra

import org.junit.Assert.assertEquals
import org.junit.Test

class MarathiTextNormalizerTest {

    // Expected words generated from AI4Bharat indic-numtowords 1.1.0 (mar.cardinal.convert), with the
    // hundred-word always "एकशे" (not the alternate "शंभर") and the thousand scale word corrected to
    // "हजार" (the reference's "हज़ार" is an internal inconsistency in that same data file) -- see
    // MarathiTextNormalizer's doc comment for the full verification (3,745 sampled values).
    private val reference = listOf(
        0 to "शून्य",
        1 to "एक",
        7 to "सात",
        10 to "दहा",
        12 to "बारा",
        19 to "एकोणीस",
        20 to "वीस",
        29 to "एकोणतीस",
        45 to "पंचेचाळीस",
        88 to "अठ्ठ्याऐंशी",
        99 to "नव्याण्णव",
        100 to "एकशे",
        101 to "एकशे एक",
        110 to "एकशे दहा",
        250 to "दोनशे पन्नास",
        300 to "तीनशे",
        999 to "नऊशे नव्याण्णव",
        1000 to "एक हजार",
        1001 to "एक हजार एक",
        1200 to "एक हजार दोनशे",
        6000 to "सहा हजार",
        9000 to "नऊ हजार",
        9999 to "नऊ हजार नऊशे नव्याण्णव",
        10000 to "दहा हजार",
        15000 to "पंधरा हजार",
        99999 to "नव्याण्णव हजार नऊशे नव्याण्णव",
        100000 to "एक लाख",
        100001 to "एक लाख एक",
        250000 to "दोन लाख पन्नास हजार",
        1234567 to "बारा लाख चौतीस हजार पाचशे सदुसष्ट",
        9999999 to "नव्याण्णव लाख नव्याण्णव हजार नऊशे नव्याण्णव",
        10000000 to "एक कोटी",
        12345678 to "एक कोटी तेवीस लाख पंचेचाळीस हजार सहाशे अठ्ठ्याहत्तर",
        123456789 to "बारा कोटी चौतीस लाख छप्पन्न हजार सातशे एकोणनव्वद",
        999999999 to "नव्याण्णव कोटी नव्याण्णव लाख नव्याण्णव हजार नऊशे नव्याण्णव",
        100000000 to "दहा कोटी",
        347713782 to "चौतीस कोटी सत्त्याहत्तर लाख तेरा हजार सातशे ब्याऐंशी",
        161974069 to "सोळा कोटी एकोणीस लाख चौर्‍याहत्तर हजार एकोणसत्तर",
        423939499 to "बेचाळीस कोटी एकोणचाळीस लाख एकोणचाळीस हजार चारशे नव्याण्णव",
        698936572 to "एकोणसत्तर कोटी एकोणनव्वद लाख छत्तीस हजार पाचशे बाहत्तर",
        51848156 to "पाच कोटी अठरा लाख अठ्ठेचाळीस हजार एकशे छप्पन्न",
        77778868 to "सात कोटी सत्त्याहत्तर लाख अठ्ठ्याहत्तर हजार आठशे अडुसष्ट",
        881837553 to "अठ्ठ्याऐंशी कोटी अठरा लाख सदतीस हजार पाचशे त्रेपन्न",
        575399922 to "सत्तावन्न कोटी त्रेपन्न लाख नव्याण्णव हजार नऊशे बावीस",
        101072364 to "दहा कोटी दहा लाख बाहत्तर हजार तीनशे चौसष्ट",
        392656486 to "एकोणचाळीस कोटी सव्वीस लाख छप्पन्न हजार चारशे शहाऐंशी",
        625764863 to "बासष्ट कोटी सत्तावन्न लाख चौसष्ट हजार आठशे त्रेसष्ट",
        62276869 to "सहा कोटी बावीस लाख शाहत्तर हजार आठशे एकोणसत्तर",
        976788301 to "सत्त्याण्णव कोटी सदुसष्ट लाख अठ्ठ्याऐंशी हजार तीनशे एक",
        544855973 to "चौपन्न कोटी अठ्ठेचाळीस लाख पंचावन्न हजार नऊशे त्र्याहत्तर",
        230531419 to "तेवीस कोटी पाच लाख एकतीस हजार चारशे एकोणीस",
        40261662 to "चार कोटी दोन लाख एकसष्ट हजार सहाशे बासष्ट",
        92286142 to "नऊ कोटी बावीस लाख शहाऐंशी हजार एकशे बेचाळीस",
        465624510 to "सेहेचाळीस कोटी छप्पन्न लाख चोवीस हजार पाचशे दहा",
        449009934 to "चव्वेचाळीस कोटी नव्वद लाख नऊ हजार नऊशे चौतीस",
        75007691 to "सात कोटी पन्नास लाख सात हजार सहाशे एक्याण्णव",
    )

    @Test
    fun cardinalsMatchIndicNumToWords() {
        for ((n, words) in reference) {
            assertEquals("n=$n", words, MarathiTextNormalizer.verbalize(n.toString()))
        }
    }

    @Test
    fun devanagariAndAsciiDigitsBothBecomeWordsAttachedToClassifier() {
        assertEquals("बारा वाजता", MarathiTextNormalizer.normalize("१२ वाजता"))
        assertEquals("बारा वाजता", MarathiTextNormalizer.normalize("12 वाजता"))
        assertEquals("दोनशे पन्नास विद्यार्थी", MarathiTextNormalizer.normalize("२५० विद्यार्थी"))
    }

    @Test
    fun oovAsciiDigitsAreVerbalisedNotDropped() {
        // 3, 5 and 8 are individually absent from the MMS Marathi vocabulary (desktop probe
        // confirmed "Skip unknown character" for each); verbalising removes the digit before it
        // ever reaches the model, for the whole run, not just the missing ones.
        assertEquals("तीन वाजता", MarathiTextNormalizer.normalize("3 वाजता"))
        assertEquals("पाच वाजता", MarathiTextNormalizer.normalize("5 वाजता"))
        assertEquals("एकशे आठ या क्रमांकावर", MarathiTextNormalizer.normalize("108 या क्रमांकावर"))
    }

    @Test
    fun leadingZeroAndLongRunsAreReadDigitByDigit() {
        assertEquals("शून्य एक सात", MarathiTextNormalizer.normalize("०१७"))
        assertEquals("एक दोन तीन चार पाच सहा सात आठ नऊ शून्य", MarathiTextNormalizer.verbalize("1234567890"))
        assertEquals("शून्य", MarathiTextNormalizer.normalize("०"))
    }

    @Test
    fun textWithoutDigitsIsUnchanged() {
        val s = "कृपया मला थोडं पाणी द्या"
        assertEquals(s, MarathiTextNormalizer.normalize(s))
    }
}
