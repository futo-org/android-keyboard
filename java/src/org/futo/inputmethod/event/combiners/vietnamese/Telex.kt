package org.futo.inputmethod.event.combiners.vietnamese

object Telex {
    val TONES = mapOf(
        'f' to ToneMark.GRAVE,
        'j' to ToneMark.DOT,
        'r' to ToneMark.HOOK,
        's' to ToneMark.ACUTE,
        'x' to ToneMark.TILDE
    )

    /** These are the modifiers that should only be active if they come after the first vowel letter.
     * For example, `sao` should not output any tone marks, but `aso` should output `áo`.
     */
    val AFTER_VOWEL_MODIFIERS = setOf('f', 'j', 'r', 's', 'w', 'x')

    /** Convert a string that represents a Vietnamese syllable written in the Telex convention ([input])
     * to a syllable written in Vietnamese orthography.
     * Example: input = "vietej", output = "việt"
     */
    public fun telexToVietnamese(input: String): String {

        // STAGE 1: calculate modifierIndices, firstVowelIndex, startedFinal and lowercaseVowel
        // Example:
        //   Input: "vietej"
        //   Output:
        //     modifierIndices: { 'e': [2, 4], 'j': [5], the rest are empty lists }
        //     firstVowelIndex: 1
        //     startedFinal: true
        //     lowercaseVowel: "ie"
        val lowercaseInput = input.lowercase()
        var startedVowel = false
        var startedFinal = false
        var firstVowelIndex = -1

        val lowercaseVowel = StringBuilder()

        /** Map of 'modifier' characters that can add a diacritic or tone mark,
         * to lists of indices of occurrences of these characters
         */
        val modifierIndices: Map<Char, MutableList<Int>> = mapOf(
            'a' to mutableListOf(),
            'd' to mutableListOf(),
            'e' to mutableListOf(),
            'f' to mutableListOf(),
            'j' to mutableListOf(),
            'o' to mutableListOf(),
            'r' to mutableListOf(),
            's' to mutableListOf(),
            'w' to mutableListOf(),
            'x' to mutableListOf(),
        )

        for ((index, ch) in lowercaseInput.withIndex()) {

            if (!startedVowel) {
                if (Common.VOWELS.contains(ch)) {
                    // TODO: this code needs to be refined further
                    // if a syllable has a weird initial (like 'cl' in 'clown') that we are sure does not belong to Vietnamese,
                    // then stop the conversion process and just output the input as it is
                    // if (!(index in 0..3)) return input
                    // if (index in 2..3)
                    //     if (!INITIALS.contains(lowercaseInput.slice(0..<index)))
                    //         return input

                    firstVowelIndex = index
                    startedVowel = true
                }
            }

            if (startedVowel && !startedFinal && !AFTER_VOWEL_MODIFIERS.contains(ch)) {
                if (Common.CONSONANTS.contains(ch)) {
                    startedFinal = true
                } else {
                    lowercaseVowel.append(ch)
                }
            }

            if (AFTER_VOWEL_MODIFIERS.contains(ch)) {
                if (startedVowel) modifierIndices[ch]!!.add(index)
            } else if (modifierIndices.containsKey(ch)) {
                modifierIndices[ch]!!.add(index)
            }
        }


        // STAGE 1.5: apply a correction to firstVowelIndex
        // If the input contains more than one 'd' before the vowel starts
        // (example: "ddi" > "đi", "dddi" > "ddi"), one of the characters will be deleted
        // and therefore the firstVowelIndex needs to be corrected to account for this
        if (modifierIndices['d']!!.size > 1 && modifierIndices['d']!!.last() < firstVowelIndex)
            firstVowelIndex--

        // apply correction to lowercaseVowel:
        // "gi" (unless there is no other vowel letter) and "qu" should be considered consonants
        if (lowercaseVowel.length > 1 && (lowercaseInput.slice(0..<2) == "gi" || lowercaseInput.slice(0..<2) == "qu"))
            lowercaseVowel.deleteAt(0)


        // STAGE 2: use modifierIndices to apply diacritics (except tone marks) to the syllable
        // Example:
        //   Input: "vietej" with its modifierIndices and firstVowelIndex as detailed in Stage 1
        //   Output:
        //     outputWithoutTone: "viêt"
        //     tone: ToneMark.DOT
        //     vowelCount: 2
        val output = StringBuilder()
        var tone: ToneMark? = null
        var doNotOutputNextChar = false // this handles the "uwow" edge case
        var vowelCount = 0
        var wHasBeenUsed = false

        for ((index, ch) in input.withIndex()) {
            if (doNotOutputNextChar) {
                doNotOutputNextChar = false
                continue
            }

            val lowercaseCh = lowercaseInput[index]

            when (lowercaseCh) {
                'a', 'd', 'e', 'o' -> {
                    // handle letters that can be doubled

                    val thisModifierIndices = modifierIndices[lowercaseCh]!!

                    // if there is a string such as `ddi` (output: `đi`) or `dddi` (output: ddi),
                    // the last `d` (or any modifier that can be doubled) needs to be omitted from the output
                    if (thisModifierIndices.size >= 2 && index == thisModifierIndices.last()) continue

                    // if there is a string such as `ddi` (output: `đi`),
                    // a diacritic needs to be applied to the first `d`
                    if (thisModifierIndices.size == 2 && index == thisModifierIndices[0]) {
                        if (lowercaseCh == 'd') {
                            output.append(Common.STROKE_MAP[ch])
                        } else if (lowercaseCh == 'o' && lowercaseVowel.contentEquals("oeo")) {
                            // handle "oeo" edge case (should output "oeo", not "ôe"):
                            // remove the second 'o''s index from modifierIndices so that it will be outputted
                            modifierIndices['o']!!.removeLast()
                            output.append(ch)
                        } else {
                            output.append(Common.CIRCUMFLEX_MAP[ch])
                            vowelCount++
                        }

                        continue // after outputting the character with diacritic,
                        // suppress outputting the original character
                    }

                    val wIndices = modifierIndices['w']!!

                    if (wIndices.size == 1 && lowercaseCh == 'a' && !wHasBeenUsed) {
                        output.append(Common.BREVE_MAP[ch])
                        wHasBeenUsed = true
                        vowelCount++
                        continue
                    }

                    if (wIndices.size == 1 && lowercaseCh == 'o'
                        && !lowercaseVowel.contentEquals("oa")
                        // ↑ add edge case for "oaw" (should output "oă", not "ơă" or "ơa")
                        && !(firstVowelIndex != 0 && lowercaseVowel.contentEquals("ou"))
                    // ↑ add edge case: any initial consonant + vowel "ou" with modifier 'w' + no final
                    // should output "oư" and not "ơư"
                    ) {
                        output.append(Common.HORN_MAP[ch])
                        wHasBeenUsed = true
                        vowelCount++
                        continue
                    }
                }

                // handling tones
                'f', 'j', 'r', 's', 'x' -> {
                    val thisModifierIndices = modifierIndices[lowercaseCh]!!

                    if (thisModifierIndices.size == 1)
                        tone = TONES[lowercaseCh]!!

                    if (thisModifierIndices.size >= 1 && index == thisModifierIndices.last()) continue
                }

                'u' -> {
                    // edge case for `uwow` > `ươ`:
                    // the first instance of
                    if (lowercaseInput.length >= index + 4) {
                        if (lowercaseInput.slice(index..<index+4) == "uwow" && modifierIndices['w']!!.size == 2) {
                            modifierIndices['w']!!.removeAt(0)
                            doNotOutputNextChar = true
                        }
                    }

                    // Check if "uo" with modifier 'w' should output "uơ" instead of "ươ"
                    // This only applies when:
                    // * There is an initial consonant, i.e. the syllable does not start with a vowel
                    // * The vowel is only "uo", nothing else
                    // * There is no final consonant
                    // For example: "huow" -> "huơ" (uowIsNotUwow=true), but "uow" -> "ươ" (uowIsNotUwow=false)
                    var uowIsNotUwow = false
                    if ((firstVowelIndex > 0) && !startedFinal && !doNotOutputNextChar
                        && modifierIndices['w']!!.size == 1 && lowercaseVowel.contentEquals("uo")) {
                        uowIsNotUwow = true
                    }

                    if (modifierIndices['w']!!.size == 1 && !wHasBeenUsed && !(lowercaseInput[0] == 'q' && index == 1) && !uowIsNotUwow) {
                        output.append(Common.HORN_MAP[ch])
                        vowelCount++
                        wHasBeenUsed = true
                        continue
                    }
                }

                'w' -> {
                    if (modifierIndices['w']!!.size >= 1 && index == modifierIndices['w']!!.last() &&
                        lowercaseVowel.any { it == 'a' || it == 'o' || it == 'u'}) continue
                }
            }

            output.append(ch) // default behavior: just output the character from input as it is
            if (Common.VOWELS.contains(lowercaseCh)) vowelCount++
        }

        // STAGE 3: apply a tone mark (if any)
        if (tone == null) return output.toString()

        // edge case: "gija" should output "gịa"
        if (lowercaseInput == "gija") {
            output[1] = tone.map[output[1]] ?: output[1]
            return output.toString()
        }

        // apply corrections to vowelCount and firstVowelIndex:
        // 'gi' (if there is another vowel after it) and 'qu' should be considered as consonants
        // There is no Vietnamese word which consists of the initial 'qu' without another vowel letter,
        // but for the sake of better error/edge case handling the correction will only be applied
        // if there is another vowel letter.
        if (vowelCount > 1 && (lowercaseInput.slice(0..<2) == "gi" || lowercaseInput.slice(0..<2) == "qu")) {
            vowelCount--
            firstVowelIndex++
        }

        // if there has been some error applying the correction, just output without the tone mark
        if (vowelCount <= 0 || firstVowelIndex < 0 || firstVowelIndex + vowelCount - 1 >= output.length)
            return output.toString()

        // add tone mark
        val toneMarkPosition = Common.getToneMarkPosition(output, firstVowelIndex, vowelCount)
        // avoid index out of bounds error
        if (toneMarkPosition !in 0..<output.length)
            return output.toString()
        output[toneMarkPosition] = tone.map[output[toneMarkPosition]] ?:
                output[toneMarkPosition]

        return output.toString()
    }
}
