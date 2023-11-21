package org.futo.inputmethod.latin.xlm

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.random.nextInt

class Vector2(val x: Float, val y: Float) {
    operator fun plus(other: Vector2): Vector2 {
        return Vector2(x + other.x, y + other.y)
    }

    operator fun minus(other: Vector2): Vector2 {
        return Vector2(x - other.x, y - other.y)
    }

    fun magnitudeSquared(): Float {
        return (x * x) + (y * y)
    }
}

fun randomNormal(mean: Float, standardDeviation: Float): Float {
    val u1 = Random.nextFloat()
    val u2 = Random.nextFloat()

    val randStdNormal = sqrt(-2.0 * ln(u1.toDouble())) * cos(2.0 * PI * u2.toDouble())

    return (mean + standardDeviation * randStdNormal).toFloat()
}

private interface KeyboardLayout {
    val tapSize: Vector2

    fun getKeyPosition(character: Char): Vector2?
    fun getClosestKey(position: Vector2): Char
}

const val SHIFT_KEY = '\u000f'
const val BACKSPACE_KEY = '\u0008'
object QWERTYKeyboardLayout : KeyboardLayout {
    override val tapSize: Vector2 = Vector2(80.0f, 80.0f)

    // Rough QWERTY positions based on eyeballing it
    private val KEYBOARD_KEYS = hashMapOf(
        'q' to Vector2(75.0f, 106.0f),
        'w' to Vector2(214.0f, 106.0f),
        'e' to Vector2(363.0f, 106.0f),
        'r' to Vector2(499.0f, 106.0f),
        't' to Vector2(645.0f, 106.0f),
        'y' to Vector2(789.0f, 106.0f),
        'u' to Vector2(928.0f, 106.0f),
        'i' to Vector2(1073.0f, 106.0f),
        'o' to Vector2(1216.0f, 106.0f),
        'p' to Vector2(1357.0f, 106.0f),
        'a' to Vector2(150.0f, 312.0f),
        's' to Vector2(291.0f, 312.0f),
        'd' to Vector2(434.0f, 312.0f),
        'f' to Vector2(574.0f, 312.0f),
        'g' to Vector2(717.0f, 312.0f),
        'h' to Vector2(859.0f, 312.0f),
        'j' to Vector2(1005.0f, 312.0f),
        'k' to Vector2(1140.0f, 312.0f),
        'l' to Vector2(1288.0f, 312.0f),
        SHIFT_KEY to Vector2(113.0f, 515.0f),
        'z' to Vector2(287.0f, 515.0f),
        'x' to Vector2(434.0f, 515.0f),
        'c' to Vector2(576.0f, 515.0f),
        'v' to Vector2(718.0f, 515.0f),
        'b' to Vector2(860.0f, 515.0f),
        'n' to Vector2(1003.0f, 515.0f),
        'm' to Vector2(1145.0f, 515.0f),
        BACKSPACE_KEY to Vector2(1329.0f, 515.0f),
    )

    override fun getKeyPosition(character: Char): Vector2? {
        return KEYBOARD_KEYS[character]
    }

    override fun getClosestKey(position: Vector2): Char {
        return KEYBOARD_KEYS.minBy {
            (it.value - position).magnitudeSquared()
        }.key
    }

}

private object WordMisspelling {
    fun substituteKeyboardLetters(layout: KeyboardLayout, word: String, temperature: Float = 0.6f): String {
        val keys = word.lowercase().toList()
        val newKeys = mutableListOf<Char>()

        keys.forEach { char ->
            val position = layout.getKeyPosition(char) ?: return@forEach

            val newPosition = Vector2(
                randomNormal(position.x, temperature * layout.tapSize.x),
                randomNormal(position.y, temperature * layout.tapSize.y)
            )

            val newKey = layout.getClosestKey(newPosition)

            if(newKey == SHIFT_KEY) {
                // next char should be uppercased, but it currently doesn't matter
            }else if(newKey == BACKSPACE_KEY) {
                if(newKeys.size > 0) newKeys.removeLast()
            }else {
                newKeys.add(newKey)
            }
        }

        return String(newKeys.toCharArray())
    }

    fun transposeRandomLetters(word: String): String {
        if (word.length < 2) return word

        val charArray = word.toCharArray()
        val index1 = Random.nextInt(word.length)
        var index2: Int

        do {
            index2 = Random.nextInt(word.length)
        } while (index1 == index2)

        val temp = charArray[index1]
        charArray[index1] = charArray[index2]
        charArray[index2] = temp

        return String(charArray)
    }

    fun transposeAdjacentLetters(word: String): String {
        if (word.length < 2) return word

        val charArray = word.toCharArray()
        val index = Random.nextInt(word.length - 1)

        val temp = charArray[index]
        charArray[index] = charArray[index + 1]
        charArray[index + 1] = temp

        return String(charArray)
    }

    fun deleteRandomCharacter(word: String): String {
        if (word.isEmpty()) return word

        val index = Random.nextInt(word.length)
        return word.removeRange(index, index + 1)
    }

