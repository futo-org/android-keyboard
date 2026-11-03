package org.futo.voiceinput.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.futo.voiceinput.shared.R
import org.futo.voiceinput.shared.types.MagnitudeState
import org.futo.voiceinput.shared.ui.theme.Typography

data class MicrophoneDeviceState(
    val bluetoothAvailable: Boolean,
    val bluetoothActive: Boolean,
    val bluetoothPreferredByUser: Boolean,
    val setBluetooth: (Boolean) -> Unit,
    val deviceName: String
)

/** Number of bars in the equalizer shown during voice dictation. */
private const val SPECTRUM_BAR_COUNT = 7

/**
 * Draws a small bar "equalizer" that reacts to the volume of the user's voice,
 * replacing the old pulsing circle behind the microphone icon.
 *
 * Since there's no real per-frequency-band data available (only an aggregate
 * volume/RMS value, computed in [org.futo.voiceinput.shared.AudioRecognizer]),
 * the "spectrum" effect is simulated by having each bar chase the same
 * `magnitude` value with a different weight and response speed, so the bars
 * never move in perfect sync with one another.
 */
@Composable
fun AudioSpectrumBars(magnitude: MutableFloatState = mutableFloatStateOf(0.0f)) {
    val color = MaterialTheme.colorScheme.primaryContainer

    // Relative max height of each bar: the center ones are taller, the outer
    // ones a bit shorter, giving a "bell" shape.
    val barWeights = remember {
        List(SPECTRUM_BAR_COUNT) { i ->
            val mid = (SPECTRUM_BAR_COUNT - 1) / 2f
            1f - (abs(i - mid) / (mid + 1f)) * 0.6f
        }
    }

    // Each bar chases the volume with its own inertia (in ms), so they
    // desync from one another.
    val barSpeeds = remember {
        List(SPECTRUM_BAR_COUNT) { i -> 90 + (i * 41) % 110 }
    }

    val animatedLevels = FloatArray(SPECTRUM_BAR_COUNT) { i ->
        animateValueChanges(magnitude.floatValue, barSpeeds[i])
    }

    // Subtle continuous "breathing" animation, so the bars aren't flat
    // while you haven't started talking yet.
    val infiniteTransition = rememberInfiniteTransition(label = "spectrumIdle")
    val idlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "spectrumIdlePhase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val spectrumWidth = size.width * 0.55f
        val maxBarHeight = size.height * 0.4f
        val minBarHeight = size.height * 0.035f
        val barGap = spectrumWidth * 0.08f
        val barWidth = (spectrumWidth - barGap * (SPECTRUM_BAR_COUNT - 1)) / SPECTRUM_BAR_COUNT
        val startX = center.x - spectrumWidth / 2f

        for (i in 0 until SPECTRUM_BAR_COUNT) {
            val idle = (sin(idlePhase + i * 0.9f) * 0.5f + 0.5f) * 0.15f
            val level = (animatedLevels[i] * barWeights[i] + idle).coerceIn(0f, 1f)
            val barHeight = minBarHeight + (maxBarHeight - minBarHeight) * level

            drawRoundRect(
                color = color,
                topLeft = Offset(startX + i * (barWidth + barGap), center.y - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
private fun FakeToast(modifier: Modifier, message: String?) {
    val visible = remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if(message != null) {
            visible.value = true
            delay(2500L)
            visible.value = false
        } else {
            visible.value = false
        }
    }

    if(message != null) {
        AnimatedVisibility(
            visible = visible.value,
            modifier = modifier
                .alpha(0.9f)
                .background(MaterialTheme.colorScheme.surfaceDim, RoundedCornerShape(100))
                .padding(16.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box {
                Text(message, modifier = Modifier.align(Alignment.Center), style = Typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun BoxScope.BluetoothToggleIcon(device: MutableState<MicrophoneDeviceState>? = null) {
    FakeToast(modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-16).dp),
        message = if(device?.value?.bluetoothActive == true) {
            stringResource(R.string.using_bluetooth_mic, device.value.deviceName)
        } else if(device?.value?.bluetoothAvailable == true || device?.value?.bluetoothPreferredByUser == true) {
            stringResource(R.string.using_built_in_mic, device.value.deviceName)
        } else {
            null
        }
    )

    if(device?.value?.bluetoothAvailable == true) {
        val bluetoothColor = MaterialTheme.colorScheme.primary
        val iconColor = if(device.value.bluetoothActive) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        }

        IconButton(modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = (-16).dp, y = (-16).dp)
            .drawBehind {
                val radius = size.height / 4.0f
                drawRoundRect(
                    bluetoothColor,
                    topLeft = Offset(size.width * 0.1f, size.height * 0.05f),
                    size = Size(size.width * 0.8f, size.height * 0.9f),
                    cornerRadius = CornerRadius(radius, radius),
                    style = if (device.value.bluetoothActive) {
                        Fill
                    } else {
                        Stroke(width = 4.0f)
                    }
                )
            }
            .clearAndSetSemantics {
                this.text = AnnotatedString("Use bluetooth mic")
                this.role = Role.Switch
                this.toggleableState = ToggleableState(device.value.bluetoothActive)
            },
            onClick = {
                device.value.setBluetooth(!device.value.bluetoothActive)
            },
        ) {
            Icon(
                painter = painterResource(id = R.drawable.bluetooth),
                contentDescription = null,
                tint = iconColor
            )
        }
    }
}

@Composable
fun InnerRecognize(
    magnitude: MutableFloatState = mutableFloatStateOf(0.5f),
    state: MutableState<MagnitudeState> = mutableStateOf(MagnitudeState.MIC_MAY_BE_BLOCKED),
    device: MutableState<MicrophoneDeviceState>? = null
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AudioSpectrumBars(magnitude = magnitude)

        Icon(
            painter = painterResource(R.drawable.mic_2_),
            contentDescription = stringResource(R.string.stop_recording),
            modifier = Modifier
                .size(28.dp)
                .offset(y = (-72).dp),
            tint = MaterialTheme.colorScheme.onSurface
        )

        val text = when (state.value) {
            MagnitudeState.NOT_TALKED_YET -> stringResource(R.string.try_saying_something)
            MagnitudeState.MIC_MAY_BE_BLOCKED -> stringResource(R.string.no_audio_detected_is_your_microphone_blocked)
            MagnitudeState.TALKING -> stringResource(R.string.listening)
        }

        Text(
            text,
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = 0.dp, y = 72.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        BluetoothToggleIcon(device)
    }
}


@Composable
fun ColumnScope.RecognizeLoadingCircle(text: String = "Initializing...") {
    CircularProgressIndicator(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(text, modifier = Modifier.align(Alignment.CenterHorizontally))
}

@Composable
fun ColumnScope.PartialDecodingResult(text: String = "I am speaking [...]") {
    CircularProgressIndicator(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        color = MaterialTheme.colorScheme.onPrimary
    )
    Spacer(modifier = Modifier.height(6.dp))
    Surface(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(8.dp)
                .defaultMinSize(0.dp, 64.dp),
            textAlign = TextAlign.Start,
            style = Typography.bodyMedium
        )
    }
}

@Composable
fun ColumnScope.RecognizeMicError(openSettings: () -> Unit) {
    Box(Modifier
        .fillMaxSize()
        .clickable { openSettings() }) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.grant_microphone_permission_to_use_voice_input),
                modifier = Modifier
                    .padding(8.dp, 2.dp)
                    .align(Alignment.CenterHorizontally),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                Icons.Default.Settings,
                contentDescription = stringResource(R.string.grant_microphone_permission_to_use_voice_input),
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}