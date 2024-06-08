package org.futo.inputmethod.latin.uix.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import org.futo.inputmethod.latin.uix.AutoFitText
import org.futo.inputmethod.latin.uix.EmojiTracker.getRecentEmojis
import org.futo.inputmethod.latin.uix.EmojiTracker.useEmoji
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.inputmethod.latin.uix.actions.emoji.EmojiItem
import org.futo.inputmethod.latin.uix.actions.emoji.EmojiView
import org.futo.voiceinput.shared.ui.theme.Typography
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.streams.toList


data class PopupInfo(val emoji: EmojiItem, val x: Int, val y: Int)

sealed class EmojiViewItem
class CategoryItem(val title: String) : EmojiViewItem() {
    override fun equals(other: Any?): Boolean {
        return (other is CategoryItem) && (title == other.title)
    }

    override fun hashCode(): Int {
        return title.hashCode()
    }
}
class EmojiItemItem(val emoji: EmojiItem) : EmojiViewItem() {
    override fun equals(other: Any?): Boolean {
        return (other is EmojiItemItem) && (emoji == other.emoji)
    }

    override fun hashCode(): Int {
        return emoji.hashCode()
    }
}

const val VIEW_EMOJI = 0
const val VIEW_CATEGORY = 1

// Note: Using traditional View here, because Android Compose leaves a lot of performance to be desired
class EmojiGridAdapter(
    private val data: List<EmojiViewItem>,
    private val onClick: (EmojiItem) -> Unit,
    private val onSelectSkinTone: (PopupInfo) -> Unit,
    private val emojiCellWidth: Int,
    private val contentColor: Color
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

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
            onSelectSkinTone: (PopupInfo) -> Unit,
            color: Int
        ) {
            emojiView.setTextColor(color)
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


    internal inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: CategoryItem) {
            itemView.layoutParams.width = emojiCellWidth * 9
            itemView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            itemView.findViewById<TextView>(R.id.text_section).text = item.title
            itemView.findViewById<TextView>(R.id.text_section).setTextColor(contentColor.toArgb())
        }
    }

    @UiThread
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_EMOJI -> EmojiViewHolder(parent.context, width = emojiCellWidth, height = emojiCellWidth)
            VIEW_CATEGORY -> CategoryViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.emoji_category, parent, false))
            else -> throw RuntimeException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = data[position]
        if(item is EmojiItemItem && holder is EmojiViewHolder) {
            holder.bindEmoji(item.emoji, onClick, onSelectSkinTone, contentColor.toArgb())
        }else if(item is CategoryItem && holder is CategoryViewHolder) {
            holder.bind(item)
        }
    }

    override fun getItemCount() = data.size

    override fun getItemViewType(position: Int): Int {
        return when(data[position]) {
            is CategoryItem -> VIEW_CATEGORY
            is EmojiItemItem -> VIEW_EMOJI
        }
    }
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
    emojis: List<EmojiViewItem>,
    onClick: (EmojiItem) -> Unit,
    modifier: Modifier = Modifier,
    emojiMap: Map<String, EmojiItem>,
    currentCategory: MutableState<CategoryItem>,
    jumpCategory: MutableState<CategoryItem?>
) {
    val emojiWidth = with(LocalDensity.current) {
        remember {
            42.dp.toPx().roundToInt()
        }
    }

    var activePopup: PopupInfo? by rememberSaveable { mutableStateOf(null) }
    var popupIsActive by rememberSaveable { mutableStateOf(false) }

    val color = LocalContentColor.current

    val emojiAdapter = remember {
        EmojiGridAdapter(
            emojis,
            onClick,
            onSelectSkinTone = {
                activePopup = it
                popupIsActive = true
            },
            emojiWidth,
            color
        )
    }

    var viewWidth by remember { mutableIntStateOf(0) }
    var viewHeight by remember { mutableIntStateOf(0) }
    var popupSize by remember { mutableStateOf(IntSize(0, 0)) }

    Box(modifier = modifier) {
        AndroidView(
            factory = { context ->
                RecyclerView(context).apply {
                    layoutManager = GridLayoutManager(context, 8).apply {
                        spanSizeLookup = object : SpanSizeLookup() {
                            override fun getSpanSize(position: Int): Int {
                                return when(emojis[position]) {
                                    is EmojiItemItem -> 1
                                    is CategoryItem -> spanCount
                                }
                            }
                        }
                    }
                    adapter = emojiAdapter

                    addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            super.onScrolled(recyclerView, dx, dy)
                            val layoutManager = recyclerView.layoutManager as GridLayoutManager
                            val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
                            val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()

                            val finalCategoryIndex = emojis.indexOfLast { it is CategoryItem }
                            if(finalCategoryIndex < lastVisiblePosition) {
                                currentCategory.value = emojis[finalCategoryIndex] as CategoryItem
                            } else {
                                val itm = emojis.subList(0, firstVisiblePosition + 1)
                                    .lastOrNull { it is CategoryItem }

                                if (itm != null) {
                                    currentCategory.value = itm as CategoryItem
                                }
                            }
                        }
                    })
                }
            },
            update = {
                if (viewWidth > 0) {
                    (it.layoutManager as GridLayoutManager).spanCount = viewWidth / emojiWidth
                }

                jumpCategory.value?.let { item ->
                    val idx = emojis.indexOf(item)
                    if (idx != -1) {
                        it.post {
                            (it.layoutManager as GridLayoutManager).scrollToPositionWithOffset(
                                idx,
                                0
                            )
                        }
                    }
                    jumpCategory.value = null
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged {
                    viewWidth = it.width
                    viewHeight = it.height
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press && popupIsActive) {
                                popupIsActive = false
                                event.changes
                                    .firstOrNull()
                                    ?.consume()
                            } else if (event.type == PointerEventType.Move && popupIsActive) {
                                event.changes
                                    .firstOrNull()
                                    ?.consume()
                            }
                        }
                    }
                }
        )

        val posX = (activePopup?.x ?: 0) - popupSize.width / 2
        val posY = (activePopup?.y ?: 0) - popupSize.height

        // Calculate the maximum possible x and y values
        val maxX = viewWidth - popupSize.width
        val maxY = viewHeight - popupSize.height

        // Calculate the x and y values, clamping them to the maximum values if necessary
        val x = min(maxX, max(0, posX))
        val y = min(maxY, max(0, posY))

        AnimatedVisibility(visible = popupIsActive, modifier = Modifier
            .onSizeChanged {
                popupSize = it
            }
            .absoluteOffset {
                IntOffset(x, y)
            }) {
            activePopup?.let { popupInfo ->
                Box {
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
                                                skinTones = false,
                                                aliases = listOf(),
                                                tags = listOf()
                                            )
                                        )
                                        popupIsActive = false
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
}

