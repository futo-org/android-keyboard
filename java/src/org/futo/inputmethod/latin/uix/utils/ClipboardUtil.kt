package org.futo.inputmethod.latin.uix.utils

import android.content.ClipboardManager
import android.content.Context

fun latestClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return null
    val clip = try {
        clipboard.primaryClip
    } catch (_: Throwable) { null } ?: return null
    val item = try { clip.getItemAt(0) } catch (_: Throwable) { null } ?: return null
    return item.coerceToText(context)?.toString()
}

