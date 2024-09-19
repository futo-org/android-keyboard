package org.futo.inputmethod.latin.uix

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.inputmethod.keyboard.Keyboard
import org.futo.inputmethod.keyboard.KeyboardView
import org.futo.inputmethod.keyboard.internal.KeyboardLayoutElement
import org.futo.inputmethod.keyboard.internal.KeyboardLayoutKind
import org.futo.inputmethod.keyboard.internal.KeyboardLayoutPage
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2Params
import org.futo.inputmethod.v2keyboard.LayoutManager
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun KeyboardViewCompose(keyboard: Keyboard?, width: Dp) {
    if(keyboard == null) {
        val presumedHeight = width * 0.61f

        Box(modifier = Modifier.size(width, presumedHeight), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val scale = with(LocalDensity.current) {
        width.toPx() / keyboard.mId.mWidth.toFloat()
    }

    val height = with(LocalDensity.current) {
        (keyboard.mOccupiedHeight.toFloat() * scale).toDp()
    }

    Box(modifier = Modifier.size(width, height)) {
        AndroidView(
            factory = { context ->
                val ctx = ContextThemeWrapper(context, R.style.KeyboardTheme_LXX_Light)

                KeyboardView(ctx, null).apply {
                    setKeyboard(keyboard)
                }
            },
            modifier = Modifier.scale(scale).size(width, height)
        )
    }
}

@Composable
fun KeyboardLayoutPreview(id: String, width: Dp = 172.dp, locale: Locale? = null) {
    val context = LocalContext.current

    val loc = remember(id) {
        locale ?: LayoutManager.getLayout(context, id).languages.firstOrNull()?.let {
            Locale.forLanguageTag(it)
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = false//configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val widthPx: Int
    val heightPx: Int

    when {
        isLandscape -> {
            widthPx  = (500.0 * context.resources.displayMetrics.density).roundToInt()
            heightPx = (180.0 * context.resources.displayMetrics.density).roundToInt()
        }

        else -> {
            widthPx  = (320.0 * context.resources.displayMetrics.density).roundToInt()
            heightPx = (200.0 * context.resources.displayMetrics.density).roundToInt()
        }
    }

    val keyboard = remember { mutableStateOf<Keyboard?>(null) }

    KeyboardViewCompose(keyboard.value, width)

    LaunchedEffect(id) {
        withContext(Dispatchers.Default) {
            val editorInfo = EditorInfo()

            val numberRow = false

            val layoutSet = KeyboardLayoutSetV2(
                context,
                KeyboardLayoutSetV2Params(
                    width = widthPx,
                    height = heightPx,
                    gap = 4.0f,
                    keyboardLayoutSet = id,
                    locale = loc ?: Locale.ENGLISH,
                    editorInfo = editorInfo,
                    numberRow = numberRow,
                    useSplitLayout = isLandscape,
                    bottomActionKey = null
                )
            )

            keyboard.value = layoutSet.getKeyboard(KeyboardLayoutElement(
                kind = KeyboardLayoutKind.Alphabet,
                page = KeyboardLayoutPage.Base
            ))
        }
    }
}