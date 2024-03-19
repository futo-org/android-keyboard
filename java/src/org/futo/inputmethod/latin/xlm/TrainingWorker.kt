package org.futo.inputmethod.latin.xlm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.USE_TRANSFORMER_FINETUNING
import org.futo.inputmethod.latin.uix.getSetting
import java.io.File
import java.util.concurrent.TimeUnit

val NUM_TRAINING_RUNS_KEY = intPreferencesKey("training_runs_count")

const val CHANNEL_ID = "TRAINING"
const val NOTIFICATION_ID = 1

enum class TrainingState {
    None,
    Training,
    ErrorInadequateData,
    Finished,
    FatalError,
}

data class TrainingStateWithModel(
    val state: TrainingState,
    val model: String?
)

enum class LanguageModelFacilitatorRequest {
    ResetModel,
    ClearTrainingLog
}

object TrainingWorkerStatus {
    val state = MutableSharedFlow<TrainingStateWithModel>(replay = 1)
    val lmRequest = MutableSharedFlow<LanguageModelFacilitatorRequest>(replay = 0)
    val isTraining = mutableStateOf(false)

    val loss = MutableSharedFlow<Float>(replay = 4)
    val progress = MutableSharedFlow<Float>(replay = 4)
}

class TrainingWorker(val context: Context, val parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager

    override suspend fun doWork(): Result {
        println("TrainingWorker is starting")

        val shouldTrain = context.getSetting(USE_TRANSFORMER_FINETUNING)
        if(!shouldTrain) {
            println("TrainingWorker is exiting as training is disabled")
            saveHistoryLogBackup(applicationContext, listOf())
            TrainingWorkerStatus.lmRequest.emit(LanguageModelFacilitatorRequest.ClearTrainingLog)
            return Result.success()
        }

        TrainingWorkerStatus.isTraining.value = true
        setForeground(createForegroundInfo("Training..."))

        val modelToTrain = parameters.inputData.getString("modelToTrain")
        val trainingData = parameters.inputData.getString("trainingData")

        TrainingWorkerStatus.state.emit(train(customModel = modelToTrain, customTrainingData = trainingData))
        TrainingWorkerStatus.isTraining.value = false
        println("TrainingWorker has ended")
        return Result.success()
    }

    private fun getTrainingData(locales: Set<String>): String {
        val data = mutableListOf<HistoryLogForTraining>()
        loadHistoryLogBackup(applicationContext, data)

        data.removeAll { !locales.contains(it.locale) }

        if(data.size < 100) {
            return ""
        }

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

    private suspend fun train(customModel: String?, customTrainingData: String?): TrainingStateWithModel {
        val modelToTrain = if(customModel != null) {
            val file = File(ModelPaths.getModelDirectory(context), "$customModel.gguf")
            ModelInfoLoader(
                file,
                file.nameWithoutExtension,
            ).loadDetails() ?: return TrainingStateWithModel(TrainingState.FatalError, customModel)
        } else {
            val trainableModels = ModelPaths.getModelOptions(applicationContext)

            val modelInfo = trainableModels.firstNotNullOfOrNull {
                val data = getTrainingData(setOf(it.key))
                if(data.isEmpty()) {
                    null
                } else {
                    it.value
                }
            } ?: return TrainingStateWithModel(TrainingState.ErrorInadequateData, null)

            modelInfo.loadDetails() ?: return TrainingStateWithModel(TrainingState.FatalError, model = modelInfo.path.nameWithoutExtension)
        }

        val modelFile = File(modelToTrain.path)

        TrainingWorkerStatus.state.emit(
            TrainingStateWithModel(
                TrainingState.Training,
                model = modelFile.nameWithoutExtension
            )
        )

        val data = if(customModel != null && customTrainingData != null) {
            customTrainingData // TODO: This must be preprocessed into word correction format!
        } else {
            getTrainingData(modelToTrain.languages.toSet())
        }

        if (data.isEmpty()) {
            return TrainingStateWithModel(TrainingState.ErrorInadequateData, modelFile.nameWithoutExtension)
        }

        val outputModel = File(applicationContext.cacheDir, modelFile.name + ".tmp")
        val cacheLoraPath = File(applicationContext.cacheDir, "adapter.bin")

        val builder = AdapterTrainerBuilder(
            modelFile.absolutePath,
            cacheLoraPath.absolutePath,
            outputModel.absolutePath
        )

        builder.setLossFlow(TrainingWorkerStatus.loss)
        builder.setProgressFlow(TrainingWorkerStatus.progress)

        builder.setWeight(0.75f)

        builder.addExamples(data.lines())

        val trainer = try {
             builder.loadAndPrepare()
        } catch(e: InadequateDataException) {
            return TrainingStateWithModel(TrainingState.ErrorInadequateData, modelFile.nameWithoutExtension)
        }

        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FUTOLatinIME::modelTrainer")
        withContext(Dispatchers.Default) {
            println("Staring to train")
            wakeLock.acquire(120*60*1000L /*1 hour*/)
            try {
                trainer.train()
            } finally {
                wakeLock.release()
            }
            println("Finished training")
        }

        // In case there's no one to receive ClearTrainingLog, save an empty log
        saveHistoryLogBackup(applicationContext, listOf())

        TrainingWorkerStatus.lmRequest.emit(LanguageModelFacilitatorRequest.ClearTrainingLog)

        val fallback = File(
            modelFile.absolutePath + ".bak"
        )

        // TODO: A better solution for backup/reverting, etc
        //modelFile.copyTo(fallback, overwrite = true)
        outputModel.copyTo(modelFile, overwrite = true)

        ModelPaths.signalReloadModels()

        return TrainingStateWithModel(TrainingState.Finished, modelFile.nameWithoutExtension)
    }
    // Creates an instance of ForegroundInfo which can be used to update the
    // ongoing notification.
    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val title = "Model Training"
        val cancel = "Halt"
        // This PendingIntent can be used to cancel the worker
        val intent = WorkManager.getInstance(applicationContext)
                .createCancelPendingIntent(id)

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

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
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

private val WORKER_TAG: String = "TRAINING_WORKER"
public fun scheduleTrainingWorkerBackground(context: Context) {
    val workManager = WorkManager.getInstance(context)
    workManager.cancelAllWorkByTag(WORKER_TAG)

    val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .setRequiresCharging(true)
        .setRequiredNetworkType(NetworkType.UNMETERED) // If device is on a metered network, the user may be travelling
        //.setRequiresDeviceIdle(true)
        .build()
    
    val request = PeriodicWorkRequest.Builder(
        TrainingWorker::class.java,
        20L, TimeUnit.HOURS,
        // 12L, TimeUnit.HOURS
    ).addTag(WORKER_TAG).setConstraints(constraints).build()

    workManager.enqueue(request)
}

public fun scheduleTrainingWorkerImmediately(context: Context, model: ModelInfoLoader? = null, trainingData: String? = null) {
    val workManager = WorkManager.getInstance(context)

    val data = Data.Builder()

    if(model != null) {
        data.putString("modelToTrain", model.path.nameWithoutExtension)
    }

    if(trainingData != null) {
        data.putString("trainingData", trainingData)
    }

    val workRequest = OneTimeWorkRequestBuilder<TrainingWorker>()
        .setInitialDelay(0, TimeUnit.SECONDS) // Run immediately
        .addTag(WORKER_TAG)
        .setInputData(data.build())
        .build()

    workManager.enqueue(workRequest)
}