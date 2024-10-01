package org.futo.inputmethod.latin.uix.resizing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.uix.safeKeyboardPadding
import org.futo.inputmethod.v2keyboard.ComputedKeyboardSize
import org.futo.inputmethod.v2keyboard.FloatingKeyboardSize
import org.futo.inputmethod.v2keyboard.KeyboardMode
import org.futo.inputmethod.v2keyboard.OneHandedDirection
import org.futo.inputmethod.v2keyboard.OneHandedKeyboardSize
import org.futo.inputmethod.v2keyboard.RegularKeyboardSize
import org.futo.inputmethod.v2keyboard.SavedKeyboardSizingSettings
import org.futo.inputmethod.v2keyboard.SplitKeyboardSize
import org.futo.inputmethod.v2keyboard.getHeight
import org.futo.inputmethod.v2keyboard.getPadding

open class KeyboardResizeHelper(
    val computedKeyboardSize: ComputedKeyboardSize,
    val density: Density,
    initialSettings: SavedKeyboardSizingSettings,
    val delta: DragDelta
) {
    val minimumKeyboardHeight = 32.dp * 3
    val maximumKeyboardHeight = 128.dp * 3
    val maximumSidePadding = 64.dp
    val maximumBottomPadding = 72.dp

    var result = true

    var editedSettings = initialSettings

    fun editBottomPaddingAndHeightAddition() {
        var heightCorrection = 0.0f
        val bottomPadding = with(density) {
            var newBottomPadding = when (editedSettings.currentMode) {
                KeyboardMode.Regular -> editedSettings.paddingDp.bottom
                KeyboardMode.Split -> editedSettings.splitPaddingDp.bottom
                KeyboardMode.OneHanded -> editedSettings.oneHandedRectDp.bottom
                KeyboardMode.Floating -> 0.dp
            } - delta.bottom.toDp()

            if (newBottomPadding !in 0.dp..maximumBottomPadding) {
                // Correct for height difference if it's being dragged up/down
                val correction = if (newBottomPadding < 0.dp) {
                    newBottomPadding.toPx().coerceAtLeast(-delta.top)
                } else {
                    (newBottomPadding - maximumBottomPadding).toPx()
                        .coerceAtMost(-delta.top)
                }
                heightCorrection += correction

                newBottomPadding = newBottomPadding.coerceIn(0.dp..maximumBottomPadding)
                result = false
            }

            newBottomPadding
        }

        val heightAdditionDiffDp = with(density) {
            var heightDiff = (-delta.top - heightCorrection).toDp()
            val currHeight =
                (computedKeyboardSize.getHeight() - computedKeyboardSize.getPadding().bottom - computedKeyboardSize.getPadding().top).toDp()
            if (currHeight + heightDiff < minimumKeyboardHeight) {
                heightDiff += minimumKeyboardHeight - (currHeight + heightDiff)
                result = false
            } else if (currHeight + heightDiff > maximumKeyboardHeight) {
                heightDiff += maximumKeyboardHeight - (currHeight + heightDiff)
                result = false
            }

            heightDiff
        }

        editedSettings = when(editedSettings.currentMode) {
            KeyboardMode.Regular -> editedSettings.copy(
                heightAdditionDp = editedSettings.heightAdditionDp + heightAdditionDiffDp.value,
                paddingDp = editedSettings.paddingDp.copy(bottom = bottomPadding)
            )
            KeyboardMode.Split -> editedSettings.copy(
                splitHeightAdditionDp = editedSettings.splitHeightAdditionDp + heightAdditionDiffDp.value,
                splitPaddingDp = editedSettings.splitPaddingDp.copy(bottom = bottomPadding)
            )
            KeyboardMode.OneHanded -> editedSettings.copy(
                oneHandedHeightAdditionDp = editedSettings.oneHandedHeightAdditionDp + heightAdditionDiffDp.value,
                oneHandedRectDp = editedSettings.oneHandedRectDp.copy(bottom = bottomPadding)
            )
            KeyboardMode.Floating -> TODO()
        }
    }

    fun applySymmetricalPaddingForRegular() = with(density) {
        val sideDelta = delta.left - delta.right

        var newSidePadding =
            (editedSettings.paddingDp.left + sideDelta.toDp())
        if (newSidePadding !in 0.dp..maximumSidePadding) {
            newSidePadding = newSidePadding.coerceIn(0.dp..maximumSidePadding)
            result = false
        }

        editedSettings = editedSettings.copy(
            paddingDp = editedSettings.paddingDp.copy(
                left = newSidePadding,
                right = newSidePadding
            )
        )
    }
}

