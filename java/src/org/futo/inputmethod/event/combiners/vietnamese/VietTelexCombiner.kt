package org.futo.inputmethod.event.combiners.vietnamese

import android.text.TextUtils
import org.futo.inputmethod.event.Combiner
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.latin.common.Constants
import java.util.ArrayList

class VietTelexCombiner: Combiner {
    private val buffer = StringBuilder() // holds a single Vietnamese word/syllable

    override fun processEvent(
        previousEvents: ArrayList<Event?>?,
        event: Event?
    ): Event {
        if (event == null) return Event.createNotHandledEvent()
        if (event.eventType != Event.EVENT_TYPE_INPUT_KEYPRESS) return event

        val keypress = event.mCodePoint.toChar()

        if (!(keypress in 'A'..'Z' || keypress in 'a'..'z')) {
            if (!TextUtils.isEmpty(buffer)) {
                if (event.mKeyCode == Constants.CODE_DELETE) {
                    buffer.setLength(buffer.length - 1)
                    return Event.createConsumedEvent(event)
                }
            }

            if(!event.isFunctionalKeyEvent) return Event.createResetEvent(event)
            return event
        }

        buffer.append(keypress)
        return Event.createConsumedEvent(event)
    }

    override fun getCombiningStateFeedback(): CharSequence? =
        try {
            Telex.telexToVietnamese(buffer.toString())
        } catch (e: Exception) {
            buffer
        }

    override fun reset() {
        buffer.clear()
    }
}