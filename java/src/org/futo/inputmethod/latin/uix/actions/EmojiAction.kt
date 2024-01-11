package org.futo.inputmethod.latin.uix.actions

import android.content.Context
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.UiThread
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.inputmethod.latin.uix.actions.emoji.EmojiItem
import org.futo.inputmethod.latin.uix.actions.emoji.EmojiView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PopupInfo(val emoji: EmojiItem, val x: Int, val y: Int)

// Note: Using traditional View here, because Android Compose leaves a lot of performance to be desired
class EmojiGridAdapter(
    private val emojiList: List<EmojiItem>,
    private val onClick: (EmojiItem) -> Unit,
    private val onSelectSkinTone: (PopupInfo) -> Unit,
    private val emojiCellWidth: Int
) :
    RecyclerView.Adapter<EmojiGridAdapter.EmojiViewHolder>() {

    class EmojiViewHolder(
        context: Context,
        width: Int,
        height: Int
    ) : RecyclerView.ViewHolder(EmojiView(context)) {
        private val emojiView: EmojiView = (itemView as EmojiView).apply {
            layoutParams = ViewGroup.LayoutParams(width, height)
            isClickable = true
        }

        fun bindEmoji(
            emoji: EmojiItem,
            onClick: (EmojiItem) -> Unit,
            onSelectSkinTone: (PopupInfo) -> Unit
        ) {
            emojiView.emoji = emoji
            emojiView.setOnClickListener {
                it.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
                onClick(emoji)
            }

            emojiView.isLongClickable = emoji.skinTones
            if (emoji.skinTones) {
                emojiView.setOnLongClickListener {
                    onSelectSkinTone(PopupInfo(emoji, it.x.roundToInt() + it.width / 2, it.y.roundToInt() + it.height / 2))
                    emojiView.isLongClickable
                }
            }
        }
    }

    @UiThread
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        return EmojiViewHolder(parent.context, width = emojiCellWidth, height = emojiCellWidth)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        val currentEmoji = emojiList[position]
        holder.bindEmoji(currentEmoji, onClick, onSelectSkinTone)
    }

    override fun getItemCount() = emojiList.size
}


val skinTones = listOf(
    "\ud83c\udffb",
    "\ud83c\udffc",
    "\ud83c\udffd",
    "\ud83c\udffe",
    "\ud83c\udfff",
)

// TODO: Mixing multiple skin tones
//  e.g. family: woman, woman, girl, girl: medium, dark. light, medium skin tones
fun generateSkinToneVariants(emoji: String, emojiMap: Map<String, EmojiItem>): List<String> {
    val humanEmojis = emoji.split("\u200D")
    val variants = mutableListOf<String>()

    for (modifier in skinTones) {
        val variant = humanEmojis.joinToString("\u200D") { part ->
            if (emojiMap[part]?.category == "People & Body") {
                part + modifier
            } else {
                part
            }
        }
        variants.add(variant)
    }

    return variants
}


