package org.futo.voiceinput.shared.ml

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException

private fun loadTextFromResource(context: Context, resourceId: Int): String {
    val resources = context.resources
    try {
        val input = resources.openRawResource(resourceId)

        return input.bufferedReader().readText()
    } catch (e: IOException) {
        throw RuntimeException(e)
    }
}

private fun loadTextFromFile(file: File): String {
    return file.readText()
}


class WhisperTokenizer(tokenJson: String) {
    companion object {
        private var BytesEncoder: Array<Char> = arrayOf('Ā','ā','Ă','ă','Ą','ą','Ć','ć','Ĉ','ĉ','Ċ','ċ','Č','č','Ď','ď','Đ','đ','Ē','ē','Ĕ','ĕ','Ė','ė','Ę','ę','Ě','ě','Ĝ','ĝ','Ğ','ğ','Ġ','!','"','#','$','%','&','\'','(',')','*','+',',','-','.','/','0','1','2','3','4','5','6','7','8','9',':',';','<','=','>','?','@','A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z','[','\\',']','^','_','`','a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z','{','|','}','~','ġ','Ģ','ģ','Ĥ','ĥ','Ħ','ħ','Ĩ','ĩ','Ī','ī','Ĭ','ĭ','Į','į','İ','ı','Ĳ','ĳ','Ĵ','ĵ','Ķ','ķ','ĸ','Ĺ','ĺ','Ļ','ļ','Ľ','ľ','Ŀ','ŀ','Ł','ł','¡','¢','£','¤','¥','¦','§','¨','©','ª','«','¬','Ń','®','¯','°','±','²','³','´','µ','¶','·','¸','¹','º','»','¼','½','¾','¿','À','Á','Â','Ã','Ä','Å','Æ','Ç','È','É','Ê','Ë','Ì','Í','Î','Ï','Ð','Ñ','Ò','Ó','Ô','Õ','Ö','×','Ø','Ù','Ú','Û','Ü','Ý','Þ','ß','à','á','â','ã','ä','å','æ','ç','è','é','ê','ë','ì','í','î','ï','ð','ñ','ò','ó','ô','õ','ö','÷','ø','ù','ú','û','ü','ý','þ','ÿ')
        private var BytesDecoder: HashMap<Char, Byte> = hashMapOf()

        init {
            for((k, v) in BytesEncoder.withIndex()) {
                BytesDecoder[v] = k.toByte()
            }
        }
    }

    val idToToken: Array<String?>
    val tokenToId: HashMap<String, Int> = hashMapOf()

    init {
        val data = Json.parseToJsonElement(tokenJson)
        idToToken = arrayOfNulls(65536)
        for(entry in data.jsonObject.entries) {
            val id = entry.value.jsonPrimitive.int
            val text = entry.key

            idToToken[id] = text
            tokenToId[text] = id
        }
    }

    constructor(context: Context, resourceId: Int) : this(loadTextFromResource(context, resourceId))
    constructor(file: File) : this(loadTextFromFile(file))

    fun tokenToString(token: Int): String? {
        return idToToken[token]
    }

    fun stringToToken(token: String): Int? {
        return tokenToId[token]
    }

    fun makeStringUnicode(text: String): String {
        val charArray = text.toCharArray()

        val byteList = charArray.map {
            BytesDecoder[it] ?: throw IllegalArgumentException("Invalid character $it")
        }

        val byteArray = byteList.toByteArray()

        return byteArray.decodeToString(throwOnInvalidSequence = false)
    }
}