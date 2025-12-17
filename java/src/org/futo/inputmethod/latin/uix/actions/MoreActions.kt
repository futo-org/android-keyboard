package org.futo.inputmethod.latin.uix.actions

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.LocalKeyboardScheme
import org.futo.inputmethod.latin.uix.LocalManager
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.WarningTip
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.theme.Typography
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState


@Composable
fun ActionItem(action: Action, modifier: Modifier = Modifier, dragIcon: Boolean = false, dragIconModifier: Modifier = Modifier) {
    Surface(color = LocalKeyboardScheme.current.keyboardContainer,
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides LocalKeyboardScheme.current.onKeyboardContainer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {

                if (dragIcon) {
                    Icon(
                        painterResource(id = R.drawable.move),
                        contentDescription = null,
                        modifier = dragIconModifier
                            .size(16.dp)
                            .align(Alignment.TopEnd),
                        tint = LocalContentColor.current.copy(alpha = 0.6f)
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Center)
                        .padding(6.dp)
                ) {
                    Spacer(modifier = Modifier.weight(1.0f))
                    Icon(
                        painterResource(id = action.icon),
                        contentDescription = null,
                        modifier = Modifier.align(
                            CenterHorizontally
                        )
                    )

                    Spacer(modifier = Modifier.weight(1.0f))

                    Text(
                        stringResource(id = action.name),
                        modifier = Modifier.align(
                            CenterHorizontally
                        ),
                        style = Typography.Small.copy(lineHeight = 12.sp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}


@Composable
@Preview(showBackground = true)
fun MoreActionsView() {
    val manager = if(LocalInspectionMode.current) { null } else { LocalManager.current }
    val context = LocalContext.current

    val actionList = if(LocalInspectionMode.current) {
        ActionsSettings.default
    } else {
        useDataStoreValue(ActionsSettings)
    }

    val map = remember(actionList) {
        actionList.toActionEditorItems().ensureWellFormed().toActionMap()
    }

    val actions = remember(actionList) {
        (map[ActionCategory.Favorites] ?: listOf()) +
                (map[ActionCategory.ActionKey] ?: listOf()) +
                (map[ActionCategory.PinnedKey] ?: listOf()) +
                (map[ActionCategory.More] ?: listOf())
    }


    if(actions.isEmpty()) {
        ScreenTitle(stringResource(R.string.action_editor_warning_no_actions))
    }

    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        columns = GridCells.Adaptive(98.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(actions, key = { it.name }) {
            ActionItem(it, Modifier.clickable {
                manager!!.activateAction(it)
            })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionsEditor(header: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current

    val initialList: List<ActionEditorItem> = if(!LocalInspectionMode.current) {
        remember {
            context.getSettingBlocking(ActionsSettings).toActionEditorItems().ensureWellFormed().filter {
                it !is ActionEditorItem.Item || it.action.shownInEditor
            }
        }
    } else {
        DefaultActionSettings.flattenToActionEditorItems()
    }

    val list = remember { initialList.toMutableStateList() }
    val lazyListState = rememberLazyGridState()
    val reorderableLazyListState = rememberReorderableLazyGridState(lazyListState) { from, to ->
        val itemToAdd = list.removeAt(from.index)
        list.add(to.index, itemToAdd)

        view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
    }

    if(!LocalInspectionMode.current) {
        DisposableEffect(Unit) {
            onDispose {
                val map = list.toActionMap()
                context.updateSettingsWithNewActions(map)
            }
        }
    }

    val actionMap = list.toActionMap()

    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp, 0.dp),
        state = lazyListState,
        columns = GridCells.Adaptive(98.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        items(list, key = { it.toKey() }, span = {
            when(it) {
                is ActionEditorItem.Item -> GridItemSpan(1)
                is ActionEditorItem.Separator -> GridItemSpan(maxLineSpan)
            }
        }) {
            when(it) {
                is ActionEditorItem.Item -> {
                    ReorderableItem(reorderableLazyListState, key = it.toKey()) { isDragging ->
                        ActionItem(it.action, Modifier.longPressDraggableHandle(
                            onDragStarted = {
                                view.performHapticFeedback(HapticFeedbackConstants.DRAG_START)
                            },
                            onDragStopped = {
                                view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                            },
                        ), dragIcon = true, dragIconModifier = Modifier.draggableHandle())
                    }
                }
                is ActionEditorItem.Separator -> {
                    ReorderableItem(reorderableLazyListState, modifier = Modifier.fillMaxWidth(), key = it.toKey(), enabled = it.category != ActionCategory.entries[0]) { _ ->
                        Column {
                            if (it.category == ActionCategory.entries[0]) {
                                header()
                                
                                if (actionMap[ActionCategory.ActionKey]?.let { it.size > 1 } == true) {
                                    WarningTip(stringResource(R.string.action_editor_error_more_than_one_action_key))
                                } else if (actionMap[ActionCategory.PinnedKey]?.let { it.size >= 3 } == true) {
                                    WarningTip(stringResource(R.string.action_editor_warning_too_many_pinned))
                                }
                            }
                            Text(it.category.name(context), modifier = Modifier.padding(top = 24.dp), style = Typography.Heading.MediumMl, color = LocalContentColor.current.copy(alpha = 0.6f))

                            if(actionMap[it.category]?.isEmpty() == true && it.category != ActionCategory.entries.last()) {
                                TextButton(onClick = {
                                    val selfIdx = list.indexOf(it)
                                    val itemToMove = list.subList(
                                        selfIdx,
                                        list.size
                                    ).firstOrNull { v -> v is ActionEditorItem.Item }

                                    if (itemToMove != null) {
                                        val idx = list.indexOf(itemToMove)

                                        list.add(selfIdx + 1, list.removeAt(idx))
                                    }
                                }) {
                                    Text(stringResource(R.string.action_editor_add_next_action))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionEditor() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f),
        color = LocalKeyboardScheme.current.keyboardSurface,
        contentColor = LocalKeyboardScheme.current.onSurface,
        shape = RoundedCornerShape(32.dp, 32.dp, 0.dp, 0.dp)
    ) {
        ActionsEditor {
            ScreenTitle(title = stringResource(R.string.action_editor_edit_actions))
        }
    }
}


val MoreActionsAction = Action(
    icon = R.drawable.more_horizontal,
    name = R.string.action_more_actions_title,
    simplePressImpl = null,
    shownInEditor = false,
    windowImpl = { manager, _ ->
        object : ActionWindow() {
            @Composable
            override fun windowName(): String = stringResource(id = R.string.action_more_actions_title)

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                MoreActionsView()
            }

            @Composable
            override fun WindowTitleBar(rowScope: RowScope) {
                super.WindowTitleBar(rowScope)

                OutlinedButton(onClick = { manager.showActionEditor() }, modifier = Modifier.padding(8.dp, 0.dp)) {
                    Text(stringResource(R.string.action_editor_edit_actions), color = LocalKeyboardScheme.current.onSurface)
                }
            }
        }
    },
)
