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
    val right: Key? = null
) : AbstractKey {
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
        flick = ComputedFlickData(
            directions = buildMap {
                (up as? BaseKey)?.let    { put(Direction.North, it.computeData(params, row, keyboard, coordinate)) }
                (down as? BaseKey)?.let  { put(Direction.South, it.computeData(params, row, keyboard, coordinate)) }
                (left as? BaseKey)?.let  { put(Direction.West,  it.computeData(params, row, keyboard, coordinate)) }
                (right as? BaseKey)?.let { put(Direction.East,  it.computeData(params, row, keyboard, coordinate)) }
            }
        ),
        showPopup = true
    )

}