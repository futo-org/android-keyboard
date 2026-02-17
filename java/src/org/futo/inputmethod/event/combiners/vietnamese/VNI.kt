package org.futo.inputmethod.event.combiners.vietnamese

object VNI {
    val TONES = mapOf(
        '1' to ToneMark.ACUTE,
        '2' to ToneMark.GRAVE,
        '3' to ToneMark.HOOK,
        '4' to ToneMark.TILDE,
        '5' to ToneMark.DOT
    )

    fun VNIToVietnamese(input: String): String {
        val lowercaseInput = input.lowercase()

        val modifierExists = MutableList(10) { false }

        val lowercaseInitial = StringBuilder()
        val lowercaseVowel = StringBuilder()

        var hasLetters = false

        var startedVowel = false
        var startedFinal = false

        var tone: ToneMark? = null

        // STAGE 1: build modifierIndices and lowercaseVowel
        for ((index, ch) in lowercaseInput.withIndex()) {
            //if (ch.isAsciiDigit()) modifierIndices[ch.digitToInt()].add(index)
            if (ch.isLetter()) hasLetters = true

                // update firstModifierIndex
                if (ch.isDigit() && !modifierExists[ch.digitToInt()])
                    modifierExists[ch.digitToInt()] = true

                    if (!startedVowel && Common.CONSONANTS.contains(ch)) lowercaseInitial.append(ch)

                        if (!startedFinal && Common.VOWELS.contains(ch)) {
                            if (!startedVowel) startedVowel = true
                                lowercaseVowel.append(ch)
                        }

                        if (startedVowel && Common.CONSONANTS.contains(ch))
                            startedFinal = true

                            when (ch) {
                                '1', '2', '3', '4', '5' -> tone = TONES[ch]!!
                            }
        }

        // apply correction to lowercaseInitial and lowercaseVowel
        var giQuCorrectionApplied = false
        if (lowercaseVowel.length > 1 && (lowercaseInitial.contentEquals("q") && lowercaseVowel[0] == 'u' ||
            lowercaseInitial.contentEquals("g") && lowercaseVowel[0] == 'i'
        )) {
            giQuCorrectionApplied = true
            lowercaseInitial.append(lowercaseVowel[0])
            lowercaseVowel.deleteAt(0)
        }

        if (!hasLetters) return input

            // STAGE 2: remove numbers and add diacritics
            val output = StringBuilder()

            /** Tracks if an 'u' has been converted to 'ư'.
             * This variable is checked to ensure that only the first 'u' is converted to 'ư' when there are multiple 'u's.
             * For example, "uou7" should output "ươu", not "ươư"; "uu7" should output "ưu", not "ưư".*/
            var uHornOutputted = false

            for ((index, ch) in lowercaseInput.withIndex()) {
                when (ch) {
                    // handle numbers
                    '1', '2', '3', '4', '5', '6', '7', '8', '9', '0' -> continue

                    // handle modifiable characters
                    'a' -> {
                        if (modifierExists[8]) {
                            output.append(Common.BREVE_MAP[input[index]])
                            continue
                        }

                        if (modifierExists[6]) {
                            output.append(Common.CIRCUMFLEX_MAP[input[index]])
                            continue
                        }
                    }
                    'd' -> if (modifierExists[9]) {
                        output.append(Common.STROKE_MAP[input[index]])
                        continue
                    }
                    'e', 'o' -> {
                        if (modifierExists[6]) {
                            output.append(Common.CIRCUMFLEX_MAP[input[index]])
                            continue
                        }

                        if (ch == 'o' && modifierExists[7] &&
                            !(output.length != 0 && lowercaseVowel.contentEquals("ou") && !startedFinal)) {
                            output.append(Common.HORN_MAP[input[index]])
                            continue
                            }
                    }

                    'u' -> if (modifierExists[7] &&
                    !uHornOutputted &&
                    !(output.getOrNull(0)?.lowercaseChar() == 'q' && output.length == 1) &&
                    !(output.length != 0 && lowercaseVowel.contentEquals("uo") && !startedFinal)) {
                        output.append(Common.HORN_MAP[input[index]])
                        uHornOutputted = true
                        continue
                    }
                }

                //default behavior: output the char in input
                output.append(input[index])
            }

            // STAGE 3: add tone mark
            if (tone == null) return output.toString()

                //edge case for gi5a > gịa
                if (lowercaseInput == "gi5a") {
                    output[1] = tone.map[output[1]] ?: output[1]
                    return output.toString()
                }

                val toneMarkPosition = Common.getToneMarkPosition(output, lowercaseInitial.length, lowercaseVowel.length)
                output[toneMarkPosition] = tone.map[output[toneMarkPosition]] ?: output[toneMarkPosition]

                return output.toString()
    }
}
