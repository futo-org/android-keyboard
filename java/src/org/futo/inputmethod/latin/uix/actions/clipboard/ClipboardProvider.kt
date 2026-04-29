package org.futo.inputmethod.latin.uix.actions.clipboard

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.futo.inputmethod.latin.BuildConfig
import java.io.File
import java.util.UUID


val CLIPBOARD_AUTHORITY = BuildConfig.APPLICATION_ID + ".clipboard"
private val CODE_CLIP = 1

private val URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
    addURI(CLIPBOARD_AUTHORITY, "clip/*", CODE_CLIP)
}


data class ClipboardPasteRequest(
    val file: File,
    val mimeType: String,
    val expiration: Long
)

object ClipboardProviderState {
    val requests: HashMap<UUID, ClipboardPasteRequest> = HashMap()

    fun addRequest(request: ClipboardPasteRequest): UUID {
        val uuid = UUID.randomUUID()
        requests.put(uuid, request)
        return uuid
    }

    fun fulfillRequest(context: Context, uuid: UUID): ParcelFileDescriptor? {
        val request = requests[uuid] ?: throw IllegalArgumentException("Invalid request")
        if(System.currentTimeMillis() > request.expiration) throw IllegalArgumentException("Invalid request")

        val file = request.file


        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    fun getMimeType(context: Context, uuid: UUID): String {
        val request = requests[uuid] ?: throw IllegalArgumentException("Invalid request")

        return request.mimeType
    }
}


class ClipboardProvider: ContentProvider() {
    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String?>?,
        selection: String?,
        selectionArgs: Array<out String?>?,
        sortOrder: String?
    ): Cursor? {
        return null
    }

    private fun getUUID(uri: Uri): UUID {
        if (URI_MATCHER.match(uri) != CODE_CLIP) {
            throw IllegalArgumentException("Unsupported URI: $uri")
        }

        val id = uri.lastPathSegment ?: throw IllegalArgumentException("Invalid URI")

        return UUID.fromString(id)
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor?
            = ClipboardProviderState.fulfillRequest(context!!, getUUID(uri))

    override fun getType(uri: Uri): String
            = ClipboardProviderState.getMimeType(context!!, getUUID(uri))


    override fun insert(
        uri: Uri,
        values: ContentValues?
    ): Uri? = throw UnsupportedOperationException("Provider is read-only")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String?>?
    ): Int = throw UnsupportedOperationException("Provider is read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String?>?
    ): Int = throw UnsupportedOperationException("Provider is read-only")
}