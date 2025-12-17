package org.futo.inputmethod.latin.uix.resizing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.uix.keyboardBottomPadding
import org.futo.inputmethod.latin.uix.navBarHeight
import org.futo.inputmethod.latin.uix.safeKeyboardPadding
import org.futo.inputmethod.v2keyboard.ComputedKeyboardSize
import org.futo.inputmethod.v2keyboard.FloatingKeyboardSize
import org.futo.inputmethod.v2keyboard.KeyboardMode
import org.futo.inputmethod.v2keyboard.OneHandedDirection
import org.futo.inputmethod.v2keyboard.OneHandedKeyboardSize
import org.futo.inputmethod.v2keyboard.RegularKeyboardSize
import org.futo.inputmethod.v2keyboard.SavedKeyboardSizingSettings
import org.futo.inputmethod.v2keyboard.SplitKeyboardSize

open class KeyboardResizeHelper(
    val viewSize: IntSize,
    val computedKeyboardSize: ComputedKeyboardSize,
    val density: Density,
    initialSettings: SavedKeyboardSizingSettings,
    val delta: DragDelta
) {
    val minimumKeyboardWidth = 48.dp * 3
    val maximumKeyboardWidth = with(density) { viewSize.width.toDp() }

    val minimumKeyboardHeight = 32.dp * 3
    val maximumKeyboardHeight = with(density) { (viewSize.height * 2.0f / 3.0f).toDp() }.coerceAtLeast(128.dp * 3)

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
                (computedKeyboardSize.height - computedKeyboardSize.padding.bottom - computedKeyboardSize.padding.top).toDp()
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
            KeyboardMode.Floating -> editedSettings // unused by Floating
        }
    }

    fun applySymmetricalPaddingForRegular(sideDelta: Float) = with(density) {
        var newSidePadding =
            when (editedSettings.currentMode) {
                KeyboardMode.Regular -> editedSettings.paddingDp.left
                KeyboardMode.Split -> editedSettings.splitPaddingDp.left
                KeyboardMode.OneHanded -> editedSettings.oneHandedRectDp.left
                KeyboardMode.Floating -> 0.dp
            } + sideDelta.toDp()

        if (newSidePadding !in 0.dp..maximumSidePadding) {
            newSidePadding = newSidePadding.coerceIn(0.dp..maximumSidePadding)
            result = false
        }

        editedSettings = when (editedSettings.currentMode) {
            KeyboardMode.Regular -> editedSettings.copy(
                paddingDp = editedSettings.paddingDp.copy(
                    left = newSidePadding,
                    right = newSidePadding
                )
            )
            KeyboardMode.Split -> editedSettings.copy(
                splitPaddingDp = editedSettings.splitPaddingDp.copy(
                    left = newSidePadding,
                    right = newSidePadding
                )
            )
            KeyboardMode.OneHanded -> editedSettings // unused by OneHanded
            KeyboardMode.Floating -> editedSettings // unused by Floating
        }
    }
}

class OneHandedKeyboardResizeHelper(
    viewSize: IntSize,
    computedKeyboardSize: OneHandedKeyboardSize,
    density: Density,
    initialSettings: SavedKeyboardSizingSettings,
    delta: DragDelta
) : KeyboardResizeHelper(viewSize, computedKeyboardSize, density, initialSettings, delta) {

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
        val limit = (viewSize.width.toDp()) / 2.0f
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

    fun limitMinimumWidth() = with(density) {
        editedSettings = editedSettings.copy(
            oneHandedRectDp = editedSettings.oneHandedRectDp.copy(
                right = editedSettings.oneHandedRectDp.right.coerceAtLeast(
                    editedSettings.oneHandedRectDp.left + minimumKeyboardWidth
                )
            )
        )
    }
}

