package org.futo.inputmethod.latin

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder
import android.widget.Toast
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import org.futo.inputmethod.latin.uix.setSettingBlocking
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.utils.SubtypeLocaleUtils
import org.futo.inputmethod.v2keyboard.LayoutManager
import java.util.Locale
import kotlin.math.sign

fun localeFromString(s: String): Locale =
    Locale.forLanguageTag(s.replace("__#", "-").replace("_", "-"))

fun String.toLocale() = localeFromString(this)

fun Locale.stripExtensionsIfNeeded(): Locale {
    val newLocale = if(Build.VERSION.SDK_INT >= 26) {
        this.stripExtensions().stripExtensions() // TODO: Sometimes this requires two calls??
    } else {
        this
    }

    return newLocale
}

val SubtypesSetting = SettingsKey(
    stringSetPreferencesKey("subtypes"),
    setOf()
)

val ActiveSubtype = SettingsKey(
    stringPreferencesKey("activeSubtype"),
    ""
)

val MultilingualBucketSetting = SettingsKey(
    stringSetPreferencesKey("multilingual_bucket"),
    emptySet()
)

object Subtypes {
    // Removes extensions from existing existing subtypes which are not meant to be there
    private fun removeExtensionsIfNecessary(context: Context) {
        val currentSubtypes = context.getSettingBlocking(SubtypesSetting)
        if(currentSubtypes.isEmpty()) return

        val newSubtypes = currentSubtypes.map {
            val subtype = convertToSubtype(it)
            if(subtype.locale.contains("_#u-")) {
                subtypeToString(InputMethodSubtypeBuilder()
                    .setSubtypeLocale(subtype.locale.split("_#u-")[0])
                    .setSubtypeExtraValue(subtype.extraValue)
                    .setLanguageTag(subtype.languageTag)
                    .build())
            } else {
                it
            }
        }.toSet()

        if(newSubtypes != currentSubtypes) {
            Log.w("Subtypes", "Removed extensions: $currentSubtypes - $newSubtypes")
            context.setSettingBlocking(SubtypesSetting.key, newSubtypes)
        }
    }

    fun addDefaultSubtypesIfNecessary(context: Context) {
        if(!context.isDirectBootUnlocked) return

        val currentSubtypes = context.getSettingBlocking(SubtypesSetting)
        if(currentSubtypes.isNotEmpty()) {
            removeExtensionsIfNecessary(context)
            return
        }

        val locales = context.resources.configuration.locales
        if(locales.size() == 0) return

        var numAdded = 0
        for(i in 0 until locales.size()) {
            val locale = locales.get(i).stripExtensionsIfNeeded()
            val layout = findClosestLocaleLayouts(context, locale).firstOrNull() ?: continue

            addLanguage(context, locale, layout)
            numAdded += 1
        }

        if(numAdded == 0) {
            // We need to have something...
            addLanguage(context, Locale.forLanguageTag("zz"), "qwerty")
        }

        context.setSettingBlocking(ActiveSubtype.key, context.getSettingBlocking(SubtypesSetting).firstOrNull() ?: return)
    }

