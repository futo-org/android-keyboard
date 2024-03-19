package org.futo.inputmethod.latin.uix.settings

import android.app.Activity
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.PendingIntent.getBroadcast
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.InfoDialog
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.updates.InstallReceiver
import org.futo.inputmethod.updates.LAST_UPDATE_CHECK_RESULT
import org.futo.inputmethod.updates.UpdateResult
import org.futo.inputmethod.updates.openURI
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

@Composable
fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private fun InputStream.copyToOutputStream(inputStreamLength: Long, outputStream: OutputStream, onProgress: (Float) -> Unit) {
    val buffer = ByteArray(16384);
    var n: Int;
    var total = 0;
    val inputStreamLengthFloat = inputStreamLength.toFloat();

    while (read(buffer).also { n = it } >= 0) {
        total += n;
        outputStream.write(buffer, 0, n);
        onProgress.invoke(total.toFloat() / inputStreamLengthFloat);
    }
}


object UpdateStatus {
    val isDownloading = mutableStateOf(false)
    val downloadText = mutableStateOf("")

    var downloadedUpdate: ByteArrayOutputStream? = null
}

private suspend fun install(scope: CoroutineScope, context: Context, inputStream: InputStream, dataLength: Long, updateStatusText: (String) -> Unit) {
    var lastProgressText = "";
    var session: PackageInstaller.Session? = null;

    try {
        //Log.i("UpdateScreen", "Hooked InstallReceiver.onReceiveResult.")
        InstallReceiver.onReceiveResult.onEach { message -> updateStatusText("Fatal error: $message") }.launchIn(scope)

        val packageInstaller: PackageInstaller = context.packageManager.packageInstaller;
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        val sessionId = packageInstaller.createSession(params);
        session = packageInstaller.openSession(sessionId)

        if(UpdateStatus.downloadedUpdate == null) {
            UpdateStatus.downloadedUpdate = ByteArrayOutputStream(dataLength.toInt()).use { outputStream ->
                inputStream.copyToOutputStream(dataLength, outputStream) { progress ->
                    val progressText = "${(progress * 100.0f).toInt()}%";
                    if (lastProgressText != progressText) {
                        lastProgressText = progressText

                        updateStatusText("Downloading... $progressText")
                    }
                }

                // Note: .use will close the outputStream, but closing has no effect
                // on ByteArrayOutputStream
                outputStream
            }
        }


        session.openWrite("package", 0, dataLength).use { sessionStream ->
            UpdateStatus.downloadedUpdate!!.writeTo(sessionStream)
            session.fsync(sessionStream)
        };

        val intent = Intent(context, InstallReceiver::class.java);
        val pendingIntent = getBroadcast(context, 0, intent, FLAG_MUTABLE or FLAG_UPDATE_CURRENT);
        val statusReceiver = pendingIntent.intentSender;

        session.commit(statusReceiver);
        session.close();

        withContext(Dispatchers.Main) {
            updateStatusText("Installing update")
            //_textProgress.text = "";
            //_text.text = context.resources.getText(R.string.installing_update);
        }
    } catch (e: Throwable) {
        Log.w("UpdateScreen", "Exception thrown while downloading and installing latest version of app.", e);
        session?.abandon();
        withContext(Dispatchers.Main) {
            updateStatusText("Failed to install update")
        }
    } finally {
        withContext(Dispatchers.Main) {
            Log.i("UpdateScreen", "Keep screen on unset install")
            //window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }
}

@DelicateCoroutinesApi
private suspend fun downloadAndInstall(scope: CoroutineScope, context: Context, updateResult: UpdateResult, updateStatusText: (String) -> Unit) = GlobalScope.launch(Dispatchers.IO) {
    UpdateStatus.isDownloading.value = true

    var inputStream: InputStream? = null;
    try {
        val httpClient = OkHttpClient()
        val request = Request.Builder().method("GET", null).url(updateResult.apkUrl).build()

        val response = httpClient.newCall(request).execute()
        val body = response.body
        if (response.isSuccessful && body != null) {
            inputStream = body.byteStream();
            val dataLength = body.contentLength();
            install(scope, context, inputStream, dataLength, updateStatusText)
        } else {
            throw Exception("Failed to download latest version of app.");
        }
    } catch (e: Throwable) {
        Log.w("UpdateScreen", "Exception thrown while downloading and installing latest version of app.", e);
        withContext(Dispatchers.Main) {
            updateStatusText("Failed to download update: ${e.message}");
            context.openURI(updateResult.apkUrl)
        }
    } finally {
        inputStream?.close();
        UpdateStatus.isDownloading.value = false
    }
}

@Composable
fun UpdateDialog(navController: NavHostController) {
    val scope = LocalLifecycleOwner.current
    val context = LocalContext.current
    val updateInfo = remember { runBlocking {
        context.getSetting(LAST_UPDATE_CHECK_RESULT, "")
    } }

    val lastUpdateResult = if(!LocalInspectionMode.current){
        remember { UpdateResult.fromString(updateInfo) }
    } else {
        UpdateResult(123, "abc", "1.2.3")
    }

    val isDownloading = UpdateStatus.isDownloading
    val showSpinner = remember { mutableStateOf(true) }
    val statusText = UpdateStatus.downloadText

    if(lastUpdateResult == null || !lastUpdateResult.isNewer()) {
        InfoDialog(title = "Up-to-date", body = "As of the last update check, the app is up to date.")
    } else {
        AlertDialog(
            icon = {
                Icon(Icons.Filled.Info, contentDescription = "Info")
            },
            title = {
                Text(text = "Update available")
            },
            text = {
                if(isDownloading.value) {
                    KeepScreenOn()
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if(showSpinner.value) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(text = statusText.value)
                    }
                } else {
                    Text(text = "A new version ${lastUpdateResult.nextVersionString} is available, would you like to update?")
                }
            },
            onDismissRequest = {
                if(!isDownloading.value) {
                    navController.navigateUp()
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    GlobalScope.launch { downloadAndInstall(scope.lifecycleScope, context, lastUpdateResult) {
                        statusText.value = it

                        if(!it.endsWith("%")) {
                            showSpinner.value = false
                        }
                    } }
                }, enabled = !isDownloading.value) {
                    Text(stringResource(R.string.update))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    navController.navigateUp()
                }, enabled = !isDownloading.value) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }
}