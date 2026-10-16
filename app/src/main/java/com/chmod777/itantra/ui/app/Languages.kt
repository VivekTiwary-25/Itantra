package com.chmod777.itantra.ui.app

/**
 * The languages offered in the UI, with the script each one is actually read in.
 *
 * Same ten codes as `SUPPORTED_LANGUAGES` in LegacyModels.kt, which is what the speech engine
 * keys its models on; the native names are carried over from the demo build. Someone who
 * speaks Odia should be able to find their language without reading English first.
 */
data class UiLanguage(val code: String, val englishName: String, val nativeName: String?)

val UI_LANGUAGES = listOf(
    UiLanguage("en", "English", null),
    UiLanguage("hi", "Hindi", "हिन्दी"),
    UiLanguage("gu", "Gujarati", "ગુજરાતી"),
    UiLanguage("mr", "Marathi", "मराठी"),
    UiLanguage("kn", "Kannada", "ಕನ್ನಡ"),
    UiLanguage("ml", "Malayalam", "മലയാളം"),
    UiLanguage("ta", "Tamil", "தமிழ்"),
    UiLanguage("te", "Telugu", "తెలుగు"),
    UiLanguage("or", "Odia", "ଓଡ଼ିଆ"),
    UiLanguage("bn", "Bengali", "বাংলা"),
)

fun uiLanguage(code: String): UiLanguage =
    UI_LANGUAGES.firstOrNull { it.code == code } ?: UI_LANGUAGES.first()
