package org.futo.inputmethod.event.combiners

import org.futo.inputmethod.event.Combiner
import org.futo.inputmethod.event.Event
import java.util.ArrayList

class DeadKeyPreCombiner : Combiner {
    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event?): Event {
        if (event == null) return Event.createNotHandledEvent()
        //If we get an event with a combining accent character, we send it as a DeadKeyEvent
        if (event.mCodePoint in 0x300..0x35b) {
            return Event.createDeadEvent(event.mCodePoint, 0, null);
        }
        //Otherwise just pass the event as it is
        return event
    }

    override fun getCombiningStateFeedback(): CharSequence {
        return ""
        //This combiner has no state, so no state feedback
    }

    override fun reset() {
        //Nothing, this combiner has no state
    }
}