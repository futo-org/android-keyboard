package org.futo.inputmethod.latin.uix.actions

import android.content.Context
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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionTextEditor
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.AutoFitText
import org.futo.inputmethod.latin.uix.DialogRequestItem
import org.futo.inputmethod.latin.uix.EmojiTracker.getRecentEmojis
import org.futo.inputmethod.latin.uix.EmojiTracker.resetRecentEmojis
import org.futo.inputmethod.latin.uix.EmojiTracker.useEmoji
import org.futo.inputmethod.latin.uix.LocalKeyboardScheme
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.inputmethod.latin.uix.actions.emoji.EmojiItem
import org.futo.inputmethod.latin.uix.actions.emoji.EmojiView
import org.futo.inputmethod.latin.uix.theme.Typography
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.streams.toList

private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
    val lhsLen = lhs.length
    val rhsLen = rhs.length
    var cost = IntArray(lhsLen + 1) { it }
    for (i in 1 until rhsLen + 1) {
        val newCost = IntArray(lhsLen + 1)
        newCost[0] = i
        for (j in 1 until lhsLen + 1) {
            val match = if (lhs[j - 1].lowercaseChar() == rhs[i - 1].lowercaseChar()) 0 else 1
            val costReplace = cost[j - 1] + match
            val costInsert = cost[j] + 1
            val costDelete = newCost[j - 1] + 1
            newCost[j] = minOf(costInsert, costDelete, costReplace)
        }
        cost = newCost
    }
    return cost[lhsLen]
}

fun <T> List<T>.searchMultiple(searchTarget: String, maxDistance: Int = searchTarget.length * 2 / 3, limitLength: Boolean = false, keyFunction: (T) -> List<String>): List<T> {
    return this.mapNotNull { item ->
        val keys = keyFunction(item).let {
            if(limitLength) {
                it.map { it.substring(0 until searchTarget.length.coerceAtMost(it.length)) }
            } else {
                it
            }
        }
        val minDistanceKey = keys.minByOrNull { levenshteinDistance(searchTarget, it) }
        val minDistance = minDistanceKey?.let { levenshteinDistance(searchTarget, it) }
        if (minDistance != null && minDistance <= maxDistance) Pair(item, minDistance) else null
    }.sortedBy { it.second }.map { it.first }
}

fun <T> List<T>.searchMultiple2(searchTarget: String, keyFunction: (T) -> List<String>): List<T> {
    val query = searchTarget.lowercase()
    return this.mapNotNull { item ->
        val keys = keyFunction(item).map { it.lowercase() }
        val matches = keys.any { it == query }
        val starts = keys.any { it.startsWith(query) }
        val contains = keys.any { it.contains(query) }

        val score = when {
            matches -> 0
            starts -> 1
            contains -> 2
            else -> return@mapNotNull null
        }

        item to score
    }.sortedBy { it.second }.map { it.first }
}


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

    fun isWide() = emoji.category == "ASCII"
}

const val VIEW_EMOJI = 0
const val VIEW_CATEGORY = 1

private object EmojiViewItemDiffCallback : DiffUtil.ItemCallback<EmojiViewItem>() {
    override fun areItemsTheSame(oldItem: EmojiViewItem, newItem: EmojiViewItem): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: EmojiViewItem, newItem: EmojiViewItem): Boolean {
        return oldItem == newItem
    }

}

