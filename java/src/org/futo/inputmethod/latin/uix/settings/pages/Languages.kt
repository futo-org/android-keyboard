package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.RichInputMethodManager
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.openLanguageSettings
import org.futo.inputmethod.latin.utils.SubtypeLocaleUtils
import org.futo.inputmethod.updates.openURI

data class LanguageOptions(
    val voiceInputModel: String?,
    val dictionary: String?,
    val transformerModel: String?
)


@Preview
@Composable
fun LanguagesScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    ScrollableList {
        ScreenTitle("Languages", showBack = true, navController)

        NavigationItem(
            title = "Enable/disable languages",
            style = NavigationItemStyle.Misc,
            navigate = { context.openLanguageSettings() },
        )

        Tip("Note: The list below will only update after opening the keyboard")

        RichInputMethodManager.getInstance().getMyEnabledInputMethodSubtypeList(true).forEach {
            val name = SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(it)

            val dummyOptions = LanguageOptions(
                voiceInputModel = "Built-in English-39",
                dictionary = "main.dict",
                transformerModel = null
            )

            ScreenTitle(name)
            NavigationItem(
                title = dummyOptions.voiceInputModel ?: "None",
                style = dummyOptions.voiceInputModel?.let { NavigationItemStyle.HomeTertiary } ?: NavigationItemStyle.MiscNoArrow,
                navigate = {
                    context.openURI("https://keyboard.futo.org/voice-input-models", true)
                },
                icon = painterResource(id = R.drawable.mic_fill)
            )
            NavigationItem(
                title = dummyOptions.dictionary ?: "None",
                style = dummyOptions.dictionary?.let { NavigationItemStyle.HomeTertiary } ?: NavigationItemStyle.MiscNoArrow,
                navigate = {
                   context.openURI("https://codeberg.org/Helium314/aosp-dictionaries#dictionaries", true)
                },
                icon = painterResource(id = R.drawable.book)
            )
            NavigationItem(
                title = dummyOptions.transformerModel ?: "None",
                style = dummyOptions.transformerModel?.let { NavigationItemStyle.HomeTertiary } ?: NavigationItemStyle.MiscNoArrow,
                navigate = {
                    context.openURI("https://keyboard.futo.org/models", true)
                },
                icon = painterResource(id = R.drawable.cpu)
            )
        }
    }
}