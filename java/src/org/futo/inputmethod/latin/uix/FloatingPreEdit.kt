package org.futo.inputmethod.latin.uix

import android.text.InputFilter
import android.text.Spanned
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.uix.theme.Typography
import kotlin.math.roundToInt

data class PreEditEntry(
    val text: String,
    val normalized: String,
    val highlighted: Boolean
)

interface PreEditListener {
    fun onStartEdit()
    fun onUpdateEdit(value: String)
    fun onFinishEdit(value: String, candidateWord: String?, candidateIndex: Int?)
}

val NoOpPreEditListener = object : PreEditListener {
    override fun onStartEdit() { }

    override fun onUpdateEdit(value: String) { }

    override fun onFinishEdit(value: String, candidateWord: String?, candidateIndex: Int?) { }
}

data class FloatingPreEdit(
    val entries: List<PreEditEntry>,
    val transformation: Map<Char, Char>,
    val listener: PreEditListener
) {
    companion object {
        @JvmStatic
        fun build(text: String, listener: PreEditListener, transformation: Map<Char, Char>, normalized: String) = FloatingPreEdit(
            entries = listOfNotNull(text.ifBlank { null }?.let { PreEditEntry(it, normalized, true) }),
            transformation = transformation,
            listener = listener,
        )
    }
    private val reverseTransformation = transformation.toList().associate { it.second to it.first }

    fun transform(string: CharSequence) = string.map { transformation[it] ?: it }.joinToString("")
    fun untransform(string: CharSequence) = string.map { reverseTransformation[it] ?: it }.joinToString("")
}

@Composable
fun FloatingPreEditView(
    preedit: FloatingPreEdit = FloatingPreEdit.build("Some text here", NoOpPreEditListener, emptyMap(), "sometexthere"),
    anchorCoords: LayoutCoordinates? = null,
    updateHeight: (Int) -> Unit = { },
    editingText: MutableState<String>,
    editing: MutableState<Boolean>
) {
    var height by remember { mutableIntStateOf(0) }
    val pos = anchorCoords?.positionInWindow()

    LaunchedEffect(editingText.value) {
        if (editing.value) {
            preedit.listener.onUpdateEdit(preedit.untransform(editingText.value))
        }
    }

    if(editing.value) {
        Box(
            Modifier.Companion
            .onSizeChanged {
                height = it.height
                updateHeight(it.height)
            }
            .offset {
                pos?.let { IntOffset(it.x.roundToInt(), it.y.roundToInt() - height) }
                    ?: IntOffset.Companion.Zero
            }
            .background(LocalKeyboardScheme.current.surface)
            .padding(4.dp)) {
            CompositionLocalProvider(LocalContentColor provides LocalKeyboardScheme.current.onSurface) {
                Row(Modifier.Companion.height(48.dp)) {
                    ActionTextEditor(
                        editingText, modifier = Modifier.Companion
                            .fillMaxHeight()
                            .weight(1.0f),
                        afterUnOverride = {
                            if (it) {
                                preedit.listener.onFinishEdit(
                                    preedit.untransform(editingText.value),
                                    null, null
                                )
                            }
                        },
                        autofocus = true,
                        onEnter = { editing.value = false },
                        inputFilters = arrayOf(object : InputFilter {
                            override fun filter(
                                source: CharSequence,
                                start: Int,
                                end: Int,
                                dest: Spanned?,
                                dstart: Int,
                                dend: Int
                            ): CharSequence = preedit.transform(source)
                        })
                    )
                    IconButton(onClick = {
                        editing.value = false
                    }, modifier = Modifier.Companion.size(48.dp)) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                }
            }
        }
    } else {
        Box(
            Modifier.Companion
            .onSizeChanged {
                height = it.height
                updateHeight(it.height)
            }
            .offset {
                pos?.let { IntOffset(it.x.roundToInt(), it.y.roundToInt() - height) }
                    ?: IntOffset.Companion.Zero
            }
            .background(Color.Companion.Gray.copy(alpha = 0.7f))
            .clickable {
                // Reject editing if there are already any Chinese characters in here. Only Pinyin editing supported
                if (preedit.entries.all { it.normalized.all { it in 'a'..'z' || it in 'A'..'Z' || it == ' ' } }) {
                    preedit.listener.onStartEdit()
                    editing.value = true
                    editingText.value =
                        preedit.entries.joinToString(separator = " ") { it.normalized }
                }
            }
            .padding(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                preedit.entries.forEach {
                    Text(
                        it.text, style = Typography.SmallMl.copy(
                            color = if (it.highlighted) Color.Companion.Black else Color.Companion.DarkGray
                        ), maxLines = 1, overflow = TextOverflow.Companion.Visible
                    )
                }
            }
        }
    }
}