    fun findClosestLocaleLayouts(context: Context, locale: Locale): List<String> {
        val supportedLocales = LayoutManager.getLayoutMapping(context)

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
        return getSwitchableSubtypes(context).size > 1
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

    // TODO: Set extra value MultilingualTyping=en;lt;etc
    fun makeSubtype(locale: String, layout: String): InputMethodSubtype =
        InputMethodSubtypeBuilder()
            .setSubtypeLocale(locale)
            .setSubtypeExtraValue("KeyboardLayoutSet=$layout")
            .build()

    fun addLanguage(context: Context, language: Locale, layout: String) {
        val value = subtypeToString(makeSubtype(
            language.stripExtensionsIfNeeded().toString(), layout
        ))

        val currentSetting = context.getSettingBlocking(SubtypesSetting)

        context.setSettingBlocking(SubtypesSetting.key, currentSetting + setOf(value))
    }

    fun getName(inputMethodSubtype: InputMethodSubtype): String {
        val locale = getLocale(inputMethodSubtype)
        return getLocaleDisplayName(locale, locale)
    }

    fun getNameForLocale(locale: String): String {
        return getName(InputMethodSubtypeBuilder().setSubtypeLocale(locale).build())
    }

    fun getLocale(locale: String): Locale {
        return localeFromString(locale).stripExtensionsIfNeeded()
    }

    fun getLocale(inputMethodSubtype: InputMethodSubtype): Locale {
        return getLocale(inputMethodSubtype.locale)
    }

    fun getLayoutName(context: Context, layout: String): String {
        return LayoutManager.getLayoutOrNull(context, layout)?.name ?: layout
    }

    fun getLocaleDisplayName(locale: Locale, nameLocale: Locale): String {
        val definedName = LayoutManager.getExceptionalNameForLocale(locale, nameLocale)
        if(definedName != null) return definedName

        val localeString = locale.toString()
        if(SubtypeLocaleUtils.isExceptionalLocale(localeString)) {
            return SubtypeLocaleUtils.getSubtypeLocaleDisplayNameInternal(localeString, nameLocale)
        } else {
            return locale.getDisplayName(nameLocale)
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
        }
    }

    private fun InputMethodSubtype.layoutSetName(): String =
        SubtypeLocaleUtils.getKeyboardLayoutSetName(this)

    private fun multilingualBucketLocales(multilingualBucket: Set<String>): Set<Locale> =
        multilingualBucket.map { getLocale(it) }.toSet()

    private fun multilingualGroupKey(
        subtype: InputMethodSubtype,
        enabledSubtypes: List<InputMethodSubtype>,
        multilingualBucket: Set<Locale>
    ): String {
        val locale = getLocale(subtype)
        val layout = subtype.layoutSetName()

        val localesInSameLayoutBucket = enabledSubtypes.asSequence()
            .filter { it.layoutSetName() == layout }
            .map { getLocale(it) }
            .filter { multilingualBucket.contains(it) }
            .distinct()
            .sortedBy { it.toLanguageTag() }
            .toList()

        return if(multilingualBucket.contains(locale) && localesInSameLayoutBucket.size > 1) {
            "multilingual:$layout:${localesInSameLayoutBucket.joinToString(";") { it.toLanguageTag() }}"
        } else {
            "single:${subtypeToString(subtype)}"
        }
    }

    fun getSwitchableSubtypes(context: Context): List<InputMethodSubtype> =
        getSwitchableSubtypes(
            context.getSettingBlocking(SubtypesSetting),
            context.getSettingBlocking(MultilingualBucketSetting)
        )

    fun getSwitchableSubtypes(
        subtypeSet: Set<String>,
        multilingualBucketSet: Set<String>
    ): List<InputMethodSubtype> {
        val enabledSubtypes = subtypeSet.map { convertToSubtype(it) }
        val multilingualBucket = multilingualBucketLocales(multilingualBucketSet)
        val seen = mutableSetOf<String>()

        return enabledSubtypes.filter { subtype ->
            seen.add(multilingualGroupKey(subtype, enabledSubtypes, multilingualBucket))
        }
    }

    fun isSameSwitchableSubtype(
        subtypeSet: Set<String>,
        multilingualBucketSet: Set<String>,
        firstSubtype: String,
        secondSubtype: InputMethodSubtype
    ): Boolean {
        if(firstSubtype.isEmpty()) return false
        if(firstSubtype == subtypeToString(secondSubtype)) return true

        val enabledSubtypes = subtypeSet.map { convertToSubtype(it) }
        val multilingualBucket = multilingualBucketLocales(multilingualBucketSet)
        val first = convertToSubtype(firstSubtype)

        return multilingualGroupKey(first, enabledSubtypes, multilingualBucket) ==
                multilingualGroupKey(secondSubtype, enabledSubtypes, multilingualBucket)
    }

    fun getSwitchableSubtypeDisplayName(
        context: Context,
        subtype: InputMethodSubtype
    ): String {
        val locale = getLocale(subtype)
        val locales = listOf(locale) + getMultilingualBucket(context, locale, subtype.layoutSetName())

        return locales.distinct().joinToString(" / ") {
            getLocaleDisplayName(it, it)
        }
    }

    fun getDirectBootInitialLayouts(context: Context): Set<String> {
        val layouts = mutableSetOf("en_US:")

        val locales = context.resources.configuration.locales
        if(locales.size() == 0) return layouts

        for(i in 0 until locales.size()) {
            val locale = locales.get(i).stripExtensionsIfNeeded()
            val layout = findClosestLocaleLayouts(context, locale).firstOrNull() ?: continue

            val value = subtypeToString(
                InputMethodSubtypeBuilder()
                    .setSubtypeLocale(locale.stripExtensionsIfNeeded().toString())
                    .setSubtypeExtraValue("KeyboardLayoutSet=$layout")
                    .build()
            )

            layouts.add(value)
        }

        return layouts
    }

    fun switchToNextLanguage(
        context: Context,
        direction: Int
    ): Boolean {
        if(direction == 0) return true

        val enabledSubtypes = getSwitchableSubtypes(context).map { subtypeToString(it) }
        val currentSubtype = context.getSettingBlocking(ActiveSubtype)

        if(enabledSubtypes.isEmpty()) return false

        if(enabledSubtypes.size == 1 && currentSubtype == enabledSubtypes.first()) {
            return false
        }

        val allSubtypes = context.getSettingBlocking(SubtypesSetting)
        val multilingualBucket = context.getSettingBlocking(MultilingualBucketSetting)

        val index = enabledSubtypes.indexOf(currentSubtype).let { directIndex ->
            if(directIndex != -1) {
                directIndex
            } else {
                enabledSubtypes.indexOfFirst {
                    isSameSwitchableSubtype(
                        allSubtypes,
                        multilingualBucket,
                        currentSubtype,
                        convertToSubtype(it)
                    )
                }
            }
        }
        val nextIndex = if(index == -1) {
            0
        } else {
            (index + direction.sign).mod(enabledSubtypes.size)
        }

        context.setSettingBlocking(ActiveSubtype.key, enabledSubtypes[nextIndex])
        return true
    }

    @JvmOverloads
    fun getMultilingualBucket(
        context: Context,
        locale: Locale,
        keyboardLayoutSet: String? = null
    ): List<Locale> {
        val set = multilingualBucketLocales(context.getSetting(MultilingualBucketSetting))
        if(!set.contains(locale)) {
            return emptyList()
        } else {
            val enabledLocales = if(keyboardLayoutSet != null) {
                context.getSettingBlocking(SubtypesSetting)
                    .asSequence()
                    .map { convertToSubtype(it) }
                    .filter { it.layoutSetName() == keyboardLayoutSet }
                    .map { getLocale(it) }
                    .filter { set.contains(it) }
                    .distinct()
                    .toList()
            } else {
                set.toList()
            }

            return enabledLocales.filter { it != locale }
        }
    }
}


@Composable
@Preview
fun LanguageSwitcherDialog(
    onDismiss: () -> Unit = { },
    switchToIme: (InputMethodInfo) -> Unit = { }
) {
    val inspection = LocalInspectionMode.current
    val context = LocalContext.current
    val subtypeSet = if(inspection) {
        setOf("en_US:", "pt_PT:KeyboardLayoutSet=portugues:", "lt:", "fr:KeyboardLayoutSet=bepo:")
    } else {
        useDataStoreValue(SubtypesSetting)
    }

    val multilingualBucketSet = if(inspection) {
        setOf("en_US", "pt_PT")
    } else {
        useDataStoreValue(MultilingualBucketSetting)
    }

    val subtypes = remember(subtypeSet, multilingualBucketSet) {
        Subtypes.getSwitchableSubtypes(subtypeSet, multilingualBucketSet)
    }

    val activeSubtype = if(inspection) {
        "pt_PT:KeyboardLayoutSet=portugues:"
    } else {
        useDataStoreValue(ActiveSubtype)
    }

    val activeIMEs = if(LocalInspectionMode.current) {
        listOf(
            InputMethodInfo("com.example.Keyboard", ".Keyboard", "Joe's Keyboard", ""),
            InputMethodInfo("com.example.Keyboard", ".Keyboard", "Example Keyboard", ""),
            InputMethodInfo("com.example.Keyboard", ".Keyboard", "Company's Very Special Keyboard Application", ""),
            InputMethodInfo("com.example.Keyboard", ".Keyboard", null, ""),
        )
    } else {
        remember {
            RichInputMethodManager.init(context)
            RichInputMethodManager.getInstance().enabledInputMethodList.filter {
                it.packageName != context.packageName
            }
        }
    }

    Surface(shape = RoundedCornerShape(48.dp), color = MaterialTheme.colorScheme.background) {
        Column {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.select_language),
                textAlign = TextAlign.Center,
                style = Typography.Heading.MediumMl,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp, 16.dp)
            )

