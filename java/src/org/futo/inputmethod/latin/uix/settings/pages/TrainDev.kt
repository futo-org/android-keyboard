package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Context
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.xlm.AdapterTrainerBuilder
import org.futo.inputmethod.latin.xlm.TrainingDataGenerator
import org.futo.inputmethod.latin.xlm.loadHistoryLogBackup
import org.futo.inputmethod.latin.xlm.HistoryLogForTraining
import org.futo.inputmethod.latin.uix.theme.Typography
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream


private fun getPathToModelResource(
    context: Context,
    modelResource: Int,
    tokenizerResource: Int,
    forceDelete: Boolean
): Pair<String, String> {
    val outputDir = context.cacheDir
    val outputFile = File(outputDir, "ggml-model-$modelResource.gguf")
    val outputFileTokenizer = File(
        outputDir,
        "tokenizer-$tokenizerResource.tokenizer"
    )
    if (forceDelete && outputFile.exists()) {
        outputFile.delete()
        outputFileTokenizer.delete()
    }
    if (!outputFile.exists() || forceDelete) {
        // FIXME: We save this to a random temporary file so that we can have a path instead of an InputStream
        val `is` = context.resources.openRawResource(modelResource)
        val is_t = context.resources.openRawResource(tokenizerResource)
        try {
            val os: OutputStream = FileOutputStream(outputFile)
            var read = 0
            val bytes = ByteArray(1024)
            while (`is`.read(bytes).also { read = it } != -1) {
                os.write(bytes, 0, read)
            }
            os.flush()
            os.close()
            `is`.close()
            val os_t: OutputStream = FileOutputStream(outputFileTokenizer)
            read = 0
            while (is_t.read(bytes).also { read = it } != -1) {
                os_t.write(bytes, 0, read)
            }
            os_t.flush()
            os_t.close()
            is_t.close()
        } catch (e: IOException) {
            e.printStackTrace()
            throw RuntimeException("Failed to write model asset to file")
        }
    }
    return Pair(outputFile.absolutePath, outputFileTokenizer.absolutePath)
}


val exampleText = """
What is FUTO?
FUTO is an organization dedicated to developing, both through in-house engineering and investment, technologies that frustrate centralization and industry consolidation.
FUTO believes in the power of individual freedom and economic competition, yet we must concede the free market is failing to challenge the Tech Giants. Anti-trust enforcement has proven impotent to restore a balance that would actually threaten the oligopoly’s domination.
FUTO Can Help
GrayJay - A universal video app for following creators, not platforms.
Circles - A private photo sharing feed for families.
Live Captions - Accessible live captions that are completely private.
Polycentric - A distributed text-based social network centered around communities.
FUBS - A frictionless and modifiable software development system.
Harbor - An app for preserving identity on the internet.
FUTO Voice Input - A privacy-friendly voice input application.
All FUTO companies and FUTO-funded projects are expected to remain fiercely independent.
""".trimIndent()

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun TrainDevScreen(navController: NavHostController = rememberNavController()) {
    var trainText by remember { mutableStateOf(exampleText.trim()) }
    var isTraining by remember { mutableStateOf(false) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val data = mutableListOf<HistoryLogForTraining>()
        loadHistoryLogBackup(context, data)

        trainText = data.map { entry ->
            if(entry.misspelledWord != null) {
                if(entry.importance == 3) {
                    listOf(
                        (0 until 4).map {
                            TrainingDataGenerator.concatWordMisspelling(entry.ngramContext, entry.committedWord, 64.0f)
                        }.joinToString(separator = "\n"),
                        (0 until 4).map {
                            TrainingDataGenerator.concatWordMisspelling(entry.ngramContext, entry.committedWord, 16.0f)
                        }.joinToString(separator = "\n"),
                        (0 until 4).map {
                            TrainingDataGenerator.concatWordMisspelling(entry.ngramContext, entry.committedWord, 4.0f)
                        }.joinToString(separator = "\n"),
                        (0 until 4).map {
                            TrainingDataGenerator.concatWordMisspelling(entry.ngramContext, entry.committedWord, 1.0f)
                        }.joinToString(separator = "\n"),
                        (0 until 4).map {
                            TrainingDataGenerator.concatWordMisspelling(entry.ngramContext, entry.committedWord, 0.8f)
                        }.joinToString(separator = "\n"),
                        /*
                        (0 until 4).map {
                            TrainingDataGenerator.concatWordMisspelling(entry.ngramContext, entry.committedWord, 0.6f)
                        }.joinToString(separator = "\n"),
                        */
                    ).joinToString(separator = "\n")
                } else if(entry.importance == 1) {
                    listOf(
                        TrainingDataGenerator.concatFormatWordMisspelling(entry.ngramContext, entry.misspelledWord, entry.committedWord),
                        TrainingDataGenerator.concatFormatWordMisspelling(entry.ngramContext, entry.misspelledWord, entry.committedWord),
                        TrainingDataGenerator.concatFormatWordMisspelling(entry.ngramContext, entry.misspelledWord, entry.committedWord),
                        TrainingDataGenerator.concatFormatWordMisspelling(entry.ngramContext, entry.misspelledWord, entry.committedWord),
                        TrainingDataGenerator.concatWordMisspelling(entry.ngramContext, entry.committedWord, 1.0f),
                        TrainingDataGenerator.concatWordMisspelling(entry.ngramContext, entry.committedWord, 1.0f),
                        TrainingDataGenerator.concatWordMisspelling(entry.ngramContext, entry.committedWord, 0.6f),
                        TrainingDataGenerator.concatWordMisspelling(entry.ngramContext, entry.committedWord, 0.6f)
                    ).joinToString(separator = "\n")
                } else {
                    listOf(
                        TrainingDataGenerator.concatFormatWordMisspelling(entry.ngramContext, entry.misspelledWord, entry.committedWord),
                        TrainingDataGenerator.concatWordMisspelling(entry.ngramContext, entry.committedWord, 1.0f),
                    ).joinToString(separator = "\n")
                }
            } else {
                listOf(
                    entry.ngramContext.trim() + " " + entry.committedWord,
                    TrainingDataGenerator.concatWordMisspelling(entry.ngramContext, entry.committedWord, 4.0f),
                    TrainingDataGenerator.concatWordMisspelling(entry.ngramContext, entry.committedWord, 1.0f)
                ).joinToString(separator = "\n")
            }
        }.map{ it.trim() }.joinToString(separator = "\n")
    }

    ScrollableList {
        ScreenTitle("Training", showBack = true, navController)


        TextField(
            value = trainText,
            onValueChange = { trainText = it },
            enabled = !isTraining,
            textStyle = Typography.labelSmall
        )

        val scope = LocalLifecycleOwner.current
        Button(onClick = {
            val result = getPathToModelResource(context, R.raw.ml4_1_f16, R.raw.ml3_tokenizer, true)

            val outputDir = context.cacheDir
            val outputFile = File(outputDir, "test-adapter.bin")

            val builder = AdapterTrainerBuilder(
                result.first,
                result.second,
                outputFile.absolutePath
            )

            builder.addExamples(trainText.lines())

            val trainer = builder.loadAndPrepare()

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FUTOLatinIME::modelTrainer")
            scope.lifecycleScope.launch {
                isTraining = true
                println("Staring to train")
                wakeLock.acquire(120*60*1000L /*1 hour*/)
                trainer.train()
                wakeLock.release()
                println("Finished training")
                isTraining = false
            }
        }, enabled = !isTraining) {
            if(isTraining) {
                Text("Currently training, check status in logcat")
            } else {
                Text("Train model")
            }
        }
    }
}