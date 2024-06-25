package org.futo.inputmethod.latin.uix.settings.pages

import android.view.inputmethod.EditorInfo
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList


@Composable
private fun TextEdit(name: String, type: Int, imeOptions: Int? = null, imeActionLabel: String? = null, imeActionId: Int? = null) {
    val context = LocalContext.current
    val bgColor = MaterialTheme.colorScheme.background
    val fgColor = MaterialTheme.colorScheme.onBackground

    if(!LocalInspectionMode.current) {
        val editText = remember {
            EditText(context).apply {
                this.inputType = type

                if(imeOptions != null) {
                    this.imeOptions = imeOptions
                }

                if(imeActionId != null && imeActionLabel != null) {
                    setImeActionLabel(
                        imeActionLabel,
                        imeActionId
                    )
                }

                setHint(name)
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
            .padding(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun DevEditTextVariationsScreen(navController: NavHostController = rememberNavController()) {

    ScrollableList {
        ScreenTitle("Text Edit Variations", showBack = true, navController)

        TextEdit("multi lines none",
            EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT,
            EditorInfo.IME_ACTION_UNSPECIFIED
        )

        TextEdit("multi lines send",
            EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT,
            EditorInfo.IME_ACTION_SEND
        )


        TextEdit("short message send multi lines",
            EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES,
            EditorInfo.IME_ACTION_SEND
        )

        TextEdit("multi lines search",
            EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES,
            EditorInfo.IME_ACTION_SEARCH
        )

        TextEdit("short message send",
            EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES,
            EditorInfo.IME_ACTION_SEND
        )

        TextEdit("autocap none",
            EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES,
            EditorInfo.IME_ACTION_NONE
        )

        TextEdit("autocap send",
            EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES,
            EditorInfo.IME_ACTION_SEND
        )

        TextEdit("uri go",
            EditorInfo.TYPE_TEXT_VARIATION_URI,
            EditorInfo.IME_ACTION_GO
        )

        TextEdit("email address done",
            EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            EditorInfo.IME_ACTION_DONE
        )

        TextEdit("auto correct search",
            EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT,
            EditorInfo.IME_ACTION_SEARCH
        )

        TextEdit("auto correct search",
            EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT,
            EditorInfo.IME_ACTION_SEARCH
        )

        TextEdit("auto correct previous",
            EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT
        )

        TextEdit("auto correct custom",
            EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT,
            imeActionLabel = "custom action label",
            imeActionId = 100
        )

        TextEdit("phone",
            EditorInfo.TYPE_CLASS_PHONE
        )

        TextEdit("phone no action",
            EditorInfo.TYPE_CLASS_PHONE,
            EditorInfo.IME_ACTION_NONE
        )

        TextEdit("number send",
            EditorInfo.TYPE_CLASS_NUMBER,
            EditorInfo.IME_ACTION_SEND
        )

        TextEdit("number no action",
            EditorInfo.TYPE_CLASS_NUMBER,
            EditorInfo.IME_ACTION_NONE
        )

        TextEdit("password next",
            EditorInfo.TYPE_TEXT_VARIATION_PASSWORD,
            EditorInfo.IME_ACTION_NEXT
        )

        TextEdit("visible password done",
            EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            EditorInfo.IME_ACTION_DONE
        )

        TextEdit("number password send",
            EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD,
            EditorInfo.IME_ACTION_SEND
        )

        TextEdit("text no suggestion",
            EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            EditorInfo.IME_ACTION_GO
        )

        TextEdit("text no autocorrection",
            EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
            EditorInfo.IME_ACTION_DONE
        )


        TextEdit("cap characters with autocorrect",
            EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT
        )

        TextEdit("cap words with autocorrect",
            EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT
        )

        TextEdit("cap sentences with autocorrect",
            EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT
        )

        TextEdit("cap characters",
            EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS
        )

        TextEdit("cap words",
            EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS
        )

        TextEdit("cap sentences",
            EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
        )

        TextEdit("email subject",
            EditorInfo.TYPE_TEXT_VARIATION_EMAIL_SUBJECT
        )

        TextEdit("person name",
            EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME
        )

        TextEdit("postal address",
            EditorInfo.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
        )

        TextEdit("signed number",
            EditorInfo.TYPE_NUMBER_FLAG_SIGNED
        )

        TextEdit("decimal number",
            EditorInfo.TYPE_NUMBER_FLAG_DECIMAL
        )

        TextEdit("signed decimal number",
            EditorInfo.TYPE_NUMBER_FLAG_DECIMAL or EditorInfo.TYPE_NUMBER_FLAG_SIGNED
        )

        TextEdit("datetime",
            EditorInfo.TYPE_DATETIME_VARIATION_NORMAL
        )

        TextEdit("date",
            EditorInfo.TYPE_DATETIME_VARIATION_DATE
        )

        TextEdit("time",
            EditorInfo.TYPE_DATETIME_VARIATION_TIME
        )

    }
}