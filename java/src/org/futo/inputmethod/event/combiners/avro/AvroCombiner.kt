package org.futo.inputmethod.event.combiners.avro

import org.futo.inputmethod.event.Combiner
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.latin.common.Constants
import java.util.ArrayList

/**
 * Combiner that transliterates Latin text typed with the Avro Phonetic scheme into Bengali script,
 * e.g. "ami banglay gan gai" becomes "আমি বাংলায় গান গাই".
 *
 * Typed characters are buffered until a character that is not part of the scheme (such as space)
 * arrives, and the whole buffer is re-converted on every keypress so that context dependent rules
 * (dependent vs. independent vowels, conjuncts, ...) always see the full word.
 */
class AvroCombiner : Combiner {
    private val buffer = StringBuilder()

    private fun isAvroChar(char: Char): Boolean {
        return char.code <= 0x7f && (char.isLetterOrDigit() || char in AVRO_SYMBOLS)
    }

    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event?): Event {
        if (event == null) return Event.createNotHandledEvent()

        val codePoint = event.mCodePoint
        val isAvro = codePoint in 0..0x7f && isAvroChar(codePoint.toChar())

        if (!isAvro) {
            if (buffer.isNotEmpty() && event.mKeyCode == Constants.CODE_DELETE) {
                buffer.setLength(buffer.length - 1)
                return Event.createConsumedEvent(event)
            }

            if (!event.isFunctionalKeyEvent) return Event.createResetEvent(event)
            return event
        }

        buffer.append(codePoint.toChar())
        return Event.createConsumedEvent(event)
    }

    override fun getCombiningStateFeedback(): CharSequence {
        return AvroPhoneticConverter.convert(buffer)
    }

    override fun reset() {
        buffer.setLength(0)
    }

    companion object {
        /** Punctuation that is meaningful in Avro: ঃ ঁ ৳ । ৎ and the ` separator */
        private const val AVRO_SYMBOLS = ".,:^$`"
    }
}
