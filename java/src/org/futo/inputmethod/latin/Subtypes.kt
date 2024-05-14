package org.futo.inputmethod.latin

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import okhttp3.internal.toImmutableList
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.setSettingBlocking
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
import org.futo.inputmethod.latin.uix.settings.pages.LocaleLayoutMap
import org.futo.inputmethod.latin.uix.settings.useDataStoreValueBlocking
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.utils.SubtypeLocaleUtils
import java.util.Locale

val SubtypesSetting = SettingsKey(
    stringSetPreferencesKey("subtypes"),
    setOf()
)

val ActiveSubtype = SettingsKey(
    stringPreferencesKey("activeSubtype"),
    ""
)

object Subtypes {
    fun addDefaultSubtypesIfNecessary(context: Context) {
        val currentSubtypes = context.getSettingBlocking(SubtypesSetting)
        if(currentSubtypes.isNotEmpty()) return

        val locales = context.resources.configuration.locales
        if(locales.size() == 0) return

        var numAdded = 0
        for(i in 0 until locales.size()) {
            val locale = locales.get(i)
            val layout = findClosestLocaleLayouts(locale).firstOrNull() ?: continue

            addLanguage(context, locale, layout)
            numAdded += 1
        }

        if(numAdded == 0) {
            // We need to have something...
            addLanguage(context, Locale.forLanguageTag("zz"), "qwerty")
        }

        context.setSettingBlocking(ActiveSubtype.key, context.getSettingBlocking(SubtypesSetting).first())
    }

    fun findClosestLocaleLayouts(locale: Locale): List<String> {
        val supportedLocales = LocaleLayoutMap.toList().associate {
            getLocale(it.first) to it.second
        }

        val perfectMatch = supportedLocales.keys.find { it.language == locale.language && it.country == locale.country }
        val languageMatch = supportedLocales.keys.find { it.language == locale.language }

        val match = perfectMatch ?: languageMatch

        return match?.let { supportedLocales[it] } ?: listOf()
    }

    fun convertToSubtype(string: String): InputMethodSubtype {
        val splits = string.split(":")
        val locale = splits[0]

        val extraValue = splits.getOrNull(1) ?: ""
        val languageTag = splits.getOrNull(2) ?: ""

        return InputMethodSubtypeBuilder()
            .setSubtypeLocale(locale)
            .setSubtypeExtraValue(extraValue)
            .setLanguageTag(languageTag)
            .build()
    }

    fun getActiveSubtype(context: Context): InputMethodSubtype {
        val activeSubtype = context.getSettingBlocking(ActiveSubtype).ifEmpty {
            context.getSettingBlocking(SubtypesSetting).firstOrNull() ?: "en_US:"
        }

        return convertToSubtype(activeSubtype)
    }

    fun hasMultipleEnabledSubtypes(context: Context): Boolean {
        return context.getSettingBlocking(SubtypesSetting).size > 1
    }

    fun subtypeToString(subtype: InputMethodSubtype): String {
        return subtype.locale + ":" + (subtype.extraValue ?: "") + ":" + subtype.languageTag
    }

    fun removeLanguage(context: Context, entry: InputMethodSubtype) {
        val value = subtypeToString(entry)
        val currentSetting = context.getSettingBlocking(SubtypesSetting)

        context.setSettingBlocking(SubtypesSetting.key, currentSetting.filter { it != value && it != value.replace("::", ":") }.toSet())

        if(context.getSettingBlocking(ActiveSubtype) == value) {
            context.setSettingBlocking(ActiveSubtype.key, currentSetting.find {
                it != value
            } ?: "")
        }
    }

    fun addLanguage(context: Context, language: Locale, layout: String) {
        val value = subtypeToString(
            InputMethodSubtypeBuilder()
                .setSubtypeLocale(language.toString())
                .setSubtypeExtraValue("KeyboardLayoutSet=$layout")
                .build()
        )
        val currentSetting = context.getSettingBlocking(SubtypesSetting)

        context.setSettingBlocking(SubtypesSetting.key, currentSetting + setOf(value))
    }

