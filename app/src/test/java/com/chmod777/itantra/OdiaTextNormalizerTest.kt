package com.chmod777.itantra

import org.junit.Assert.assertEquals
import org.junit.Test

class OdiaTextNormalizerTest {

    // Expected words generated from AI4Bharat indic-numtowords 1.1.0
    // (tools/vendor/indic_numtowords/ori/cardinal.convert, first variant).
    private val reference = listOf(
        0 to "ଶୁନ୍ୟ",
        1 to "ଏକ",
        7 to "ସାତ",
        10 to "ଦଶ",
        12 to "ବାର",
        19 to "ଊଣେଇଶ",
        20 to "କୋଡିଏ",
        29 to "ଅଣତିରିଶ",
        45 to "ପଇଁଚାଳିଶ",
        88 to "ଅଠାଅଶୀ",
        99 to "ଅନେଶତ",
        100 to "ଶହେ",
        101 to "ଶହେ ଏକ",
        110 to "ଶହେ ଦଶ",
        250 to "ଦୁଇ ଶହ ପଚାଶ",
        999 to "ନଅ ଶହ ଅନେଶତ",
        1000 to "ଏକ ହଜାର",
        1001 to "ଏକ ହଜାର ଏକ",
        1200 to "ଏକ ହଜାର ଦୁଇ ଶହ",
        6000 to "ଛଅ ହଜାର",
        9000 to "ନଅ ହଜାର",
        9999 to "ନଅ ହଜାର ନଅ ଶହ ଅନେଶତ",
        10000 to "ଦଶ ହଜାର",
        15000 to "ପନ୍ଦର ହଜାର",
        99999 to "ଅନେଶତ ହଜାର ନଅ ଶହ ଅନେଶତ",
        100000 to "ଏକ ଲକ୍ଷ",
        100001 to "ଏକ ଲକ୍ଷ ଏକ",
        250000 to "ଦୁଇ ଲକ୍ଷ ପଚାଶ ହଜାର",
        1234567 to "ବାର ଲକ୍ଷ ଚଉତିରିଶ ହଜାର ପାଞ୍ଚ ଶହ ସତଷଠି",
        9999999 to "ଅନେଶତ ଲକ୍ଷ ଅନେଶତ ହଜାର ନଅ ଶହ ଅନେଶତ",
        10000000 to "ଏକ କୋଟି",
        12345678 to "ଏକ କୋଟି ତେଇଶ ଲକ୍ଷ ପଇଁଚାଳିଶ ହଜାର ଛଅ ଶହ ଅଠସ୍ତରି",
        123456789 to "ବାର କୋଟି ଚଉତିରିଶ ଲକ୍ଷ ଛପନ ହଜାର ସାତ ଶହ ଅଣାନବେ",
        999999999 to "ଅନେଶତ କୋଟି ଅନେଶତ ଲକ୍ଷ ଅନେଶତ ହଜାର ନଅ ଶହ ଅନେଶତ",
        100000000 to "ଦଶ କୋଟି",
        696579303 to "ଅଣସ୍ତରି କୋଟି ପଞ୍ଚଷଠି ଲକ୍ଷ ଅଣାଅଶୀ ହଜାର ତିନି ଶହ ତିନି",
        129540831 to "ବାର କୋଟି ପଞ୍ଚାନବେ ଲକ୍ଷ ଚାଳିଶ ହଜାର ଆଠ ଶହ ଏକତିରିଶ",
        36855092 to "ତିନି କୋଟି ଅଠଷଠି ଲକ୍ଷ ପଞ୍ଚାବନ ହଜାର ବୟାନବେ",
        806233790 to "ଅଶୀ କୋଟି ବାଷଠି ଲକ୍ଷ ତେତିଶ ହଜାର ସାତ ଶହ ନବେ",
        305310485 to "ତିରିଶ କୋଟି ତେପନ ଲକ୍ଷ ଦଶ ହଜାର ଚାରି ଶହ ପଞ୍ଚାଅଶୀ",
        272950628 to "ସତାଇଶ କୋଟି ଅଣତିରିଶ ଲକ୍ଷ ପଚାଶ ହଜାର ଛଅ ଶହ ଅଠାଇଶ",
        249670711 to "ଚବିଶ କୋଟି ଛୟାନବେ ଲକ୍ଷ ସତୂରି ହଜାର ସାତ ଶହ ଏଗାର",
        159827706 to "ପନ୍ଦର କୋଟି ଅଠାନବେ ଲକ୍ଷ ସତାଇଶ ହଜାର ସାତ ଶହ ଛଅ",
        800779946 to "ଅଶୀ କୋଟି ସାତ ଲକ୍ଷ ଅଣାଅଶୀ ହଜାର ନଅ ଶହ ଛୟାଳିଶ",
        120053353 to "ବାର କୋଟି ତେପନ ହଜାର ତିନି ଶହ ତେପନ",
        736600539 to "ତେସ୍ତରି କୋଟି ଛଅଷଠି ଲକ୍ଷ ପାଞ୍ଚ ଶହ ଅଣଚାଳିଶ",
        805285932 to "ଅଶୀ କୋଟି ବାଉନ ଲକ୍ଷ ପଞ୍ଚାଅଶୀ ହଜାର ନଅ ଶହ ବତିଶ",
        967970516 to "ଛୟାନବେ କୋଟି ଅଣାଅଶୀ ଲକ୍ଷ ସତୂରି ହଜାର ପାଞ୍ଚ ଶହ ଷୋହଳ",
        595582861 to "ଅଣଷଠି କୋଟି ପଞ୍ଚାବନ ଲକ୍ଷ ବୟାଅଶୀ ହଜାର ଆଠ ଶହ ଏକଷଠି",
        103349856 to "ଦଶ କୋଟି ତେତିଶ ଲକ୍ଷ ଅଣଚାଶ ହଜାର ଆଠ ଶହ ଛପନ",
        644036506 to "ଚଉଷଠି କୋଟି ଚାଳିଶ ଲକ୍ଷ ଛତିଶ ହଜାର ପାଞ୍ଚ ଶହ ଛଅ",
        463035110 to "ଛୟାଳିଶ କୋଟି ତିରିଶ ଲକ୍ଷ ପଞ୍ଚତିରିଶ ହଜାର ଶହେ ଦଶ",
        44126396 to "ଚାରି କୋଟି ଏକଚାଳିଶ ଲକ୍ଷ ଛବିଶ ହଜାର ତିନି ଶହ ଛୟାନବେ",
        41994523 to "ଚାରି କୋଟି ଊଣେଇଶ ଲକ୍ଷ ଚଉରାନବେ ହଜାର ପାଞ୍ଚ ଶହ ତେଇଶ",
        110604502 to "ଏଗାର କୋଟି ଛଅ ଲକ୍ଷ ଚାରି ହଜାର ପାଞ୍ଚ ଶହ ଦୁଇ",
    )