@Composable
fun EmojiNavigation(
    showKeys: Boolean,
    onExit: () -> Unit,
    onBackspace: (Boolean) -> Unit,
    categories: List<CategoryItem>,
    activeCategoryItem: CategoryItem,
    goToCategory: (CategoryItem) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background, modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(modifier = Modifier.padding(2.dp, 0.dp)) {
            if(showKeys) {
                LettersKey(onExit)
            }

            EmojiCategoriesContainer(Modifier.weight(1.0f), categories, goToCategory, activeCategoryItem)

            if(showKeys) {
                BackspaceKey(onBackspace)
            }
        }
    }
}

val iconMap = mapOf(
    "Recent" to R.drawable.ic_emoji_recents_activated_lxx_dark,
    "Smileys & Emotion" to R.drawable.smileys_and_emotion,
    "People & Body" to R.drawable.people_and_body,
    "Animals & Nature" to R.drawable.animals_and_nature,
    "Food & Drink" to R.drawable.food_and_drink,
    "Travel & Places" to R.drawable.travel_and_places,
    "Activities" to R.drawable.activities,
    "Objects" to R.drawable.objects,
    "Symbols" to R.drawable.symbols,
    "Flags" to R.drawable.flags,
    "ASCII" to R.drawable.ic_emoji_emoticons_activated_lxx_dark,
)
@Composable
private fun EmojiCategoriesContainer(
    modifier: Modifier,
    categories: List<CategoryItem>,
    goToCategory: (CategoryItem) -> Unit,
    activeCategoryItem: CategoryItem
) {
    val listState = rememberLazyListState()
    LaunchedEffect(activeCategoryItem) {
        val idx = categories.indexOf(activeCategoryItem)
        if(idx != -1) {
            val visibleSize = listState.layoutInfo.viewportEndOffset
            val itemWidth = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: return@LaunchedEffect
            listState.animateScrollToItem(idx, itemWidth / 2 - visibleSize / 2)
        }
    }

    LazyRow(state = listState, modifier = modifier.padding(8.dp, 0.dp)) {
        items(categories) {
            IconButton(
                onClick = { goToCategory(it) }, modifier = if (it == activeCategoryItem) {
                    Modifier.background(
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(100)
                    )
                } else {
                    Modifier
                }.clearAndSetSemantics {
                    contentDescription = "Jump to ${it.title}"
                    toggleableState = ToggleableState(it == activeCategoryItem)
                }
            ) {
                val color = MaterialTheme.colorScheme.onBackground.copy(
                    alpha = if (it == activeCategoryItem) {
                        1.0f
                    } else {
                        0.6f
                    }
                )

                val icon = iconMap[it.title]
                if(icon != null) {
                    Icon(
                        painterResource(id = icon),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    AutoFitText(
                        it.title,
                        style = Typography.labelSmall.copy(color = color)
                    )
                }
            }
        }
    }
}

@Composable
private fun BackspaceKey(onBackspace: (Boolean) -> Unit) {
    Box(modifier = Modifier
        .minimumInteractiveComponentSize()
        .repeatablyClickableAction(onTrigger = onBackspace)
        .size(48.dp)
        .clearAndSetSemantics {
            this.role = Role.Button
            this.text = AnnotatedString("Delete")
        },
        contentAlignment = Alignment.Center
    ) {
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
                        colorFilter = ColorFilter.tint(
                            iconColor
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun LettersKey(onExit: () -> Unit) {
    IconButton(onClick = { onExit() }, modifier = Modifier
        .clearAndSetSemantics {
            this.role = Role.Button
            this.text = AnnotatedString("Letters")
        }
        .size(48.dp)) {
        Text("ABC", fontSize = 14.sp)
    }
}

@Composable
fun EmojiGrid(
    onClick: (EmojiItem) -> Unit,
    onExit: () -> Unit,
    onBackspace: (Boolean) -> Unit,
    onSpace: () -> Unit,
    emojis: List<EmojiItem>,
    keyboardShown: Boolean,
    emojiMap: Map<String, EmojiItem>,
    keyBackground: Drawable
) {
    val context = LocalContext.current
    val recentEmojis = remember {
        runBlocking { context.getRecentEmojis() }.map {
            EmojiItem(it, description = "", category = "", skinTones = false, tags = listOf(), aliases = listOf())
        }
    }

    val categorizedEmojis = remember {
        var prevCategory = ""
        val data = emojis.flatMap { emoji ->
            listOfNotNull(
                if (emoji.category != prevCategory) {
                    prevCategory = emoji.category
                    CategoryItem(emoji.category)
                } else {
                    null
                },
                EmojiItemItem(emoji)
            )
        }

        data
    }

    val currentCategory = remember { mutableStateOf(CategoryItem("Recent")) }
    val jumpCategory: MutableState<CategoryItem?> = remember { mutableStateOf(null) }


    Column {
        Emojis(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth()
                .weight(1.0f)
                .drawBehind {
                    keyBackground.setBounds(
                        0,
                        0,
                        this.size.width.roundToInt(),
                        this.size.height.roundToInt()
                    )
                    keyBackground.state = intArrayOf()
                    keyBackground.draw(this.drawContext.canvas.nativeCanvas)
                },
            emojis = listOf(CategoryItem("Recent")) + recentEmojis.map { EmojiItemItem(it) } + categorizedEmojis,
            onClick = onClick,
            emojiMap = emojiMap,
            currentCategory = currentCategory,
            jumpCategory = jumpCategory
        )

        EmojiNavigation(
            showKeys = !keyboardShown,
            onExit = onExit,
            onBackspace = onBackspace,
            categories = listOf(
                CategoryItem("Recent")
            ) + categorizedEmojis.filterIsInstance<CategoryItem>(),
            activeCategoryItem = currentCategory.value,
            goToCategory = {
                jumpCategory.value = it
            }
        )
    }
}

class PersistentEmojiState : PersistentActionState {
    var emojis: MutableState<List<EmojiItem>?> = mutableStateOf(null)
    var emojiMap: HashMap<String, EmojiItem> = HashMap()
    var emojiAliases: HashMap<String, EmojiItem> = HashMap()

    suspend fun loadEmojis(context: Context) = withContext(Dispatchers.IO) {
        val stream = context.resources.openRawResource(R.raw.gemoji)
        val text = stream.bufferedReader().readText()

        withContext(Dispatchers.Default) {
            val emojiData = Json.parseToJsonElement(text)

            emojis.value = emojiData.jsonArray.mapNotNull {
                val emoji = it.jsonObject["emoji"]!!.jsonPrimitive.content
                val supported = emoji.codePoints().toList().all { c -> Character.getName(c) != null }

                if(!supported) {
                    //Log.d("Emoji", "Emoji $emoji is unsupported")
                    null
                } else {
                    EmojiItem(
                        emoji = emoji,
                        description = it.jsonObject["description"]!!.jsonPrimitive.content,
                        category = it.jsonObject["category"]!!.jsonPrimitive.content,
                        skinTones = it.jsonObject["skin_tones"]?.jsonPrimitive?.booleanOrNull
                            ?: false,
                        tags = it.jsonObject["tags"]?.jsonArray?.map { it.jsonPrimitive.content }
                            ?.toList() ?: listOf(),
                        aliases = it.jsonObject["aliases"]?.jsonArray?.map { it.jsonPrimitive.content }
                            ?.toList() ?: listOf(),
                    )
                }
            }

            emojiMap = HashMap<String, EmojiItem>().apply {
                emojis.value!!.forEach {
                    put(it.emoji, it)
                }
            }

            emojiAliases = HashMap<String, EmojiItem>().apply {
                // Add absolute alias matches first (e.g. "joy") and only later put first-word tag/alias matches (e.g. "joy_cat")
                emojis.value!!.forEach { emoji ->
                    emoji.aliases.forEach {
                        val x = it
                        if (!containsKey(x)) {
                            put(x, emoji)
                        }
                    }
                }

                emojis.value!!.forEach { emoji ->
                    (emoji.tags + emoji.aliases).forEach {
                        val x = it.split("_").first()
                        if (!containsKey(x)) {
                            put(x, emoji)
                        }
                    }
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
                val view = LocalView.current
                state.emojis.value?.let { emojis ->
                    EmojiGrid(onClick = {
                        manager.typeText(it.emoji)
                        manager.getLifecycleScope().launch {
                            manager.getContext().useEmoji(it.emoji)
                        }
                        manager.performHapticAndAudioFeedback(Constants.CODE_EMOJI, view)
                    }, onExit = {
                        manager.closeActionWindow()
                        manager.performHapticAndAudioFeedback(Constants.CODE_SWITCH_ALPHA_SYMBOL, view)
                    }, onSpace = {
                        manager.sendCodePointEvent(Constants.CODE_SPACE)
                        manager.performHapticAndAudioFeedback(Constants.CODE_SPACE, view)
                    }, onBackspace = { isRepeated ->
                        manager.sendCodePointEvent(Constants.CODE_DELETE)
                        if(!isRepeated) {
                            manager.performHapticAndAudioFeedback(Constants.CODE_DELETE, view)
                        }
                    }, emojis = emojis, keyboardShown = keyboardShown, emojiMap = state.emojiMap, keyBackground = manager.getThemeProvider().keyBackground)
                }
            }

            override fun close() {

            }
        }
    }
)



@Preview(showBackground = true)
@Composable
fun EmojiGridPreview() {
    val context = LocalContext.current
    EmojiGrid(
        onBackspace = {},
        onClick = {},
        onExit = {},
        onSpace = {},
        emojis = listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇").map {
            EmojiItem(emoji = it, description = "", category = "Smileys & Emotion", skinTones = false, aliases = listOf(), tags = listOf())
        },
        keyboardShown = false,
        emojiMap = hashMapOf(),
        keyBackground = context.getDrawable(R.drawable.btn_keyboard_spacebar_lxx_dark)!!
    )
}
