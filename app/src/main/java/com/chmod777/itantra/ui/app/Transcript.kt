package com.chmod777.itantra.ui.app

/**
 * The one place that decides a take contained no speech.
 *
 * Both capture paths ask this, because the two engines say "I heard nothing" in different ways
 * and neither of them says it by failing:
 *
 *  - IndicConformer (hi, gu, mr, kn, ml, ta, te, or, bn) returns an empty string, and so does the
 *    recognizer manager when a language has no model on the phone at all.
 *  - Whisper (en) returns one of its annotation tokens instead: `[BLANK_AUDIO]`, `[ Silence ]`,
 *    `[Music]`, `[SIDE CONVERSATION]` and others, for anything it will not transcribe as speech
 *    — an empty room, but equally a television playing in the next one. Either way nobody has
 *    said anything to send, so both belong here. All of them are a bracketed label and nothing
 *    else, which is the shape matched rather than a list of the particular ones we have happened
 *    to see; four different ones turned up in a single afternoon of testing. Speech mixed with
 *    noise still comes back with words in it, so the test is safe in the direction that matters:
 *    it can only discard labels, never a sentence.
 *
 * A long take of it repeats the token until the decoder runs out of room, which leaves the last
 * one unfinished: 17 seconds produced `[Music] [Music] … [Music`, 278 characters of it, and an
 * earlier version of this function passed that through to the editor because of the one missing
 * bracket. The final label may therefore be unclosed.
 *
 * Getting this wrong in the other direction is what the user hit: `[BLANK_AUDIO]` and
 * `[ Silence ]` were reaching Logs as if they were messages somebody had spoken.
 */
internal fun heardNothing(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return true
    if (!ANNOTATION_ONLY.matches(trimmed)) return false
    // A label is a label, not a sentence in brackets. Whisper does sometimes put speech it is
    // unsure of inside parentheses, and "(we are at the north gate, bring water)" must not be
    // thrown away for its punctuation. Every placeholder seen on the phone is one or two words:
    // [BLANK_AUDIO], [ Silence ], [Music], [SIDE CONVERSATION], [S], (footsteps).
    return BRACKETED.findAll(trimmed).all { it.groupValues[1].trim().split(WHITESPACE).size <= MAX_LABEL_WORDS }
}

private const val MAX_LABEL_WORDS = 3

/**
 * Nothing but `[...]` / `(...)` labels, separated by spaces or punctuation, where the last one
 * may have been cut off before its closing bracket.
 */
private val ANNOTATION_ONLY =
    Regex("""^(?:[\[(][^\[\]()]*[])][\s.,!?;:-]*)*(?:[\[(][^\[\]()]*)?$""")

/** The inside of each label, closing bracket optional for the one the decoder cut short. */
private val BRACKETED = Regex("""[\[(]([^\[\]()]*)[])]?""")

private val WHITESPACE = Regex("""\s+""")
