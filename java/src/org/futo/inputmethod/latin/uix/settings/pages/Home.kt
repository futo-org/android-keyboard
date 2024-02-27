package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.openLanguageSettings
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.updates.ConditionalUpdate

@Preview(showBackground = true)
@Composable
fun HomeScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    ScrollableList {
        Spacer(modifier = Modifier.height(24.dp))
        ScreenTitle("FUTO Keyboard Settings")

        ConditionalUpdate(navController)

        NavigationItem(
            title = "Languages",
            style = NavigationItemStyle.HomePrimary,
            navigate = { context.openLanguageSettings() },
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


        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "v${BuildConfig.VERSION_NAME}",
            style = Typography.labelSmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}