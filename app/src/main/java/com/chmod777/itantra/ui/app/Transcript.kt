package com.chmod777.itantra.ui.app

/**
 * The one place that decides a take contained no speech.
 *
 * Both capture paths ask this, because the two engines say "I heard nothing" in different ways
 * and neither of them says it by failing:
 *
 *  - IndicConformer (hi, gu, mr, kn, ml, ta, te, or, bn) returns an empty string, and so does the
 *    recognizer manager when a language has no model on the phone at all.
 *  - Whisper (en) returns one of its annotation tokens instead: `[BLANK_AUDIO]` on silence, and
 *    `[MUSIC]`, `[SIDE CONVERSATION]` and others on sounds it will not transcribe. All of them
 *    are a bracketed label and nothing else, which is the shape matched here rather than a list
 *    of the particular ones we have happened to see. Real speech never comes back as a single
 *    bracketed token, so the test is safe in the direction that matters: it can only ever
 *    discard a token, never a sentence.
 *
 * Getting this wrong in the other direction is what the user hit: `[BLANK_AUDIO]` was reaching
 * Logs as if it were a message somebody had spoken.
 */
internal fun heardNothing(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return true
    return ANNOTATION_ONLY.matches(trimmed)
}

/** One or more `[...]` / `(...)` labels, separated by nothing but spaces or punctuation. */
private val ANNOTATION_ONLY = Regex("""^(?:[\[(][^\[\]()]*[])][\s.,!?;:-]*)+$""")
