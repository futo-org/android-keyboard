package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.openLanguageSettings

@Preview
@Composable
fun HomeScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    ScrollableList {
        ScreenTitle("FUTO Keyboard Settings")
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
            navigate = { /* TODO */ },
            icon = painterResource(id = R.drawable.eye)
        )

        NavigationItem(
            title = "Advanced",
            style = NavigationItemStyle.Misc,
            navigate = { /* TODO */ },
            icon = painterResource(id = R.drawable.delete)
        )

    }
}