class OneHandedKeyboardResizeHelper(
    val viewWidth: Int,
    computedKeyboardSize: OneHandedKeyboardSize,
    density: Density,
    initialSettings: SavedKeyboardSizingSettings,
    delta: DragDelta
) : KeyboardResizeHelper(computedKeyboardSize, density, initialSettings, delta) {

    // These have to be flipped in right handed mode, because the setting values are relative to
    // left-handed mode.

    val deltaLeft = if(computedKeyboardSize.direction == OneHandedDirection.Left) {
        delta.left
    } else {
        -delta.right
    }

    val deltaRight = if(computedKeyboardSize.direction == OneHandedDirection.Left) {
        delta.right
    } else {
        -delta.left
    }


    fun moveSideToSide() = with(density) {
        var rightCorrection = 0.dp
        var newLeft = editedSettings.oneHandedRectDp.left + deltaLeft.toDp()
        if(newLeft < 0.dp) {
            // prevent shrinking when being dragged into the wall
            if(deltaRight < 0.0f) {
                rightCorrection -= newLeft
            }
            newLeft = 0.dp

            result = false
        }

        val newRight = editedSettings.oneHandedRectDp.right + deltaRight.toDp() + rightCorrection

        editedSettings = editedSettings.copy(
            oneHandedRectDp = editedSettings.oneHandedRectDp.copy(
                left = newLeft,
                right = newRight
            )
        )
    }

    fun limitToCorrectSide() = with(density) {
        var newLeft = editedSettings.oneHandedRectDp.left
        var newRight = editedSettings.oneHandedRectDp.right

        val newCenter = (newLeft + newRight) / 2.0f
        val limit = (viewWidth.toDp()) / 2.0f
        if(newCenter > limit) {
            val diff = newCenter - limit
            newLeft -= diff
            newRight -= diff
            result = false
        }

        editedSettings = editedSettings.copy(
            oneHandedRectDp = editedSettings.oneHandedRectDp.copy(
                left = newLeft,
                right = newRight
            )
        )
    }

}

class KeyboardResizers(val latinIME: LatinIME) {
    private val resizing = mutableStateOf(false)

    @Composable
    private fun BoxScope.FloatingKeyboardResizer(size: FloatingKeyboardSize) = with(LocalDensity.current) {
        ResizerRect({ delta ->
            // Matching the necessary coordinate space
            var deltaX = delta.left
            var deltaY = -delta.bottom
            var deltaWidth = delta.right - delta.left
            var deltaHeight = delta.bottom - delta.top

            var result = true

            // TODO: Limit the values so that we do not go off-screen
            // If we have reached a minimum limit, return false

            // Basic limiting for minimum size
            val currSettings = latinIME.sizingCalculator.getSavedSettings()
            val currSize = Size(
                currSettings.floatingWidthDp.dp.toPx(),
                currSettings.floatingHeightDp.dp.toPx()
            )

            if(currSize.width + deltaWidth < 200.dp.toPx()) {
                deltaWidth = deltaWidth.coerceAtLeast(200.dp.toPx() - currSize.width)
                deltaX = 0.0f
                result = false
            }

            if(currSize.height + deltaHeight < 160.dp.toPx()) {
                deltaHeight = deltaHeight.coerceAtLeast(160.dp.toPx() - currSize.height)
                deltaY = 0.0f
                result = false
            }

            latinIME.sizingCalculator.editSavedSettings { settings ->
                settings.copy(
                    floatingBottomOriginDp = Pair(
                        settings.floatingBottomOriginDp.first + deltaX.toDp().value,
                        settings.floatingBottomOriginDp.second + deltaY.toDp().value
                    ),
                    floatingWidthDp = settings.floatingWidthDp + deltaWidth.toDp().value,
                    floatingHeightDp = settings.floatingHeightDp + deltaHeight.toDp().value
                )
            }

            result
        }, true, {
            resizing.value = false
        }, {
            // Reset
        })
    }

    @Composable
    private fun BoxScope.RegularKeyboardResizer(size: RegularKeyboardSize) = with(LocalDensity.current) {
        ResizerRect({ delta ->
            var result = true

            latinIME.sizingCalculator.editSavedSettings { settings ->
                val helper = KeyboardResizeHelper(
                    latinIME.size.value ?: size, this, settings, delta
                )

                helper.editBottomPaddingAndHeightAddition()
                helper.applySymmetricalPaddingForRegular()

                result = result && helper.result

                helper.editedSettings
            }
            result
        }, true, {
            resizing.value = false
        }, {
            // TODO: Reset
        })
    }

    @Composable
    private fun BoxScope.OneHandedResizer(size: OneHandedKeyboardSize) = with(LocalDensity.current) {
        ResizerRect({ delta ->
            var result = true

            latinIME.sizingCalculator.editSavedSettings { settings ->
                val helper = OneHandedKeyboardResizeHelper(
                    latinIME.getViewWidth(),
                    latinIME.size.value as? OneHandedKeyboardSize ?: size,
                    this, settings, delta
                )

                helper.editBottomPaddingAndHeightAddition()
                helper.moveSideToSide()
                helper.limitToCorrectSide()

                result = result && helper.result

                helper.editedSettings
            }
            result
        }, true, {
            resizing.value = false
        }, {
            // TODO: Reset
        })
    }

    @Composable
    private fun BoxScope.SplitKeyboardResizer(size: SplitKeyboardSize) = with(LocalDensity.current) {
        Box(
            modifier = Modifier.matchParentSize()
                .width(size.splitLayoutWidth.toDp()).align(
                    Alignment.CenterStart
                )
        ) {
            ResizerRect({ delta ->
                true
            }, true, {
                resizing.value = false
            }, {

            })
        }
    }



    @Composable
    fun Resizer(boxScope: BoxScope, size: ComputedKeyboardSize) = with(boxScope) {
        if(!resizing.value) return

        Box(Modifier.matchParentSize().safeKeyboardPadding()) {
            when (size) {
                is OneHandedKeyboardSize -> OneHandedResizer(size)
                is RegularKeyboardSize -> RegularKeyboardResizer(size)
                is SplitKeyboardSize -> SplitKeyboardResizer(size)
                is FloatingKeyboardSize -> FloatingKeyboardResizer(size)
            }
        }
    }

    fun displayResizer() {
        resizing.value = true
    }

    fun hideResizer() {
        resizing.value = false
    }
}