@Composable
fun Emojis(
    emojis: List<EmojiItem>,
    onClick: (EmojiItem) -> Unit,
    modifier: Modifier = Modifier,
    emojiMap: Map<String, EmojiItem>
) {
    val emojiWidth = with(LocalDensity.current) {
        remember {
            42.dp.toPx().roundToInt()
        }
    }

    var activePopup: PopupInfo? by rememberSaveable { mutableStateOf(null) }

    val emojiAdapter = remember {
        EmojiGridAdapter(
            emojis,
            onClick,
            onSelectSkinTone = { activePopup = it },
            emojiWidth
        )
    }

    var viewWidth by remember { mutableStateOf(0) }
    var viewHeight by remember { mutableStateOf(0) }

    Box(modifier = modifier) {
        AndroidView(
            factory = { context ->
                RecyclerView(context).apply {
                    layoutManager = GridLayoutManager(context, 8)
                    adapter = emojiAdapter
                }
            },
            update = {
                if (viewWidth > 0) {
                    (it.layoutManager as GridLayoutManager).spanCount = viewWidth / emojiWidth
                }
            },
            modifier = Modifier.fillMaxSize()
                .clipToBounds()
                .onSizeChanged {
                    viewWidth = it.width
                    viewHeight = it.height
                }.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if(event.type == PointerEventType.Press) {
                                activePopup = null
                            }
                        }
                    }
                }
        )

        activePopup?.let { popupInfo ->
            var popupSize by remember { mutableStateOf(IntSize(0, 0)) }

            val posX = popupInfo.x - popupSize.width / 2
            val posY = popupInfo.y - popupSize.height

            // Calculate the maximum possible x and y values
            val maxX = viewWidth - popupSize.width
            val maxY = viewHeight - popupSize.height

            // Calculate the x and y values, clamping them to the maximum values if necessary
            val x = min(maxX, max(0, posX))
            val y = min(maxY, max(0, posY))

            Box(modifier = Modifier.onSizeChanged {
                popupSize = it
            }.absoluteOffset {
                IntOffset(x, y)
            }) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row {
                        generateSkinToneVariants(popupInfo.emoji.emoji, emojiMap).map { emoji ->
                            IconButton(
                                onClick = {
                                    onClick(
                                        EmojiItem(
                                            emoji = emoji,
                                            description = popupInfo.emoji.description,
                                            category = popupInfo.emoji.category,
                                            skinTones = false
                                        )
                                    )
                                    activePopup = null
                                }, modifier = Modifier
                                    .width(42.dp)
                                    .height(42.dp)
                            ) {
                                Box {
                                    Text(emoji, modifier = Modifier.align(Alignment.Center))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomRowKeyboardNavigation(onExit: () -> Unit, onBackspace: () -> Unit, onSpace: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background, modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(modifier = Modifier.padding(2.dp, 8.dp, 2.dp, 0.dp)) {
            IconButton(onClick = { onExit() }) {
                Text("ABC", fontSize = 14.sp)
            }

            Button(
                onClick = { onSpace() }, modifier = Modifier
                    .weight(1.0f)
                    .padding(8.dp, 2.dp), colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.33f),
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    disabledContainerColor = MaterialTheme.colorScheme.outline,
                    disabledContentColor = MaterialTheme.colorScheme.onBackground,
                ), shape = RoundedCornerShape(32.dp)
            ) {
                Text("")
            }

            IconButton(onClick = { onBackspace() }) {
                val icon = painterResource(id = R.drawable.delete)
                val iconColor = MaterialTheme.colorScheme.onBackground

                Canvas(modifier = Modifier.fillMaxSize()) {
                    translate(
                        left = this.size.width / 2.0f - icon.intrinsicSize.width / 2.0f,
                        top = this.size.height / 2.0f - icon.intrinsicSize.height / 2.0f
                    ) {
                        with(icon) {
                            draw(
                                icon.intrinsicSize,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                    iconColor
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmojiGrid(
    onClick: (EmojiItem) -> Unit,
    onExit: () -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    emojis: List<EmojiItem>,
    keyboardShown: Boolean,
    emojiMap: Map<String, EmojiItem>
) {
    Column {
        Emojis(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth()
                .weight(1.0f),
            emojis = emojis,
            onClick = onClick,
            emojiMap = emojiMap
        )

        if (!keyboardShown) {
            BottomRowKeyboardNavigation(
                onExit = onExit,
                onBackspace = onBackspace,
                onSpace = onSpace
            )
        }
    }
}

class PersistentEmojiState : PersistentActionState {
    var emojis: MutableState<List<EmojiItem>?> = mutableStateOf(null)
    var emojiMap: HashMap<String, EmojiItem> = HashMap()

    suspend fun loadEmojis(context: Context) = withContext(Dispatchers.IO) {
        val stream = context.resources.openRawResource(R.raw.gemoji)
        val text = stream.bufferedReader().readText()

        withContext(Dispatchers.Default) {
            val emojiData = Json.parseToJsonElement(text)
            emojis.value = emojiData.jsonArray.map {
                EmojiItem(
                    emoji = it.jsonObject["emoji"]!!.jsonPrimitive.content,
                    description = it.jsonObject["description"]!!.jsonPrimitive.content,
                    category = it.jsonObject["category"]!!.jsonPrimitive.content,
                    skinTones = it.jsonObject["skin_tones"]?.jsonPrimitive?.booleanOrNull ?: false
                )
            }

            emojiMap = HashMap<String, EmojiItem>().apply {
                emojis.value!!.forEach {
                    put(it.emoji, it)
                }
            }
        }
    }

    override suspend fun cleanUp() {

    }
}


val EmojiAction = Action(
    icon = R.drawable.smile,
    name = R.string.emoji_action_title,
    canShowKeyboard = true,
    simplePressImpl = null,
    persistentState = { manager ->
        val state = PersistentEmojiState()
        manager.getLifecycleScope().launch {
            state.loadEmojis(manager.getContext())
        }

        state
    },
    windowImpl = { manager, persistentState ->
        val state = persistentState as PersistentEmojiState
        object : ActionWindow {
            @Composable
            override fun windowName(): String {
                return stringResource(R.string.emoji_action_title)
            }

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                state.emojis.value?.let { emojis ->
                    EmojiGrid(onClick = {
                        manager.typeText(it.emoji)
                    }, onExit = {
                        manager.closeActionWindow()
                    }, onSpace = {
                        manager.sendCodePointEvent(Constants.CODE_SPACE)
                    }, onBackspace = {
                        manager.sendCodePointEvent(Constants.CODE_DELETE)
                    }, emojis = emojis, keyboardShown = keyboardShown, emojiMap = state.emojiMap)
                }
            }

            override fun close() {

            }
        }
    }
)


/*
@Preview(showBackground = true)
@Composable
fun EmojiGridPreview() {
    EmojiGrid(
        onBackspace = {},
        onClick = {},
        onExit = {},
        onSpace = {},
        emojis = listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇").map {
            EmojiItem(emoji = it, description = "", category = "", skinTones = false)
        },
        keyboardShown = false,
        emojiMap = hashMapOf()
    )
}
*/