package org.futo.inputmethod.v2keyboard

import org.futo.inputmethod.event.Combiner
import org.futo.inputmethod.event.DeadKeyCombiner

enum class CombinerKind(val factory: () -> Combiner) {
    DeadKey({ DeadKeyCombiner() }),
}