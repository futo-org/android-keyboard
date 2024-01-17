package org.futo.inputmethod.updates

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R

const val CHANNEL_ID = "UPDATES"
const val NOTIFICATION_ID = 1

class UpdateCheckingService : JobService() {
    private var job: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        job = CoroutineScope(Dispatchers.IO).launch {
            if(checkForUpdateAndSaveToPreferences(applicationContext)) {
                val updateResult = retrieveSavedLastUpdateCheckResult(applicationContext)

                if(updateResult != null && updateResult.isNewer()) {
                    // Show a notification : "Update available"
                    val manager = applicationContext.getSystemService(
                        Context.NOTIFICATION_SERVICE
                    ) as NotificationManager

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel = NotificationChannel(
                            CHANNEL_ID,
                            "Update Notifications",
                            NotificationManager.IMPORTANCE_MIN
                        )

                        manager.createNotificationChannel(channel)
                    }

                    val contentIntent = PendingIntent.getActivity(
                        applicationContext,
                        0,
                        Intent(Intent.ACTION_VIEW, Uri.parse(updateResult.apkUrl)),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                        .setContentTitle(getString(R.string.update_available))
                        .setContentText(getString(R.string.update_available_notification,"${UpdateResult.currentVersionString()} -> ${updateResult.nextVersionString}"))
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentIntent(contentIntent)

                    manager.notify(NOTIFICATION_ID, notification.build())
                }
            } else {
                Log.i("UpdateCheckingService", "no update available, or failed to check")
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        job?.cancel()

        return false
    }
}