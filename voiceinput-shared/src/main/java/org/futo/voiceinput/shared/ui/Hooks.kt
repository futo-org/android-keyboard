package org.futo.voiceinput.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.core.math.MathUtils
import com.google.android.material.math.MathUtils.lerp
import kotlinx.coroutines.launch

@Composable
fun animateValueChanges(value: Float, timeMs: Int): Float {
    val animatedValue = remember { mutableStateOf(0.0f) }
    val previousValue = remember { mutableStateOf(0.0f) }

    LaunchedEffect(value) {
        val lastValue = previousValue.value
        if (previousValue.value != value) {
            previousValue.value = value
        }

        launch {
            val startTime = withFrameMillis { it }

            while (true) {
                val time = withFrameMillis { frameTime ->
                    val t = (frameTime - startTime).toFloat() / timeMs.toFloat()

                    val t1 = MathUtils.clamp(t * t * (3f - 2f * t), 0.0f, 1.0f)

                    animatedValue.value = lerp(lastValue, value, t1)

                    frameTime
                }
                if (time > (startTime + timeMs)) break
            }
        }
    }

    return animatedValue.value
}
