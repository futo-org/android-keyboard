package org.futo.inputmethod.v2keyboard

import kotlinx.serialization.Serializable
import org.futo.inputmethod.keyboard.internal.KeyboardLayoutElement
import org.futo.inputmethod.keyboard.internal.KeyboardParams
import org.futo.inputmethod.keyboard.internal.MoreKeySpec

data class KeyCoordinate(
    val regularRow: Int,
    val regularColumn: Int,
    val element: KeyboardLayoutElement,
    val measurement: KeyCoordinateMeasurement
)

data class KeyCoordinateMeasurement(
    val totalRows: Int,
    val numColumnsByRow: List<Int>
)

@Serializable
sealed interface AbstractKey {
    fun countsToKeyCoordinate(params: KeyboardParams, row: Row, keyboard: Keyboard): Boolean
    fun computeData(params: KeyboardParams, row: Row, keyboard: Keyboard, coordinate: KeyCoordinate): ComputedKeyData?
}

data class ComputedKeyData(
    val label: String,
    val code: Int,
    val outputText: String?,
    val width: KeyWidth,
    val icon: String,
    val style: KeyVisualStyle,
    val anchored: Boolean,
    val showPopup: Boolean,
    val moreKeys: List<MoreKeySpec>,
    val longPressEnabled: Boolean,
    val repeatable: Boolean,
    val moreKeyFlags: Int,
    val countsToKeyCoordinate: Boolean,
    val hint: String,
    val labelFlags: Int,
    val fastLongPress: Boolean = false
)
