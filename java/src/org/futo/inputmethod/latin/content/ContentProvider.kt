package org.futo.inputmethod.latin.content

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.uix.actions.fonttyper.ImageTyperState
import java.io.File
import java.util.UUID


val FONT_AUTHORITY = BuildConfig.APPLICATION_ID + ".font"
private val CODE_RENDERED_FONT_IMAGE = 1

private val URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
    addURI(FONT_AUTHORITY, "render/*", CODE_RENDERED_FONT_IMAGE)
}

class KbContentProvider: ContentProvider() {
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

    private val fileCache = HashMap<Uri, File>()
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if(fileCache.containsKey(uri)) return ParcelFileDescriptor.open(
            fileCache[uri],
            ParcelFileDescriptor.MODE_READ_ONLY
        )

        if (URI_MATCHER.match(uri) != CODE_RENDERED_FONT_IMAGE) {
            throw IllegalArgumentException("Unsupported URI: $uri")
        }

        val id = uri.lastPathSegment ?: throw IllegalArgumentException("Invalid URI")
        return ImageTyperState.renderRequest(context!!, UUID.fromString(id))
    }


    override fun getType(uri: Uri): String =
        when(URI_MATCHER.match(uri)) {
            CODE_RENDERED_FONT_IMAGE -> "image/png"
            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }

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