class FloatingKeyboardResizeHelper(
    viewSize: IntSize,
    computedKeyboardSize: ComputedKeyboardSize,
    density: Density,
    initialSettings: SavedKeyboardSizingSettings,
    delta: DragDelta
) : KeyboardResizeHelper(viewSize, computedKeyboardSize, density, initialSettings, delta) {
    // Matching the necessary coordinate space
    var deltaX = delta.left
    var deltaY = -delta.bottom
    var deltaWidth = delta.right - delta.left
    var deltaHeight = delta.bottom - delta.top

    fun applyOriginOffsetWithLimits() = with(density) {
        var newX = editedSettings.floatingBottomOriginDp.first.dp + deltaX.toDp()
        var newY = editedSettings.floatingBottomOriginDp.second.dp + deltaY.toDp()

        val maxX = (viewSize.width.toDp() - editedSettings.floatingWidthDp.dp).coerceAtLeast(0.dp)
        val maxY = (viewSize.height.toDp() - editedSettings.floatingHeightDp.dp).coerceAtLeast(0.dp)

        val xRange = 0.dp .. maxX
        val yRange = 0.dp .. maxY

        if(newX !in xRange) {
            newX = newX.coerceIn(xRange)
            result = false
        }

        if(newY !in yRange) {
            newY = newY.coerceIn(yRange)
            result = false
        }

        editedSettings = editedSettings.copy(
            floatingBottomOriginDp = Pair(newX.value, newY.value)
        )
    }

    fun applyDeltaSizeWithLimits() = with(density) {
        var newWidth = editedSettings.floatingWidthDp.dp + deltaWidth.toDp()
        var newHeight = editedSettings.floatingHeightDp.dp + deltaHeight.toDp()

        val widthRange = minimumKeyboardWidth .. maximumKeyboardWidth
        val heightRange = minimumKeyboardHeight .. maximumKeyboardHeight

        if(newWidth !in widthRange) {
            deltaX = 0.0f
            newWidth = newWidth.coerceIn(widthRange)
            result = false
        }

        if(newHeight !in heightRange) {
            deltaY = 0.0f
            newHeight = newHeight.coerceIn(heightRange)
            result = false
        }

        editedSettings = editedSettings.copy(
            floatingWidthDp = newWidth.value,
            floatingHeightDp = newHeight.value
        )
    }
}

class SplitKeyboardResizeHelper(
    viewSize: IntSize,
    computedKeyboardSize: SplitKeyboardSize,
    density: Density,
    initialSettings: SavedKeyboardSizingSettings,
    delta: DragDelta
) : KeyboardResizeHelper(viewSize, computedKeyboardSize, density, initialSettings, delta) {
    fun editSplitLayoutWidth(delta: Float) {
        val oldSplitWidth = (computedKeyboardSize as SplitKeyboardSize).splitLayoutWidth
        val newSplitWidth = oldSplitWidth + 2*delta

        var newFraction = editedSettings.splitWidthFraction * (newSplitWidth / oldSplitWidth)

        val fractionRange = 0.1f .. 0.9f
        if(newFraction !in fractionRange) {
            newFraction = newFraction.coerceIn(fractionRange)
            result = false
        }

        editedSettings = editedSettings.copy(
            splitWidthFraction = newFraction
        )
    }
}

class KeyboardResizers(val latinIME: LatinIME) {
    private val resizing = mutableStateOf(false)

    private fun finishResizer() {
        resizing.value = false
    }

    @Composable
    private fun BoxScope.FloatingKeyboardResizer(size: FloatingKeyboardSize, shape: RoundedCornerShape) = with(LocalDensity.current) {
        ResizerRect({ delta ->
            var result = true

            latinIME.sizingCalculator.editSavedSettings { settings ->
                val helper = FloatingKeyboardResizeHelper(
                    IntSize(latinIME.getViewWidth(), latinIME.getViewHeight()),
                    latinIME.size.value as? FloatingKeyboardSize ?: size,
                    this,
                    settings,
                    delta
                )

                helper.applyDeltaSizeWithLimits()
                helper.applyOriginOffsetWithLimits()

                result = result && helper.result

                helper.editedSettings
            }

            result
        }, true, {
            finishResizer()
        }, {
            latinIME.sizingCalculator.resetCurrentMode()
        }, shape)
    }

