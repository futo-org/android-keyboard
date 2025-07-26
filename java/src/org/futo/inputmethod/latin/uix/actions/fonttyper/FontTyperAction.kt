package org.futo.inputmethod.latin.uix.actions.fonttyper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.os.ParcelFileDescriptor
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.content.FONT_AUTHORITY
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionTextEditor
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.CloseResult
import org.futo.inputmethod.latin.uix.LocalKeyboardScheme
import org.futo.inputmethod.latin.uix.LocalManager
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.pages.VerticalGrid
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class LineRenderResult(
    val bitmap: Bitmap,
    val lineHeight: Int,
    val startXOffset: Int,
    val startYOffset: Int
)

abstract class WordImageRenderer {
    open val isSlow: Boolean = false
    open val useZealousCrop: Boolean = true
    open val font: ((Context) -> Typeface)? = null
    open val backgroundColor: Int = Color.TRANSPARENT

    @get:StringRes abstract val name: Int
    protected abstract fun renderLine(context: Context, text: String): LineRenderResult?

    protected open fun renderMultiLine(context: Context, text: String): Bitmap? {
        val renders = text.lines().mapNotNull { renderLine(context, it.trim()) }
        if(renders.isEmpty()) return null

        var bitmaps = renders.map { it.bitmap }
        val topImageHeight = bitmaps.first().height

        val padding = 32 // pad 16px on each side
        val newWidth = bitmaps.maxOf { it.width } + padding
        val newHeight = renders.sumOf { it.lineHeight * 12 / 10 } + padding + topImageHeight
        var newBitmap = createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(newBitmap)
        canvas.drawColor(Color.TRANSPARENT)

        var yOffset = padding / 2 + topImageHeight
        renders.forEach { render ->
            val xOffset = padding / 2
            canvas.drawBitmap(
                render.bitmap,
                xOffset.toFloat() - render.startXOffset,
                yOffset.toFloat() - render.startYOffset,
                null
            )
            yOffset += render.lineHeight * 12 / 10
        }

        return newBitmap
    }

    fun render(context: Context, text: String): Bitmap? = when {
        text.count { it == '\n' } == 0 -> renderLine(context, text)?.bitmap
        else -> renderMultiLine(context, text)
    }?.let { bitmap ->
        var bitmap = bitmap
        if(useZealousCrop) {
            bitmap = zealousCrop(bitmap, 16)
        }
        if(backgroundColor != Color.TRANSPARENT) {
            val canvas = Canvas(bitmap)
            val paint = Paint()
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
            paint.color = backgroundColor
            canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
            paint.xfermode = null
        }

        bitmap
    }
}

private val renderers = listOf(
    SuperheroRenderer,
    CandyRenderer,
    HorizonRenderer,
    RainbowRenderer,
    ScratchRenderer,
    OpeningCrawlRenderer,
    DramaticTextRenderer
)

data class ImageTypeRequest(
    val text: String,
    val generator: String,
    val rendererInstance: WordImageRenderer
)

object ImageTyperState {
    val requests: HashMap<UUID, ImageTypeRequest> = HashMap()

    fun addRequest(request: ImageTypeRequest): UUID {
        val uuid = UUID.randomUUID()
        requests.put(uuid, request)
        return uuid
    }

