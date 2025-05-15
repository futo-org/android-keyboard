package org.futo.inputmethod.latin.uix

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.Typography

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

@get:StringRes
val QuickClipKind.accessibilityDescription: Int get() =
    when(this) {
        QuickClipKind.FullString -> R.string.quick_clip_full_string
        QuickClipKind.NumericCode -> R.string.quick_clip_numeric_code
        QuickClipKind.EmailAddress -> R.string.quick_clip_email_address
        QuickClipKind.Link -> R.string.quick_clip_url
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

@Composable
private fun QuickClipPill(icon: Painter, contentDescription: String, text: String?, uri: Uri?, onActivate: () -> Unit) {
    Box(Modifier.fillMaxHeight().padding(4.dp).clickable {
        onActivate()
    }.clearAndSetSemantics {
        this.role = Role.Button
        this.contentDescription = contentDescription
        this.onClick(action = { onActivate(); true })
    }) {
        Surface(
            color = LocalKeyboardScheme.current.keyboardContainer,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = CenterVertically,
                modifier = Modifier.padding(6.dp).fillMaxHeight()
            ) {
                Icon(icon, contentDescription = null)

                if(text != null) {
                    val cutText = if (text.length > 10) {
                        text.substring(0, 8) + "..."
                    } else {
                        text
                    }.replace("\n", " ")
                    Text(cutText, style = Typography.Small)
                }else if(uri != null) {
                    Icon(painterResource(R.drawable.image), contentDescription = null)
                }
            }
        }
    }
}

@Composable
fun RowScope.QuickClipView(state: QuickClipState, dismiss: () -> Unit) {
    val manager = if(!LocalInspectionMode.current) {
        LocalManager.current
    } else {
        null
    }

    LazyRow(Modifier.weight(1.0f)) {
        state.image?.let { uri ->
            item {
                QuickClipPill(
                    icon = painterResource(R.drawable.clipboard),
                    contentDescription = stringResource(R.string.quick_clip_image),
                    text = null,
                    uri = uri
                ) {
                    manager!!.typeUri(uri, state.imageMimeTypes)
                    QuickClip.markQuickClipDismissed()
                    dismiss()
                }
            }
        }

        state.texts.forEach {
            item {
                QuickClipPill(
                    icon = painterResource(it.first.icon),
                    contentDescription = stringResource(it.first.accessibilityDescription, it.second),
                    text = it.second,
                    uri = null
                ) {
                    manager!!.typeText(it.second)
                    QuickClip.markQuickClipDismissed()
                    dismiss()
                }
            }
        }
    }
}

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