    @Composable
    private fun BoxScope.RegularKeyboardResizer(size: RegularKeyboardSize, shape: RoundedCornerShape) = with(LocalDensity.current) {
        ResizerRect({ delta ->
            var result = true

            latinIME.sizingCalculator.editSavedSettings { settings ->
                val helper = KeyboardResizeHelper(
                    IntSize(latinIME.getViewWidth(), latinIME.getViewHeight()),
                    latinIME.size.value ?: size,
                    this, settings, delta
                )

                helper.editBottomPaddingAndHeightAddition()
                helper.applySymmetricalPaddingForRegular(delta.left - delta.right)

                result = result && helper.result

                helper.editedSettings
            }
            result
        }, true, {
            finishResizer()
        }, {
            latinIME.sizingCalculator.resetCurrentMode()
        }, shape)
    }

    @Composable
    private fun BoxScope.OneHandedResizer(size: OneHandedKeyboardSize, shape: RoundedCornerShape) = with(LocalDensity.current) {
        ResizerRect({ delta ->
            var result = true

            latinIME.sizingCalculator.editSavedSettings { settings ->
                val helper = OneHandedKeyboardResizeHelper(
                    IntSize(latinIME.getViewWidth(), latinIME.getViewHeight()),
                    latinIME.size.value as? OneHandedKeyboardSize ?: size,
                    this, settings, delta
                )

                helper.editBottomPaddingAndHeightAddition()
                helper.moveSideToSide()
                helper.limitToCorrectSide()
                helper.limitMinimumWidth()

                result = result && helper.result

                helper.editedSettings
            }
            result
        }, true, {
            finishResizer()
        }, {
            latinIME.sizingCalculator.resetCurrentMode()
        }, shape)
    }

    @Composable
    private fun BoxScope.SplitKeyboardResizer(size: SplitKeyboardSize, shape: RoundedCornerShape) = with(LocalDensity.current) {
        println("Active size: ${size.width} ${size.splitLayoutWidth} ${size.padding}")
        Box(
            modifier = Modifier.matchParentSize()
                .absolutePadding(right = (size.width - size.splitLayoutWidth * 0.55f - size.padding.right - size.padding.left).toDp().coerceAtLeast(0.dp))
        ) {
            ResizerRect({ delta ->
                var result = true

                latinIME.sizingCalculator.editSavedSettings { settings ->
                    val helper = SplitKeyboardResizeHelper(
                        IntSize(latinIME.getViewWidth(), latinIME.getViewHeight()),
                        latinIME.size.value as? SplitKeyboardSize ?: size,
                        this@with, settings, delta
                    )

                    helper.editBottomPaddingAndHeightAddition()
                    helper.applySymmetricalPaddingForRegular(delta.left)
                    helper.editSplitLayoutWidth(delta.right - delta.left)

                    result = result && helper.result

                    helper.editedSettings
                }

                result
            }, true, {
                finishResizer()
            }, {
                latinIME.sizingCalculator.resetCurrentMode()
            }, shape)
        }
    }



    @Composable
    fun Resizer(boxScope: BoxScope, size: ComputedKeyboardSize, shape: RoundedCornerShape = RoundedCornerShape(4.dp)) = with(boxScope) {
        if (!resizing.value) return

        val modifier = Modifier.matchParentSize().let { mod ->
            if (size is FloatingKeyboardSize) mod
            else mod
                .safeKeyboardPadding()
                .keyboardBottomPadding(size)
                .absolutePadding(bottom = navBarHeight())
        }

        Box(modifier) {
            when (size) {
                is OneHandedKeyboardSize -> OneHandedResizer(size, shape)
                is RegularKeyboardSize -> RegularKeyboardResizer(size, shape)
                is SplitKeyboardSize -> SplitKeyboardResizer(size, shape)
                is FloatingKeyboardSize -> FloatingKeyboardResizer(size, shape)
            }
        }
    }

    fun displayResizer() {
        resizing.value = true
    }

    fun hideResizer() {
        if(resizing.value) finishResizer()
    }
}