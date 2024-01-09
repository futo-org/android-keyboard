package org.futo.inputmethod.latin.uix.actions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.util.TypedValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.applyCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.PersistentActionState
import kotlin.math.ceil
import kotlin.math.roundToInt

data class EmojiItem(
    val emoji: String,
    val description: String,
    val category: String
)

const val EMOJI_HEIGHT = 30.0f //sp

data class BitmapRecycler(
    private val freeBitmaps: MutableList<Bitmap> = mutableListOf()
) {
    var textPaint: TextPaint? = null
    var total = 0
    fun getTextPaint(context: Context): TextPaint {
        return textPaint ?: run {
            this.textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                textSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    EMOJI_HEIGHT,
                    context.resources.displayMetrics
                )
            }

            this.textPaint!!
        }
    }

    fun getBitmap(): Bitmap {
        return freeBitmaps.removeFirstOrNull()?.apply {
            eraseColor(Color.TRANSPARENT)
        } ?: with(textPaint!!.fontMetricsInt) {
            println("creating new bitmap, total $total")
            total += 1
            val size = bottom - top
            Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        }
    }

    fun freeBitmap(bitmap: Bitmap) {
        if(freeBitmaps.size > 60) {
            println("Recycling bitmap, new total $total")
            total -= 1
            bitmap.recycle()
        } else {
            freeBitmaps.add(bitmap)
        }
    }

    fun freeAllBitmaps() {
        freeBitmaps.forEach {
            println("Recycling bitmap due to freeAllBitmaps, new total $total")
            total -= 1
            it.recycle()
        }
        freeBitmaps.clear()
    }
}

@Composable fun EmojiIcon(emoji: String, bitmaps: BitmapRecycler) {
    var rendering by remember { mutableStateOf(true) }
    val offscreenCanvasBitmap: Bitmap = remember { bitmaps.getBitmap() }
    val imageBitmap = remember { offscreenCanvasBitmap.asImageBitmap() }

    DisposableEffect(offscreenCanvasBitmap) {
        onDispose {
            bitmaps.freeBitmap(bitmap = offscreenCanvasBitmap)
        }
    }

    LaunchedEffect(emoji) {
        withContext(Dispatchers.Unconfined) {
            val textPaint = bitmaps.textPaint!!
            yield()
            offscreenCanvasBitmap.applyCanvas {
                yield()
                val textWidth = textPaint.measureText(emoji, 0, emoji.length)
                yield()
                drawText(
                    emoji,
                    /* start = */ 0,
                    /* end = */ emoji.length,
                    /* x = */ (width - textWidth) / 2,
                    /* y = */ -textPaint.fontMetrics.top,
                    textPaint,
                )
                yield()
            }
            yield()
            rendering = false
        }
    }

    Image(
        bitmap = imageBitmap,
        contentDescription = emoji,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun EmojiGrid(onClick: (EmojiItem) -> Unit, onExit: () -> Unit, onBackspace: () -> Unit, onSpace: () -> Unit, bitmaps: BitmapRecycler, emojis: List<EmojiItem>, keyboardShown: Boolean) {
    val context = LocalContext.current
    val spToDp = context.resources.displayMetrics.scaledDensity / context.resources.displayMetrics.density

    Column {
        LazyVerticalGrid(
            columns = GridCells.Adaptive((40.sp * spToDp).value.dp),
            contentPadding = PaddingValues(10.dp),
            modifier = Modifier.weight(1.0f)
        ) {
            items(emojis, key = { it.emoji }) { emoji ->
                Box(modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        onClick(emoji)
                    }) {
                    EmojiIcon(emoji.emoji, bitmaps)
                }
            }
        }

        if(!keyboardShown) {
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
    }
}

/*
@Preview(showBackground = true)
@Composable
fun EmojiGridPreview() {
    EmojiGrid(
        onBackspace = {},
        onClick = {},
        onExit = {},
        onSpace = {}
    )
}
*/

class PersistentEmojiState: PersistentActionState {
    val bitmaps: BitmapRecycler = BitmapRecycler()
    var emojis: MutableState<List<EmojiItem>?> = mutableStateOf(null)

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
                )
            }
        }
    }

    override suspend fun cleanUp() {
        bitmaps.freeAllBitmaps()
    }
}


val EmojiAction = Action(
    icon = R.drawable.smile,
    name = R.string.emoji_action_title,
    canShowKeyboard = true,
    simplePressImpl = null,
    persistentState = { manager ->
        val state = PersistentEmojiState()
        state.bitmaps.getTextPaint(manager.getContext())
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
                    }, bitmaps = state.bitmaps, emojis = emojis, keyboardShown = keyboardShown)
                }
            }

            override fun close() {
                state.bitmaps.freeAllBitmaps()
            }
        }
    }
)