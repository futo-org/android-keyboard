package org.futo.inputmethod.latin.uix

import android.app.Dialog
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.R
import java.net.URLDecoder
import java.net.URLEncoder

// Not exhaustive
fun ColorScheme.differsFrom(other: ColorScheme): Boolean {
    return this.background != other.background
            || this.surface != other.surface
            || this.primary != other.primary
            || this.secondary != other.secondary
            || this.primaryContainer != other.primaryContainer
            || this.secondaryContainer != other.secondaryContainer
            || this.onSurface != other.onSurface
            || this.onBackground != other.onBackground
            || this.onPrimary != other.onPrimary
}

fun Context.fromDp(v: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}

fun String.urlEncode(): String {
    return URLEncoder.encode(this, "utf-8")
}

fun String.urlDecode(): String {
    return URLDecoder.decode(this, "utf-8")
}


// This ugly workaround is required as Android Compose freaks out when you use a Dialog outside of
// an activity (i.e. in an input method service)
data class DialogComposeView(
    val dialog: Dialog,
    val composeView: ComposeView
)

fun createDialogComposeView(
    latinIME: LatinIME,
    maxWidthProportion: Float = 0.9f,
    maxHeightProportion: Float = 0.75f,
    dimAmount: Float = 0.5f,
    onDismiss: () -> Unit = { },
    content: @Composable (Dialog) -> Unit,
): DialogComposeView {
    val context: Context = latinIME

    val composeView = ComposeView(context)

    val dialog = Dialog(context).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(composeView)

        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        setOnDismissListener {
            onDismiss()
        }
    }

    val window = dialog.window
    window?.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    window?.attributes?.token = latinIME.latinIMELegacy.mKeyboardSwitcher.mainKeyboardView.windowToken
    window?.attributes?.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG

    window?.apply {
        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val maxWidth = (width * maxWidthProportion).toInt()
        val maxHeight = (height * maxHeightProportion).toInt()

        setLayout(maxWidth, maxHeight)
        setGravity(Gravity.CENTER)

        setBackgroundDrawable(AppCompatResources.getDrawable(context, R.drawable.empty))
        setDimAmount(dimAmount)
    }

    composeView.setViewTreeLifecycleOwner(latinIME)
    composeView.setViewTreeSavedStateRegistryOwner(latinIME)

    composeView.setContent { content(dialog) }

    return DialogComposeView(dialog, composeView)
}

fun DialogComposeView.show() {
    dialog.show()
}

fun DialogComposeView.dismiss() {
    dialog.dismiss()
}