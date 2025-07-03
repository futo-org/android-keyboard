package org.futo.inputmethod.latin.uix.resizing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import org.futo.inputmethod.latin.R


enum class CurrentDraggingTarget {
    //TopLeft,
    //TopRight,
    //BottomLeft,
    //BottomRight,
    Left,
    Right,
    Top,
    Bottom,
    Center
}

private fun CurrentDraggingTarget.computeOffset(size: Size): Offset = when(this) {
    //CurrentDraggingTarget.TopLeft -> Offset(0.0f, 0.0f)
    //CurrentDraggingTarget.TopRight -> Offset(size.width, 0.0f)
    //CurrentDraggingTarget.BottomLeft -> Offset(0.0f, size.height)
    //CurrentDraggingTarget.BottomRight -> Offset(size.width, size.height)*/
    CurrentDraggingTarget.Top -> Offset(size.width * 0.5f, 0.0f)
    CurrentDraggingTarget.Bottom -> Offset(size.width * 0.5f, size.height)
    CurrentDraggingTarget.Left -> Offset(0.0f, size.height * 0.5f)
    CurrentDraggingTarget.Right -> Offset(size.width, size.height * 0.5f)
    CurrentDraggingTarget.Center -> Offset(size.width * 0.5f, size.height * 0.5f)
}

private fun CurrentDraggingTarget.computeOffset(size: IntSize): Offset =
    computeOffset(size.toSize())

private fun CurrentDraggingTarget.dragDelta(offset: Offset): DragDelta = when(this) {
    //CurrentDraggingTarget.TopLeft -> DragDelta(left = offset.x, top = offset.y)
    //CurrentDraggingTarget.TopRight -> DragDelta(right = offset.x, top = offset.y)
    //CurrentDraggingTarget.BottomLeft -> DragDelta(left = offset.x, bottom = offset.y)
    //CurrentDraggingTarget.BottomRight -> DragDelta(right = offset.x, bottom = offset.y)
    CurrentDraggingTarget.Top -> DragDelta(top = offset.y)
    CurrentDraggingTarget.Bottom -> DragDelta(bottom = offset.y)
    CurrentDraggingTarget.Left -> DragDelta(left = offset.x)
    CurrentDraggingTarget.Right -> DragDelta(right = offset.x)
    CurrentDraggingTarget.Center -> DragDelta(
        left   = offset.x,
        right  = offset.x,
        top    = offset.y,
        bottom = offset.y
    )
}

data class DragDelta(
    val left: Float = 0.0f,
    val top: Float = 0.0f,
    val right: Float = 0.0f,
    val bottom: Float = 0.0f
)


@Composable
fun BoxScope.ResizerRect(onDragged: (DragDelta) -> Boolean, showResetApply: Boolean, onApply: () -> Unit, onReset: () -> Unit, shape: RoundedCornerShape = RoundedCornerShape(4.dp)) {
    val draggingState = remember { mutableStateOf<CurrentDraggingTarget?>(null) }
    val wasAccepted = remember { mutableStateOf(onDragged(DragDelta())) }

    Box(Modifier
        .matchParentSize()
        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f), shape)
        .border(
            3.dp, if (!wasAccepted.value) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }, shape
        )
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    draggingState.value = CurrentDraggingTarget.entries.minBy {
                        offset.minus(it.computeOffset(size)).getDistanceSquared()
                    }
                },
                onDrag = { _, amount ->
                    draggingState.value?.let {
                        wasAccepted.value = onDragged(it.dragDelta(amount))
                    }
                },
                onDragEnd = {
                    draggingState.value = null
                    wasAccepted.value = true
                }
            )
        }
        .clip(shape)
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val primaryInverseColor = MaterialTheme.colorScheme.inversePrimary
        val errorColor = MaterialTheme.colorScheme.error
        val radius = with(LocalDensity.current) { 24.dp.toPx() }

        Canvas(Modifier.matchParentSize(), onDraw = {
            CurrentDraggingTarget.entries.forEach {
                drawCircle(
                    color = if (!wasAccepted.value) {
                        errorColor
                    } else if (draggingState.value == it) {
                        primaryInverseColor
                    } else {
                        primaryColor
                    },
                    radius = radius,
                    center = it.computeOffset(size)
                )
            }
        })

        if (showResetApply) {
            Row(Modifier
                .align(Alignment.Center)
                .absoluteOffset(y = 48.dp)) {
                TextButton({ onReset() }) { Text(stringResource(R.string.action_keyboard_modes_resizing_reset_size)) }
                Spacer(Modifier.width(16.dp))
                TextButton({ onApply() }) { Text(stringResource(R.string.action_keyboard_modes_resizing_apply_new_size)) }
            }
        }
    }
}