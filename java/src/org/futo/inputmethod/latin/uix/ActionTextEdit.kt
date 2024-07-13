package org.futo.inputmethod.latin.uix

import android.content.Context
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView


class ActionEditText(context: Context) :
    androidx.appcompat.widget.AppCompatEditText(context) {
    var inputConnection: InputConnection? = null
        private set

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        inputConnection = super.onCreateInputConnection(outAttrs)
        return inputConnection
    }

    private var textChanged: (String) -> Unit = { }
    fun setTextChangeCallback(
        textChanged: (String) -> Unit
    ) {
        this.textChanged = textChanged
    }

    override fun onTextChanged(
        text: CharSequence?,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)

        // For some strange reason this IS null sometimes, even though it
        // shouldn't be
        if(textChanged != null) {
            textChanged(text?.toString() ?: "")
        }
    }
}


@Composable
fun ActionTextEditor(text: MutableState<String>) {
    val context = LocalContext.current
    val manager = LocalManager.current

    val height = with(LocalDensity.current) {
        48.dp.toPx()
    }

    val inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS

    AndroidView(
        factory = {
            ActionEditText(context).apply {
                this.inputType = inputType

                setTextChangeCallback { text.value = it }

                setText(text.value)

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                setHeight(height.toInt())

                val editorInfo = EditorInfo().apply {
                    this.inputType = inputType
                }
                onCreateInputConnection(editorInfo)

                manager.overrideInputConnection(inputConnection!!, editorInfo)

                requestFocus()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        onRelease = {
            manager.unsetInputConnection()
        }
    )
}
