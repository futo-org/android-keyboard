package org.futo.inputmethod.latin.uix.settings.pages.pdict

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.futo.inputmethod.engine.GlobalIMEMessage
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.Subtypes
import org.futo.inputmethod.latin.SubtypesSetting
import org.futo.inputmethod.latin.localeFromString
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.IMPORT_SETTINGS_REQUEST
import org.futo.inputmethod.latin.uix.LocalNavController
import org.futo.inputmethod.latin.uix.PersonalWord
import org.futo.inputmethod.latin.uix.SettingsTextEdit
import org.futo.inputmethod.latin.uix.UserDictionaryIO
import org.futo.inputmethod.latin.uix.getImportedUserDictFilesForLocale
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.urlEncode
import java.util.Locale

@Composable
@Preview
fun WordPopupDialog(selectedWord: PersonalWord? = null, locale: Locale? = null) {
    if(locale?.language == "ja") {
        return JapaneseWordPopupDialog(
            selectedWord?.let { decodeJapanesePersonalWord(it) },
            locale
        )
    }

    val navController = LocalNavController.current
    val word = remember { mutableStateOf(selectedWord?.word ?: "") }
    val shortcut = remember { mutableStateOf(selectedWord?.shortcut ?: "") }
    AlertDialog(
        icon = {
        },
        title = {
            Text(
                if (selectedWord == null) {
                    stringResource(R.string.user_dict_settings_add_dialog_title)
                } else {
                    stringResource(R.string.user_dict_settings_edit_dialog_title)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.user_dict_settings_add_word_option_name))
                SettingsTextEdit(word,
                    placeholder = stringResource(R.string.user_dict_settings_add_word_hint))

                Spacer(Modifier.height(12.dp))

                Text(stringResource(R.string.user_dict_settings_add_shortcut_option_name))
                SettingsTextEdit(shortcut,
                    placeholder = stringResource(R.string.user_dict_settings_add_shortcut_hint))

                if (shortcut.value.firstOrNull()?.let {
                        Settings.getInstance().current.isWordCodePoint(it.code)
                    } == false) {
                    Text(
                        stringResource(R.string.personal_dictionary_shortcut_error_symbols),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        onDismissRequest = {
            navController!!.navigateUp()
        },
        confirmButton = {
            val context = LocalContext.current
            Row {
                if (selectedWord != null) {
                    TextButton(
                        onClick = {
                            val udictIo = UserDictionaryIO(context)
                            udictIo.remove(listOf(selectedWord))
                            navController!!.navigateUp()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.user_dict_settings_delete))
                    }
                }
                Spacer(Modifier.weight(1.0f))

                TextButton(
                    onClick = {
                        navController!!.navigateUp()
                    }
                ) {
                    Text(stringResource(R.string.action_emoji_clear_recent_emojis_cancel))
                }

                TextButton(onClick = {
                    val udictIo = UserDictionaryIO(context)
                    if (selectedWord != null) {
                        // Edit existing word by deleting it then re-inserting the new one
                        udictIo.remove(listOf(selectedWord))
                    }

                    val wordToAdd = PersonalWord(
                        word = word.value,
                        shortcut = shortcut.value.ifEmpty { null },
                        locale = locale?.toString(),
                        frequency = 250,
                        appId = 0,
                    )

                    udictIo.put(
                        listOf(
                            wordToAdd
                        ), clear = false
                    )
                    navController!!.navigateUp()
                }, enabled = word.value.isNotBlank()) {
                    Text(stringResource(R.string.user_dict_settings_add_dialog_confirm))
                }
            }
        },
    )
}

@Composable
fun WordPopupDialogF(selectedWord: String? = null, locale: String? = null) {
    WordPopupDialog(
        if(!selectedWord.isNullOrEmpty() && selectedWord != "add") { Json.decodeFromString(selectedWord) } else { null },
        if(!locale.isNullOrEmpty() && locale != "all") { localeFromString(locale) } else { null }
    )
}

@Composable
fun PersonalDictionaryLanguageListForLocale(
    navController: NavHostController,
    backStackEntry: NavBackStackEntry,
    localeString: String = "en"
) {
    val locale = if(localeString == "all") { null } else { localeFromString(localeString) }

    val context = LocalContext.current
    val refreshCounter = remember { mutableIntStateOf(0) }
    val dict = remember { UserDictionaryIO(context) }

    LaunchedEffect(Unit) {
        GlobalIMEMessage.collect {
            refreshCounter.intValue += 1
        }
    }

    DisposableEffect(backStackEntry) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshCounter.intValue += 1
            }
        }

        backStackEntry.lifecycle.addObserver(observer)

        onDispose {
            backStackEntry.lifecycle.removeObserver(observer)
        }
    }

    val words = remember(refreshCounter.intValue) {
        dict.get().filter { it.locale?.let { localeFromString(it) } == locale }
    }
    val files = remember(refreshCounter.intValue) {
        getImportedUserDictFilesForLocale(context, locale ?: return@remember emptyList())
    }
    LazyColumn {
        item {
            ScreenTitle(locale?.getDisplayName(LocalConfiguration.current.locale) ?: stringResource(R.string.user_dict_settings_all_languages), showBack = true, navController = navController)
        }

        if(localeSupportsFileImport(locale) || files.isNotEmpty()) {
            item {
                ScreenTitle(stringResource(R.string.personal_dictionary_imported_additional_files_list_header))
                NavigationItem(
                    title = stringResource(R.string.personal_dictionary_import_additional_file),
                    style = NavigationItemStyle.HomePrimary,
                    navigate = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "text/plain"
                        }
                        (context as Activity).startActivityForResult(intent, IMPORT_SETTINGS_REQUEST)
                    },
                    icon = painterResource(R.drawable.plus_circle)
                )
            }
            items(files) {
                NavigationItem(
                    title = it.second,
                    style = NavigationItemStyle.MiscNoArrow,
                    navigate = {
                        navController.navigate("pdictdelete/${it.first.name.urlEncode()}")
                    },
                    icon = painterResource(R.drawable.file_text)
                )
            }

            item { ScreenTitle(stringResource(R.string.personal_dictionary_words_list_header)) }
        }

        item {
            NavigationItem(
                title = stringResource(R.string.user_dict_settings_add_menu_title),
                style = NavigationItemStyle.HomePrimary,
                navigate = {
                    navController.navigate("pdictword/${localeString}/add")
                },
                icon = painterResource(R.drawable.plus_circle)
            )
        }
        items(words) {
            val jaWord = if(locale?.language == "ja") decodeJapanesePersonalWord(it) else null
            NavigationItem(
                title = jaWord?.output ?: it.word,
                subtitle = jaWord?.furigana ?: it.shortcut,
                style = NavigationItemStyle.MiscNoArrow,
                navigate = {
                    navController.navigate("pdictword/${localeString}/${Json.encodeToString(it).urlEncode()}")
                }
            )
        }
    }
}

