package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.BinaryDictionaryGetter
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.RichInputMethodManager
import org.futo.inputmethod.latin.uix.FileKind
import org.futo.inputmethod.latin.uix.ResourceHelper
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.openLanguageSettings
import org.futo.inputmethod.latin.utils.SubtypeLocaleUtils
import org.futo.inputmethod.latin.xlm.ModelPaths
import org.futo.inputmethod.updates.openURI
import java.util.Locale

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

        Tip("Note: This screen is a WIP, use the above option to toggle languages. The list below only updates after opening the keyboard")

        RichInputMethodManager.getInstance().getMyEnabledInputMethodSubtypeList(true).forEach {
            val name = SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(it)

            val locale = Locale.forLanguageTag(it.locale.replace("_", "-"))

            val voiceInputModelName = ResourceHelper.tryFindingVoiceInputModelForLocale(context, locale)?.name?.let { stringResource(it) }
            val dictionaryName = runBlocking { ResourceHelper.findFileForKind(context, locale, FileKind.Dictionary) }?.let {
                "Imported Dictionary"
            } ?: if(BinaryDictionaryGetter.getDictionaryFiles(locale, context, false, false).let {
                println("DICTIONARIES FOR ${locale.displayLanguage}: ${it.toList().map { it.mFilename }.joinToString(",")}")
                    it
                }.isNotEmpty()) {
                    "Built-in Dictionary"
            } else {
                    null
            }

            val transformerName = runBlocking { ModelPaths.getModelOptions(context) }.get(locale.language)?.let {
                it.loadDetails()?.name
            }

            val options = LanguageOptions(
                voiceInputModel = voiceInputModelName,
                dictionary = dictionaryName,
                transformerModel = transformerName
            )

            ScreenTitle(name)

            NavigationItem(
                title = options.voiceInputModel ?: "None",
                style = options.voiceInputModel?.let { NavigationItemStyle.HomeTertiary } ?: NavigationItemStyle.MiscNoArrow,
                navigate = {
                    context.openURI("https://keyboard.futo.org/voice-input-models", true)
                },
                icon = painterResource(id = R.drawable.mic_fill)
            )
            NavigationItem(
                title = options.dictionary ?: "None",
                style = options.dictionary?.let { NavigationItemStyle.HomeTertiary } ?: NavigationItemStyle.MiscNoArrow,
                navigate = {
                   context.openURI("https://codeberg.org/Helium314/aosp-dictionaries#dictionaries", true)
                },
                icon = painterResource(id = R.drawable.book)
            )
            NavigationItem(
                title = options.transformerModel ?: "None",
                style = options.transformerModel?.let { NavigationItemStyle.HomeTertiary } ?: NavigationItemStyle.MiscNoArrow,
                navigate = {
                    context.openURI("https://keyboard.futo.org/models", true)
                },
                icon = painterResource(id = R.drawable.cpu)
            )

        }
    }
}