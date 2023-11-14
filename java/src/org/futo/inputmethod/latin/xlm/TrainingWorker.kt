package org.futo.inputmethod.latin.xlm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

const val CHANNEL_ID = "TRAINING"
const val NOTIFICATION_ID = 1

enum class TrainingState {
    None,
    Starting,
    ErrorInadequateData,
    Finished
}

enum class LanguageModelFacilitatorRequest {
    ResetModel,
    ClearTrainingLog
}

object TrainingWorkerStatus {
    val state = MutableSharedFlow<TrainingState>(replay = 1)
    val lmRequest = MutableSharedFlow<LanguageModelFacilitatorRequest>(replay = 0)
    val isTraining = mutableStateOf(false)

    val loss = MutableSharedFlow<Float>(replay = 4)
    val progress = MutableSharedFlow<Float>(replay = 4)
}


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


class TrainingWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager

    override suspend fun doWork(): Result {
        TrainingWorkerStatus.state.emit(TrainingState.Starting)
        TrainingWorkerStatus.isTraining.value = true
        setForeground(createForegroundInfo("Training..."))

        TrainingWorkerStatus.state.emit(train())
        TrainingWorkerStatus.isTraining.value = false
        return Result.success()
    }

    private fun getTrainingData(): String {
        val data = mutableListOf<HistoryLogForTraining>()
        loadHistoryLogBackup(applicationContext, data)

        return data.map { entry ->
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

    private suspend fun train(): TrainingState {
        val result = getPathToModelResource(applicationContext, R.raw.ml4_1_f16, R.raw.ml3_tokenizer, true)

        val outputDir = applicationContext.cacheDir
        val outputFile = File(outputDir, "test-adapter.bin")

        val builder = AdapterTrainerBuilder(
            result.first,
            result.second,
            outputFile.absolutePath
        )

        builder.setLossFlow(TrainingWorkerStatus.loss)
        builder.setProgressFlow(TrainingWorkerStatus.progress)

        val data = getTrainingData()
        builder.addExamples(data.lines())

        val trainer = try {
             builder.loadAndPrepare()
        } catch(e: InadequateDataException) {
            return TrainingState.ErrorInadequateData
        }

        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FUTOLatinIME::modelTrainer")
        withContext(Dispatchers.Default) {
            println("Staring to train")
            wakeLock.acquire(120*60*1000L /*1 hour*/)
            trainer.train()
            wakeLock.release()
            println("Finished training")
        }

        TrainingWorkerStatus.lmRequest.emit(LanguageModelFacilitatorRequest.ResetModel)
        TrainingWorkerStatus.lmRequest.emit(LanguageModelFacilitatorRequest.ClearTrainingLog)

        return TrainingState.Finished
    }
    // Creates an instance of ForegroundInfo which can be used to update the
    // ongoing notification.
    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val title = "Model Training"
        val cancel = "Halt"
        // This PendingIntent can be used to cancel the worker
        val intent = WorkManager.getInstance(applicationContext)
                .createCancelPendingIntent(getId())

        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel()
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(progress)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            // Add the cancel action to the notification which can
            // be used to cancel the worker
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Model Training Notifications",
            NotificationManager.IMPORTANCE_MIN
        )

        notificationManager.createNotificationChannel(channel)
    }
}