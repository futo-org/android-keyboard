package org.futo.inputmethod.event.combiners.vietnamese

/** Code common to both Telex and VNI */
object Common {
    /** get_tone_mark_placement() function from vi-rs/src/editing.rs
     * Get nth character to place tone mark
     *
     * # Rules:
     * 1. If a vowel contains ơ or ê, tone mark goes there
     * 2. If a vowel contains `oa`, `oe`, `oo`, `oy`, tone mark should be on the
     *    second character
     *
     * If the accent style is [`AccentStyle::Old`], then:
     * - 3. For vowel length 3 or vowel length 2 with a final consonant, put it on the second vowel character
     * - 4. Else, put it on the first vowel character
     *
     * Otherwise:
     * - 3. If a vowel has 2 characters, put the tone mark on the first one
     * - 4. Otherwise, put the tone mark on the second vowel character
     */
    fun getToneMarkPosition(
        outputWithoutTone: CharSequence,
        firstVowelIndex: Int,
        vowelCount: Int
    ): Int {
        val specialVowelPairs = setOf("oa", "oe", "oo", "uy", "uo", "ie")

        // If there's only one vowel, then it's guaranteed that the tone mark will go there
        if (vowelCount == 1) return firstVowelIndex

        for (i in firstVowelIndex ..< firstVowelIndex + vowelCount) {
            when (outputWithoutTone[i]) {
                'ơ', 'Ơ' -> return i
                'ê', 'Ê' -> return i
                'â', 'Â' -> return i
            }
        }

        val vowel = outputWithoutTone.slice(firstVowelIndex ..< firstVowelIndex + vowelCount)

        // If there is only one vowel with a diacritic (circumflex, breve, horn, etc.), it should
        // get the tone mark
        val vowelsWithDiacritics = vowel.withIndex().filter { it.value !in VOWELS }
        if (vowelsWithDiacritics.size == 1) {
            return firstVowelIndex + vowelsWithDiacritics[0].index
        }

        // Special vowels require the tone mark to be placed on the second character
        if (specialVowelPairs.any { vowel.contains(it, ignoreCase = true) })
            return firstVowelIndex + 1

        // If a syllable end with 2 character vowel, put it on the first character
        if (firstVowelIndex + vowelCount == outputWithoutTone.length && vowelCount == 2)
            return firstVowelIndex

        // Else, put tone mark on second vowel
        return firstVowelIndex + 1
    }

    
    val CONSONANTS = setOf(
        'b', 'c', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'm', 'n', 'p', 'q', 'r', 's', 't', 'v', 'w', 'x', 'z')

    val VOWELS = setOf('a', 'e', 'i', 'o', 'u', 'y', 'A', 'E', 'I', 'O', 'U', 'Y')

    /** A map of characters without accent to character with circumflex accent */
    public val CIRCUMFLEX_MAP = mapOf(
        'a' to 'â',
        'e' to 'ê',
        'o' to 'ô',
        // uppercase
        'A' to 'Â',
        'E' to 'Ê',
        'O' to 'Ô',
    )

    /** A map of characters without accent to character with dyet (D WITH STROKE) accent */
    public val STROKE_MAP = mapOf(
        'd' to 'đ',
        'D' to 'Đ',
    )

    /** A map of characters without accent to character with horn accent */
    public val HORN_MAP = mapOf(
        'u' to 'ư',
        'o' to 'ơ',
        // uppercase
        'U' to 'Ư',
        'O' to 'Ơ',
    )

    /** A map of characters without accent to character with breve accent */
    public val BREVE_MAP = mapOf(
        'a' to 'ă',
        // uppercase
        'A' to 'Ă',
    )
}
