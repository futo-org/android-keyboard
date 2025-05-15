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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo.KIND_EMOJI_SUGGESTION
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo.KIND_TYPED
import org.futo.inputmethod.latin.SuggestionBlacklist
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.suggestions.SuggestionStripViewListener
import org.futo.inputmethod.latin.uix.actions.FavoriteActions
import org.futo.inputmethod.latin.uix.actions.MoreActionsAction
import org.futo.inputmethod.latin.uix.actions.PinnedActions
import org.futo.inputmethod.latin.uix.actions.toActionList
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import org.futo.inputmethod.latin.uix.theme.presets.DefaultDarkScheme
import org.futo.inputmethod.latin.uix.theme.presets.DynamicDarkTheme
import org.futo.inputmethod.latin.uix.theme.presets.DynamicLightTheme
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

val ActionBarHeight = 40.dp

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


@Composable
fun actionBarColor(): Color =
    if(LocalInspectionMode.current) {
        LocalKeyboardScheme.current.keyboardSurface
    } else if(LocalKeyboardScheme.current.keyboardBackgroundGradient != null) {
        Color.Transparent
    } else {
        LocalThemeProvider.current.actionBarColor
    }


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
    val wordInfo = words.getInfoOrNull(idx)
    val isVerbatim = wordInfo?.kind == KIND_TYPED
    val word = wordInfo?.mWord

    val isAutocorrect = isPrimary && words.mWillAutoCorrect

    val color = when(isAutocorrect) {
        true -> LocalKeyboardScheme.current.onSurface
        else -> LocalKeyboardScheme.current.onSurfaceVariant
    }

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
                        colorFilter = ColorFilter.tint(color = color)
                    )
                }
            }
        }
        else -> Modifier
    }

    val textStyle = when(isAutocorrect) {
        true -> suggestionStylePrimary
        false -> suggestionStyleAlternative
    }.copy(color = color)

    Box(
        modifier = textButtonModifier
            .weight(1.0f)
            .fillMaxHeight()
            .combinedClickable(
                enabled = word != null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("SuggestionItem"),
    ) {
        CompositionLocalProvider(LocalContentColor provides color) {
            if (word != null) {
                val modifier = Modifier
                    .align(Center)
                    .padding(2.dp)
                    .testTag("SuggestionItemText")
                if(isVerbatim) {
                    AutoFitText('"' + word + '"', style = textStyle.copy(fontStyle = FontStyle.Italic), modifier = modifier)
                } else {
                    AutoFitText(word, style = textStyle, modifier = modifier)
                }
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


data class SuggestionLayout(
    /** Set to the word to be autocorrected to */
    val autocorrectMatch: SuggestedWordInfo?,

    /** Other words, sorted by likelihood */
    val sortedMatches: List<SuggestedWordInfo>,

    /** Emoji suggestions if they are to be shown */
    val emojiMatches: List<SuggestedWordInfo>,

    /** The exact word the user typed */
    val verbatimWord: SuggestedWordInfo?,

    /** Set to true if the best match is so unlikely that we should show verbatim instead */
    val areSuggestionsClueless: Boolean,

    /** Set to true if this is a gesture update, and we should only show one suggestion */
    val isGestureBatch: Boolean,

    val presentableSuggestions: List<SuggestedWordInfo>
)

fun SuggestedWords.getInfoOrNull(idx: Int): SuggestedWordInfo? = try {
    getInfo(idx)
} catch(e: IndexOutOfBoundsException) {
    null
}

fun makeSuggestionLayout(words: SuggestedWords, blacklist: SuggestionBlacklist?): SuggestionLayout {
    val typedWord = words.getInfoOrNull(SuggestedWords.INDEX_OF_TYPED_WORD)?.let {
        if(it.kind == KIND_TYPED) { it } else { null }
    }?.let {
        if(blacklist?.isSuggestedWordOk(it) != false) {
            it
        } else {
            null
        }
    }

    val autocorrectMatch = words.getInfoOrNull(SuggestedWords.INDEX_OF_AUTO_CORRECTION)?.let {
        if(words.mWillAutoCorrect) { it } else { null }
    }

    // We actually have to avoid sorting these because they are provided sorted in an important order

    val emojiMatches = words.mSuggestedWordInfoList.filter {
        it.kind == KIND_EMOJI_SUGGESTION
    }

    val sortedMatches = words.mSuggestedWordInfoList.filter {
        it != typedWord && it.kind != KIND_TYPED && it != autocorrectMatch && !emojiMatches.contains(it)
    }

    val areSuggestionsClueless = (autocorrectMatch ?: sortedMatches.getOrNull(0))?.let {
        it.mOriginatesFromTransformerLM && it.mScore < -50
    } ?: false

    val isGestureBatch = words.mInputStyle == SuggestedWords.INPUT_STYLE_UPDATE_BATCH

    val presentableSuggestions = (
            listOf(
                typedWord,
                autocorrectMatch,
            ) + sortedMatches
    ).filterNotNull()

    return SuggestionLayout(
        autocorrectMatch = autocorrectMatch,
        sortedMatches = sortedMatches,
        emojiMatches = emojiMatches,
        verbatimWord = typedWord,
        areSuggestionsClueless = areSuggestionsClueless,
        isGestureBatch = isGestureBatch,
        presentableSuggestions = presentableSuggestions
    )
}

@Composable
fun RowScope.SuggestionItems(words: SuggestedWords, onClick: (i: Int) -> Unit, onLongClick: (i: Int) -> Unit) {
    val layout = makeSuggestionLayout(
        words,
        if(!LocalInspectionMode.current) {
            LocalManager.current.getSuggestionBlacklist()
        } else {
            null
        }
    )

    val suggestionItem = @Composable { suggestion: SuggestedWordInfo? ->
        if(suggestion != null) {
            val idx = words.indexOf(suggestion)
            SuggestionItem(
                words,
                idx,
                isPrimary = idx == SuggestedWords.INDEX_OF_AUTO_CORRECTION,
                onClick = { onClick(idx) },
                onLongClick = { onLongClick(idx) }
            )
        } else {
            Spacer(Modifier.weight(1.0f))
        }
    }

    when {
        layout.isGestureBatch ||
                (layout.emojiMatches.isEmpty() && layout.presentableSuggestions.size <= 1) ->
            suggestionItem(layout.presentableSuggestions.firstOrNull())

        layout.autocorrectMatch != null -> {
            var supplementalSuggestionIndex = 0
            if(layout.emojiMatches.isEmpty()) {
                suggestionItem(layout.sortedMatches.getOrNull(supplementalSuggestionIndex++))
            } else {
                suggestionItem(layout.emojiMatches[0])
            }
            SuggestionSeparator()
            suggestionItem(layout.autocorrectMatch)
            SuggestionSeparator()

            if(layout.verbatimWord != null && layout.verbatimWord.mWord != layout.autocorrectMatch.mWord) {
                suggestionItem(layout.verbatimWord)
            } else {
                suggestionItem(layout.sortedMatches.getOrNull(supplementalSuggestionIndex))
            }
        }

        else -> {
            var supplementalSuggestionIndex = 1
            if(layout.emojiMatches.isEmpty()) {
                suggestionItem(layout.sortedMatches.getOrNull(supplementalSuggestionIndex++))
            } else {
                suggestionItem(layout.emojiMatches[0])
            }
            SuggestionSeparator()
            suggestionItem(layout.sortedMatches.getOrNull(0))
            SuggestionSeparator()
            suggestionItem(layout.sortedMatches.getOrNull(supplementalSuggestionIndex))
        }
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
        .combinedClickable(
            onLongClick = action.altPressImpl?.let { { onLongSelect(action) } },
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
    val bgCol = LocalKeyboardScheme.current.keyboardContainer
    val fgCol = LocalKeyboardScheme.current.onKeyboardContainer

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
        actions.toActionList().toSet().toList()
    }

    val lazyListState = rememberLazyListState(scrollItemIndex, scrollItemOffset)

    DisposableEffect(Unit) {
        onDispose {
            lifecycle.deferSetSetting(context, ActionBarScrollIndexSetting, lazyListState.firstVisibleItemIndex)
            lifecycle.deferSetSetting(context, ActionBarScrollOffsetSetting, lazyListState.firstVisibleItemScrollOffset)
        }
    }

    val gradientColor = LocalKeyboardScheme.current.keyboardSurfaceDim

    val drawLeftGradient = remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 0 } }
    val drawRightGradient = remember(actionItems) { derivedStateOf {
        lazyListState.layoutInfo.visibleItemsInfo.isNotEmpty()
                && actionItems.isNotEmpty()
                && (lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.key != actionItems.lastOrNull()?.name)
    } }

    Box(Modifier.safeKeyboardPadding()) {
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


        if(drawLeftGradient.value) {
            Canvas(modifier = Modifier
                .fillMaxHeight()
                .width(72.dp)
                .align(Alignment.CenterStart)) {
                for(i in 0 until 1) {
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
        }

        if(drawRightGradient.value) {
            Canvas(modifier = Modifier
                .fillMaxHeight()
                .width(72.dp)
                .align(Alignment.CenterEnd)) {
                for(i in 0 until 1) {
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
}


@Composable
fun ExpandActionsButton(isActionsOpen: Boolean, onClick: () -> Unit) {
    val bgCol = LocalKeyboardScheme.current.keyboardContainer
    val fgCol = LocalKeyboardScheme.current.onKeyboardContainer

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
            contentDescription = stringResource(R.string.keyboard_actionbar_expand_actions_talkback),
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
                contentDescription = stringResource(R.string.keyboard_actionbar_dismiss_important_notice_talkback)
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
fun ActionSep(isExtra: Boolean = false) {
    if(isExtra && (LocalInspectionMode.current || LocalThemeProvider.current.keyBorders)) return

    val sepCol = LocalKeyboardScheme.current.keyboardContainer

    Box(modifier = Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(sepCol)) {}
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ActionBar(
    words: SuggestedWords?,
    suggestionStripListener: SuggestionStripViewListener,
    onActionActivated: (Action) -> Unit,
    onActionAltActivated: (Action) -> Unit,
    inlineSuggestions: List<MutableState<View?>>,
    isActionsExpanded: Boolean,
    toggleActionsExpanded: () -> Unit,
    importantNotice: ImportantNotice? = null,
    keyboardManagerForAction: KeyboardManagerForAction? = null,
    quickClipState: QuickClipState? = null,
    onQuickClipDismiss: () -> Unit = {}
) {
    val view = LocalView.current
    val context = LocalContext.current

    val oldActionBar = useDataStore(OldStyleActionsBar)

    val useDoubleHeight = isActionsExpanded && oldActionBar.value == false

    Column(Modifier
        .height(
            ActionBarHeight * if (useDoubleHeight) 2 else 1
        )
        .semantics {
            testTag = "ActionBar"
            testTagsAsResourceId = true
        }) {
        if(isActionsExpanded && !oldActionBar.value) {
            ActionSep()

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f),
                color = LocalKeyboardScheme.current.keyboardSurfaceDim//actionBarColor()
            ) {
                ActionItems(onActionActivated, onActionAltActivated)
            }
        }

        ActionSep()

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f), color = actionBarColor()
        ) {
            Row(Modifier.safeKeyboardPadding()) {
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

                        if(quickClipState != null) {
                            QuickClipView(quickClipState, onQuickClipDismiss)
                        } else if (words != null && inlineSuggestions.isEmpty()) {
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

                        if(inlineSuggestions.isEmpty()) {
                            PinnedActionItems(onActionActivated, onActionAltActivated)
                        }
                    }
                }
            }
        }

        ActionSep(true)
    }
}

@Composable
fun ActionWindowBar(
    windowTitleBar: @Composable RowScope.() -> Unit,
    canExpand: Boolean,
    onBack: () -> Unit,
    onExpand: () -> Unit
) {
    Column(Modifier.height(ActionBarHeight)) {
        ActionSep()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f), color = Color.Transparent
        )
        {
            Row(Modifier.safeKeyboardPadding()) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(id = R.drawable.arrow_left_26),
                        contentDescription = stringResource(R.string.keyboard_actionbar_return_from_action_window_talkback)
                    )
                }

                CompositionLocalProvider(LocalTextStyle provides Typography.Body.MediumMl) {
                    windowTitleBar()
                }

                if (canExpand) {
                    IconButton(onClick = onExpand) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_up),
                            contentDescription = stringResource(R.string.keyboard_actionbar_dock_action_window_above_keyboard_talkback)
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
    showClose: Boolean,
    showCollapse: Boolean,
    words: SuggestedWords?,
    suggestionStripListener: SuggestionStripViewListener,
) {
    Column(Modifier.height(ActionBarHeight)) {
        ActionSep()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f), color = actionBarColor()
        )
        {
            Row(Modifier.safeKeyboardPadding()) {
                val color = MaterialTheme.colorScheme.primary

                if(showClose) {
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
                            contentDescription = stringResource(R.string.keyboard_actionbar_close_docked_action_window_talkback)
                        )
                    }
                } else {
                    Spacer(Modifier.width(42.dp))
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

                if(showCollapse) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier
                            .width(42.dp)
                            .fillMaxHeight(),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_down),
                            contentDescription = stringResource(R.string.keyboard_actionbar_undock_action_window_collapse_into_keyboard_talkback)
                        )
                    }
                } else {
                    Spacer(Modifier.width(42.dp))
                }
            }
        }

        ActionSep(true)
    }
}




