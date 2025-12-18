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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.accessibility.AccessibilityUtils
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.actions.ClipboardQuickClipsEnabled
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

private val sensitiveRegexes = mapOf(
    QuickClipKind.FullString to """.+""".toRegex(setOf(RegexOption.DOT_MATCHES_ALL))
)

data class QuickClipItem(
    val kind: QuickClipKind,
    val text: String,
    val occurrenceIndex: Int,
)

data class QuickClipState(
    val texts: List<QuickClipItem>,
    val image: Uri?,
    val imageMimeTypes: List<String>,
    val validUntil: Long,
    val isSensitive: Boolean
)

@Composable
private fun QuickClipPill(icon: Painter, contentDescription: String, text: String?, uri: Uri?, onActivate: () -> Unit) {
    Box(Modifier.fillMaxHeight().clickable {
        onActivate()
    }.padding(4.dp).clearAndSetSemantics {
        this.role = Role.Button
        this.contentDescription = contentDescription
        this.onClick(action = { onActivate(); true })
    }) {
        Surface(
            color = LocalKeyboardScheme.current.keyboardContainer,
            shape = RoundedCornerShape(8.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides LocalKeyboardScheme.current.onKeyboardContainer) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = CenterVertically,
                    modifier = Modifier.padding(6.dp).fillMaxHeight()
                ) {
                    Icon(icon, contentDescription = null)

                    if (text != null) {
                        Text(text.replace("\n", " "), style = Typography.Small)
                    } else if (uri != null) {
                        Icon(painterResource(R.drawable.image), contentDescription = null)
                    }
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
    val context = LocalContext.current

    val shouldObscure = if(!LocalInspectionMode.current) remember(state.isSensitive) {
        if(state.isSensitive) {
            AccessibilityUtils.init(context)
            AccessibilityUtils.getInstance().shouldObscureInput()
        } else {
            false
        }
    } else state.isSensitive

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
                    icon = painterResource(it.kind.icon),
                    contentDescription = if(shouldObscure) {
                        stringResource(it.kind.accessibilityDescription,
                            stringResource(R.string.quick_clip_content_obscured))
                    } else {
                        stringResource(it.kind.accessibilityDescription, it.text)
                    },
                    text = it.text.let { txt ->
                        when(it.kind) {
                            QuickClipKind.FullString -> if (txt.length > 12) txt.take(10) + "..." else txt
                            QuickClipKind.NumericCode -> txt
                            QuickClipKind.EmailAddress -> txt
                            QuickClipKind.Link -> txt
                                .replace("http://", "")
                                .replace("https://", "")
                                .split("/", limit = 2)
                                .let {
                                    if(it.size == 2)
                                        it[0] + "/" + if(it[1].length > 6) it[1].take(6) + "..." else it[1]
                                    else
                                        it[0]
                                }
                        }
                    }.let {
                        if(state.isSensitive) {
                            "•".repeat(it.length)
                        } else {
                            it
                        }
                    },
                    uri = null
                ) {
                    manager!!.typeText(it.text)
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

    private fun getStateForItem(validUntil: Long, mimeTypes: List<String>, item: ClipData.Item, isSensitive: Boolean): QuickClipState? {
        val texts = mutableListOf<QuickClipItem>()
        val currTexts = mutableSetOf<String>()

        val regexesToUse = if(isSensitive) {
            sensitiveRegexes
        } else {
            regexes
        }

        val text = item.text
        if(text != null && text.length > 32_000) {
            if(text.length > 500_000) return null

            // Skip any processing for huge strings
            return QuickClipState(
                texts = listOf(QuickClipItem(QuickClipKind.FullString, text.toString(), 0)),
                image = item.uri,
                imageMimeTypes = mimeTypes,
                validUntil = validUntil,
                isSensitive = isSensitive
            )
        }

        item.text?.toString()?.let { text ->
            regexesToUse.forEach { entry ->
                entry.value.findAll(text).forEach {
                    if(!currTexts.contains(it.value)) {
                        texts.add(
                            QuickClipItem(
                                kind = entry.key,
                                text = it.value,
                                occurrenceIndex = it.range.first
                            )
                        )
                        currTexts.add(it.value)
                    }
                }
            }
        }

        return QuickClipState(
            texts = texts.sortedBy {
                it.occurrenceIndex
            },
            image = item.uri,
            imageMimeTypes = mimeTypes,
            validUntil = validUntil,
            isSensitive = isSensitive
        )
    }

    private val ClipDescription.mimeTypes: List<String>
        get() = (0 until mimeTypeCount).map { getMimeType(it) }.toList()

    private var cachedPreviousItem: ClipData.Item? = null
    private var cachedPreviousState: QuickClipState? = null
    fun getCurrentState(context: Context): QuickClipState? {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        if(context.getSetting(ClipboardQuickClipsEnabled) == false) return null

        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clip = try {
            clipboardManager.primaryClip
        } catch(_: Exception) {
            null
        }
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

        val isSensitive = clip?.description?.extras?.getBoolean(
            ClipDescription.EXTRA_IS_SENSITIVE, false
        ) == true

        return getStateForItem(
            // Valid for one minute
            validUntil = System.currentTimeMillis() + (60L * 1000L),
            item = firstItem,
            mimeTypes = description.mimeTypes,
            isSensitive = isSensitive
        ).also {
            cachedPreviousItem = firstItem
            cachedPreviousState = it
        }
    }
}

@Preview
@Composable
private fun PreviewQuickClips() {
    Row(Modifier.height(ActionBarHeight)) {
        QuickClipView(QuickClipState(
            texts = listOf(QuickClipItem(
                kind = QuickClipKind.FullString,
                text = "Some text goes here!",
                occurrenceIndex = 0
            )),
            image = null,
            imageMimeTypes = emptyList(),
            validUntil = 0L,
            isSensitive = false
        )) { }
    }
}