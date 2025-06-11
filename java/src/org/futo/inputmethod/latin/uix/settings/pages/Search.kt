package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.UserSetting
import org.futo.inputmethod.latin.uix.UserSettingsMenu
import org.futo.inputmethod.latin.uix.actions.searchMultiple

val SettingsMenus = listOf(
    KeyboardSettingsMenu,
    TypingSettingsMenu
)

data class SettingItem(
    val item: UserSetting,
    val origin: UserSettingsMenu
)

val AllSettings = SettingsMenus.flatMap { menu -> menu.settings.map { SettingItem(it, menu) } }

@OptIn(ExperimentalFoundationApi::class)
@Preview(showBackground = true)
@Composable
fun SearchScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val textFieldValue = remember { mutableStateOf(TextFieldValue("")) }

    val searchKeys = if(textFieldValue.value.text.isEmpty()) {
        AllSettings
    } else {
        AllSettings.searchMultiple(textFieldValue.value.text.lowercase(), limitLength = false, maxDistance = Int.MAX_VALUE) {
            listOf(
                context.getString(it.item.name),
                it.item.subtitle?.let { context.getString(it) } ?: "",
            ).map { normalize(it.lowercase()) }/*.flatMap { it.split(" ") }*/.filter { it.isNotBlank() }
        }
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
        items(searchKeys) {
            Box(Modifier.animateItemPlacement()) {
                Text("From ${stringResource(it.origin.title)}")
                it.item.component()
            }
        }
    }
}