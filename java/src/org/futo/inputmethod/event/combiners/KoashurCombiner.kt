// KoashurKhat Latin Script Combiner for FUTO Keyboard
// Handles Latin script with custom dead key combinations

package org.futo.inputmethod.event.combiners

import android.text.TextUtils
import org.futo.inputmethod.event.Combiner
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.latin.common.Constants

/**
 * Combiner for KoashurKhat Latin script.
 * Handles combination of dead keys with base letters to produce: ṭ, ḍ, è, ë, ē, ě, ó, ö, ÿ
 */
class KoashurCombiner: Combiner {
    // General implementation:
    // A StringBuilder called `buffer` stores uncombined Latin letters and dead keys from keypresses.
    // On every keypress these are processed to create combined diacritical characters
    // using the processCombinations() function, and passed to the system using getCombiningStateFeedback()
    
    companion object Data {
        // Dead key characters used in KoashurKhat
        private const val DEAD_ACUTE = '\u00B4'  // ´ (maps to ' key position)
        private const val DEAD_CARON = '\u030C'  // ̌ (maps to ˇ key position)
        
        // Valid base letters that can combine with dead keys
        private val baseLetters = setOf(
            't', 'T', 'd', 'D', 'e', 'E', 'o', 'O', 'y', 'Y', 'r', 'R', 'w', 'W'
        )
        
        // Dead key combination mappings (letter + dead key)
        private val deadKeyCombinations = mapOf(
            // ACUTE dead key combinations (letter + \u00B4)
            Pair('t', DEAD_ACUTE) to 'ṭ',  // U+1E6D
            Pair('T', DEAD_ACUTE) to 'Ṭ',  // U+1E6C
            Pair('d', DEAD_ACUTE) to 'ḍ',  // U+1E0D
            Pair('D', DEAD_ACUTE) to 'Ḍ',  // U+1E0C
            Pair('e', DEAD_ACUTE) to 'è',  // U+00E8
            Pair('E', DEAD_ACUTE) to 'È',  // U+00C8
            Pair('o', DEAD_ACUTE) to 'ó',  // U+00F3
            Pair('O', DEAD_ACUTE) to 'Ó',  // U+00D3
            
            // CARON dead key combinations (letter + \u030C)
            Pair('e', DEAD_CARON) to 'ë',  // U+00EB
            Pair('E', DEAD_CARON) to 'Ë',  // U+00CB
            Pair('o', DEAD_CARON) to 'ö',  // U+00F6
            Pair('O', DEAD_CARON) to 'Ö',  // U+00D6
            Pair('y', DEAD_CARON) to 'ÿ',  // U+00FF
            Pair('Y', DEAD_CARON) to 'Ÿ',  // U+0178
            Pair('r', DEAD_CARON) to 'ē',  // U+0113 (using r for ē)
            Pair('R', DEAD_CARON) to 'Ē',  // U+0112 (using R for Ē)
            Pair('w', DEAD_CARON) to 'ě',  // U+011B (using w for ě)
            Pair('W', DEAD_CARON) to 'Ě'   // U+011A (using W for Ě)
        )
        
        private val deadKeys = setOf(DEAD_ACUTE, DEAD_CARON)
    }
    
    private fun isKoashurKhatLetter(char: Char): Boolean { 
        return char.code in 0x0041..0x007A ||  // A-Z, a-z
               char.code in 0x00C0..0x024F ||  // Latin Extended-A/B
               deadKeys.contains(char)
    }
    
    private fun isDeadKey(char: Char): Boolean { 
        return deadKeys.contains(char) 
    }
    
    private fun isBaseLetter(char: Char): Boolean { 
        return baseLetters.contains(char) 
    }
    
    private val buffer = StringBuilder() // This buffer holds uncombined KoashurKhat letters and dead keys
    
    private fun processCombinations(): CharSequence {
        val combined = StringBuilder()
        
        var i = 0
        while (i < buffer.length) {
            val char = buffer[i]
            
            // Check if current character is a base letter and next is a dead key
            if (isBaseLetter(char) && i + 1 < buffer.length) {
                val nextChar = buffer[i + 1]
                
                if (isDeadKey(nextChar)) {
                    val combination = Pair(char, nextChar)
                    
                    if (deadKeyCombinations.containsKey(combination)) {
                        // Valid combination found (letter + dead key)
                        combined.append(deadKeyCombinations[combination]!!)
                        i += 2 // Skip both characters
                        continue
                    }
                }
            }
            
            // Check if current character is a dead key with no preceding base letter
            if (isDeadKey(char)) {
                // For dead keys that don't combine, we can show them or hide them
                // For now, we'll show them but they should be invisible in practice
                combined.append(char)
                i++
                continue
            }
            
            // Regular character or base letter without following dead key
            combined.append(char)
            i++
        }
        
        return combined
    }
    
    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event?): Event {
        if (event == null) return Event.createNotHandledEvent()
        val keypress = event.mCodePoint.toChar()
        
        if (!isKoashurKhatLetter(keypress)) {
            if (!TextUtils.isEmpty(buffer)) {
                if (event.mKeyCode == Constants.CODE_DELETE) {
                    return if (buffer.length == 1) {
                        reset()
                        Event.createHardwareKeypressEvent(0x20, Constants.CODE_SPACE,
                            event, event.isKeyRepeat)
                        // This space event helps with proper deletion behavior
                    }
                    else {
                        buffer.setLength(buffer.length - 1)
                        Event.createConsumedEvent(event)
                    }
                }
            }
            return event
        }
        
        buffer.append(keypress)
        return Event.createConsumedEvent(event)
    }
    
    override fun getCombiningStateFeedback(): CharSequence {
        return processCombinations()
    }
    
    override fun reset() {
        buffer.setLength(0)
    }
}