    fun getName(inputMethodSubtype: InputMethodSubtype): String {
        return SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(inputMethodSubtype)
    }

    fun getNameForLocale(locale: String): String {
        return getName(InputMethodSubtypeBuilder().setSubtypeLocale(locale).build())
    }

    fun getLocale(locale: String): Locale {
        return Locale.forLanguageTag(locale.replace("_", "-"))
    }

    fun getLocale(inputMethodSubtype: InputMethodSubtype): Locale {
        return getLocale(inputMethodSubtype.locale)
    }

    fun getLayoutName(context: Context, layout: String): String {
        val resourceId = context.resources.getIdentifier("layout_$layout", "string", context.packageName)
        if(resourceId == 0){
            return layout
        } else {
            return context.getString(resourceId)
        }
    }

    fun layoutsMappedByLanguage(layouts: Set<String>): Map<String, List<InputMethodSubtype>> {
        val subtypes = layouts.map {
            convertToSubtype(it)
        }

        return HashMap<String, MutableList<InputMethodSubtype>>().apply {
            subtypes.forEach {
                val list = this.getOrPut(it.locale) { mutableListOf() }
                list.add(it)
            }
        }.mapValues { it.value.toImmutableList() }
    }
}


@Composable
@Preview
fun LanguageSwitcherDialog(
    onDismiss: () -> Unit = { }
) {
    val inspection = LocalInspectionMode.current
    val context = LocalContext.current
    val subtypeSet = if(inspection) {
        setOf("en_US:", "pt_PT:", "lt:", "fr:KeyboardLayoutSet=bepo:")
    } else {
        useDataStoreValueBlocking(SubtypesSetting)
    }

    val subtypes = remember(subtypeSet) {
        Subtypes.layoutsMappedByLanguage(subtypeSet)
    }

    val keys = remember(subtypes) { subtypes.keys.toList().sorted() }

    val activeSubtype = if(inspection) {
        "pt_PT:"
    } else {
        useDataStoreValueBlocking(ActiveSubtype)
    }

    Surface(shape = RoundedCornerShape(48.dp), color = MaterialTheme.colorScheme.background) {
        Column {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Select language",
                textAlign = TextAlign.Center,
                style = Typography.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp, 16.dp)
            )

            LazyColumn(modifier = Modifier.weight(1.0f)) {
                items(keys) { locale ->

                    subtypes[locale]!!.forEach { subtype ->
                        val layout = Subtypes.getLayoutName(context,
                            subtype.getExtraValueOf(Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET) ?: ""
                        )

                        val title = if(inspection) { subtype.locale } else { Subtypes.getName(subtype) }

                        val selected = activeSubtype == Subtypes.subtypeToString(subtype)

                        val item = @Composable {
                            NavigationItem(
                                title = title,
                                subtitle = layout.ifBlank { null },
                                style = NavigationItemStyle.MiscNoArrow,
                                navigate = {
                                    context.setSettingBlocking(ActiveSubtype.key, Subtypes.subtypeToString(subtype))
                                    onDismiss()
                                }
                            )
                        }

                        if (selected) {
                            Surface(color = MaterialTheme.colorScheme.primary) {
                                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimary) {
                                    item()
                                }
                            }
                        } else {
                            item()
                        }

                    }
                }
            }

            Row(modifier = Modifier.height(64.dp)) {
                Spacer(modifier = Modifier.weight(1.0f))
                TextButton(onClick = {
                    val inputMethodManager =
                        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.showInputMethodPicker()

                    onDismiss()
                }) {
                    Text("Switch Keyboard")
                }
                TextButton(onClick = {
                    val intent = Intent()
                    intent.setClass(context, SettingsActivity::class.java)
                    intent.setFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                    intent.putExtra("navDest", "languages")
                    context.startActivity(intent)

                    onDismiss()
                }) {
                    Text("Languages")
                }
                Spacer(modifier = Modifier.width(32.dp))
            }
        }
    }
}