    fun misspellWord(word: String, correctness: Float = 0.8f): String {
        var misspelledWord = word.trim().lowercase().replace("'", "")

        val getRand = { Random.nextFloat().pow(correctness) }

        if(getRand() > 0.5) {
            misspelledWord = transposeRandomLetters(misspelledWord)
        }

        if(getRand() > 0.5) {
            misspelledWord = transposeAdjacentLetters(misspelledWord)
        }

        if(getRand() > 0.5) {
            misspelledWord = deleteRandomCharacter(misspelledWord)
        }

        // Substitute the word's characters with nearby ones randomly
        misspelledWord = substituteKeyboardLetters(QWERTYKeyboardLayout, misspelledWord, temperature = 1.0f * getRand())

        // Trim word randomly as if the user hasn't finished writing the word yet
        // This helps the model learn to complete partially-written words
        if((getRand() > 0.33) && (misspelledWord.length >= 2)) {
            val newLength = ceil((1.0 - (getRand() * getRand())) * misspelledWord.length).toInt().coerceAtLeast(2)
            misspelledWord = misspelledWord.substring(0, newLength.coerceAtMost(misspelledWord.length))
        }

        return misspelledWord
    }
}

const val TOKENIZER_BEGIN_USER_INPUT = "<XBU>"
const val TOKENIZER_BEGIN_CORRECTION = "<XBC>"
const val TOKENIZER_END_CORRECTION   = "<XEC>"
private val TOKENIZER_LETTER_MAPPING = hashMapOf(
    'a' to "<CHAR_A>",
    'b' to "<CHAR_B>",
    'c' to "<CHAR_C>",
    'd' to "<CHAR_D>",
    'e' to "<CHAR_E>",
    'f' to "<CHAR_F>",
    'g' to "<CHAR_G>",
    'h' to "<CHAR_H>",
    'i' to "<CHAR_I>",
    'j' to "<CHAR_J>",
    'k' to "<CHAR_K>",
    'l' to "<CHAR_L>",
    'm' to "<CHAR_M>",
    'n' to "<CHAR_N>",
    'o' to "<CHAR_O>",
    'p' to "<CHAR_P>",
    'q' to "<CHAR_Q>",
    'r' to "<CHAR_R>",
    's' to "<CHAR_S>",
    't' to "<CHAR_T>",
    'u' to "<CHAR_U>",
    'v' to "<CHAR_V>",
    'w' to "<CHAR_W>",
    'x' to "<CHAR_X>",
    'y' to "<CHAR_Y>",
    'z' to "<CHAR_Z>",
)

private fun tokenizerFormatUserInput(misspelledWord: String): String {
    return TOKENIZER_BEGIN_USER_INPUT + misspelledWord.mapNotNull { TOKENIZER_LETTER_MAPPING[it] }.joinToString(separator = "") + TOKENIZER_BEGIN_CORRECTION
}

object TrainingDataGenerator {
    fun formatWordMisspelling(misspelled: String, truth: String): String {
        if(misspelled.filter { it in TOKENIZER_LETTER_MAPPING }.isEmpty() || truth.isBlank()) return ""

        // Space after word is required for the tokenizer
        return tokenizerFormatUserInput(misspelled.trim()) + truth.trim() + " " + TOKENIZER_END_CORRECTION
    }
    fun wordMisspelling(word: String, correctness: Float = 0.8f): String {
        if(word.isBlank()) return ""
        
        val misspelled = WordMisspelling.misspellWord(word, correctness)
        return formatWordMisspelling(misspelled, word)
    }

    fun concatWordMisspelling(context: String, word: String, correctness: Float = 0.8f): String {
        val misspelledFormatted = wordMisspelling(word, correctness)
        if(misspelledFormatted.isBlank()) return ""

        return context.trim() + " " + misspelledFormatted
    }
    fun concatFormatWordMisspelling(context: String, misspelled: String, truth: String): String {
        val misspelledFormatted = formatWordMisspelling(misspelled, truth)
        if(misspelledFormatted.isBlank()) return ""

        return context.trim() + " " + misspelledFormatted
    }


    private val permittedCharacters = "abcdefghijklmnopqrstuvwxyz'-".toHashSet()
    fun suitableToMisspell(word: String): Boolean {
        return permittedCharacters.containsAll(word.lowercase().toList())
    }
    fun randomlyMisspellWords(text: String, proportion: Float = 0.333f, correctness: Float = 0.8f): String {
        val words = text.split(" ").toMutableList()
        val wordsToMisspell = mutableListOf<Int>()

        for(i in 0 until (words.size * proportion).toInt()) {
            val remainingIndices = words.indices.toSet().subtract(wordsToMisspell.toSet()).toList()
            if(remainingIndices.isEmpty()) break;

            val wordToMisspell = remainingIndices[Random.nextInt(remainingIndices.indices)]

            if(suitableToMisspell(words[wordToMisspell])) {
                wordsToMisspell.add(wordToMisspell)
            }
        }

        wordsToMisspell.toSet().forEach { i ->
            val misspelling = wordMisspelling(words[i], correctness)
            if(misspelling.isNotBlank()) {
                words[i] = misspelling
            }
        }

        return words.joinToString(separator=" ").trim()
            .replace("  ", " ")
            .replace("  ", " ")
            // Do not put spaces after these tokens, as it messes up tokenization
            .replace("$TOKENIZER_BEGIN_CORRECTION ", TOKENIZER_BEGIN_CORRECTION)
            .replace("$TOKENIZER_END_CORRECTION ", TOKENIZER_END_CORRECTION)
    }
}