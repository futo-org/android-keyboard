package org.futo.voiceinput.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.core.math.MathUtils
import com.google.android.material.math.MathUtils.lerp
import kotlinx.coroutines.launch

@Composable
fun animateValueChanges(value: Float, timeMs: Int): Float {
    val animatedValue = remember { mutableFloatStateOf(0.0f) }
    val previousValue = remember { mutableFloatStateOf(0.0f) }

    LaunchedEffect(value) {
        val lastValue = previousValue.floatValue
        if (previousValue.floatValue != value) {
            previousValue.floatValue = value
        }

        launch {
            val startTime = withFrameMillis { it }

            while (true) {
                val time = withFrameMillis { frameTime ->
                    val t = (frameTime - startTime).toFloat() / timeMs.toFloat()

                    val t1 = MathUtils.clamp(t * t * (3f - 2f * t), 0.0f, 1.0f)

                    animatedValue.floatValue = lerp(lastValue, value, t1)

                    frameTime
                }
                if (time > (startTime + timeMs)) break
            }
        }
    }

    return animatedValue.floatValue
}
