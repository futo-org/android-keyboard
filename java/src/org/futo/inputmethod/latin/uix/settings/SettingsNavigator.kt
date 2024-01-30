package org.futo.inputmethod.latin.uix.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.ErrorDialog
import org.futo.inputmethod.latin.uix.InfoDialog
import org.futo.inputmethod.latin.uix.settings.pages.FinetuneModelScreen
import org.futo.inputmethod.latin.uix.settings.pages.HomeScreen
import org.futo.inputmethod.latin.uix.settings.pages.ModelDeleteConfirmScreen
import org.futo.inputmethod.latin.uix.settings.pages.ModelManagerScreen
import org.futo.inputmethod.latin.uix.settings.pages.ModelScreenNav
import org.futo.inputmethod.latin.uix.settings.pages.PredictiveTextScreen
import org.futo.inputmethod.latin.uix.settings.pages.PrivateModelExportConfirmation
import org.futo.inputmethod.latin.uix.settings.pages.ThemeScreen
import org.futo.inputmethod.latin.uix.settings.pages.TypingScreen
import org.futo.inputmethod.latin.uix.settings.pages.VoiceInputScreen
import org.futo.inputmethod.latin.uix.urlDecode
import org.futo.inputmethod.latin.uix.urlEncode
import java.io.File

// Utility function for quick error messages
fun NavHostController.navigateToError(title: String, body: String) {
    this.navigate("error/${title.urlEncode()}/${body.urlEncode()}")
}

fun NavHostController.navigateToInfo(title: String, body: String) {
    this.navigate("info/${title.urlEncode()}/${body.urlEncode()}")
}

@Composable
fun SettingsNavigator(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") { HomeScreen(navController) }
        composable("predictiveText") { PredictiveTextScreen(navController) }
        composable("typing") { TypingScreen(navController) }
        composable("voiceInput") { VoiceInputScreen(navController) }
        composable("themes") { ThemeScreen(navController) }
        composable("models") { ModelManagerScreen(navController) }
        composable("finetune/{modelPath}") {
            val path = it.arguments!!.getString("modelPath")!!.urlDecode()
            FinetuneModelScreen(
                File(path), navController
            )

        }
        composable("finetune") {
            FinetuneModelScreen(file = null, navController = navController)
        }
        composable("model/{modelPath}") {
            val path = it.arguments!!.getString("modelPath")!!.urlDecode()
            ModelScreenNav(
                File(path), navController
            )
        }
        dialog("modelExport/{modelPath}") {
            PrivateModelExportConfirmation(
                File(it.arguments!!.getString("modelPath")!!.urlDecode()),
                navController
            )
        }
        dialog("modelDelete/{modelPath}") {
            val path = it.arguments!!.getString("modelPath")!!.urlDecode()
            ModelDeleteConfirmScreen(File(path), navController)
        }
        dialog("error/{title}/{body}") {
            ErrorDialog(
                it.arguments?.getString("title")?.urlDecode() ?: stringResource(R.string.unknown_error),
                it.arguments?.getString("body")?.urlDecode() ?: stringResource(R.string.an_unknown_error_has_occurred),
                navController
            )
        }
        dialog("info/{title}/{body}") {
            InfoDialog(
                it.arguments?.getString("title")?.urlDecode() ?: "",
                it.arguments?.getString("body")?.urlDecode() ?: "",
                navController
            )
        }
    }
}