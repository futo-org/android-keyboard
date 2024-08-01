package org.futo.inputmethod.latin.uix

import android.content.Context
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo.KIND_EMOJI_SUGGESTION
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo.KIND_TYPED
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.suggestions.SuggestionStripView
import org.futo.inputmethod.latin.uix.actions.FavoriteActions
import org.futo.inputmethod.latin.uix.actions.MoreActionsAction
import org.futo.inputmethod.latin.uix.actions.PinnedActions
import org.futo.inputmethod.latin.uix.actions.toActionList
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.theme.DarkColorScheme
import org.futo.inputmethod.latin.uix.theme.Typography
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

val ActionBarScrollIndexSetting = SettingsKey(
    intPreferencesKey("action_bar_scroll_index"),
    0
)

val ActionBarScrollOffsetSetting = SettingsKey(
    intPreferencesKey("action_bar_scroll_offset"),
    0
)

val ActionBarExpanded = SettingsKey(
    booleanPreferencesKey("actionExpanded"),
    false
)

val OldStyleActionsBar = SettingsKey(
    booleanPreferencesKey("oldActionBar"),
    false
)


interface ImportantNotice {
    @Composable fun getText(): String
    fun onDismiss(context: Context)
    fun onOpen(context: Context)
}


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

    Canvas(modifier = modifier.fillMaxSize(), contentDescription = text) {
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

        translate(left = (scale * (size.width - measurement.size.width)) / 2.0f, top = size.height / 2 - measurement.size.height / 2) {
            scale(scaleX = scale, scaleY = 1.0f) {
                drawText(
                    measurement
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.SuggestionItem(words: SuggestedWords, idx: Int, isPrimary: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
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

    Box(
        modifier = textButtonModifier
            .weight(1.0f)
            .fillMaxHeight()
            .combinedClickable(
                enabled = word != null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            if (word != null) {
                AutoFitText(word, style = textStyle, modifier = textModifier
                    .align(Center)
                    .padding(2.dp))
            }
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
fun RowScope.SuggestionItems(words: SuggestedWords, onClick: (i: Int) -> Unit, onLongClick: (i: Int) -> Unit) {
    val maxSuggestions = min(ORDER_OF_SUGGESTIONS.size, words.size())

    if(maxSuggestions == 0) {
        Spacer(modifier = Modifier.weight(1.0f))
        return
    }

    if(maxSuggestions == 1 || words.mInputStyle == SuggestedWords.INPUT_STYLE_UPDATE_BATCH) {
        SuggestionItem(
            words,
            0,
            isPrimary = true,
            onClick = { onClick(0) },
            onLongClick = { onLongClick(0) }
        )

        return
    } else if(words.mInputStyle == SuggestedWords.INPUT_STYLE_TAIL_BATCH && maxSuggestions > 1) {
        //words.mSuggestedWordInfoList.removeAt(0);
    }


    var offset = 0

    try {
        val info = words.getInfo(0)
        if (info.kind == KIND_TYPED && !info.isExactMatch && !info.isExactMatchWithIntentionalOmission) {
            offset = 1
        }
    } catch(_: IndexOutOfBoundsException) {

    }

    // Check for "clueless" suggestions, and display typed word in center if so
    try {
        if(offset == 1) {
            val info = words.getInfo(1)
            if(info.mOriginatesFromTransformerLM && info.mScore < -50) {
                offset = 0;
            }
        }
    } catch(_: IndexOutOfBoundsException) {

    }


    val suggestionOrder = mutableListOf(
        ORDER_OF_SUGGESTIONS[0] + offset,
        ORDER_OF_SUGGESTIONS[1] + offset,
        if(offset == 1) { 0 - offset } else { ORDER_OF_SUGGESTIONS[2] } + offset,
    )

    // Find emoji
    try {
        for(i in 0 until words.size()) {
            val info = words.getInfo(i)
            if(info.mKindAndFlags == KIND_EMOJI_SUGGESTION && i > 2) {
                suggestionOrder[0] = i
            }
        }
    } catch(_: IndexOutOfBoundsException) {

    }


    for (i in 0 until maxSuggestions) {
        SuggestionItem(
            words,
            suggestionOrder[i],
            isPrimary = i == (maxSuggestions / 2),
            onClick = { onClick(suggestionOrder[i]) },
            onLongClick = { onLongClick(suggestionOrder[i]) }
        )

        if (i < maxSuggestions - 1) SuggestionSeparator()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyItemScope.ActionItem(idx: Int, action: Action, onSelect: (Action) -> Unit, onLongSelect: (Action) -> Unit) {
    val width = 56.dp

    val modifier = Modifier
        .width(width)
        .fillMaxHeight()

    val contentCol = MaterialTheme.colorScheme.onBackground

    Box(modifier = modifier
        .clip(CircleShape)
        .combinedClickable(onLongClick = action.altPressImpl?.let { { onLongSelect(action) } },
            onClick = { onSelect(action) }), contentAlignment = Center) {
        Icon(
            painter = painterResource(id = action.icon),
            contentDescription = stringResource(action.name),
            tint = contentCol,
            modifier = Modifier.size(20.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionItemSmall(action: Action, onSelect: (Action) -> Unit, onLongSelect: (Action) -> Unit) {
    val bgCol = LocalKeyboardScheme.current.backgroundContainer
    val fgCol = LocalKeyboardScheme.current.onBackgroundContainer

    val circleRadius = with(LocalDensity.current) {
        16.dp.toPx()
    }

    Box(modifier = Modifier
        .width(42.dp)
        .fillMaxHeight()
        .drawBehind {
            drawCircle(
                color = bgCol,
                radius = circleRadius,
                style = Fill
            )
        }
        .clip(CircleShape)
        .combinedClickable(onLongClick = action.altPressImpl?.let { { onLongSelect(action) } }) {
            onSelect(
                action
            )
        },
        contentAlignment = Center
    ) {
        Icon(
            painter = painterResource(id = action.icon),
            contentDescription = stringResource(action.name),
            tint = fgCol,
            modifier = Modifier.size(16.dp)
        )
    }
}


@Composable
fun ActionItems(onSelect: (Action) -> Unit, onLongSelect: (Action) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val actions = if(!LocalInspectionMode.current) {
        useDataStoreValue(FavoriteActions)
    } else {
        FavoriteActions.default
    }

    val scrollItemIndex = if(LocalInspectionMode.current) { 0 } else {
        remember {
            context.getSettingBlocking(ActionBarScrollIndexSetting)
        }
    }

    val scrollItemOffset = if(LocalInspectionMode.current) { 0 } else {
        remember {
            context.getSettingBlocking(ActionBarScrollOffsetSetting)
        }
    }

    val actionItems = remember(actions) {
        actions.toActionList()
    }

    val lazyListState = rememberLazyListState(scrollItemIndex, scrollItemOffset)

    DisposableEffect(Unit) {
        onDispose {
            lifecycle.deferSetSetting(ActionBarScrollIndexSetting, lazyListState.firstVisibleItemIndex)
            lifecycle.deferSetSetting(ActionBarScrollOffsetSetting, lazyListState.firstVisibleItemScrollOffset)
        }
    }

    val bgCol = LocalKeyboardScheme.current.backgroundContainer

    val gradientColor = if(bgCol.alpha > 0.5) {
        bgCol.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    }

    val drawLeftGradient = lazyListState.firstVisibleItemIndex > 0
    val drawRightGradient = lazyListState.layoutInfo.visibleItemsInfo.isNotEmpty() && actionItems.isNotEmpty() && (lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.key != actionItems.lastOrNull()?.name)

    Box {
        LazyRow(state = lazyListState) {
            item {
                ActionItemSmall(action = MoreActionsAction, onSelect = {
                    onSelect(MoreActionsAction)
                }, onLongSelect = { })

            }
            items(actionItems.size, key = { actionItems[it].name }) {
                ActionItem(it, actionItems[it], onSelect, onLongSelect)
            }
        }


        if(drawLeftGradient) {
            Canvas(modifier = Modifier
                .fillMaxHeight()
                .width(72.dp)
                .align(Alignment.CenterStart)) {
                drawRect(
                    Brush.linearGradient(
                        0.0f to gradientColor,
                        1.0f to Color.Transparent,
                        start = Offset.Zero,
                        end = Offset(Float.POSITIVE_INFINITY, 0.0f)
                    )
                )
            }
        }

        if(drawRightGradient) {
            Canvas(modifier = Modifier
                .fillMaxHeight()
                .width(72.dp)
                .align(Alignment.CenterEnd)) {
                drawRect(
                    Brush.linearGradient(
                        0.0f to Color.Transparent,
                        1.0f to gradientColor,
                        start = Offset.Zero,
                        end = Offset(Float.POSITIVE_INFINITY, 0.0f)
                    )
                )
            }
        }
    }
}


@Composable
fun ExpandActionsButton(isActionsOpen: Boolean, onClick: () -> Unit) {
    val bgCol = LocalKeyboardScheme.current.backgroundContainer
    val fgCol = LocalKeyboardScheme.current.onBackgroundContainer

    val circleRadius = with(LocalDensity.current) {
        16.dp.toPx()
    }

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .width(42.dp)
            .rotate(
                if (isActionsOpen) {
                    -90.0f
                } else {
                    0.0f
                }
            )
            .fillMaxHeight()
            .drawBehind {
                drawCircle(
                    color = bgCol,
                    radius = circleRadius,
                    style = Fill
                )
            },

        colors = IconButtonDefaults.iconButtonColors(contentColor = fgCol)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.chevron_right),
            contentDescription = "Open Actions",
            Modifier.size(20.dp)
        )
    }
}

@Composable
fun ImportantNoticeView(
    importantNotice: ImportantNotice
) {
    val context = LocalContext.current

    Row {
        TextButton(
            onClick = { importantNotice.onOpen(context) },
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight(),
            shape = RectangleShape,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
            enabled = true
        ) {
            AutoFitText(importantNotice.getText(), style = suggestionStylePrimary.copy(color = MaterialTheme.colorScheme.onBackground))
        }

        val color = MaterialTheme.colorScheme.primary
        IconButton(
            onClick = { importantNotice.onDismiss(context) },
            modifier = Modifier
                .width(42.dp)
                .fillMaxHeight()
                .drawBehind {
                    drawCircle(color = color, radius = size.width / 3.0f + 1.0f)
                },

            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.close),
                contentDescription = "Close"
            )
        }
    }
}

@Composable
fun RowScope.PinnedActionItems(onSelect: (Action) -> Unit, onLongSelect: (Action) -> Unit) {
    val actions = if(!LocalInspectionMode.current) {
        useDataStoreValue(PinnedActions)
    } else {
        PinnedActions.default
    }

    val actionItems = remember(actions) {
        actions.toActionList()
    }

    actionItems.forEach {
        ActionItemSmall(it, onSelect, onLongSelect)
    }
}

@Composable
fun ActionSep() {
    val sepCol = LocalKeyboardScheme.current.backgroundContainer

    Box(modifier = Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(sepCol)) {}
}

@Composable
fun ActionBar(
    words: SuggestedWords?,
    suggestionStripListener: SuggestionStripView.Listener,
    onActionActivated: (Action) -> Unit,
    onActionAltActivated: (Action) -> Unit,
    inlineSuggestions: List<MutableState<View?>>,
    isActionsExpanded: Boolean,
    toggleActionsExpanded: () -> Unit,
    importantNotice: ImportantNotice? = null,
    keyboardManagerForAction: KeyboardManagerForAction? = null,
) {
    val view = LocalView.current
    val context = LocalContext.current

    val actionBarHeight = 40.dp

    val oldActionBar = useDataStore(OldStyleActionsBar)

    Column {
        if(isActionsExpanded && !oldActionBar.value) {
            ActionSep()

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(actionBarHeight), color = MaterialTheme.colorScheme.background
            ) {
                ActionItems(onActionActivated, onActionAltActivated)
            }
        }

        ActionSep()

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(actionBarHeight), color = MaterialTheme.colorScheme.background
        ) {
            Row {
                ExpandActionsButton(isActionsExpanded) {
                    toggleActionsExpanded()

                    keyboardManagerForAction?.performHapticAndAudioFeedback(
                        Constants.CODE_TAB,
                        view
                    )
                }

                if(oldActionBar.value && isActionsExpanded) {
                    Box(modifier = Modifier
                        .weight(1.0f)
                        .fillMaxHeight()) {
                        ActionItems(onActionActivated, onActionAltActivated)
                    }
                } else {
                    if (importantNotice != null) {
                        ImportantNoticeView(importantNotice)
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            AnimatedVisibility(
                                inlineSuggestions.isNotEmpty(),
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                InlineSuggestions(inlineSuggestions)
                            }
                        }

                        if (words != null && inlineSuggestions.isEmpty()) {
                            SuggestionItems(
                                words,
                                onClick = {
                                    suggestionStripListener.pickSuggestionManually(
                                        words.getInfo(it)
                                    )
                                    keyboardManagerForAction?.performHapticAndAudioFeedback(
                                        Constants.CODE_TAB,
                                        view
                                    )
                                },
                                onLongClick = {
                                    suggestionStripListener.requestForgetWord(
                                        words.getInfo(it)
                                    )
                                })
                        } else {
                            Spacer(modifier = Modifier.weight(1.0f))
                        }

                        PinnedActionItems(onActionActivated, onActionAltActivated)
                    }
                }
            }
        }
    }
}

@Composable
fun ActionWindowBar(
    windowTitleBar: @Composable RowScope.() -> Unit,
    canExpand: Boolean,
    onBack: () -> Unit,
    onExpand: () -> Unit
) {
    Column {
        ActionSep()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp), color = MaterialTheme.colorScheme.background
        )
        {
            Row {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(id = R.drawable.arrow_left_26),
                        contentDescription = "Back"
                    )
                }

                CompositionLocalProvider(LocalTextStyle provides Typography.titleMedium) {
                    windowTitleBar()
                }

                if (canExpand) {
                    IconButton(onClick = onExpand) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_up),
                            contentDescription = "Show Keyboard"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CollapsibleSuggestionsBar(
    onClose: () -> Unit,
    onCollapse: () -> Unit,
    words: SuggestedWords?,
    suggestionStripListener: SuggestionStripView.Listener,
) {
    Column {
        ActionSep()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp), color = MaterialTheme.colorScheme.background
        )
        {
            Row {
                val color = MaterialTheme.colorScheme.primary

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .width(42.dp)
                        .fillMaxHeight()
                        .drawBehind {
                            drawCircle(color = color, radius = size.width / 3.0f + 1.0f)
                        },

                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.close),
                        contentDescription = "Close"
                    )
                }

                if (words != null) {
                    SuggestionItems(
                        words,
                        onClick = {
                            suggestionStripListener.pickSuggestionManually(
                                words.getInfo(it)
                            )
                        },
                        onLongClick = { suggestionStripListener.requestForgetWord(words.getInfo(it)) })
                } else {
                    Spacer(modifier = Modifier.weight(1.0f))
                }

                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .width(42.dp)
                        .fillMaxHeight(),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.arrow_down),
                        contentDescription = "Collapse"
                    )
                }
            }
        }
    }
}