@Composable
@Preview(showBackground = true)
fun PersonalDictionaryLanguageList() {
    val nav = LocalNavController.current
    val context = LocalContext.current
    val words = if(!LocalInspectionMode.current) {
        remember { UserDictionaryIO(context).get() }
    } else {
        listOf(PersonalWord(word = "word", frequency = 0, locale = "en", appId = 0, shortcut = null))
    }
    val enabledLanguages = useDataStoreValue(SubtypesSetting).map {
        Subtypes.getLocale(Subtypes.convertToSubtype(it))
    }
    val languages = (words.mapNotNull { it.locale }.map { localeFromString(it) } + enabledLanguages)
        .toSet().map { locale ->
            locale to words.count { it.locale?.let { localeFromString(it) } == locale }
        }
    val allLanguagesCount = words.count { it.locale == null }

    ScrollableList {
        ScreenTitle(stringResource(R.string.edit_personal_dictionary), showBack = true, navController = nav)
        NavigationItem(
            title = stringResource(R.string.user_dict_settings_all_languages),
            subtitle = pluralStringResource(R.plurals.personal_dictionary_language_word_count, allLanguagesCount, allLanguagesCount),
            style = NavigationItemStyle.MiscNoArrow,
            navigate = {
                nav!!.navigate("pdict/all")
            }
        )
        languages.forEach {
            NavigationItem(
                title = it.first.getDisplayName(LocalConfiguration.current.locale),
                subtitle = pluralStringResource(R.plurals.personal_dictionary_language_word_count, it.second, it.second),
                style = NavigationItemStyle.MiscNoArrow,
                navigate = {
                    nav!!.navigate("pdict/" + it.first.toString().urlEncode())
                }
            )
        }
    }
}