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
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting
import java.io.File
import java.util.concurrent.TimeUnit

val NUM_TRAINING_RUNS_KEY = intPreferencesKey("training_runs_count")

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

class TrainingWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager

    override suspend fun doWork(): Result {
        println("TrainingWorker is starting")
        TrainingWorkerStatus.state.emit(TrainingState.Starting)
        TrainingWorkerStatus.isTraining.value = true
        setForeground(createForegroundInfo("Training..."))

        TrainingWorkerStatus.state.emit(train())
        TrainingWorkerStatus.isTraining.value = false
        println("TrainingWorker has ended")
        return Result.success()
    }

    private fun getTrainingData(): String {
        val data = mutableListOf<HistoryLogForTraining>()
        loadHistoryLogBackup(applicationContext, data)

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

    private suspend fun train(): TrainingState {
        val data = getTrainingData()
        if(data.isEmpty()) {
            return TrainingState.ErrorInadequateData
        }

        val cacheLoraPath = File(applicationContext.cacheDir, "adapter.bin")

        val builder = AdapterTrainerBuilder(
            ModelPaths.getPrimaryModel(applicationContext),
            ModelPaths.getTokenizer(applicationContext),
            cacheLoraPath.absolutePath,
            ModelPaths.getFinetunedModelOutput(applicationContext)
        )

        builder.setLossFlow(TrainingWorkerStatus.loss)
        builder.setProgressFlow(TrainingWorkerStatus.progress)

        builder.setWeight(0.75f)

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
            try {
                trainer.train()
            } finally {
                wakeLock.release()
            }
            println("Finished training")
        }

        // In case there's no one to receive ClearTrainingLog, save an empty log
        saveHistoryLogBackup(applicationContext, listOf())

        TrainingWorkerStatus.lmRequest.emit(LanguageModelFacilitatorRequest.ResetModel)
        TrainingWorkerStatus.lmRequest.emit(LanguageModelFacilitatorRequest.ClearTrainingLog)

        applicationContext.setSetting(NUM_TRAINING_RUNS_KEY, applicationContext.getSetting(NUM_TRAINING_RUNS_KEY, 0) + 1)

        return TrainingState.Finished
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
        .setRequiresDeviceIdle(true)
        .build()
    
    val request = PeriodicWorkRequest.Builder(
        TrainingWorker::class.java,
        20L, TimeUnit.HOURS,
        // 12L, TimeUnit.HOURS
    ).addTag(WORKER_TAG).setConstraints(constraints).build()

    workManager.enqueue(request)
}

public fun scheduleTrainingWorkerImmediately(context: Context) {
    val workManager = WorkManager.getInstance(context)

    val workRequest = OneTimeWorkRequestBuilder<TrainingWorker>()
        .setInitialDelay(0, TimeUnit.SECONDS) // Run immediately
        .addTag(WORKER_TAG)
        .build()

    workManager.enqueue(workRequest)
}