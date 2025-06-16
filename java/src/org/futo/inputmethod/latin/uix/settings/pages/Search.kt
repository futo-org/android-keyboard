package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.LocalNavController
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.SettingsMenus
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.theme.Typography

@OptIn(ExperimentalFoundationApi::class)
@Preview(showBackground = true)
@Composable
fun SearchScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val textFieldValue = remember { mutableStateOf(TextFieldValue("")) }

    val query = textFieldValue.value.text.lowercase()
    val results = remember(query) {
        SettingsMenus.map { menu ->
            menu to menu.settings.filter { it.name != 0 && it.appearsInSearch }
                .filter {
                    it.searchTags?.let {
                        if (context.getString(it).contains(query)) return@filter true
                    }

                    if (context.getString(it.name).lowercase()
                            .contains(query)
                    ) return@filter true

                    it.subtitle?.let {
                        if (context.getString(it).lowercase()
                                .contains(query)
                        ) return@filter true
                    }

                    return@filter false
                }


        }
    }.map { v ->
        v.first to v.second.map {
            if(it.visibilityCheck?.invoke() == false) {
                userSettingDecorationOnly {
                    val nav = LocalNavController.current
                    NavigationItem(
                        title = stringResource(it.name),
                        style = NavigationItemStyle.MiscNoArrow,
                        subtitle = stringResource(
                            R.string.settings_search_option_exists_but_disabled,
                            stringResource(v.first.title)
                        ),
                        navigate = {
                            nav.navigate(v.first.navPath)
                        }
                    )
                }
            } else {
                it
            }
        }
    }.filter {
        it.second.isNotEmpty()
    }

    LazyColumn {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .padding(8.dp)
                    .height(48.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.language_settings_search)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = textFieldValue.value,
                        onValueChange = { textFieldValue.value = it },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            platformImeOptions = PlatformImeOptions(
                                privateImeOptions = "org.futo.inputmethod.latin.NoSuggestions=1,org.futo.inputmethod.latin.ForceLayout=qwerty,org.futo.inputmethod.latin.ForceLocale=zz"
                            )
                        ),
                        modifier = Modifier.weight(1.0f),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        textStyle = TextStyle.Default.copy(color = MaterialTheme.colorScheme.onSurface)
                    )
                }
            }
        }


        if(query.isBlank()) {
            item {
                Text(
                    stringResource(R.string.settings_search_enter_your_search),
                    style = Typography.Heading.Medium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                )
            }
        } else if(results.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.settings_search_no_options_found),
                    style = Typography.Heading.Medium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                )
            }
        } else {
            results.forEach {
                val menu = it.first
                val settings = it.second
                item {
                    val nav = LocalNavController.current
                    Row(Modifier
                        .clickable {
                            nav.navigate(menu.navPath)
                        }
                        .padding(16.dp)) {
                        Text(
                            stringResource(menu.title),
                            style = Typography.Heading.Medium,
                            modifier = Modifier
                                .align(CenterVertically)
                                .weight(1.0f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }
                items(settings) {
                    it.component()
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                }
            }
        }
    }
}