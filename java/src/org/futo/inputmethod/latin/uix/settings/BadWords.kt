package org.futo.inputmethod.latin.uix.settings

val badWords = listOf(
    "anus",
    "ass",
    "assface",
    "asshole",
    "assholes",
    "bitch",
    "bitches",
    "butthole",
    "clit",
    "cock",
    "cockhead",
    "cocks",
    "cocksucker",
    "cock-sucker",
    "crap",
    "cum",
    "cunt",
    "cunts",
    "dick",
    "dildo",
    "dildos",
    "fag",
    "faggot",
    "fags",
    "fuck",
    "fucked",
    "fucker",
    "fuckin",
    "fucking",
    "fucks",
    "jizz",
    "mother-fucker",
    "motherfucker",
    "orgasm",
    "penis",
    "pussy",
    "rectum",
    "retard",
    "semen",
    "sex",
    "shit",
    "shit*",
    "shitty",
    "skank",
    "slut",
    "sluts",
    "Slutty",
    "tit",
    "vagina",
    "whore",
    "masturbate",
    "masterbat*",
    "mofo",
    "nazi",
    "nigga",
    "nigger",
    "pussy",
    "scrotum",
    "slut",
    "smut",
    "tits",
    "boobs",
    "testicle",
    "jackoff",
    "wank",
    "whore",
    "bitch*",
    "porn",
    "asdfbadwordasdf",
    "\uD83D\uDD95"
).flatMap { listOf(it, it.lowercase(), it.uppercase(), it.lowercase().capitalize()) }.toSet()

fun isFiltered(word: String): Boolean {
    if(word in badWords) {
        return true
    }

    if(word.lowercase() in badWords) {
        return true
    }

    return badWords.any { it.endsWith("*") && word.lowercase().startsWith(it.lowercase().substring(0, it.length - 1)) }
}