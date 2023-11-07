package org.futo.inputmethod.latin.uix

import android.os.Build
import android.view.View
import android.view.inputmethod.InlineSuggestion
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.Flow
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo.KIND_TYPED
import org.futo.inputmethod.latin.suggestions.SuggestionStripView
import org.futo.inputmethod.latin.uix.actions.ThemeAction
import org.futo.inputmethod.latin.uix.actions.VoiceInputAction
import org.futo.inputmethod.latin.uix.theme.DarkColorScheme
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import java.lang.Integer.min
import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 * The UIX Action Bar is intended to replace the previous top bar of the AOSP keyboard.
 * Its goal is to function similar to the old top bar by showing predictions, but also modernize
 * it with actions and new features.
 *
 * Example bar:
 * [>] word1 | word2 | word3 [mic]
 *
 * The [>] button expands the action bar, replacing word predictions with actions the user can take.
 * Actions have little icons which perform an action. Some examples:
 * - Microphone: opens the voice input menu
 * - Undo/Redo
 * - Text editing: switches to the text editing menu
 * - Settings: opens the keyboard settings menu
 * - Report problem: opens the report menu
 *
 * Generally there are a few kinds of actions:
 * - Take an action on the text being typed (undo/redo)
 * - Switch from the keyboard UI to something else (voice input, text editing)
 * - Open an app (settings, report)
 *
 * The UIX effort is to modernize the AOSP Keyboard by replacing and extending
 * parts of it with UI written in Android Compose, while keeping most of the
 * battle-tested original keyboard code the same
 *
 * TODO: Will need to make RTL languages work
 */


val suggestionStylePrimary = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 18.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.5.sp,
    //textAlign = TextAlign.Center
)

val suggestionStyleAlternative = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 18.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.5.sp,
    //textAlign = TextAlign.Center
)


// Automatically try to fit the given text to the available space in one line.
// If text is too long, the text gets scaled horizontally to fit.
// TODO: Could also put ellipsis in the middle
@OptIn(ExperimentalTextApi::class)
@Composable
fun AutoFitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr
) {
    val measurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        val measurement = measurer.measure(
            text = AnnotatedString(text),
            style = style,
            overflow = TextOverflow.Visible,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(
                maxWidth = Int.MAX_VALUE,
                maxHeight = ceil(this.size.height).roundToInt()
            ),
            layoutDirection = layoutDirection,
            density = this
        )

        val scale = (size.width / measurement.size.width).coerceAtMost(1.0f)

        translate(left = (scale * (size.width - measurement.size.width)) / 2.0f) {
            scale(scaleX = scale, scaleY = 1.0f) {
                drawText(
                    measurement
                )
            }
        }
    }
}

@Composable
fun RowScope.SuggestionItem(words: SuggestedWords, idx: Int, isPrimary: Boolean, onClick: () -> Unit) {
    val word = try {
         words.getWord(idx)
    } catch(e: IndexOutOfBoundsException) {
        null
    }

    val wordInfo = try {
        words.getInfo(idx)
    } catch(e: IndexOutOfBoundsException) {
        null
    }

    val actualIsPrimary = isPrimary && (words.mWillAutoCorrect || ((wordInfo?.isExactMatch) == true))

    val iconColor = MaterialTheme.colorScheme.onBackground
    val topSuggestionIcon = painterResource(id = R.drawable.transformer_suggestion)
    val textButtonModifier = when (wordInfo?.mOriginatesFromTransformerLM) {
        true -> Modifier.drawBehind {
            with(topSuggestionIcon) {
                val iconSize = topSuggestionIcon.intrinsicSize
                translate(
                    left = (size.width - iconSize.width) / 2.0f,
                    top = size.height - iconSize.height * 2.0f
                ) {
                    draw(
                        topSuggestionIcon.intrinsicSize,
                        alpha = if(actualIsPrimary){ 1.0f } else { 0.66f } / 1.25f,
                        colorFilter = ColorFilter.tint(color = iconColor)
                    )
                }
            }
        }
        else -> Modifier
    }

    val textModifier = when (actualIsPrimary) {
        true -> Modifier
        false -> Modifier.alpha(0.75f)
    }

    val textStyle = when (actualIsPrimary) {
        true -> suggestionStylePrimary
        false -> suggestionStyleAlternative
    }.copy(color = MaterialTheme.colorScheme.onBackground)

    TextButton(
        onClick = onClick,
        modifier = textButtonModifier
            .weight(1.0f)
            .fillMaxHeight(),
        shape = RectangleShape,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
        enabled = word != null
    ) {
        if(word != null) {
            AutoFitText(word, style = textStyle, modifier = textModifier)
        }
    }
}

