package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.updates.openURI

@Preview(showBackground = true)
@Composable
fun HelpScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current

    ScrollableList {
        ScreenTitle("Help & Feedback", showBack = true, navController)

        Tip("We want to hear from you! If you're reporting an issue, your version may be relevant: v${BuildConfig.VERSION_NAME}")

        NavigationItem(title = "Wiki", style = NavigationItemStyle.Misc, navigate = {
            context.openURI("https://gitlab.futo.org/alex/keyboard-wiki/-/wikis/FUTO-Keyboard")
        })
        NavigationItem(title = "Discord Server", style = NavigationItemStyle.Misc, navigate = {
            context.openURI("https://keyboard.futo.org/discord")
        })
        NavigationItem(title = "FUTO Chat", style = NavigationItemStyle.Misc, navigate = {
            context.openURI("https://chat.futo.org/")
        })
        NavigationItem(title = "Email keyboard@futo.org", style = NavigationItemStyle.Misc, navigate = {
            context.openURI("mailto:keyboard@futo.org")
        })
    }
}