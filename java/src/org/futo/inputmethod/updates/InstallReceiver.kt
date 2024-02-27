package org.futo.inputmethod.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch


class InstallReceiver : BroadcastReceiver() {
    private val TAG = "InstallReceiver"

    companion object {
        val onReceiveResult = MutableSharedFlow<String>(0)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        Log.i(TAG, "Received status $status.")

        GlobalScope.launch {
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val activityIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }

                    if (activityIntent == null) {
                        Log.w(TAG, "Received STATUS_PENDING_USER_ACTION and activity intent is null.")
                        return@launch
                    }
                    context.startActivity(activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PackageInstaller.STATUS_SUCCESS -> onReceiveResult.emit("Success!")
                PackageInstaller.STATUS_FAILURE -> onReceiveResult.emit("General failure")
                PackageInstaller.STATUS_FAILURE_ABORTED -> onReceiveResult.emit("The operation failed because it was actively aborted")
                PackageInstaller.STATUS_FAILURE_BLOCKED -> onReceiveResult.emit("The operation failed because it was blocked")
                PackageInstaller.STATUS_FAILURE_CONFLICT -> onReceiveResult.emit("The operation failed because it conflicts (or is inconsistent with) with another package already installed on the device")
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> onReceiveResult.emit("The operation failed because it is fundamentally incompatible with this device")
                PackageInstaller.STATUS_FAILURE_INVALID -> onReceiveResult.emit("The operation failed because one or more of the APKs was invalid")
                PackageInstaller.STATUS_FAILURE_STORAGE -> onReceiveResult.emit("The operation failed because of storage issues")
                else -> {
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    if(msg != null) {
                        onReceiveResult.emit(msg)
                    }
                }
            }
        }
    }
}