@Composable fun RowScope.SuggestionSeparator() {
    Box(
        modifier = Modifier
            .fillMaxHeight(0.66f)
            .align(CenterVertically)
            .background(color = MaterialTheme.colorScheme.outline)
            .width(1.dp)
    )
}



// Show the most probable in the middle, then left, then right
val ORDER_OF_SUGGESTIONS = listOf(1, 0, 2)

@Composable
fun RowScope.SuggestionItems(words: SuggestedWords, onClick: (i: Int) -> Unit) {
    val maxSuggestions = min(ORDER_OF_SUGGESTIONS.size, words.size())

    if(maxSuggestions == 0) {
        Spacer(modifier = Modifier.weight(1.0f))
        return
    }


    var offset = 0

    try {
        val info = words.getInfo(0)
        if (info.kind == KIND_TYPED && !info.isExactMatch && !info.isExactMatchWithIntentionalOmission) {
            offset = 1
        }
    } catch(_: IndexOutOfBoundsException) {

    }


    for (i in 0 until maxSuggestions) {
        val remapped = if(offset == 1 && i == 2) {
            0 - offset
        } else {
            ORDER_OF_SUGGESTIONS[i]
        }

        SuggestionItem(
            words,
            remapped + offset,
            isPrimary = remapped == 0
        ) { onClick(remapped + offset) }

        if (i < maxSuggestions - 1) SuggestionSeparator()
    }
}

@Composable
fun ActionItem(action: Action, onSelect: (Action) -> Unit) {
    val col = MaterialTheme.colorScheme.secondaryContainer
    val contentCol = MaterialTheme.colorScheme.onSecondaryContainer
    IconButton(onClick = { onSelect(action) }, modifier = Modifier
        .drawBehind {
            val radius = size.height / 4.0f
            drawRoundRect(
                col,
                topLeft = Offset(size.width * 0.1f, size.height * 0.05f),
                size = Size(size.width * 0.8f, size.height * 0.9f),
                cornerRadius = CornerRadius(radius, radius)
            )
        }
        .width(64.dp)
        .fillMaxHeight(),
        colors = IconButtonDefaults.iconButtonColors(contentColor = contentCol)
    ) {
        Icon(
            painter = painterResource(id = action.icon),
            contentDescription = stringResource(action.name)
        )
    }
}

@Composable
fun ActionItemSmall(action: Action, onSelect: (Action) -> Unit) {
    IconButton(onClick = {
        onSelect(action)
    }, modifier = Modifier
        .width(42.dp)
        .fillMaxHeight()) {
        Icon(
            painter = painterResource(id = action.icon),
            contentDescription = stringResource(action.name)
        )
    }
}

@Composable
fun RowScope.ActionItems(onSelect: (Action) -> Unit) {
    ActionItem(VoiceInputAction, onSelect)
    ActionItem(ThemeAction, onSelect)

    Box(modifier = Modifier
        .fillMaxHeight()
        .weight(1.0f)) {

    }
}


@Composable
fun ExpandActionsButton(isActionsOpen: Boolean, onClick: () -> Unit) {
    val moreActionsColor = MaterialTheme.colorScheme.primary

    val moreActionsFill = if(isActionsOpen) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.background
    }

    val actionsContent = if(isActionsOpen) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .width(42.dp)
            .rotate(
                if (isActionsOpen) {
                    180.0f
                } else {
                    0.0f
                }
            )
            .fillMaxHeight()
            .drawBehind {
                drawCircle(color = moreActionsColor, radius = size.width / 3.0f + 1.0f)
                drawCircle(color = moreActionsFill, radius = size.width / 3.0f - 2.0f)
            },

        colors = IconButtonDefaults.iconButtonColors(contentColor = actionsContent)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.chevron_right),
            contentDescription = "Open Actions"
        )
    }
}

