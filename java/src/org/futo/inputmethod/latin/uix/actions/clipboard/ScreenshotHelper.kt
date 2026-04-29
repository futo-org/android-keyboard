package org.futo.inputmethod.latin.uix.actions.clipboard

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.uix.actions.throwIfDebug
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.getSettingFlow

interface ScreenshotListener {
    fun onScreenshotAdded(mime: String, uri: Uri)
    fun onScreenshotChange(uri: Uri, checkTrashed: suspend () -> Boolean)
}

class ScreenshotHelper(
    val context: Context,
    val lifecycleScope: LifecycleCoroutineScope,
    val listener: ScreenshotListener
) {
    companion object {
        private val TAG = "ScreenshotHelper"

        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // Returns true if we can confirm that the given URI exists and is not trashed
    internal fun safeQueryExists(uri: Uri): Boolean {
        return try {
            contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.IS_TRASHED),
                null, null, null
            )?.use { cursor ->
                cursor.count > 0 && cursor.moveToFirst() && run {
                    val idx = cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
                    (idx == -1) || (cursor.getInt(idx) == 0)
                }
            } == true
        } catch (e: Exception) {
            // Assume IllegalStateException / SecurityException means it's gone
            false
        }
    }

    private val contentResolver = context.contentResolver
    private var lastSeenId: Long = -1L
    private var observer: ContentObserver? = null
    private fun registerObserver() {
        lifecycleScope.launch { handleNewScreenshot(dry = true) }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)

                if(uri != null) {
                    listener.onScreenshotChange(uri) { withContext(Dispatchers.IO) {
                        !safeQueryExists(uri)
                    } }
                }


                lifecycleScope.launch {
                    delay(16L)
                    handleNewScreenshot()
                }
            }
        }
        this.observer = observer

        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            Log.d(TAG, "ContentObserver registered")
        }catch(e: Exception) {
            throwIfDebug(e)
        }
    }

    private fun unregisterObserver() {
        observer?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            }catch(e: Exception) {
                throwIfDebug(e)
            }
        }
        observer = null
    }

    private fun hasPermission() = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    private var settingsObservingJob = lifecycleScope.launch {
        context.getSettingFlow(ClipboardSaveScreenshots).collect {
            if(it && observer == null && hasPermission()) {
                registerObserver()
            }else if(!it && observer != null) {
                unregisterObserver()
            }
        }
    }


    fun onDestroy() {
        settingsObservingJob.cancel()
    }

    internal suspend fun handleNewScreenshot(dry: Boolean = false) = withContext(Dispatchers.IO) {
        if(!context.getSetting(ClipboardSaveScreenshots)) return@withContext null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@withContext null

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.RELATIVE_PATH
        )

        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE '%Pictures/Screenshots%' " +
                    "AND ${MediaStore.Images.Media._ID} > ?"
        } else {
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE '%screenshot%' " +
                    "AND ${MediaStore.Images.Media._ID} > ?"
        }

        try {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(lastSeenId.toString()))

                if(dry) {
                    putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Images.Media.DATE_ADDED} DESC")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        putInt(ContentResolver.QUERY_ARG_LIMIT, 1)
                    }
                } else {
                    putString(
                        ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                        "${MediaStore.Images.Media.DATE_ADDED} ASC"
                    )
                }
            }

            contentResolver.query(
                uri,
                projection,
                queryArgs,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@withContext null

                while(true) {
                    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                    val id = cursor.getLong(idIdx)

                    if(id <= lastSeenId) {
                        return@withContext null
                    }

                    val mime = cursor.getString(mimeIdx)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )

                    if(!dry) {
                        listener.onScreenshotAdded(mime, uri)
                    }

                    lastSeenId = id

                    if(!cursor.moveToNext()) break
                }
            }
        }catch(e: Exception) {
            throwIfDebug(e)
        }
    }


}