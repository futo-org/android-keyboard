package org.futo.voiceinput.shared.whisper

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.shared.types.getSymbolTokens
import org.futo.voiceinput.shared.util.loadTextFromFile
import org.futo.voiceinput.shared.util.loadTextFromResource
import java.io.File

class Tokenizer(tokenJson: String) {
    private val idToToken: Array<String?>
    private val tokenToId: HashMap<String, Int> = hashMapOf()

    val symbolTokens: IntArray

    val decodeStartToken: Int
    val decodeEndToken: Int
    val translateToken: Int
    val noCaptionsToken: Int
    val noTimestampsToken: Int
    val transcribeToken: Int

    private val startOfLanguages: Int
    private val endOfLanguages: Int

    init {
        val data = Json.parseToJsonElement(tokenJson)
        idToToken = arrayOfNulls(65536)
        for (entry in data.jsonObject.entries) {
            val id = entry.value.jsonPrimitive.int
            val text = entry.key

            idToToken[id] = text
            tokenToId[text] = id
        }

        decodeStartToken = stringToToken("<|startoftranscript|>")!!
        decodeEndToken = stringToToken("<|endoftext|>")!!
        translateToken = stringToToken("<|translate|>")!!
        transcribeToken = stringToToken("<|transcribe|>")!!
        noCaptionsToken = stringToToken("<|nocaptions|>")!!
        noTimestampsToken = stringToToken("<|notimestamps|>")!!

        // This seems right for most models
        startOfLanguages = stringToToken("<|en|>")!!
        endOfLanguages = stringToToken("<|su|>")!!

        symbolTokens = getSymbolTokens(tokenToId)
    }

    constructor(context: Context, resourceId: Int) : this(loadTextFromResource(context, resourceId))
    constructor(file: File) : this(loadTextFromFile(file))

    fun tokenToString(token: Int): String? {
        return idToToken[token]
    }

    fun stringToToken(token: String): Int? {
        return tokenToId[token]
    }

    fun toLanguage(token: Int): Language? {
        if ((token < startOfLanguages) || (token > endOfLanguages)) return null

        val languageString = tokenToString(token)?.substring(2, 3)

        return languageString?.let { getLanguageFromWhisperString(it) }
    }

    fun generateBannedLanguageList(allowedLanguageSet: Set<Language>): IntArray {
        return (startOfLanguages..endOfLanguages).filter {
            !allowedLanguageSet.contains(toLanguage(it))
        }.toIntArray()
    }
}