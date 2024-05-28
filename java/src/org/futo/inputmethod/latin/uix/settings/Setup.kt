package org.futo.inputmethod.latin.uix.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.USE_SYSTEM_VOICE_INPUT
import org.futo.inputmethod.latin.uix.theme.Typography

@Composable
fun SetupContainer(inner: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.75f)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .align(Alignment.Center),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterVertically)
                    .padding(32.dp)
            ) {
                Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                    inner()
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.25f))
    }
}


@Composable
fun Step(fraction: Float, text: String) {
    Column(modifier = Modifier
        .padding(16.dp)
        .clearAndSetSemantics {
            this.text = AnnotatedString(text)
        }
    ) {
        Text(text, style = Typography.labelSmall)
        LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth())
    }
}

// TODO: May wish to have a skip option
@Composable
@Preview
fun SetupEnableIME() {
    val context = LocalContext.current

    val launchImeOptions = {
        // TODO: look into direct boot to get rid of direct boot warning?
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)

        intent.flags = (Intent.FLAG_ACTIVITY_NEW_TASK
                or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                or Intent.FLAG_ACTIVITY_NO_HISTORY
                or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

        context.startActivity(intent)
    }

    SetupContainer {
        Column {
            Step(fraction = 1.0f/3.0f, text = "Setup - Step 1 of 3")

            Text(
                "Welcome to FUTO Keyboard pre-alpha! Please keep in mind things may be rough. This is not a finished product in any way.\n\nFirst, enable FUTO Keyboard as an input method.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = launchImeOptions,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Open Input Method Settings")
            }
        }
    }
}


@Composable
@Preview
fun SetupChangeDefaultIME(doublePackage: Boolean = true) {
    val context = LocalContext.current

    val launchImeOptions = {
        val inputMethodManager =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        inputMethodManager.showInputMethodPicker()

        (context as SettingsActivity).updateSystemState()
    }

    SetupContainer {
        Column {
            if(doublePackage) {
                Tip("Warning: You seem to have both Play Store and Standalone versions installed, we only recommend installing one")
            }

            Step(fraction = 2.0f/3.0f, text = "Setup - Step 2 of 3")

            Text(
                "Please select FUTO Keyboard as your active input method.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = launchImeOptions,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Switch Input Methods")
            }
        }
    }
}


@Composable
@Preview
fun SetupEnableMic(onClick: () -> Unit = { }) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if(isGranted) { onClick() }
    }

    val context = LocalContext.current

    var askedCount by remember { mutableStateOf(0) }
    val askMicAccess = {
        if (askedCount++ >= 2) {
            val packageName = context.packageName
            val myAppSettings = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse(
                    "package:$packageName"
                )
            )
            myAppSettings.addCategory(Intent.CATEGORY_DEFAULT)
            myAppSettings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(myAppSettings)
        } else {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
        onClick()
    }

    val (useSystemVoiceInput, setUseSystemVoiceInput) = useDataStore(key = USE_SYSTEM_VOICE_INPUT.key, default = USE_SYSTEM_VOICE_INPUT.default)

    SetupContainer {
        Column {
            Step(fraction = 0.9f, text = "Setup - Step 3 of 3")
            Text(
                "Choose whether you want to use built-in voice input, or the system voice input.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = askMicAccess,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Use built-in (mic permission needed)")
            }

            Button(
                onClick = {
                    setUseSystemVoiceInput(true)
                    (context as SettingsActivity).updateSystemState()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 4.dp)
            ) {
                Text("Use system")
            }
        }
    }
}

