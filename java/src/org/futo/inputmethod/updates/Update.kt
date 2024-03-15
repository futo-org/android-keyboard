package org.futo.inputmethod.updates

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.NavHostController
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.useDataStore

val LAST_UPDATE_CHECK_RESULT = stringPreferencesKey("last_update_check_result")
val LAST_UPDATE_CHECK_FAILED = booleanPreferencesKey("last_update_check_failed")

fun Context.openURI(uri: String, newTask: Boolean = false) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        if (newTask) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        startActivity(intent)
    } catch(e: ActivityNotFoundException) {
        Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
    }
}

@Composable
@Preview
fun ConditionalUpdate(navController: NavHostController) {
    val (updateInfo, _) = useDataStore(key = LAST_UPDATE_CHECK_RESULT, default = "")
    val (lastFailed, _) = useDataStore(key = LAST_UPDATE_CHECK_FAILED, default = false)

    val lastUpdateResult = if(!LocalInspectionMode.current){
        UpdateResult.fromString(updateInfo)
    } else {
        UpdateResult(123, "abc", "1.2.3")
    }

    val context = LocalContext.current
    if(lastUpdateResult != null && lastUpdateResult.isNewer()) {
        SettingItem(
            title = "Update Available",
            subtitle = "${UpdateResult.currentVersionString()} -> ${lastUpdateResult.nextVersionString}",
            onClick = {
                navController.navigate("update")
                //context.openURI(lastUpdateResult.apkUrl)
            }
        ) {
            Icon(Icons.Default.ArrowForward, contentDescription = "Go")
        }
    } else if(lastFailed) {
        SettingItem(
            title = "Failed to check for updates",
            subtitle = "Tap to check manually",
            onClick = {
                context.openURI("https://voiceinput.futo.org/SuperSecretKeyboard/manual_update?version=${BuildConfig.VERSION_CODE}")
            }
        ) {
            Icon(Icons.Default.ArrowForward, contentDescription = "Go")
        }
    }
}