// Note: Using traditional View here, because Android Compose leaves a lot of performance to be desired
class EmojiGridAdapter(
    private val onClick: (EmojiItem) -> Unit,
    private val onSelectSkinTone: (PopupInfo) -> Unit,
    private val emojiCellWidth: Int,
    private val contentColor: Color,
    var wideCellWidth: Float
) : ListAdapter<EmojiViewItem, RecyclerView.ViewHolder>(EmojiViewItemDiffCallback) {

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
            color: Int,
            width: Int,
            sizeMultiplier: Float
        ) {
            emojiView.layoutParams.width = width

            emojiView.setTextColor(color)
            emojiView.setTextSizeMultiplier(sizeMultiplier)
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

            val localizedName = localizedCategoryNameMap[item.title]?.let { itemView.context.getString(it) } ?: item.title

            itemView.findViewById<TextView>(R.id.text_section).text = localizedName
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
        val item = getItem(position)
        if(item is EmojiItemItem && holder is EmojiViewHolder) {
            holder.bindEmoji(item.emoji, onClick, onSelectSkinTone, contentColor.toArgb(),
                if(item.isWide()) (emojiCellWidth * wideCellWidth).roundToInt() else emojiCellWidth,
                if(item.isWide()) 0.8f else 1.0f
            )
        }else if(item is CategoryItem && holder is CategoryViewHolder) {
            holder.bind(item)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return when(item) {
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
        variants.add(variant.replace("\uFE0F", ""))
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

    val color = LocalKeyboardScheme.current.onKeyboardContainer

    var wideEmojiWidth by remember { mutableIntStateOf(100) }
    val emojiAdapter = remember {
        EmojiGridAdapter(
            onClick,
            onSelectSkinTone = {
                activePopup = it
                popupIsActive = true
            },
            emojiWidth,
            color,
            wideEmojiWidth / 100.0f
        )
    }

    LaunchedEffect(emojis) {
        emojiAdapter.submitList(emojis)
    }

    var viewWidth by remember { mutableIntStateOf(0) }
    var viewHeight by remember { mutableIntStateOf(0) }
    var popupSize by remember { mutableStateOf(IntSize(0, 0)) }

    Box(modifier = modifier) {
        AndroidView(
            factory = { context ->
                RecyclerView(context).apply {
                    layoutManager = GridLayoutManager(context, 800).apply {
                        spanSizeLookup = object : SpanSizeLookup() {
                            override fun getSpanSize(position: Int): Int {
                                val item = emojiAdapter.currentList[position]
                                return when(item) {
                                    is EmojiItemItem -> if(item.isWide()) wideEmojiWidth else 100
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
                            if(finalCategoryIndex == -1) return

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
                    val spanCount = (viewWidth / emojiWidth)
                    val wideSpanCount = (spanCount / 2 + 1).coerceAtLeast(2)
                    val newWideCellWidth = ((spanCount.toFloat() / wideSpanCount.toFloat()) * 100.0f).toInt()
                    (it.layoutManager as GridLayoutManager).spanCount = (viewWidth / emojiWidth) * 100
                    if(wideEmojiWidth != newWideCellWidth) {
                        wideEmojiWidth = newWideCellWidth
                        emojiAdapter.wideCellWidth = newWideCellWidth / 100.0f
                        emojiAdapter.notifyDataSetChanged()
                    }
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
                                                skinTones = false
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
    Box(
        modifier = Modifier
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

val localizedCategoryNameMap = mapOf(
    "Recent" to R.string.action_emoji_category_most_recently_used,
    "Smileys & Emotion" to R.string.action_emoji_category_smileys_emotion,
    "People & Body" to R.string.action_emoji_category_people_body,
    "Animals & Nature" to R.string.action_emoji_category_animals_nature,
    "Food & Drink" to R.string.action_emoji_category_food_drink,
    "Travel & Places" to R.string.action_emoji_category_travel_places,
    "Activities" to R.string.action_emoji_category_activities,
    "Objects" to R.string.action_emoji_category_objects,
    "Symbols" to R.string.action_emoji_category_symbols,
    "Flags" to R.string.action_emoji_category_flags,
    "ASCII" to R.string.action_emoji_category_ascii,
    "No results found" to R.string.action_emoji_search_no_results_found
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

    val context = LocalContext.current

    LazyRow(state = listState, modifier = modifier.padding(8.dp, 0.dp)) {
        items(categories) {
            val localizedTitle = localizedCategoryNameMap[it.title]?.let { context.getString(it) } ?: it.title
            IconButton(
                onClick = { goToCategory(it) }, modifier = if (it == activeCategoryItem) {
                    Modifier.background(
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(100)
                    )
                } else {
                    Modifier
                }.clearAndSetSemantics {
                    contentDescription = context.getString(R.string.action_emoji_jump_to_category, localizedTitle)
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
                        localizedTitle,
                        style = Typography.SmallMl.copy(color = color)
                    )
                }
            }
        }
    }
}

@Composable
private fun BackspaceKey(onBackspace: (Boolean) -> Unit) {
    val context = LocalContext.current
    Box(modifier = Modifier
        .minimumInteractiveComponentSize()
        .repeatablyClickableAction(onTrigger = onBackspace)
        .size(48.dp)
        .clearAndSetSemantics {
            this.role = Role.Button
            this.text = AnnotatedString(context.getString(R.string.spoken_description_delete))
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
        Text("ABC", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
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
    isSearching: Boolean,
    searchFilter: String
) {
    val context = LocalContext.current

    val emojiCategoryMap = remember {
        emojis.associate { it.emoji to it.category }
    }

    val recentEmojis = remember {
        runBlocking { context.getRecentEmojis() }.map {
            EmojiItem(it, description = "", category = emojiCategoryMap[it] ?: "", skinTones = false)
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


    var emojiList = listOf(CategoryItem("Recent")) + recentEmojis.map { EmojiItemItem(it) } + categorizedEmojis

    if(isSearching) {
        val locale = context.resources.configuration.locale
        val context = LocalContext.current
        LaunchedEffect(locale.language) {
            PersistentEmojiState.loadTranslationsForLanguage(context, locale)
        }

        val translations = remember(locale.language) {
            PersistentEmojiState.getTranslationForLocale(locale)
        }

        emojiList =
            emojiList.filterIsInstance<EmojiItemItem>().searchMultiple2(searchFilter) { item ->
                translations?.let {
                    it.emojiToNames[item.emoji.emoji]?.names
                        ?: it.emojiToNames[item.emoji.emoji.replace("\uFE0F", "")]?.names
                        ?: it.emojiToNames[item.emoji.emoji + "\uFE0F"]?.names
                } ?: listOf(item.emoji.description)
            }.take(30).distinctBy { it.emoji.emoji }

        if(emojiList.isEmpty()) {
            emojiList = emojiList + listOf(CategoryItem("No results found"))
        }
    }

    Column {
        Emojis(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth()
                .weight(1.0f)
                .background(
                    LocalKeyboardScheme.current.keyboardContainer,
                    RoundedCornerShape(9.dp)
                ),
            emojis = emojiList,
            onClick = onClick,
            emojiMap = emojiMap,
            currentCategory = currentCategory,
            jumpCategory = jumpCategory
        )

        if(!isSearching) {
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
}

data class EmojiNames(val names: List<String>)
data class EmojiTranslations(val emojiToNames: Map<String, EmojiNames>)

class PersistentEmojiState : PersistentActionState {
    companion object {
        var emojis: MutableState<List<EmojiItem>?> = mutableStateOf(null)
        var emojiMap: HashMap<String, EmojiItem> = HashMap()

        // Language name to translations
        private val loadedTranslations: HashMap<String, EmojiTranslations> = hashMapOf()
        private val loadedTranslatedShortcuts: HashMap<String, Map<String, String>> = hashMapOf()

        @JvmStatic
        fun getTranslationForLocale(locale: Locale): EmojiTranslations? {
            return loadedTranslations[locale.language]
        }

        @JvmStatic
        fun getShortcut(locale: Locale, text: String): String? {
            return loadedTranslatedShortcuts[locale.language]?.get(text)
        }

        @JvmStatic
        fun loadTranslationsForLanguage(context: Context, locale: Locale) {
            val language = locale.language
            if (loadedTranslations.contains(language)) return
            loadedTranslations.put(language, EmojiTranslations(hashMapOf()))

            if(language == "en") {
                // Shortcuts are sourced from gemoji
                GlobalScope.launch(Dispatchers.IO) { loadEmojis(context) }
            }

            GlobalScope.launch(Dispatchers.IO) {
                val inputStream = GZIPInputStream(context.resources.openRawResource(R.raw.emoji_i18n))

                var data: JsonObject? = null
                BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = reader.readLine()
                        if (line == null) break
                        if (line.startsWith("#")) {
                            val lineLanguage = line.substring(1).trim()
                            if (lineLanguage == language) {
                                val jsonLine = reader.readLine()
                                data = Json.parseToJsonElement(jsonLine).jsonObject
                                break
                            }
                        }
                    }
                }

                if (data != null) {
                    val translations = data.map { entry ->
                        val names = entry.value.jsonArray.map { it.jsonPrimitive.content }
                        entry.key to EmojiNames(names)
                    }.toMap()
                    loadedTranslations.put(language, EmojiTranslations(translations))

                    // Shortcuts are unique words
                    val wordCounts = hashMapOf<String, Int>()
                    val words = translations.values.flatMap { it.names.flatMap { it.split(" ") }.toSet() }
                    words.forEach {
                        wordCounts[it] = (wordCounts[it] ?: 0) + 1
                    }

                    val aliases = translations.flatMap { entry ->
                        val ttsName = entry.value.names.last()

                        val names = entry.value.names.flatMap { it.split(" ") }
                        names.filter { wordCounts[it] == 1 && it.length > 1 }.map { it.lowercase() to entry.key } +
                                if(!ttsName.contains(' ')) {
                                    listOf(ttsName.lowercase() to entry.key)
                                } else {
                                    emptyList()
                                }
                    }.reversed().toMap()

                    if(language != "en") loadedTranslatedShortcuts.put(language, aliases)
                }
            }

        }

        @JvmStatic
        suspend fun loadEmojis(context: Context) = withContext(Dispatchers.IO) {
            val stream = GZIPInputStream(context.resources.openRawResource(R.raw.gemoji))
            val text = stream.bufferedReader().readText()

            val supplementalEmoteText = context.resources.openRawResource(R.raw.supplemental_emotes)
                .bufferedReader().readText()

            withContext(Dispatchers.Default) {
                val emojiData = Json.parseToJsonElement(text).jsonArray.toList()
                val supplementalEmoteData = Json.parseToJsonElement(supplementalEmoteText).jsonArray
                    .toList()

                val englishShortcuts = hashMapOf<String, String>()
                val englishLooseShortcuts = hashMapOf<String, String>()
                //val englishTranslations = hashMapOf<String, EmojiNames>()

                emojis.value = (emojiData + supplementalEmoteData).mapNotNull {
                    val emoji = it.jsonObject["emoji"]!!.jsonPrimitive.content
                    val category = it.jsonObject["category"]!!.jsonPrimitive.content
                    val supported =
                        emoji.codePoints().toList().all { c -> Character.getName(c) != null }
                                || category == "ASCII"

                    val tags = it.jsonObject["tags"]?.jsonArray?.map { it.jsonPrimitive.content }
                        ?.toList() ?: listOf()
                    val aliases = it.jsonObject["aliases"]?.jsonArray?.map { it.jsonPrimitive.content }
                        ?.toList() ?: listOf()

                    if(!supported) {
                        null
                    } else {
                        //englishTranslations.put(emoji, EmojiNames((tags + aliases)
                        //    .flatMap { listOf(it) + it.split("_") }
                        //    .toSet().toList()))

                        aliases.forEach { x ->
                            if(!englishShortcuts.containsKey(x)) {
                                englishShortcuts.put(x, emoji)
                            }
                        }

                        if(category != "ASCII") {
                            (tags + aliases).forEach { x ->
                                val v = x.split("_").first()
                                if (!englishLooseShortcuts.containsKey(v)) {
                                    englishLooseShortcuts.put(v, emoji)
                                }
                            }
                        }

                        EmojiItem(
                            emoji = emoji,
                            description = it.jsonObject["description"]!!.jsonPrimitive.content,
                            category = category,
                            skinTones = it.jsonObject["skin_tones"]?.jsonPrimitive?.booleanOrNull == true,
                            //tags = it.jsonObject["tags"]?.jsonArray?.map { it.jsonPrimitive.content }
                            //    ?.toList() ?: listOf(),
                            //aliases =
                        )
                    }
                }

                emojiMap = HashMap<String, EmojiItem>().apply {
                    emojis.value!!.forEach {
                        put(it.emoji, it)
                    }
                }

                loadedTranslatedShortcuts["en"] = englishShortcuts.apply {
                    englishLooseShortcuts.forEach {
                        if(!containsKey(it.key)) put(it.key, it.value)
                    }
                }
            }
        }
    }

    override suspend fun cleanUp() { }
    override fun close() { }
}


val EmojiAction = Action(
    icon = R.drawable.smile,
    name = R.string.action_emoji_title,
    canShowKeyboard = true,
    simplePressImpl = null,
    persistentState = { manager ->
        val state = PersistentEmojiState()
        manager.getLifecycleScope().launch {
            PersistentEmojiState.loadEmojis(manager.getContext())
        }

        state
    },
    windowImpl = { manager, persistentState ->
        object : ActionWindow() {
            private val searchText = mutableStateOf("")
            private val searching = mutableStateOf(false)

            @Composable
            override fun windowName(): String {
                return stringResource(R.string.action_emoji_title)
            }

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                val view = LocalView.current
                PersistentEmojiState.emojis.value?.let { emojis ->
                    EmojiGrid(onClick = {
                        if(it.category == "ASCII") {
                            manager.typeTextSurroundedByWhitespace(it.emoji)
                        } else {
                            manager.typeText(it.emoji)
                        }
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
                        manager.backspace(1)
                        if(!isRepeated) {
                            manager.performHapticAndAudioFeedback(Constants.CODE_DELETE, view)
                        }
                    }, emojis = emojis, keyboardShown = keyboardShown, emojiMap = PersistentEmojiState.emojiMap,
                        isSearching = searching.value, searchFilter = searchText.value)
                }
            }


            @Composable
            override fun WindowTitleBar(rowScope: RowScope) {
                val context = LocalContext.current
                if(searching.value) {
                    with(rowScope) {
                        Surface(
                            color = LocalKeyboardScheme.current.keyboardContainer,
                            contentColor = LocalKeyboardScheme.current.onKeyboardContainer,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .padding(2.dp)
                                .weight(1.0f)
                        ) {
                            Box(
                                modifier = Modifier.padding(8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                ActionTextEditor(text = searchText)
                            }
                        }
                    }
                } else {
                    super.WindowTitleBar(rowScope)
                    Surface(
                        color = LocalKeyboardScheme.current.keyboardContainer,
                        contentColor = LocalKeyboardScheme.current.onKeyboardContainer,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .padding(2.dp)
                            .width(128.dp),
                        onClick = { searching.value = true }
                    ) {
                        Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.CenterStart) {
                            Row {
                                Icon(Icons.Default.Search, contentDescription = null)
                                Text(
                                    stringResource(R.string.action_emoji_search_for_emojis), style = Typography.SmallMl, modifier = Modifier
                                        .alpha(0.75f)
                                        .align(Alignment.CenterVertically))
                            }
                        }
                    }

                    IconButton(onClick = {
                        manager.requestDialog(
                            context.getString(R.string.action_emoji_clear_recent_emojis_question),
                            listOf(
                                DialogRequestItem(context.getString(R.string.action_emoji_clear_recent_emojis_cancel)) {},
                                DialogRequestItem(context.getString(R.string.action_emoji_clear_recent_emojis_clear)) {
                                    runBlocking {
                                        manager.getContext().resetRecentEmojis()
                                    }
                                    manager.closeActionWindow()
                                },
                            ),
                            {}
                        )
                    }) {
                        Icon(painterResource(id = R.drawable.close), contentDescription = stringResource(
                            R.string.action_emoji_clear_recent_emojis_content_description
                        ))
                    }

                }
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
            EmojiItem(emoji = it, description = "", category = "Smileys & Emotion",
                skinTones = false)
        },
        keyboardShown = false,
        emojiMap = hashMapOf(),
        isSearching = false,
        searchFilter = ""
    )
}
