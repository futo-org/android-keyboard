package org.futo.inputmethod.latin.uix.settings.pages

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputSession
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.uix.KeyboardLayoutPreview
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.v2keyboard.LayoutManager

@Composable
fun DevLayoutList(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val names = remember {
        LayoutManager.getAllLayoutNames(context)
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val textInputService = LocalTextInputService.current
    val session = remember { mutableStateOf<TextInputSession?>(null) }

    LazyVerticalGrid(
        modifier = Modifier.fillMaxWidth(),
        columns = GridCells.Adaptive(minSize = 172.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenTitle(title = "Layouts")
        }

        items(names) { name ->
            val language = remember(name) {
                LayoutManager.getLayout(context, name).languages.firstOrNull()
            }

            Box(
                Modifier.padding(4.dp).fillMaxWidth().border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(8.dp)
                ).clickable {

                    session.value = textInputService?.startInput(
                        TextFieldValue(""),
                        imeOptions = ImeOptions.Default.copy(
                            platformImeOptions = PlatformImeOptions(
                                privateImeOptions = "org.futo.inputmethod.latin.NoSuggestions=1,org.futo.inputmethod.latin.ForceLayout=$name,org.futo.inputmethod.latin.ForceLocale=$language"
                            )
                        ),
                        onEditCommand = {
                            Log.i("DevLayoutEditor", "onEditCommand: $it")
                        },
                        onImeActionPerformed = {
                            Log.i("DevLayoutEditor", "onImeActionPerformed: $it")
                        }
                    )
                }) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    KeyboardLayoutPreview(id = name, width = 172.dp)
                    Text(
                        name,
                        style = Typography.SmallMl,
                        modifier = Modifier.padding(4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}