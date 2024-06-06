package org.futo.voiceinput.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.platform.LocalDensity
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

@Composable
fun AnimatedRecognizeCircle(magnitude: MutableFloatState = mutableFloatStateOf(0.5f)) {
    val radius = animateValueChanges(magnitude.floatValue, 100)
    val color = MaterialTheme.colorScheme.primaryContainer

    val radiusMod = with(LocalDensity.current) {
        80.dp.toPx()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val drawRadius = radiusMod * (0.8f + radius * 2.0f)
        drawCircle(color = color, radius = drawRadius)
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
            modifier = modifier.alpha(0.9f).background(MaterialTheme.colorScheme.surfaceDim, RoundedCornerShape(100)).padding(16.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box {
                Text(message, modifier = Modifier.align(Alignment.Center), style = Typography.labelSmall)
            }
        }
    }
}

@Composable
private fun BoxScope.BluetoothToggleIcon(device: MutableState<MicrophoneDeviceState>? = null) {
    FakeToast(modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-16).dp), message = if(device?.value?.bluetoothActive == true) {
        "Using Bluetooth mic (${device.value.deviceName})"
    } else if(device?.value?.bluetoothAvailable == true || device?.value?.bluetoothPreferredByUser == true) {
        "Using Built-in mic (${device.value.deviceName})"
    } else {
        null
    })

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
        AnimatedRecognizeCircle(magnitude = magnitude)

        Icon(
            painter = painterResource(R.drawable.mic_2_),
            contentDescription = stringResource(R.string.stop_recording),
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
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
                .offset(x = 0.dp, y = 48.dp),
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
    Text(
        stringResource(R.string.grant_microphone_permission_to_use_voice_input),
        modifier = Modifier
            .padding(8.dp, 2.dp)
            .align(Alignment.CenterHorizontally),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface
    )
    IconButton(
        onClick = { openSettings() },
        modifier = Modifier
            .padding(4.dp)
            .align(Alignment.CenterHorizontally)
            .size(64.dp)
    ) {
        Icon(
            Icons.Default.Settings,
            contentDescription = stringResource(R.string.open_voice_input_settings),
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}