package org.futo.inputmethod.latin.uix.settings.pages.themes

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.uix.theme.ZipThemes
import org.futo.inputmethod.latin.uix.urlDecode

@Composable
fun CustomThemeScreen(imgUri: String, navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val bitmap = remember {
        try {
            val uri = imgUri.urlDecode().toUri()
            val resolver = context.contentResolver
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch(e: Exception) {
            navController.navigateUp()
            null
        }
    }

    val nextAvailableName = remember {
        var i = 0
        val themes = ZipThemes.listCustom(context).map { it.name }
        while(themes.contains("$i")) i++
        "$i"
    }

    if(bitmap != null) {
        ThemeEditor(navController, name = nextAvailableName, startingBitmap = bitmap)
    }
}