/* ---- Previews ---- */

class ExampleListener : SuggestionStripViewListener {
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
fun PreviewActionBarWithSuggestions(colorScheme: ThemeOption = DefaultDarkScheme) {
    UixThemeWrapper(colorScheme.obtainColors(LocalContext.current)) {
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
fun PreviewActionBarWithQuickClip(colorScheme: ThemeOption = DefaultDarkScheme) {
    UixThemeWrapper(colorScheme.obtainColors(LocalContext.current)) {
        ActionBar(
            words = exampleSuggestedWords,
            suggestionStripListener = ExampleListener(),
            onActionActivated = { },
            inlineSuggestions = listOf(),
            isActionsExpanded = false,
            toggleActionsExpanded = { },
            onActionAltActivated = { },
            quickClipState = QuickClipState(
                texts = listOf(
                    QuickClipItem(QuickClipKind.EmailAddress, "keyboard@futo.org", 0),
                    QuickClipItem(QuickClipKind.NumericCode, "123456", 0),
                    QuickClipItem(QuickClipKind.FullString, "Hello world, this is a full string.", 0),
                ),
                image = "content://example".toUri(),
                validUntil = Long.MAX_VALUE,
                imageMimeTypes = listOf()
            )
        )
    }
}

@Composable
@Preview
fun PreviewActionBarWithNotice(colorScheme: ThemeOption = DefaultDarkScheme) {
    UixThemeWrapper(colorScheme.obtainColors(LocalContext.current)) {
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

                override fun onDismiss(context: Context) { }
                override fun onOpen(context: Context) { }
            }
        )
    }
}

@Composable
@Preview
fun PreviewActionBarWithEmptySuggestions(colorScheme: ThemeOption = DefaultDarkScheme) {
    UixThemeWrapper(colorScheme.obtainColors(LocalContext.current)) {
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
fun PreviewExpandedActionBar(colorScheme: ThemeOption = DefaultDarkScheme) {
    UixThemeWrapper(colorScheme.obtainColors(LocalContext.current)) {
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
fun PreviewCollapsibleBar(colorScheme: ThemeOption = DefaultDarkScheme) {
    CollapsibleSuggestionsBar(
        onCollapse = { },
        onClose = { },
        words = exampleSuggestedWords,
        showClose = true,
        showCollapse = true,
        suggestionStripListener = ExampleListener()
    )
}


@RequiresApi(Build.VERSION_CODES.S)
@Composable
@Preview
fun PreviewActionBarWithSuggestionsDynamicLight() {
    PreviewActionBarWithSuggestions(DynamicLightTheme)
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
@Preview
fun PreviewActionBarWithEmptySuggestionsDynamicLight() {
    PreviewActionBarWithEmptySuggestions(DynamicLightTheme)
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
@Preview
fun PreviewExpandedActionBarDynamicLight() {
    PreviewExpandedActionBar(DynamicLightTheme)
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
@Preview
fun PreviewActionBarWithSuggestionsDynamicDark() {
    PreviewActionBarWithSuggestions(DynamicDarkTheme)
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
@Preview
fun PreviewActionBarWithEmptySuggestionsDynamicDark() {
    PreviewActionBarWithEmptySuggestions(DynamicDarkTheme)
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
@Preview
fun PreviewExpandedActionBarDynamicDark() {
    PreviewExpandedActionBar(DynamicDarkTheme)
}