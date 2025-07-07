package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.CustomAccentColor
import org.futo.inputmethod.latin.uix.CustomBaseColor
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview
import org.futo.inputmethod.latin.uix.theme.presets.CustomTheme

@Composable
fun ThemeGeneratorScreen(navController: NavHostController) {
    val (accent, setAccent) = useDataStore(CustomAccentColor)
    val (base, setBase) = useDataStore(CustomBaseColor)
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(stringResource(R.string.theme_generator_title), showBack = true, navController)
        OutlinedTextField(
            value = accent,
            onValueChange = setAccent,
            label = { Text(stringResource(R.string.theme_generator_accent)) },
            modifier = Modifier.fillMaxSize().padding(16.dp, 16.dp, 16.dp, 8.dp)
        )
        OutlinedTextField(
            value = base,
            onValueChange = setBase,
            label = { Text(stringResource(R.string.theme_generator_base)) },
            modifier = Modifier.fillMaxSize().padding(16.dp, 8.dp)
        )
        Button(onClick = { navController.navigateUp() }, modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.theme_generator_save))
        }
        ThemePreview(CustomTheme, modifier = Modifier.padding(16.dp)) {}
    }
}
