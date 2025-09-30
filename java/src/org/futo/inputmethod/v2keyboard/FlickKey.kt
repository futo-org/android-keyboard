package org.futo.inputmethod.v2keyboard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.futo.inputmethod.keyboard.internal.KeyboardParams

@Serializable
@SerialName("flick")
data class FlickKey(
    val primary: Key,

    val up: Key? = null,
    val down: Key? = null,
    val left: Key? = null,
    val right: Key? = null,

    val upLeft: Key? = null,
    val upRight: Key? = null,
    val downLeft: Key? = null,
    val downRight: Key? = null,

    val attributes: KeyAttributes? = null,
    val label: String? = null,
    val icon: String? = null,
) : AbstractKey {
    private val extraAttrs = attributes?.let { listOf(attributes) } ?: emptyList()

    override fun countsToKeyCoordinate(
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard
    ): Boolean = false

    override fun computeData(
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard,
        coordinate: KeyCoordinate
    ): ComputedKeyData? = primary.computeData(params, row, keyboard, coordinate)?.copy(
        moreKeys = emptyList(),
        longPressEnabled = false,
        flick = ComputedFlickData(
            directions = buildMap {
                (up as? BaseKey)?.let    { put(Direction.North, it.computeDataWithExtraAttrs(params, row, keyboard, coordinate, extraAttrs)) }
                (down as? BaseKey)?.let  { put(Direction.South, it.computeDataWithExtraAttrs(params, row, keyboard, coordinate, extraAttrs)) }
                (left as? BaseKey)?.let  { put(Direction.West,  it.computeDataWithExtraAttrs(params, row, keyboard, coordinate, extraAttrs)) }
                (right as? BaseKey)?.let { put(Direction.East,  it.computeDataWithExtraAttrs(params, row, keyboard, coordinate, extraAttrs)) }
                (upLeft    as? BaseKey)?.let { put(Direction.NorthWest, it.computeDataWithExtraAttrs(params, row, keyboard, coordinate, extraAttrs)) }
                (upRight   as? BaseKey)?.let { put(Direction.NorthEast, it.computeDataWithExtraAttrs(params, row, keyboard, coordinate, extraAttrs)) }
                (downLeft  as? BaseKey)?.let { put(Direction.SouthWest, it.computeDataWithExtraAttrs(params, row, keyboard, coordinate, extraAttrs)) }
                (downRight as? BaseKey)?.let { put(Direction.SouthEast, it.computeDataWithExtraAttrs(params, row, keyboard, coordinate, extraAttrs)) }
            },
            label = label,
            icon = icon
        ),
        showPopup = true
    )

}