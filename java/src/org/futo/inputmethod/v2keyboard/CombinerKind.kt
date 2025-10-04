package org.futo.inputmethod.v2keyboard

import org.futo.inputmethod.event.Combiner
import org.futo.inputmethod.event.DeadKeyCombiner
import org.futo.inputmethod.event.combiners.NFCNormalizingCombiner
import org.futo.inputmethod.event.combiners.DeadKeyPreCombiner
import org.futo.inputmethod.event.combiners.KoreanCombiner
import org.futo.inputmethod.event.combiners.wylie.WylieCombiner

enum class CombinerKind(val factory: () -> Combiner) {
    DeadKey({ DeadKeyCombiner() }),
    DeadKeyPreCombiner({ DeadKeyPreCombiner() }),
    NFCNormalize({ NFCNormalizingCombiner() }),
    Korean({ KoreanCombiner() }),
    KoreanCombineInitials({ KoreanCombiner(combineInitials = true) }),
    Wylie({ WylieCombiner() }),
}