package org.futo.inputmethod.latin.uix

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.DrawableRes
import org.futo.inputmethod.latin.R

enum class QuickClipKind {
    FullString,
    NumericCode,
    EmailAddress,
    Link
}

@get:DrawableRes
val QuickClipKind.icon: Int get() =
        when(this) {
            QuickClipKind.FullString -> R.drawable.clipboard
            QuickClipKind.NumericCode -> R.drawable.hash
            QuickClipKind.EmailAddress -> R.drawable.at_sign
            QuickClipKind.Link -> R.drawable.link
        }

// The order here defines which ones to favor when theres a duplicate
// (e.g. if the text is only an email, EmailAddress will be favored over FullString)
private val regexes = mapOf(
    QuickClipKind.EmailAddress to """[\w\-\.\+]+@([\w-]+\.)+[\w-]{2,}""".toRegex(),
    QuickClipKind.Link to """https?:\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\+.~#?&\/\/=]*)""".toRegex(),
    QuickClipKind.NumericCode to """\b(?=[A-Z0-9]{5,8}\b)(?=.*\d)[A-Z0-9]+\b""".toRegex(),
    QuickClipKind.FullString to """.+""".toRegex(setOf(RegexOption.DOT_MATCHES_ALL)),
)

data class QuickClipState(
    val texts: List<Pair<QuickClipKind, String>>,
    val image: Uri?,
    val imageMimeTypes: List<String>,
    val validUntil: Long
)

object QuickClip {
    private var timeOfDismissal = 0L

    // This shall be called when a quick clip is either used or it was dismissed.
    // It can be dismissed by typing something
    fun markQuickClipDismissed() {
        timeOfDismissal = System.currentTimeMillis()
    }

    private fun getStateForItem(validUntil: Long, mimeTypes: List<String>, item: ClipData.Item): QuickClipState? {
        val texts = mutableListOf<Pair<QuickClipKind, String>>()
        val currTexts = mutableSetOf<String>()

        item.text?.toString()?.let { text ->
            regexes.forEach { entry ->
                entry.value.findAll(text).forEach {
                    if(!currTexts.contains(it.value)) {
                        texts.add(entry.key to it.value)
                        currTexts.add(it.value)
                    }
                }
            }
        }

        return QuickClipState(
            texts = texts,
            image = item.uri,
            imageMimeTypes = mimeTypes,
            validUntil = validUntil
        )
    }

    private val ClipDescription.mimeTypes: List<String>
        get() = (0 until mimeTypeCount).map { getMimeType(it) }.toList()

    private var cachedPreviousItem: ClipData.Item? = null
    private var cachedPreviousState: QuickClipState? = null
    fun getCurrentState(context: Context): QuickClipState? {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clip = clipboardManager.primaryClip
        if(clip == null) return null

        val firstItem = clip.getItemAt(0)
        if(firstItem == null) return null

        if(cachedPreviousItem == firstItem) return cachedPreviousState?.let {
            if(it.validUntil < System.currentTimeMillis()) it else null
        }

        val description = clip.description
        if(description == null) return null

        val timestamp = description.timestamp

        // Only display if copied within the last minute
        val minimumTimeForQuickClip = System.currentTimeMillis() - (60L * 1000L)
        if(timestamp < minimumTimeForQuickClip || timestamp < timeOfDismissal) return null

        return getStateForItem(
            // Valid for one minute
            validUntil = System.currentTimeMillis() + (60L * 1000L),
            item = firstItem,
            mimeTypes = description.mimeTypes
        ).also {
            cachedPreviousItem = firstItem
            cachedPreviousState = it
        }
    }
}