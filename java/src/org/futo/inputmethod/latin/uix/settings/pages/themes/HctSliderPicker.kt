package org.futo.inputmethod.latin.uix.settings.pages.themes

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.material.color.utilities.Hct

@SuppressLint("RestrictedApi")
@Composable
internal fun HctSliderPicker(
    hct: Hct2,
    onColorChange: (Hct2) -> Unit
) {
    // Wrapping it in MutableState to avoid stale closure
    val hctv = remember { mutableStateOf(hct) }
    LaunchedEffect(hct) { hctv.value = hct }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.Companion
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        LabeledSlider(
            label = "Hue",
            value = hct.hue,
            valueRange = 0f..360f,
            gradient = remember {
                Brush.Companion.linearGradient(
                    (0..360 step 30).map { h ->
                        Color(Hct.from(h.toDouble(), 100.0, 50.0).toInt())
                    }
                )
            },
            onValueChange = { onColorChange(hctv.value.copy(hue = it)) }
        )

        LabeledSlider(
            label = "Chroma",
            value = hct.chroma,
            valueRange = 0f..114.0f,
            gradient = remember(hct.hue) {
                Brush.Companion.linearGradient(
                    (0..114 step 20).map { c ->
                        Color(Hct.from(hct.hue.toDouble(), c.toDouble(), 50.0).toInt())
                    }
                )
            },
            onValueChange = { onColorChange(hctv.value.copy(chroma = it)) }
        )

        LabeledSlider(
            label = "Tone",
            value = hct.tone,
            valueRange = 0f..100f,
            gradient = remember {
                Brush.Companion.linearGradient(
                    (0..100 step 10).map { t ->
                        Color(Hct.from(0.0, 0.0, t.toDouble()).toInt())
                    }
                )
            },
            onValueChange = { onColorChange(hctv.value.copy(tone = it)) }
        )
    }
}