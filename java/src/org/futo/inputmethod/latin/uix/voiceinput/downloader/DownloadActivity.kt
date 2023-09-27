package org.futo.inputmethod.latin.uix.voiceinput.downloader

// TODO: Rework this

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.deferGetSetting
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.ThemeOptions
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import org.futo.inputmethod.latin.uix.theme.presets.VoiceInputTheme
import org.futo.voiceinput.shared.ui.theme.Typography
import java.io.File
import java.io.IOException


data class ModelInfo(
    val name: String,
    val url: String,
    var size: Long?,
    var progress: Float = 0.0f,
    var error: Boolean = false,
    var finished: Boolean = false
)

val EXAMPLE_MODELS = listOf(
    ModelInfo(
        name = "tiny-encoder-xatn.tflite",
        url = "example.com",
        size = 56L * 1024L * 1024L,
        progress = 0.5f,
        error = true
    ),
    ModelInfo(
        name = "tiny-decoder.tflite",
        url = "example.com",
        size = 73L * 1024L * 1024L,
        progress = 0.3f,
        error = false
    ),
)

@Composable
fun ModelItem(model: ModelInfo, showProgress: Boolean) {
    Column(modifier = Modifier.padding(4.dp)) {
        val color = if (model.error) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
        Surface(modifier = Modifier, color = color, shape = RoundedCornerShape(4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (model.error) {
                    Icon(
                        Icons.Default.Warning, contentDescription = "Failed", modifier = Modifier
                            .align(CenterVertically)
                            .padding(4.dp)
                    )
                }

                val size = if (model.size != null) {
                    "%.1f".format(model.size!!.toFloat() / 1000000.0f)
                } else {
                    "?"
                }

                Column {
                    Text(model.name, style = Typography.bodyLarge)
                    Text(
                        "$size MB",
                        style = Typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (showProgress && !model.error) {
                        LinearProgressIndicator(
                            progress = model.progress, modifier = Modifier
                                .fillMaxWidth()
                                .padding(0.dp, 8.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

            }
        }
    }
}


@Composable
fun ScreenTitle(title: String, showBack: Boolean = false, navController: NavHostController = rememberNavController()) {
    val rowModifier = if(showBack) {
        Modifier
            .fillMaxWidth()
            .clickable { navController.popBackStack() }
    } else {
        Modifier.fillMaxWidth()
    }
    Row(modifier = rowModifier) {
        Spacer(modifier = Modifier.width(16.dp))

        if(showBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.align(CenterVertically))
            Spacer(modifier = Modifier.width(18.dp))
        }
        Text(title, style = Typography.titleLarge, modifier = Modifier
            .align(CenterVertically)
            .padding(0.dp, 16.dp))
    }
}
@Composable
fun ScrollableList(content: @Composable () -> Unit) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        content()
    }
}



@Composable
@Preview
fun DownloadPrompt(
    onContinue: () -> Unit = {},
    onCancel: () -> Unit = {},
    models: List<ModelInfo> = EXAMPLE_MODELS
) {
    ScrollableList {
        ScreenTitle(title = stringResource(R.string.download_required))
        Text(
            stringResource(R.string.download_required_body),
            style = Typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        for(model in models) {
            ModelItem(model, showProgress = false)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row {
            Button(
                onClick = onCancel, colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ), modifier = Modifier
                    .padding(8.dp)
                    .weight(1.0f)
            ) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = onContinue, modifier = Modifier
                    .padding(8.dp)
                    .weight(1.5f)
            ) {
                Text(stringResource(R.string.continue_))
            }
        }
    }
}


@Composable
@Preview
fun DownloadScreen(models: List<ModelInfo> = EXAMPLE_MODELS) {
    ScrollableList {
        ScreenTitle(stringResource(R.string.download_progress))
        if (models.any { it.error }) {
            Text(
                stringResource(R.string.download_failed),
                style = Typography.bodyMedium
            )
        } else {
            Text(
                stringResource(R.string.download_in_progress),
                style = Typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        for(model in models) {
            ModelItem(model, showProgress = true)
        }
    }
}

fun Context.fileNeedsDownloading(file: String): Boolean {
    return !File(this.filesDir, file).exists()
}

class DownloadActivity : ComponentActivity() {
    private lateinit var modelsToDownload: List<ModelInfo>
    private val httpClient = OkHttpClient()
    private var isDownloading = false

    private val themeOption: MutableState<ThemeOption?> = mutableStateOf(null)
    private fun updateContent() {
        // TODO: In some cases seems to cause a crash?
        // May be related https://github.com/mozilla-mobile/focus-android/issues/7712
        setContent {
            themeOption.value?.let { themeOption ->
                val themeIdx = useDataStore(key = THEME_KEY.key, default = themeOption.key)
                val theme: ThemeOption = ThemeOptions[themeIdx.value] ?: themeOption
                UixThemeWrapper(theme.obtainColors(LocalContext.current)) {
                    // A surface container using the 'background' color from the theme
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (isDownloading) {
                            DownloadScreen(models = modelsToDownload)
                        } else {
                            DownloadPrompt(
                                onContinue = { startDownload() },
                                onCancel = { cancel() },
                                models = modelsToDownload
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startDownload() {
        isDownloading = true
        updateContent()

        modelsToDownload.forEach {
            val request = Request.Builder().method("GET", null).url(it.url).build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    it.error = true
                    updateContent()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.body?.source()?.let { source ->

                        try {
                            it.size = response.headers["content-length"]!!.toLong()
                        } catch (e: Exception) {
                            println("url failed ${it.url}")
                            println(response.headers)
                            e.printStackTrace()
                        }

                        val fileName = it.name + ".download"
                        val file =
                            File.createTempFile(fileName, null, this@DownloadActivity.cacheDir)
                        val os = file.outputStream()

                        val buffer = ByteArray(128 * 1024)
                        var downloaded = 0
                        while (true) {
                            val read = source.read(buffer)
                            if (read == -1) {
                                break
                            }

                            os.write(buffer.sliceArray(0 until read))

                            downloaded += read

                            if (it.size != null) {
                                it.progress = downloaded.toFloat() / it.size!!.toFloat()
                            }

                            lifecycleScope.launch {
                                withContext(Dispatchers.Main) {
                                    updateContent()
                                }
                            }
                        }

                        it.finished = true
                        it.progress = 1.0f
                        os.flush()
                        os.close()

                        assert(file.renameTo(File(this@DownloadActivity.filesDir, it.name)))

                        if (modelsToDownload.all { a -> a.finished }) {
                            downloadsFinished()
                        }
                    }
                }
            })
        }
    }

    private fun cancel() {
        val returnIntent = Intent()
        setResult(RESULT_CANCELED, returnIntent)
        finish()
    }

    private fun downloadsFinished() {
        val returnIntent = Intent()
        setResult(RESULT_OK, returnIntent)
        finish()
    }

    private fun obtainModelSizes() {
        modelsToDownload.forEach {
            val request =
                Request.Builder().method("HEAD", null).header("accept-encoding", "identity")
                    .url(it.url).build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    it.error = true
                    updateContent()
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        it.size = response.headers["content-length"]!!.toLong()
                    } catch (e: Exception) {
                        println("url failed ${it.url}")
                        println(response.headers)
                        e.printStackTrace()
                        it.error = true
                    }

                    if (response.code != 200) {
                        println("Bad response code ${response.code}")
                        it.error = true
                    }
                    updateContent()
                }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val models = intent.getStringArrayListExtra("models")
            ?: throw IllegalStateException("intent extra `models` must be specified for DownloadActivity")

        modelsToDownload = models.distinct().filter { this.fileNeedsDownloading(it) }.map {
            ModelInfo(
                name = it,
                url = "https://voiceinput.futo.org/VoiceInput/${it}",
                size = null,
                progress = 0.0f
            )
        }

        if (modelsToDownload.isEmpty()) {
            cancel()
        }

        isDownloading = false

        deferGetSetting(THEME_KEY) {
            val themeOptionFromSettings = ThemeOptions[it]
            val themeOption = when {
                themeOptionFromSettings == null -> VoiceInputTheme
                !themeOptionFromSettings.available(this) -> VoiceInputTheme
                else -> themeOptionFromSettings
            }

            this.themeOption.value = themeOption
        }

        updateContent()
        obtainModelSizes()
    }
}
