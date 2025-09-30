package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.Subtypes
import org.futo.inputmethod.latin.localeFromString
import org.futo.inputmethod.latin.uix.KeyboardLayoutPreview
import org.futo.inputmethod.latin.uix.SettingsTextEdit
import org.futo.inputmethod.latin.uix.actions.searchMultiple
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.uix.urlEncode
import org.futo.inputmethod.v2keyboard.LayoutManager
import java.text.Normalizer
import java.util.Locale

private val alphaRegex = Regex("[^a-zA-Z\\s]")
fun normalize(str: String): String {
    return Normalizer.normalize(str, Normalizer.Form.NFD)
        .replace(alphaRegex, "")
}


@Preview
@Composable
fun SelectLanguageScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current

    val layoutMapping = remember { LayoutManager.getLayoutMapping(context) }

    val systemLocale = remember { context.resources.configuration.locales[0] }
    val textFieldValue = remember { mutableStateOf("") }

    val locales = remember {
        layoutMapping.keys.toList().sortedBy {
            it.getDisplayName(systemLocale)
        }
    }

    val searchKeys = if(textFieldValue.value.isEmpty()) {
        locales
    } else {
        locales.searchMultiple(textFieldValue.value.lowercase(), limitLength = true, maxDistance = Int.MAX_VALUE) {
            listOf(
                it.language,
                Subtypes.getLocaleDisplayName(it, systemLocale),
                Subtypes.getLocaleDisplayName(it, it),
            ).map { normalize(it.lowercase()) }.flatMap { it.split(" ") }.filter { it.isNotBlank() }
        }
    }

    LazyColumn {
        item {
            ScreenTitle(stringResource(R.string.language_settings_select_language), showBack = true, navController)
        }

        item {
            Box(Modifier.padding(8.dp)) {
                SettingsTextEdit(textFieldValue, icon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.settings_search_menu_title)
                    )
                }, autofocus = true, forceQwerty = true)
            }
        }

        items(searchKeys) {
            NavigationItem(
                title = Subtypes.getLocaleDisplayName(it, systemLocale),
                subtitle = Subtypes.getLocaleDisplayName(it, it),
                style = NavigationItemStyle.MiscNoArrow,
                navigate = {
                    navController.navigate("addLayout/" + it.toLanguageTag().urlEncode())
                }
            )
        }
    }
}


@Composable
fun LayoutPreview(name: String, locale: Locale, onClick: () -> Unit) {
    val context = LocalContext.current
    val layoutName = remember { LayoutManager.getLayout(context, name).name }

    Box(
        Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            KeyboardLayoutPreview(id = name, width = 172.dp, locale = locale)

            Text(
                layoutName,
                style = Typography.SmallMl,
                modifier = Modifier.padding(4.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview
@Composable
fun SelectLayoutsScreen(navController: NavHostController = rememberNavController(), language: String = "en_US") {
    val context = LocalContext.current

    val locale = remember { localeFromString(language) }

    val layoutMapping = remember { LayoutManager.getLayoutMapping(context) }

    val systemLocale = remember { context.resources.configuration.locales[0] }

    val relevantLayouts = remember {
        layoutMapping.entries.filter {
            (it.key.language == locale.language) && (it.key.script == locale.script)
        }.flatMap { it.value }.toSet()
    }

    ScrollableList {
        ScreenTitle(Subtypes.getLocaleDisplayName(locale, locale), showBack = true, navController)

        ScreenTitle(stringResource(R.string.language_settings_select_layout))

        relevantLayouts.forEach {
            LayoutPreview(it, locale) {
                Subtypes.addLanguage(context, locale, it)

                // Go back to languages
                for(x in 0 until 3) navController.navigateUp()
                navController.navigate("languages")
            }
        }
    }
}