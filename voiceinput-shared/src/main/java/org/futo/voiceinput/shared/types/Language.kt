package org.futo.voiceinput.shared.types

enum class Language {
    English
    // TODO
}

fun Language.toWhisperString(): String {
    return when (this) {
        Language.English -> "en"
    }
}

fun getLanguageFromWhisperString(str: String): Language? {
    return when (str) {
        "en" -> Language.English
        else -> null
    }
}