    @Test
    fun cardinalsMatchIndicNumToWords() {
        for ((n, words) in reference) {
            assertEquals("n=$n", words, OdiaTextNormalizer.verbalize(n.toString()))
        }
    }

    @Test
    fun odiaAndAsciiDigitsBothBecomeWordsAttachedToClassifier() {
        assertEquals("ଔଷଧ ପାଇଁ ବାରଟି ବାକ୍ସ ଆଣନ୍ତୁ।", OdiaTextNormalizer.normalize("ଔଷଧ ପାଇଁ ୧୨ଟି ବାକ୍ସ ଆଣନ୍ତୁ।"))
        assertEquals("ଔଷଧ ପାଇଁ ବାରଟି ବାକ୍ସ ଆଣନ୍ତୁ।", OdiaTextNormalizer.normalize("ଔଷଧ ପାଇଁ 12ଟି ବାକ୍ସ ଆଣନ୍ତୁ।"))
        assertEquals("ଦୁଇ ଶହ ପଚାଶ ଜଣ", OdiaTextNormalizer.normalize("୨୫୦ ଜଣ"))
    }

    @Test
    fun leadingZeroAndLongRunsAreReadDigitByDigit() {
        assertEquals("ଶୁନ୍ୟ ଏକ ସାତ", OdiaTextNormalizer.normalize("୦୧୭"))
        assertEquals("ଏକ ଦୁଇ ତିନି ଚାରି ପାଞ୍ଚ ଛଅ ସାତ ଆଠ ନଅ ଶୁନ୍ୟ", OdiaTextNormalizer.verbalize("1234567890"))
        assertEquals("ଶୁନ୍ୟ", OdiaTextNormalizer.normalize("୦"))
    }

    @Test
    fun precomposedNuktaLettersAreDecomposed() {
        // U+0B5C/U+0B5D (precomposed DDDA/RHA, NOT in the MMS vocabulary) must become
        // base letter U+0B21/U+0B22 + nukta U+0B3C (both in the vocabulary).
        val precomposed = "ଡ଼ଢ଼"
        val decomposed = "ଡ଼ଢ଼"
        assertEquals(decomposed, OdiaTextNormalizer.normalize(precomposed))
    }

    @Test
    fun textWithoutDigitsIsUnchangedApartFromNfc() {
        val s = "ନଦୀର ପାଣି ଶୀଘ୍ର ବଢ଼ୁଛି। ସୁରକ୍ଷିତ ସ୍ଥାନକୁ ଯାଆନ୍ତୁ।"
        assertEquals(java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC), OdiaTextNormalizer.normalize(s))
    }
}
