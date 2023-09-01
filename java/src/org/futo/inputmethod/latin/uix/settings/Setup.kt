package org.futo.inputmethod.latin.uix.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.Typography

@Composable
fun SetupContainer(inner: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth(fraction = 1.0f)
                .fillMaxHeight(fraction = 0.4f)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.futo_logo),
                contentDescription = "FUTO Logo",
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.75f)
                    .align(Alignment.CenterHorizontally),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction = 0.5f)
                    .align(Alignment.CenterVertically)
                    .padding(32.dp)
            ) {
                Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                    inner()
                }
            }
        }
    }
}


@Composable
fun Step(fraction: Float, text: String) {
    Column(modifier = Modifier.padding(16.dp)) {
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
            Step(fraction = 1.0f/3.0f, text = "Setup - Step 1 of 2")

            Text(
                "To use FUTO Keyboard, you must first enable FUTO Keyboard as an input method.",
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
fun SetupChangeDefaultIME() {
    val context = LocalContext.current

    val launchImeOptions = {
        val inputMethodManager =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        inputMethodManager.showInputMethodPicker()
    }

    SetupContainer {
        Column {
            Step(fraction = 2.0f/3.0f, text = "Setup - Step 2 of 2")

            Text(
                "Next, select FUTO Keyboard as your active input method.",
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