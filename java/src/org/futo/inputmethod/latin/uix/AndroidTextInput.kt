package org.futo.inputmethod.latin.uix

import android.content.Context
import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.futo.inputmethod.latin.R

@Composable
fun AndroidTextInput(allowPredictions: Boolean = true, customOptions: Set<String> = setOf(), autoshow: Boolean = true) {
    val context = LocalContext.current
    val bgColor = MaterialTheme.colorScheme.background
    val fgColor = MaterialTheme.colorScheme.onBackground
    val primaryColor = MaterialTheme.colorScheme.primary

    if(!LocalInspectionMode.current) {
        val editText = remember {
            EditText(context).apply {
                inputType = EditorInfo.TYPE_CLASS_TEXT

                privateImeOptions = (customOptions + if(!allowPredictions) {
                    listOf("org.futo.inputmethod.latin.NoSuggestions")
                } else {
                    listOf()
                }).joinToString { "$it=1" }

                isSingleLine = !allowPredictions


                setHint(R.string.settings_try_typing_here)
                setBackgroundColor(bgColor.toArgb())
                setTextColor(fgColor.toArgb())
                setHintTextColor(fgColor.copy(alpha = 0.7f).toArgb())
            }
        }

        LaunchedEffect(bgColor, fgColor, primaryColor) {
            editText.setBackgroundColor(bgColor.toArgb())
            editText.setTextColor(fgColor.toArgb())
            editText.setHintTextColor(fgColor.copy(alpha = 0.7f).toArgb())
            editText.highlightColor = primaryColor.copy(alpha = 0.7f).toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                editText.textCursorDrawable?.setTint(primaryColor.toArgb())
                editText.textSelectHandle?.setTint(primaryColor.toArgb())
                editText.textSelectHandleLeft?.setTint(primaryColor.toArgb())
                editText.textSelectHandleRight?.setTint(primaryColor.toArgb())
            }
        }

        AndroidView({ editText }, modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp))

        LaunchedEffect(Unit) {
            delay(50L)
            editText.requestFocus()
            if(autoshow) {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                        as InputMethodManager
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }
}