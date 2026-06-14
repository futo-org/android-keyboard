package org.futo.inputmethod.latin.uix.settings

private fun List<String>.extendSwears() = toSet()

private val slurs = listOf(
    "faggot", "faggots", "faggot's", "faggy",
    "fag", "fags", "fag's",
    "skank", "skanks", "skank's",
    "nigga", "niggas", "nigga's",
    "nigger", "niggers", "nigger's",
    "chink", "chinks", "chink's",
    "kike", "kikes", "kike's",
    "negro", "negros", "negro's"
).extendSwears()

private val englishSpecificSlurs = listOf(
    "negro", "negros", "negro's"
).extendSwears()

private val swearWords = listOf(
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
    "shitty",
    "slut",
    "sluts",
    "Slutty",
    "tit",
    "vagina",
    "whore",
    "masturbate",
    "mofo",
    "nazi",
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
    "porn",
    "asdfbadwordasdf",
    "\uD83D\uDD95"
).extendSwears()

private val slurPrefixes = listOf("nigger", "nigga", "fagg")
private val englishSpecificSlurPrefixes = listOf("negro")
private val swearPrefixes = listOf("bitch", "fuck", "shit", "masturbat")

data class BadWordMode(
    val language: String,
    val blockSlurs: Boolean,
    val blockOffensive: Boolean
)

fun shouldBlockWord(mode: BadWordMode, word: String): Boolean {
    val word = word.lowercase()
    if(mode.blockSlurs || mode.blockOffensive) {
        if(word in slurs) return true
        if(slurPrefixes.any { word.startsWith(it) }) return true

        if(mode.language == "en") {
            if(word in englishSpecificSlurs) return true
            if(englishSpecificSlurPrefixes.any { word.startsWith(it) }) return true
        }
    }

    if(mode.blockOffensive) {
        if(word in swearWords) return true
        if(swearPrefixes.any { word.startsWith(it) }) return true
    }

    return false
}