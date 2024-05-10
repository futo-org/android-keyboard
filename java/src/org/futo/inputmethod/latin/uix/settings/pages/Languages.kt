package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.internal.toImmutableList
import org.futo.inputmethod.latin.BinaryDictionaryGetter
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.RichInputMethodManager
import org.futo.inputmethod.latin.saveSubtypes
import org.futo.inputmethod.latin.uix.FileKind
import org.futo.inputmethod.latin.uix.ResourceHelper
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.namePreferenceKeyFor
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.openLanguageSettings
import org.futo.inputmethod.latin.uix.youAreImporting
import org.futo.inputmethod.latin.utils.SubtypeLocaleUtils
import org.futo.inputmethod.latin.xlm.ModelPaths
import org.futo.inputmethod.updates.openURI
import java.util.Locale

data class LanguageOptions(
    val voiceInputModel: String?,
    val dictionary: String?,
    val transformerModel: String?
)

@Composable
fun ConfirmDeleteResourceDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogTitle: String,
    dialogText: String,
) {
    AlertDialog(
        icon = {
            Icon(painterResource(id = R.drawable.delete), contentDescription = "Example Icon")
        },
        title = {
            Text(text = dialogTitle)
        },
        text = {
            Text(text = dialogText)
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation()
                }
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}

data class DeleteInfo(
    val locale: Locale,
    val kind: FileKind
)

@Preview
@Composable
fun LanguagesScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val deleteDialogInfo: MutableState<DeleteInfo?> = remember { mutableStateOf(null) }

    val inputMethodManager = remember { context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }
    val inputMethodList = remember { mutableStateOf(
        inputMethodManager.getEnabledInputMethodSubtypeList(
            RichInputMethodManager.getInstance().inputMethodInfoOfThisIme,
            true
        ).toImmutableList()
    ) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    LaunchedEffect(lifecycleState) {
        delay(250L)
        inputMethodList.value = inputMethodManager.getEnabledInputMethodSubtypeList(
            RichInputMethodManager.getInstance().inputMethodInfoOfThisIme,
            true
        )

        context.saveSubtypes()
    }


    if(deleteDialogInfo.value != null) {
        val info = deleteDialogInfo.value!!
        ConfirmDeleteResourceDialog(
            onDismissRequest = { deleteDialogInfo.value = null },
            onConfirmation = {
                ResourceHelper.deleteResourceForLanguage(context, info.kind, info.locale)
                deleteDialogInfo.value = null
            },
            dialogTitle = "Delete ${info.kind.youAreImporting()} for ${info.locale.displayLanguage}?",
            dialogText = "If deleted, the imported ${info.kind.youAreImporting()} file for ${info.locale.displayLanguage} will be deleted. If there is no built-in fallback for this language, the feature may cease to function. You can always download and re-import a different ${info.kind.youAreImporting()} file."
        )
    }
    ScrollableList {
        ScreenTitle("Languages", showBack = true, navController)

        NavigationItem(
            title = "Enable/disable languages",
            style = NavigationItemStyle.Misc,
            navigate = { context.openLanguageSettings() },
        )

        Tip("Note: This screen is a WIP, use the above option to toggle languages")

        inputMethodList.value.forEach {
            val name = SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(it)

            val locale = Locale.forLanguageTag(it.locale.replace("_", "-"))

            val voiceInputModelName = ResourceHelper.tryFindingVoiceInputModelForLocale(context, locale)?.name?.let { stringResource(it) }
            val dictionaryName = runBlocking { ResourceHelper.findKeyForLocaleAndKind(context, locale, FileKind.Dictionary) }?.let {
                runBlocking { context.getSetting(FileKind.Dictionary.namePreferenceKeyFor(it), "Dictionary") } + " (Imported)"
            } ?: if(BinaryDictionaryGetter.getDictionaryFiles(locale, context, false, false).isNotEmpty()) {
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
                    if(runBlocking { ResourceHelper.findFileForKind(context, locale, FileKind.VoiceInput) } == null) {
                        context.openURI("https://keyboard.futo.org/voice-input-models", true)
                    } else {
                        deleteDialogInfo.value = DeleteInfo(locale, FileKind.VoiceInput)
                    }
                },
                icon = painterResource(id = R.drawable.mic_fill)
            )
            NavigationItem(
                title = options.dictionary ?: "None",
                style = options.dictionary?.let { NavigationItemStyle.HomeTertiary } ?: NavigationItemStyle.MiscNoArrow,
                navigate = {
                    if(runBlocking { ResourceHelper.findFileForKind(context, locale, FileKind.Dictionary) } == null) {
                        context.openURI("https://keyboard.futo.org/dictionaries", true)
                    } else {
                        deleteDialogInfo.value = DeleteInfo(locale, FileKind.Dictionary)
                    }
                },
                icon = painterResource(id = R.drawable.book)
            )
            NavigationItem(
                title = options.transformerModel ?: "None",
                style = options.transformerModel?.let { NavigationItemStyle.HomeTertiary } ?: NavigationItemStyle.MiscNoArrow,
                navigate = {
                    if(options.transformerModel == null) {
                        context.openURI("https://keyboard.futo.org/models", true)
                    } else {
                        navController.navigate("models")
                    }
                },
                icon = painterResource(id = R.drawable.cpu)
            )

        }
    }
}