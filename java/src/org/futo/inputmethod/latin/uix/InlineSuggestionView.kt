package org.futo.inputmethod.latin.uix

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.ImageViewStyle
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
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
        context.fromDp(v).roundToInt()
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
fun Context.inflateInlineSuggestion(inlineSuggestion: InlineSuggestion): MutableState<View?> {
    val mutableState: MutableState<View?> = mutableStateOf(null)

    val size = Size(ViewGroup.LayoutParams.WRAP_CONTENT, fromDp(32f).roundToInt())

    try {
        inlineSuggestion.inflate(this, size, mainExecutor) { inflatedView ->
            if (inflatedView != null) {
                mutableState.value = inflatedView
            }
        }
    } catch (e: Exception) {
        println(e.toString())
    }

    return mutableState
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun InlineSuggestionView(inlineSuggestion: MutableState<View?>) {
    if (inlineSuggestion.value != null) {
        // TODO: For some reason this appears over top of keyboard key previews
        // We should also make it animate in and round corners
        AndroidView(
            factory = { inlineSuggestion.value!! },
            modifier = Modifier.padding(4.dp, 0.dp)
        )
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun RowScope.InlineSuggestions(suggestions: List<MutableState<View?>>) {
    LazyRow(modifier = Modifier
        .weight(1.0f)
        .padding(0.dp, 4.dp)) {
        items(suggestions.size) {
            InlineSuggestionView(suggestions[it])
        }
    }
}