/* ---- Previews ---- */

class ExampleListener : SuggestionStripView.Listener {
    override fun showImportantNoticeContents() {
    }

    override fun pickSuggestionManually(word: SuggestedWordInfo?) {
    }

    override fun requestForgetWord(word: SuggestedWordInfo?) {
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
    UixThemeWrapper(wrapColorScheme(colorScheme)) {
        ActionBar(
            words = exampleSuggestedWords,
            suggestionStripListener = ExampleListener(),
            onActionActivated = { },
            inlineSuggestions = listOf(),
            isActionsExpanded = false,
            toggleActionsExpanded = { },
            onActionAltActivated = { }
        )
    }
}

@Composable
@Preview
fun PreviewActionBarWithNotice(colorScheme: ColorScheme = DarkColorScheme) {
    UixThemeWrapper(wrapColorScheme(colorScheme)) {
        ActionBar(
            words = exampleSuggestedWords,
            suggestionStripListener = ExampleListener(),
            onActionActivated = { },
            inlineSuggestions = listOf(),
            isActionsExpanded = true,
            toggleActionsExpanded = { },
            onActionAltActivated = { },
            importantNotice = object : ImportantNotice {
                @Composable
                override fun getText(): String {
                    return "Update available: v1.2.3"
                }

                override fun onDismiss(context: Context) {
                    TODO("Not yet implemented")
                }

                override fun onOpen(context: Context) {
                    TODO("Not yet implemented")
                }

            }
        )
    }
}

@Composable
@Preview
fun PreviewActionBarWithEmptySuggestions(colorScheme: ColorScheme = DarkColorScheme) {
    UixThemeWrapper(wrapColorScheme(colorScheme)) {
        ActionBar(
            words = exampleSuggestedWordsEmpty,
            suggestionStripListener = ExampleListener(),
            onActionActivated = { },
            inlineSuggestions = listOf(),
            isActionsExpanded = true,
            toggleActionsExpanded = { },
            onActionAltActivated = { }
        )
    }
}

@Composable
@Preview
fun PreviewExpandedActionBar(colorScheme: ColorScheme = DarkColorScheme) {
    UixThemeWrapper(wrapColorScheme(colorScheme)) {
        ActionBar(
            words = exampleSuggestedWordsEmpty,
            suggestionStripListener = ExampleListener(),
            onActionActivated = { },
            inlineSuggestions = listOf(),
            isActionsExpanded = true,
            toggleActionsExpanded = { },
            onActionAltActivated = { }
        )
    }
}

@Composable
@Preview
fun PreviewCollapsibleBar(colorScheme: ColorScheme = DarkColorScheme) {
    CollapsibleSuggestionsBar(
        onCollapse = { },
        onClose = { },
        words = exampleSuggestedWords,
        suggestionStripListener = ExampleListener()
    )
}


@RequiresApi(Build.VERSION_CODES.S)
@Composable
@Preview
fun PreviewActionBarWithSuggestionsDynamicLight() {
    PreviewActionBarWithSuggestions(dynamicLightColorScheme(LocalContext.current))
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
@Preview
fun PreviewActionBarWithEmptySuggestionsDynamicLight() {
    PreviewActionBarWithEmptySuggestions(dynamicLightColorScheme(LocalContext.current))
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
@Preview
fun PreviewExpandedActionBarDynamicLight() {
    PreviewExpandedActionBar(dynamicLightColorScheme(LocalContext.current))
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
@Preview
fun PreviewActionBarWithSuggestionsDynamicDark() {
    PreviewActionBarWithSuggestions(dynamicDarkColorScheme(LocalContext.current))
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
@Preview
fun PreviewActionBarWithEmptySuggestionsDynamicDark() {
    PreviewActionBarWithEmptySuggestions(dynamicDarkColorScheme(LocalContext.current))
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
@Preview
fun PreviewExpandedActionBarDynamicDark() {
    PreviewExpandedActionBar(dynamicDarkColorScheme(LocalContext.current))
}