    val renders: HashMap<UUID, File> = HashMap()
    fun renderRequest(context: Context, uuid: UUID): ParcelFileDescriptor? =
        (renders[uuid] ?: run {
            val request = requests[uuid] ?: throw IllegalArgumentException("Invalid request")
            var bitmap = request.rendererInstance.render(context, request.text) ?: throw Exception("Failed to render text")

            val tempFile = File.createTempFile("rendered_image", ".png")
            FileOutputStream(tempFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }

            renders[uuid] = tempFile

            tempFile
        }).let {
            ParcelFileDescriptor.open(
                it,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
        }
}

internal fun zealousCrop(bitmap: Bitmap, allowPadding: Int = 0): Bitmap {
    val width = bitmap.width
    val height = bitmap.height

    var top = 0
    var left = 0
    var right = width - 1
    var bottom = height - 1

    val row = IntArray(width)

    loop@ for (y in 0 until height) {
        bitmap.getPixels(row, 0, width, 0, y, width, 1)
        for (x in 0 until width) {
            if ((row[x] ushr 24) != 0) break@loop
        }
        top++
    }
    if (top == height) return createBitmap(1, 1, bitmap.config!!) // fully transparent

    loop@ for (y in height - 1 downTo top) {
        bitmap.getPixels(row, 0, width, 0, y, width, 1)
        for (x in 0 until width) {
            if ((row[x] ushr 24) != 0) break@loop
        }
        bottom--
    }

    loop@ for (x in 0 until width) {
        for (y in top..bottom) {
            val pixel = bitmap[x, y]
            if ((pixel ushr 24) != 0) break@loop
        }
        left++
    }

    loop@ for (x in width - 1 downTo left) {
        for (y in top..bottom) {
            val pixel = bitmap[x, y]
            if ((pixel ushr 24) != 0) break@loop
        }
        right--
    }

    if (right <= left || bottom <= top) return createBitmap(1, 1, bitmap.config!!)

    left -= allowPadding
    right += allowPadding
    top -= allowPadding
    bottom += allowPadding

    left = left.coerceIn(0, width - 1)
    right = right.coerceIn(0, width - 1)
    top = top.coerceIn(0, height - 1)
    bottom = bottom.coerceIn(0, height - 1)

    if (right <= left || bottom <= top) return createBitmap(1, 1, bitmap.config!!)

    val cropWidth = right - left + 1
    val cropHeight = bottom - top + 1
    return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
}


@Composable
@Preview(showBackground = true)
internal fun FontTyperContents(
    currRenderer: WordImageRenderer = SuperheroRenderer,
    text: MutableState<String>? = null,
    onSent: () -> Unit = {}
) {
    val manager = if(!LocalInspectionMode.current) {
        LocalManager.current
    } else {
        null
    }

    val context = LocalContext.current

    val previewBitmap = remember { mutableStateOf<Bitmap?>(null) }
    val bitmapInvalidated = remember { mutableStateOf(false) }

    val font = remember {
        currRenderer.font?.invoke(context)
    }

    val text = text ?: remember { mutableStateOf("") }

    DisposableEffect(currRenderer) {
        if(font != null) manager?.overrideKeyboardTypeface(font)

        onDispose {
            manager?.overrideKeyboardTypeface(null)
        }
    }

    LaunchedEffect(text.value, currRenderer.isSlow) {
        if(currRenderer.isSlow) {
            bitmapInvalidated.value = true
            delay(700L)
        }

        withContext(Dispatchers.Default) {
            previewBitmap.value = currRenderer.render(context, text.value)
            bitmapInvalidated.value = false
        }
    }

    Column {
        Box(Modifier.fillMaxWidth().weight(1.0f)) {
            previewBitmap.value?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            if (bitmapInvalidated.value) {
                Box(
                    Modifier.matchParentSize().background(
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    )
                )
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.padding(8.dp, 0.dp)) {
            Surface(
                color = LocalKeyboardScheme.current.surface,
                border = BorderStroke(1.dp, SolidColor(LocalKeyboardScheme.current.outline)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1.0f).height(
                    (with(LocalDensity.current) {
                        (18.sp.toDp()) * (text.value.count { it == '\n' } + 1)
                    } + 16.dp).coerceAtLeast(48.dp)
                ),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                    ActionTextEditor(text, multiline = true, typeface = font, autocorrect = false)
                }
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = {
                    if(text.value.isBlank()) return@Button

                    val request = ImageTypeRequest(
                        text = text.value,
                        generator = "",
                        rendererInstance = currRenderer
                    )

                    val id = ImageTyperState.addRequest(request)
                    val uri = "content://${FONT_AUTHORITY}/render/$id".toUri()
                    manager!!.typeUri(uri, listOf("image/png"), true)
                    text.value = ""
                    onSent()
                },
                enabled = text.value.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(48.dp).align(Alignment.Bottom)
            ) {
                Icon(
                    painterResource(R.drawable.check),
                    //modifier = Modifier.size(16.dp),
                    contentDescription = stringResource(R.string.action_fonttyper_finish)
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
internal fun PresetPicker(
    setPreset: (WordImageRenderer) -> Unit = { }
) {
    val manager = if(LocalInspectionMode.current == false) {
        LocalManager.current
    } else {
        null
    }
    ScrollableList(Modifier.padding(8.dp, 0.dp)) {
        if(manager?.appSupportsImageInsertion("image/png", true) == false) {
            Tip("⚠ " + stringResource(R.string.action_fonttyper_app_unsupported_warning))

        }
        Text(stringResource(R.string.action_fonttyper_select_a_preset))
        Spacer(Modifier.height(8.dp))
        VerticalGrid(items = renderers, columns = 2) {
            OutlinedButton(onClick = {
                setPreset(it)
            }, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                Text(stringResource(it.name))
            }
        }
    }
}

val FontTyperAction = Action(
    icon = R.drawable.type,
    name = R.string.action_fonttyper_title,
    simplePressImpl = null,
    windowImpl = { manager, _ ->
        val presetToUse = mutableStateOf<WordImageRenderer?>(null)
        val enteredText = mutableStateOf("")
        val sentSomething = mutableStateOf(false)
        object : ActionWindow() {
            override val showCloseButton: Boolean
                get() = false

            override val positionIsUserManagable: Boolean
                get() = false

            @Composable
            override fun windowName(): String =
                stringResource(R.string.action_fonttyper_title)

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                presetToUse.value?.let {
                    FontTyperContents(it, enteredText) { sentSomething.value = true }
                    it
                } ?: run {
                    PresetPicker({
                        presetToUse.value = it
                        manager.forceActionWindowAboveKeyboard(true)
                    })
                }
            }

            override fun close(): CloseResult {
                if(presetToUse.value == null
                    || sentSomething.value) return CloseResult.Default

                presetToUse.value = null
                manager.forceActionWindowAboveKeyboard(false)
                enteredText.value = ""
                return CloseResult.PreventClosing
            }
        }
    },
)