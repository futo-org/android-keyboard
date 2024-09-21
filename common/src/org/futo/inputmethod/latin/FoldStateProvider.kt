package org.futo.inputmethod.latin

import androidx.window.layout.FoldingFeature

data class FoldingOptions(
    val feature: FoldingFeature?
)

interface FoldStateProvider {
    val foldState: FoldingOptions
}
