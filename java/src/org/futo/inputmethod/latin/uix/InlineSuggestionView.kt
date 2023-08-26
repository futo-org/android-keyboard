package org.futo.inputmethod.latin.uix

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Size
import android.util.TypedValue
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.inline.InlineContentView
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.ImageViewStyle
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

@SuppressLint("RestrictedApi")
@RequiresApi(Build.VERSION_CODES.R)
fun createInlineSuggestionsRequest(
    context: Context,
    activeColorScheme: ColorScheme
): InlineSuggestionsRequest {
    val fromDp = { v: Float ->
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v,
            context.resources.displayMetrics
        ).roundToInt()
    }

    val stylesBuilder = UiVersions.newStylesBuilder()
    val suggestionStyle = InlineSuggestionUi.newStyleBuilder()
        .setSingleIconChipStyle(
            ViewStyle.Builder()
                .setBackgroundColor(activeColorScheme.secondaryContainer.toArgb())
                .setPadding(0, 0, 0, 0)
                .build()
        )
        .setChipStyle(
            ViewStyle.Builder()
                .setBackgroundColor(activeColorScheme.secondaryContainer.toArgb())
                .setPadding(
                    fromDp(8.0f),
                    fromDp(0.0f),
                    fromDp(8.0f),
                    fromDp(0.0f),
                )
                .build()
        )
        .setStartIconStyle(ImageViewStyle.Builder().setLayoutMargin(0, 0, 0, 0).build())
        .setTitleStyle(
            TextViewStyle.Builder()
                .setLayoutMargin(
                    fromDp(4.0f),
                    fromDp(0.0f),
                    fromDp(4.0f),
                    fromDp(0.0f),
                )
                .setTextColor(activeColorScheme.onSecondaryContainer.toArgb())
                .setTextSize(14.0f)
                .build()
        )
        .setSubtitleStyle(
            TextViewStyle.Builder()
                .setLayoutMargin(
                    fromDp(4.0f),
                    fromDp(0.0f),
                    fromDp(4.0f),
                    fromDp(0.0f),
                )
                .setTextColor(activeColorScheme.onSecondaryContainer.copy(alpha = 0.5f).toArgb())
                .setTextSize(12.0f)
                .build()
        )
        .setEndIconStyle(
            ImageViewStyle.Builder()
                .setLayoutMargin(0, 0, 0, 0)
                .build()
        )
        .build()
    stylesBuilder.addStyle(suggestionStyle)

    val stylesBundle = stylesBuilder.build()

    val spec = InlinePresentationSpec.Builder(
        Size(0, 0),
        Size(Int.MAX_VALUE, Int.MAX_VALUE)
    ).setStyle(stylesBundle).build()

    return InlineSuggestionsRequest.Builder(listOf(spec)).let { request ->
        request.setMaxSuggestionCount(InlineSuggestionsRequest.SUGGESTION_COUNT_UNLIMITED)
        request.build()
    }
}


@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun InlineSuggestionView(inlineSuggestion: InlineSuggestion) = with(LocalDensity.current) {
    val context = LocalContext.current

    val size = Size(ViewGroup.LayoutParams.WRAP_CONTENT, 32.dp.toPx().toInt())
    var inlineContentView by remember { mutableStateOf<InlineContentView?>(null) }

    LaunchedEffect(Unit) {
        try {
            inlineSuggestion.inflate(context, size, context.mainExecutor) { inflatedView ->
                if (inflatedView != null) {
                    inlineContentView = inflatedView
                }
            }
        } catch (e: Exception) {
            println(e.toString())
        }
    }

    if (inlineContentView != null) {
        AndroidView(
            factory = { inlineContentView!! },
            modifier = Modifier.padding(4.dp, 0.dp)
        )
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun RowScope.InlineSuggestions(suggestions: List<InlineSuggestion>) {
    LazyRow(modifier = Modifier.weight(1.0f).padding(0.dp, 4.dp)) {
        items(suggestions.size) {
            InlineSuggestionView(suggestions[it])
        }
    }
}