@Composable
fun ActionBar(
    words: SuggestedWords?,
    suggestionStripListener: SuggestionStripView.Listener,
    onActionActivated: (Action) -> Unit,
    inlineSuggestions: List<MutableState<View?>>,
    forceOpenActionsInitially: Boolean = false,
) {
    val isActionsOpen = remember { mutableStateOf(forceOpenActionsInitially) }

    Surface(modifier = Modifier
        .fillMaxWidth()
        .height(40.dp), color = MaterialTheme.colorScheme.background)
    {
        Row {
            ExpandActionsButton(isActionsOpen.value) { isActionsOpen.value = !isActionsOpen.value }

            if(isActionsOpen.value) {
                ActionItems(onActionActivated)
            } else if(inlineSuggestions.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                InlineSuggestions(inlineSuggestions)
            } else if(words != null) {
                SuggestionItems(words) {
                    suggestionStripListener.pickSuggestionManually(
                        words.getInfo(it)
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1.0f))
            }

            ActionItemSmall(VoiceInputAction, onActionActivated)
        }
    }
}




/* ---- Previews ---- */

class ExampleListener : SuggestionStripView.Listener {
    override fun showImportantNoticeContents() {
    }

    override fun pickSuggestionManually(word: SuggestedWordInfo?) {
    }

    override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) {
    }
}

val exampleSuggestionsList = arrayListOf(
    SuggestedWordInfo("verylongword123", "", 100, 1, null, 0, 0),
    SuggestedWordInfo("world understanding of patience", "",  99, 1, null, 0, 0),
    SuggestedWordInfo("short", "", 98, 1, null, 0, 0),
    SuggestedWordInfo("extra1", "", 97, 1, null, 0, 0),
    SuggestedWordInfo("extra2", "", 96, 1, null, 0, 0),
    SuggestedWordInfo("extra3", "", 95, 1, null, 0, 0)
)

val exampleSuggestedWords = SuggestedWords(
    exampleSuggestionsList,
    exampleSuggestionsList,
    exampleSuggestionsList[0],
    true,
    true,
    false,
    0,
    0
)

val exampleSuggestedWordsEmpty = SuggestedWords(
    arrayListOf(),
    arrayListOf(),
    exampleSuggestionsList[0],
    true,
    true,
    false,
    0,
    0
)

@Composable
@Preview
fun PreviewActionBarWithSuggestions(colorScheme: ColorScheme = DarkColorScheme) {
    UixThemeWrapper(colorScheme) {
        ActionBar(
            words = exampleSuggestedWords,
            suggestionStripListener = ExampleListener(),
            onActionActivated = { },
            inlineSuggestions = listOf()
        )
    }
}

@Composable
@Preview
fun PreviewActionBarWithEmptySuggestions(colorScheme: ColorScheme = DarkColorScheme) {
    UixThemeWrapper(colorScheme) {
        ActionBar(
            words = exampleSuggestedWordsEmpty,
            suggestionStripListener = ExampleListener(),
            onActionActivated = { },
            inlineSuggestions = listOf()
        )
    }
}

@Composable
@Preview
fun PreviewExpandedActionBar(colorScheme: ColorScheme = DarkColorScheme) {
    UixThemeWrapper(colorScheme) {
        ActionBar(
            words = exampleSuggestedWordsEmpty,
            suggestionStripListener = ExampleListener(),
            onActionActivated = { },
            inlineSuggestions = listOf(),
            forceOpenActionsInitially = true
        )
    }
}


@Composable
@Preview
fun PreviewActionBarWithSuggestionsDynamicLight() {
    PreviewActionBarWithSuggestions(dynamicLightColorScheme(LocalContext.current))
}

@Composable
@Preview
fun PreviewActionBarWithEmptySuggestionsDynamicLight() {
    PreviewActionBarWithEmptySuggestions(dynamicLightColorScheme(LocalContext.current))
}

@Composable
@Preview
fun PreviewExpandedActionBarDynamicLight() {
    PreviewExpandedActionBar(dynamicLightColorScheme(LocalContext.current))
}

@Composable
@Preview
fun PreviewActionBarWithSuggestionsDynamicDark() {
    PreviewActionBarWithSuggestions(dynamicDarkColorScheme(LocalContext.current))
}

@Composable
@Preview
fun PreviewActionBarWithEmptySuggestionsDynamicDark() {
    PreviewActionBarWithEmptySuggestions(dynamicDarkColorScheme(LocalContext.current))
}

@Composable
@Preview
fun PreviewExpandedActionBarDynamicDark() {
    PreviewExpandedActionBar(dynamicDarkColorScheme(LocalContext.current))
}