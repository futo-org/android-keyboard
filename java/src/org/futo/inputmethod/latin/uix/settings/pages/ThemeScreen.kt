package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.selector.ThemePicker

@Preview
@Composable
fun ThemeScreen(navController: NavHostController = rememberNavController()) {
    val (theme, setTheme) = useDataStore(THEME_KEY.key, THEME_KEY.default)

    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTitle("Theme", showBack = true, navController)
        ThemePicker {
            setTheme(it.key)
        }
    }
}