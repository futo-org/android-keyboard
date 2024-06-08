package org.futo.inputmethod.latin.uix

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView


class ActionEditText(context: Context, val textChanged: (String) -> Unit) :
    androidx.appcompat.widget.AppCompatEditText(context) {
    var inputConnection: InputConnection? = null
        private set

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        inputConnection = super.onCreateInputConnection(outAttrs)
        return inputConnection
    }

    override fun onTextChanged(
        text: CharSequence?,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        textChanged(text?.toString() ?: "")
    }
}


@Composable
fun ActionTextEditor(text: MutableState<String>) {
    val context = LocalContext.current
    val manager = LocalManager.current

    AndroidView(
        factory = {
            ActionEditText(context) {
                text.value = it
            }.apply {
                onCreateInputConnection(
                    EditorInfo()
                )
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                inputType = InputType.TYPE_CLASS_TEXT

                manager.overrideInputConnection(inputConnection!!)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        onRelease = {
            manager.unsetInputConnection()
        }
    )
}
