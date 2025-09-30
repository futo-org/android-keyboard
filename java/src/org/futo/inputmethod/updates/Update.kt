package org.futo.inputmethod.updates

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.pages.ParagraphText
import org.futo.inputmethod.latin.uix.settings.pages.PaymentSurface
import org.futo.inputmethod.latin.uix.settings.pages.PaymentSurfaceHeading
import org.futo.inputmethod.latin.uix.settings.useDataStore

val LAST_UPDATE_CHECK_RESULT = stringPreferencesKey("last_update_check_result")
val LAST_UPDATE_CHECK_FAILED = booleanPreferencesKey("last_update_check_failed")

val DISABLE_UPDATE_REMINDER = SettingsKey(booleanPreferencesKey("disable_update_reminder"), false)

val DEFER_MANUAL_UPDATE_UNTIL = longPreferencesKey("defer_manual_update_until")
val DEFERMENT_VERSION = intPreferencesKey("defer_manual_update_version")
const val MANUAL_UPDATE_PERIOD_MS = 1000L * 60L * 60L * 24L * 7L * 10L // Every ten weeks (~2.5 months)

suspend fun deferManualUpdate(context: Context) {
    context.setSetting(
        DEFER_MANUAL_UPDATE_UNTIL,
        System.currentTimeMillis() + MANUAL_UPDATE_PERIOD_MS
    )
    context.setSetting(
        DEFERMENT_VERSION,
        BuildConfig.VERSION_CODE
    )
}

suspend fun isManualUpdateTimeExpired(context: Context): Boolean {
    if(context.getSetting(DEFERMENT_VERSION, 0) < BuildConfig.VERSION_CODE) {
        deferManualUpdate(context)
        return false
    }

    if(context.getSetting(DISABLE_UPDATE_REMINDER)) {
        return false
    }

    val defermentTime = context.getSetting(DEFER_MANUAL_UPDATE_UNTIL, Long.MAX_VALUE)
    return (System.currentTimeMillis() > defermentTime)
}

val LAST_VERSION = longPreferencesKey("last_version")
suspend fun autoDeferManualUpdateIfNeeded(context: Context) {
    if(context.getSetting(LAST_VERSION, 0L) != BuildConfig.VERSION_CODE.toLong()) {
        context.setSetting(LAST_VERSION, BuildConfig.VERSION_CODE.toLong())
        deferManualUpdate(context)
    }
}

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

fun Context.openManualUpdateCheck() {
    openURI("https://keyboard.futo.org/manual_update?version=${BuildConfig.VERSION_CODE}&build=${BuildConfig.FLAVOR}".let {
        if(BuildConfig.BRANCH != "master") {
            it + "&branch=${BuildConfig.BRANCH}&name=${BuildConfig.VERSION_NAME}"
        } else {
            it
        }
    }, newTask = true)
}

@Composable
@Preview
fun ConditionalUpdate(navController: NavHostController = rememberNavController()) {
    if(!BuildConfig.UPDATE_CHECKING) return

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
            title = stringResource(R.string.settings_update_available),
            subtitle = "${UpdateResult.currentVersionString()} -> ${lastUpdateResult.nextVersionString}",
            onClick = {
                navController.navigate("update")
                //context.openURI(lastUpdateResult.apkUrl)
            }
        ) {
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    } else if(lastFailed) {
        SettingItem(
            title = stringResource(R.string.settings_check_for_updates_manually),
            onClick = {
                context.openManualUpdateCheck()
            }
        ) {
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}

val dismissedMigrateUpdateNotice = SettingsKey(
    key = booleanPreferencesKey("dismissedMigrateFdroidObtainiumNotice"),
    default = BuildConfig.IS_PLAYSTORE_BUILD
)

@Composable
@Preview
fun ConditionalMigrateUpdateNotice() {
    val context = LocalContext.current
    val value = useDataStore(dismissedMigrateUpdateNotice, blocking = true)
    if(!value.value) {
        PaymentSurface(isPrimary = true) {
            PaymentSurfaceHeading(stringResource(R.string.manual_update_notice_title))

            ParagraphText(stringResource(R.string.manual_update_notice_paragraph_1))

            if(booleanResource(R.bool.use_dev_styling)) {
                ParagraphText(stringResource(R.string.manual_update_notice_dev_paragraph))
            } else {
                ParagraphText(stringResource(R.string.manual_update_notice_paragraph_2))
            }

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.weight(1.0f)) {
                    Button(onClick = {
                        context.openURI("https://keyboard.futo.org/#downloads")
                    }, modifier = Modifier.align(Alignment.Center)) {
                        Text(stringResource(R.string.manual_update_notice_visit_site_button))
                    }
                }
                Box(modifier = Modifier.weight(1.0f)) {
                    Button(
                        onClick = { value.setValue(true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ), modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text(stringResource(R.string.manual_update_notice_dismiss_notice_button))
                    }
                }
            }
        }
    }
}
