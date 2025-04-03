package org.futo.inputmethod.latin.uix.settings.pages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.updates.openURI
import org.futo.inputmethod.latin.R

private fun Context.copyToClipboard(text: CharSequence, label: String = "Copied Text") {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText(label, text)
    clipboardManager.setPrimaryClip(clipData)
}


@Preview(showBackground = true)
@Composable
fun HelpScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current

    val numPresses = remember { mutableIntStateOf(0) }
    var lastToast: MutableState<Toast?> = remember { mutableStateOf(null) }

    ScrollableList {
        ScreenTitle(stringResource(R.string.help_menu_title), showBack = true, navController)

        NavigationItem(
            title = stringResource(R.string.help_menu_version_name, BuildConfig.VERSION_NAME),
            style = NavigationItemStyle.MiscNoArrow,
            navigate = {
                context.copyToClipboard(BuildConfig.VERSION_NAME)
            }
        )
        NavigationItem(
            title = stringResource(R.string.help_menu_version_code, BuildConfig.VERSION_CODE),
            style = NavigationItemStyle.MiscNoArrow,
            navigate = {
                val makeToast: (String) -> Unit = { text ->
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).let {
                        lastToast.value?.cancel()
                        lastToast.value = it
                        it.show()
                    }
                }
                if(runBlocking { context.getSetting(IS_DEVELOPER) }) {
                    makeToast("You're already a developer")
                } else {
                    numPresses.intValue += 1
                    if (numPresses.intValue > 3) {
                        val pressesUntilDeveloper = 8 - numPresses.intValue
                        if (pressesUntilDeveloper <= 0) {
                            makeToast("You're now a developer")
                            runBlocking { context.setSetting(IS_DEVELOPER, true) }
                        } else {
                            makeToast("You are $pressesUntilDeveloper steps until becoming a developer")
                        }
                    }
                }
            }
        )

        Tip(
            stringResource(
                R.string.help_menu_version_tip,
                BuildConfig.VERSION_NAME
            ))

         NavigationItem(title = stringResource(R.string.help_menu_documentation), subtitle = stringResource(R.string.help_menu_documentation_subtitle), style = NavigationItemStyle.Misc, navigate = {
            context.openURI("https://docs.keyboard.futo.org/")
        })
        NavigationItem(title = stringResource(R.string.help_menu_discord), subtitle = stringResource(R.string.help_menu_discord_subtitle), style = NavigationItemStyle.Misc, navigate = {
            context.openURI("https://keyboard.futo.org/discord")
        })
        NavigationItem(title = stringResource(R.string.help_menu_futo_chat), subtitle = stringResource(R.string.help_menu_futo_chat_subtitle), style = NavigationItemStyle.Misc, navigate = {
            context.openURI("https://chat.futo.org/")
        })
        NavigationItem(title = stringResource(R.string.help_menu_github), subtitle = stringResource(
            R.string.help_menu_github_subtitle
        ), style = NavigationItemStyle.Misc, navigate = {
            context.openURI("https://github.com/futo-org/android-keyboard/issues")
        })
        NavigationItem(title = stringResource(R.string.help_menu_email), subtitle = stringResource(
            R.string.help_menu_email_subtitle
        ), style = NavigationItemStyle.Mail, navigate = {
            context.openURI("mailto:keyboard@futo.org")
        })
    }
}