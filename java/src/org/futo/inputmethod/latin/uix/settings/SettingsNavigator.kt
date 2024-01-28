package org.futo.inputmethod.latin.uix.settings

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.uix.settings.pages.HomeScreen
import org.futo.inputmethod.latin.uix.settings.pages.ManageModelScreen
import org.futo.inputmethod.latin.uix.settings.pages.ModelManagerScreen
import org.futo.inputmethod.latin.uix.settings.pages.PredictiveTextScreen
import org.futo.inputmethod.latin.uix.settings.pages.ThemeScreen
import org.futo.inputmethod.latin.uix.settings.pages.TrainDevScreen
import org.futo.inputmethod.latin.uix.settings.pages.TypingScreen
import org.futo.inputmethod.latin.uix.settings.pages.VoiceInputScreen
import org.futo.inputmethod.latin.xlm.ModelInfoLoader
import java.io.File
import java.net.URLDecoder

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
        composable("trainDev") { TrainDevScreen(navController) }
        composable("models") { ModelManagerScreen(navController) }
        composable("model/{modelPath}") {
            val path = URLDecoder.decode(it.arguments!!.getString("modelPath")!!, "utf-8")
            val model = ModelInfoLoader(name = "", path = File(path)).loadDetails()
            ManageModelScreen(model = model, navController)
        }
    }
}