            LazyColumn(modifier = Modifier.weight(1.0f)) {
                items(subtypes) { subtype ->
                    val layoutSetName = subtype.getExtraValueOf(Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET) ?: ""

                    val layout = if(inspection) { layoutSetName } else { Subtypes.getLayoutName(context, layoutSetName) }
                    val title = if(inspection) { subtype.locale } else { Subtypes.getSwitchableSubtypeDisplayName(context, subtype) }

                    val selected = Subtypes.isSameSwitchableSubtype(
                        subtypeSet,
                        multilingualBucketSet,
                        activeSubtype,
                        subtype
                    )

                    val item = @Composable {
                        NavigationItem(
                            title = title,
                            subtitle = layout.ifBlank { null },
                            style = NavigationItemStyle.MiscNoArrow,
                            navigate = {
                                context.setSettingBlocking(ActiveSubtype.key, Subtypes.subtypeToString(subtype))
                                onDismiss()
                            },
                            icon = if(selected) painterResource(R.drawable.check_circle) else painterResource(R.drawable.circle)
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

                item {
                    if(activeIMEs.isNotEmpty() && subtypes.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))
                    }
                }

                items(activeIMEs) { ime ->
                    val title = try {
                        ime.loadLabel(context.packageManager)?.toString()
                    } catch(_: Exception) {
                        null
                    } ?: ime.id

                    NavigationItem(
                        title = title,
                        style = NavigationItemStyle.MiscNoArrow,
                        navigate = {
                            switchToIme(ime)
                        },
                        compact = true,
                        icon = painterResource(R.drawable.circle)
                    )
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
                    Text(stringResource(R.string.keyboard_switch_keyboard))
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
                    Text(stringResource(R.string.keyboard_language_settings))
                }
                Spacer(modifier = Modifier.width(32.dp))
            }
        }
    }
}
