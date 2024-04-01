package org.futo.inputmethod.latin.uix.settings.pages

import android.widget.EditText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.openLanguageSettings
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.updates.ConditionalUpdate

@Composable
fun AndroidTextInput() {
    val context = LocalContext.current
    val bgColor = MaterialTheme.colorScheme.background
    val fgColor = MaterialTheme.colorScheme.onBackground

    if(!LocalInspectionMode.current) {
        val editText = remember {
            EditText(context).apply {
                setHint(R.string.try_typing)
                setBackgroundColor(bgColor.toArgb())
                setTextColor(fgColor.toArgb())
                setHintTextColor(fgColor.copy(alpha = 0.7f).toArgb())
            }
        }
        AndroidView({ editText }, modifier = Modifier.fillMaxWidth().padding(8.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    Column {
        Column(
            modifier = Modifier
                .weight(1.0f).fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            ScreenTitle("FUTO Keyboard Settings")

            ConditionalUpdate(navController)

            NavigationItem(
                title = "Languages",
                style = NavigationItemStyle.HomePrimary,
                navigate = { navController.navigate("languages") },
                icon = painterResource(id = R.drawable.globe)
            )

            NavigationItem(
                title = "Predictive Text",
                style = NavigationItemStyle.HomeSecondary,
                navigate = { navController.navigate("predictiveText") },
                icon = painterResource(id = R.drawable.shift)
            )

            NavigationItem(
                title = "Typing Preferences",
                style = NavigationItemStyle.HomeSecondary,
                navigate = { navController.navigate("typing") },
                icon = painterResource(id = R.drawable.delete)
            )

            NavigationItem(
                title = "Voice Input",
                style = NavigationItemStyle.HomeSecondary,
                navigate = { navController.navigate("voiceInput") },
                icon = painterResource(id = R.drawable.mic_fill)
            )

            NavigationItem(
                title = "Theme",
                style = NavigationItemStyle.HomeTertiary,
                navigate = { navController.navigate("themes") },
                icon = painterResource(id = R.drawable.eye)
            )

            NavigationItem(
                title = "Help & Feedback",
                style = NavigationItemStyle.HomePrimary,
                navigate = { navController.navigate("help") },
                icon = painterResource(id = R.drawable.help_circle)
            )


            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "v${BuildConfig.VERSION_NAME}",
                style = Typography.labelSmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
        AndroidTextInput()
    }
}