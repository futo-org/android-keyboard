package org.futo.inputmethod.latin.uix

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.futo.inputmethod.latin.R

@Composable
fun AndroidTextInput() {
    val context = LocalContext.current
    val bgColor = MaterialTheme.colorScheme.background
    val fgColor = MaterialTheme.colorScheme.onBackground

    if(!LocalInspectionMode.current) {
        val editText = remember {
            EditText(context).apply {
                inputType = EditorInfo.TYPE_CLASS_TEXT
                isSingleLine = false
                this.

                setHint(R.string.try_typing)
                setBackgroundColor(bgColor.toArgb())
                setTextColor(fgColor.toArgb())
                setHintTextColor(fgColor.copy(alpha = 0.7f).toArgb())
            }
        }

        LaunchedEffect(bgColor, fgColor) {
            editText.setBackgroundColor(bgColor.toArgb())
            editText.setTextColor(fgColor.toArgb())
            editText.setHintTextColor(fgColor.copy(alpha = 0.7f).toArgb())
        }

        AndroidView({ editText }, modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp), update = { editText.requestFocus() })
    }
}


class TextEditPopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Surface(modifier = Modifier.padding(8.dp), shape = RoundedCornerShape(16.dp)) {
                AndroidTextInput()
            }
        }
    }

}
