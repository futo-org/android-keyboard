package org.futo.inputmethod.latin.uix.settings.pages.themes

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    gradient: Brush,
    onValueChange: (Float) -> Unit
) {
    Column {
        //Text(label, fontSize = 14.sp)
        Spacer(Modifier.Companion.height(4.dp))
        BoxWithConstraints(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .height(40.dp)
        ) {

            val widthPx = this.constraints.maxWidth.toFloat()
            val fraction = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)

            Box(
                modifier = Modifier.Companion
                    .fillMaxSize()
                    .drawWithContent {
                        drawRect(brush = gradient)
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach {
                                    val newPosition = it.position.x
                                    val newValue = newPosition.coerceIn(
                                        0f,
                                        widthPx
                                    ) / widthPx * (valueRange.endInclusive - valueRange.start)
                                    onValueChange(newValue)
                                    it.consume()
                                }
                            }
                        }
                    }
                    .clip(CircleShape)
            )

            Box(
                modifier = Modifier.Companion
                    .offset { IntOffset((fraction * widthPx - 12.dp.toPx()).toInt(), 0) }
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Companion.White)
                    .align(Alignment.Companion.CenterStart)
            )
        }
    }
}