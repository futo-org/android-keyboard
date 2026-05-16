package org.futo.inputmethod.latin.uix.actions.clipboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.collection.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.util.AtomicFile
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.DialogRequestItem
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.inputmethod.latin.uix.PersistentStateInitialization
import org.futo.inputmethod.latin.uix.QuickClip
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.actions.BugInfo
import org.futo.inputmethod.latin.uix.actions.BugViewerAction
import org.futo.inputmethod.latin.uix.actions.BugViewerState
import org.futo.inputmethod.latin.uix.actions.PasteAction
import org.futo.inputmethod.latin.uix.actions.fonttyper.SuperheroRenderer
import org.futo.inputmethod.latin.uix.actions.throwIfDebug
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.getUnlockedSetting
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingSlider
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.SettingToggleRaw
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.pages.ParagraphText
import org.futo.inputmethod.latin.uix.settings.pages.PaymentSurface
import org.futo.inputmethod.latin.uix.settings.pages.PaymentSurfaceHeading
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.settings.userSettingToggleDataStore
import org.futo.inputmethod.latin.uix.theme.Typography
import java.io.File
import java.security.MessageDigest
import kotlin.math.roundToInt

val ClipboardHistoryEnabled = SettingsKey(
    booleanPreferencesKey("enableClipboardHistory"),
    false
)

val ClipboardHistoryItemsToKeep = SettingsKey(
    intPreferencesKey("clipboard_history_items_to_keep"),
    25
)

val ClipboardHistoryTimeToKeep = SettingsKey(
    intPreferencesKey("clipboard_history_time_to_keep"),
    3 * 24
)

val ClipboardHistoryUnpinnedFileMbToKeep = SettingsKey(
    intPreferencesKey("clipboard_history_unpinned_file_mb_to_keep"),
    40
)

val ClipboardHistorySaveSensitive = SettingsKey(
    booleanPreferencesKey("clipboard_history_save_sensitive"),
    false
)

val ClipboardShowPinnedOnTop = SettingsKey(
    booleanPreferencesKey("clipboard_history_show_pinned_on_top"),
    false
)

val ClipboardSingleColumn = SettingsKey(
    booleanPreferencesKey("clipboard_history_single_column"),
    false
)

val ClipboardQuickClipsEnabled = SettingsKey(
    booleanPreferencesKey("clipboard_quick_clips_enabled"),
    true
)

val ClipboardSkipDeleteConfirmation = SettingsKey(
    booleanPreferencesKey("clipboard_skip_delete_confirmation"),
    false
)

val ClipboardSaveImages = SettingsKey(
    booleanPreferencesKey("clipboard_save_images"),
    true
)

val ClipboardSaveScreenshots = SettingsKey(
    booleanPreferencesKey("clipboard_save_screenshots"),
    false
)

val ClipboardLastBackup = SettingsKey(
    longPreferencesKey("clipboard_last_backup"),
    0
)


object UriSerializer : KSerializer<Uri> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Uri", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Uri) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Uri {
        return Uri.parse(decoder.decodeString())
    }
}

@Serializable
data class ClipboardEntry(
    val timestamp: Long,
    val pinned: Boolean,

    val text: String?,

    @Serializable(with = UriSerializer::class)
    val uri: Uri?,
    val mimeTypes: List<String>,

    val backingFile: String? = null,
    val sizeMb: Float? = null,
)

fun ClipboardEntry.getFile(context: Context): File? =
    backingFile?.let { File(context.clipboardDir, it) }

fun ClipboardEntry.fileSizeMb(context: Context): Float? =
    sizeMb ?: getFile(context)?.let {
        if(it.isFile) {
            it.length() / (1024f * 1024f)
        } else {
            null
        }
    }

internal fun sanitizeClipboardText(text: String, maxLength: Int = 64): String {
    var result = text.replace("\n", " ")
    if(result.length > maxLength) {
        result = result.substring(0, maxLength) + "..."
    }
    return result
}

object ClipboardThumbCache {
    private val cache = LruCache<String, ImageBitmap>(20)

    fun getOrPut(path: String, lambda: () -> ImageBitmap?): ImageBitmap?
        = cache[path] ?: lambda()?.also { cache.put(path, it) }

    fun clear() {
        cache.evictAll()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardEntryView(modifier: Modifier, clipboardEntry: ClipboardEntry, onPaste: (ClipboardEntry) -> Unit, onRemove: (ClipboardEntry) -> Unit, onPin: (ClipboardEntry) -> Unit, bitmapOverride: ImageBitmap? = null) {
    val context = LocalContext.current

    val bitmap = remember(clipboardEntry) {
        if(clipboardEntry.text == null && clipboardEntry.backingFile != null) {
            val thumbnail = ClipboardUtil.thumbnailFor(File(context.clipboardDir, clipboardEntry.backingFile))
            if (thumbnail.exists() || bitmapOverride != null) {
                bitmapOverride ?: ClipboardThumbCache.getOrPut(thumbnail.name) {
                    BitmapFactory.decodeFile(thumbnail.absolutePath)?.asImageBitmap()
                }
            } else {
                null
            }
        } else {
            null
        }
    }

    val shape = RoundedCornerShape(8.dp)

    val color = if(clipboardEntry.pinned) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val mainModifier = modifier
        .padding(2.dp)
        .combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            enabled = true,
            onClick = { onPaste(clipboardEntry) },
            onLongClick = { onPin(clipboardEntry) }
        ).let {
            if(bitmap != null) {
                val maxHeight = 120.dp
                it
                    .requiredHeightIn(max = maxHeight)
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                    .requiredHeightIn(max = maxHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .drawBehind {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val bitmapWidth = bitmap.width.toFloat()
                        val bitmapHeight = bitmap.height.toFloat()

                        val scale = maxOf(
                            canvasWidth / bitmapWidth,
                            canvasHeight / bitmapHeight
                        )

                        val scaledWidth = bitmapWidth * scale
                        val scaledHeight = bitmapHeight * scale

                        val offsetX = (canvasWidth - scaledWidth) / 2
                        val offsetY = (canvasHeight - scaledHeight) / 2

                        drawImage(
                            bitmap,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bitmap.width, bitmap.height),
                            dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                            dstSize = IntSize(scaledWidth.toInt(), scaledHeight.toInt())
                        )

                        val height = 32.dp.toPx()
                        val brush = Brush.linearGradient(
                            0.0f to color,
                            1.0f to Color.Transparent,
                            start = Offset.Zero,
                            end = Offset(0.0f, height * 2.0f)
                        )

                        drawRect(brush)
                    }
            } else {
                it
            }
        }

    Surface(
        color = if(bitmap != null) Color.Transparent else color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = mainModifier,
        shape = shape,
    ) {
        Column {
            Row(modifier = Modifier.padding(0.dp)) {
                IconButton(onClick = {
                    onPin(clipboardEntry)
                }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painterResource(id = R.drawable.push_pin),
                        contentDescription = if(clipboardEntry.pinned) {
                            stringResource(R.string.action_clipboard_manager_unpin_item)
                        } else {
                            stringResource(R.string.action_clipboard_manager_pin_item)
                        },
                        tint = if(clipboardEntry.pinned) {
                            contentColorFor(color)
                        } else {
                            contentColorFor(color).copy(alpha = 0.5f)
                        },
                        modifier = Modifier.size(16.dp).rotate(
                            if(clipboardEntry.pinned) { 0f } else { 45f }
                        )
                    )
                }

                Spacer(modifier = Modifier.weight(1.0f))

                IconButton(onClick = {
                    onRemove(clipboardEntry)
                }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painterResource(id = R.drawable.close),
                        contentDescription = stringResource(R.string.action_clipboard_manager_remove_item),
                        tint = contentColorFor(color),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if(clipboardEntry.text != null) {
                val text = remember(clipboardEntry.text) { sanitizeClipboardText(clipboardEntry.text) }
                Text(text, modifier = Modifier.padding(8.dp, 2.dp), style = Typography.SmallMl)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}




@Preview(showBackground = true)
@Composable
fun ClipboardEntryViewPreview() {
    val sampleText = listOf("This is an entry", "Copying text a lot", "hunter2", "https://www.example.com/forum/viewpost/1234573193.html?parameter=1234")
    LazyVerticalStaggeredGrid(
        modifier = Modifier.fillMaxWidth(),
        columns = StaggeredGridCells.Adaptive(160.dp),
        verticalItemSpacing = 4.dp,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            ClipboardEntryView(
                modifier = Modifier,
                clipboardEntry = ClipboardEntry(0L, false, null, null, listOf("image/png"), "[test]", 0.0f),
                onPin = {}, onPaste = {}, onRemove = {},
                bitmapOverride = ClipboardUtil.generateCheckerboardBitmap()
            )
        }
        items(sampleText.size) {
            ClipboardEntryView(modifier = Modifier, clipboardEntry = ClipboardEntry(0L, it % 2 == 0, sampleText[it], null, listOf()), onPin = {}, onPaste = {}, onRemove = {})
        }

        item {
            ClipboardEntryView(
                modifier = Modifier,
                clipboardEntry = ClipboardEntry(0L, false, null, null, listOf("image/png"), "[test]", 0.0f),
                onPin = {}, onPaste = {}, onRemove = {},
                bitmapOverride = ClipboardUtil.generateTestPatternBitmap()
            )
        }

        item {
            ClipboardEntryView(
                modifier = Modifier,
                clipboardEntry = ClipboardEntry(0L, false, null, null, listOf("image/png"), "[test]", 0.0f),
                onPin = {}, onPaste = {}, onRemove = {},
                bitmapOverride = SuperheroRenderer.render(LocalContext.current, "my clipboard image")?.asImageBitmap()
            )
        }

        items(sampleText.size / 2) {
            ClipboardEntryView(modifier = Modifier, clipboardEntry = ClipboardEntry(0L, it % 2 == 0, sampleText[it], null, listOf()), onPin = {}, onPaste = {}, onRemove = {})
        }

        item {
            ClipboardEntryView(
                modifier = Modifier,
                clipboardEntry = ClipboardEntry(0L, false, null, null, listOf("image/png"), "[test]", 0.0f),
                onPin = {}, onPaste = {}, onRemove = {},
                bitmapOverride = SuperheroRenderer.render(LocalContext.current, "hey")?.asImageBitmap()
            )
        }

        items(sampleText.size / 2) {
            ClipboardEntryView(modifier = Modifier, clipboardEntry = ClipboardEntry(0L, it % 2 == 0, sampleText[it], null, listOf()), onPin = {}, onPaste = {}, onRemove = {})
        }
    }
}

val DefaultClipboardEntry = ClipboardEntry(
    timestamp = 0L,
    pinned = true,
    text = "Clipboard entries will appear here",
    uri = null,
    mimeTypes = listOf()
)

const val ClipboardFileName = "clipboard.json"
val Context.clipboardFile get() = File(filesDir, ClipboardFileName)
val Context.clipboardDir get() = File(filesDir, "clipboardfiles")

@OptIn(ExperimentalCoroutinesApi::class)
private val ClipboardIOContext = Dispatchers.IO.limitedParallelism(1)

class ClipboardHistoryManager(val context: Context, val coroutineScope: LifecycleCoroutineScope) : PersistentActionState {
    var clipboardIOFailureReason = ""
    val clipboardIOFailure = mutableStateOf(false)

    companion object {
        val onClipboardImportedFlow = MutableSharedFlow<File>()
    }

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val clipboardHistory = mutableStateListOf<ClipboardEntry>()

    // Primary file
    val clipboardFile = context.clipboardFile
    val atomicClipboardFile = AtomicFile(clipboardFile)

    // Backup in case primary gets corrupted somehow
    val clipboardFileBak = File(context.filesDir, "$ClipboardFileName.backup")
    val clipboardFileBakLegacy = File(context.filesDir, "$ClipboardFileName.bak")

    // Temporary file used during saving, after writing we delete previous backup, move primary to backup, move swap to primary
    val clipboardFileSwap = File(context.filesDir, "$ClipboardFileName.swap")

    var clipboardLoaded = false

    private val screenshotHelper = ScreenshotHelper(context, coroutineScope, object : ScreenshotListener {
        override fun onScreenshotAdded(mime: String, uri: Uri) {
            onImageAdded(listOf(mime), uri, keepUri = true)
        }

        override fun onScreenshotChange(
            uri: Uri,
            checkTrashed: suspend () -> Boolean
        ) {
            val item = clipboardHistory.find { it.uri == uri }
            if(item != null) {
                coroutineScope.launch {
                    if(checkTrashed()) {
                        withContext(Dispatchers.Main) { onRemove(item) }
                    }
                }
            }
        }
    })

    override suspend fun onDeviceUnlocked() {
        loadClipboard()
    }

    internal fun onImageAdded(mimeTypes: List<String>, uri: Uri, timestamp: Long = System.currentTimeMillis(), keepUri: Boolean = false) {
        // We may get exceptions here from opening uri, invalid image, etc
        // so let's just ignore them
        try {
            val targetMime = mimeTypes.firstOrNull { it == "image/png" }
                ?: mimeTypes.firstOrNull { it == "image/jpeg" || it == "image/jpg" }
                ?: mimeTypes.firstOrNull { it == "image/webp" }
                ?: return

            val resolver = context.contentResolver
            val stream = resolver.openInputStream(uri) ?: return
            val md = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(8 * 1024)
            var totalBytes = 0L
            var bytesRead: Int

            val tempFile = File(context.cacheDir, "temp_img")
            tempFile.outputStream().use { out ->
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    totalBytes += bytesRead
                    if (totalBytes > 10 * 1024 * 1024) {
                        tempFile.delete()
                        return
                    }
                    md.update(buffer, 0, bytesRead)
                    out.write(buffer, 0, bytesRead)
                }
            }
            stream.close()

            val md5Hex = md.digest().joinToString("") { "%02x".format(it) }
            val extension = when (targetMime) {
                "image/png" -> "png"
                "image/jpeg", "image/jpg" -> "jpg"
                "image/webp" -> "webp"
                else -> "img"
            }

            context.clipboardDir.mkdirs()
            val finalFile = File(context.clipboardDir, "$md5Hex.$extension")
            if (!finalFile.exists()) {
                tempFile.renameTo(finalFile)
                ClipboardUtil.generateThumbnail(finalFile)
            } else {
                tempFile.delete()
            }

            val isAlreadyPinned = clipboardHistory.firstOrNull {
                it.backingFile == finalFile.name && it.pinned
            }?.pinned == true

            clipboardHistory.removeAll { it.backingFile == finalFile.name }

            val newEntry = ClipboardEntry(
                timestamp = timestamp,
                pinned = isAlreadyPinned,
                text = null,
                uri = if(keepUri) uri else null,
                backingFile = finalFile.name,
                sizeMb = totalBytes / (1024f * 1024f),
                mimeTypes = listOf(targetMime)
            )
            clipboardHistory.add(newEntry)
        }catch(e: Exception) {
            throwIfDebug(e)
        } finally {
            saveClipboard()
        }
    }

    private val primaryClipChangedListener = object : ClipboardManager.OnPrimaryClipChangedListener {
            override fun onPrimaryClipChanged() {
                if (!context.getSettingBlocking(ClipboardHistoryEnabled)) return

                val clip = try {
                    clipboardManager.primaryClip
                } catch(_: Exception) {
                    null
                }

                val uri = clip?.getItemAt(0)?.uri
                val mimeTypes = List(clip?.description?.mimeTypeCount ?: 0) {
                    clip?.description?.getMimeType(it)
                }.filterNotNull()

                // Text is only possible if no URI is specified, or a text mimetype is specified
                var textChrSeq = if(uri == null || mimeTypes.any { it.startsWith("text/") }){
                    clip?.getItemAt(0)?.coerceToText(context)
                } else null

                // Ignore massive entries
                if(textChrSeq != null && textChrSeq.length > 500_000) {
                    textChrSeq = null
                }

                val text = textChrSeq?.toString()

                // Nothing to save here
                if(text == null && uri == null) return

                val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    clip?.description?.timestamp
                } else {
                    null
                } ?: System.currentTimeMillis()


                val canSaveSensitive = context.getSetting(ClipboardHistorySaveSensitive)
                val canSaveImages = context.getSetting(ClipboardSaveImages)
                val isSensitive = clip?.description?.extras?.getBoolean(
                    ClipDescription.EXTRA_IS_SENSITIVE, false
                ) == true

                if(isSensitive && !canSaveSensitive) return

                if(text != null) {
                    val isAlreadyPinned = clipboardHistory.firstOrNull {
                        (it.text != null && it.text == text) && it.pinned
                    }?.pinned == true

                    clipboardHistory.removeAll { it.text != null && it.text == text }

                    val newEntry = ClipboardEntry(
                        timestamp = timestamp,
                        pinned = isAlreadyPinned,
                        text = text,
                        uri = null,
                        mimeTypes = mimeTypes
                    )
                    clipboardHistory.add(newEntry)

                    saveClipboard()
                }else if (uri != null && canSaveImages) {
                    onImageAdded(mimeTypes, uri, timestamp)
                }
            }
        }

    init {
        coroutineScope.launch {
            loadClipboard()

            withContext(Dispatchers.Main) {
                clipboardManager.addPrimaryClipChangedListener(primaryClipChangedListener)
            }

            onClipboardImportedFlow.collectLatest {
                coroutineScope.ensureActive()
                onClipboardImported(it)
            }
        }
    }

    private suspend fun onClipboardImported(file: File) {
        migrateLegacyClipboardBak()
        val data = decodeFile(file).map {
            // Restore all saved items
            it.copy(timestamp = System.currentTimeMillis())
        }

        withContext(Dispatchers.Main) {
            clipboardHistory.clear()
            clipboardHistory.addAll(data)
            clipboardLoaded = true
        }

        saveClipboard()
    }

    suspend fun pruneOldItems() = withContext(Dispatchers.Main) {
        val numHoursToKeep = context.getSetting(ClipboardHistoryTimeToKeep)
        val numItemsToKeep = context.getSetting(ClipboardHistoryItemsToKeep)
        val megabytesToKeep = context.getSetting(ClipboardHistoryUnpinnedFileMbToKeep)
        val minimumTimestamp = System.currentTimeMillis() - (numHoursToKeep * 60L * 60L * 1000L)
        clipboardHistory.removeAll {
            (!it.pinned) && (it.timestamp < minimumTimestamp)
        }

        // Remove duplicates of entries, if any appeared
        // Duplicates will have same timestamp, same text, etc
        val set = clipboardHistory.toSet()
        if(set.size < clipboardHistory.size) {
            clipboardHistory.clear()
            clipboardHistory.addAll(set)
        }

        val maxItems = numItemsToKeep
        val numUnpinnedItems = clipboardHistory.filter { !it.pinned }.size

        val numItemsToRemove = numUnpinnedItems - maxItems
        if(numItemsToRemove > 0) {
            for(i in 0 until numItemsToRemove) {
                val idx = clipboardHistory.indexOfFirst { !it.pinned }
                if(idx == -1) break
                clipboardHistory.removeAt(idx)
            }
        }

        // Remove entries with nonexistent files
        clipboardHistory.removeAll {
            it.backingFile != null && it.getFile(context).let { it == null || !it.isFile }
        }

        // Limit size
        val removalCandidates = clipboardHistory.filter { it.backingFile != null && !it.pinned }
            .sortedBy { -it.timestamp }
        val removalItems = mutableListOf<ClipboardEntry>()

        var quotaUsed = 0.0f
        for(candidate in removalCandidates) {
            val size = candidate.fileSizeMb(context)
            if(size != null) {
                quotaUsed += size
                if(quotaUsed > megabytesToKeep) removalItems.add(candidate)
            } else {
                removalItems.add(candidate)
            }
        }

        removalItems.forEach { clipboardHistory.remove(it) }

        // Remove unreferenced files
        val stillReferenced = clipboardHistory
            .mapNotNull { it.backingFile }
            .flatMap { listOf(it, ClipboardUtil.thumbnailForName(it)) }
            .toHashSet()

        context.clipboardDir.listFiles()?.forEach {
            if(!stillReferenced.contains(it.name)) {
                it.delete()
            }
        }
    }

    private fun migrateLegacyClipboardBak() {
        // AtomicFile interprets a (filename + ".bak") file entirely differently, we
        // need to make sure it's gone before any AtomicFile operations.
        if(clipboardFileBakLegacy.exists()) { clipboardFileBakLegacy.renameTo(clipboardFileBak) }
    }

    var saveClipboardLoadJob: Job? = null
    var lastClipboardWritten: List<ClipboardEntry>? = null
    internal fun saveClipboard(): Job? {
        if(!context.isDirectBootUnlocked) return null
        if(!clipboardLoaded) {
            if(saveClipboardLoadJob?.isActive == true) return null

            val currentEntries = clipboardHistory.toList()
            saveClipboardLoadJob = coroutineScope.launch {
                loadClipboard()

                if(clipboardLoaded) {
                    clipboardHistory.addAll(currentEntries)
                    saveClipboard()
                } else {
                    clipboardIOFailure.value = true
                }
            }

            return saveClipboardLoadJob
        }

        return coroutineScope.launch(context = ClipboardIOContext) {
            try {
                val list = withContext(Dispatchers.Main) { clipboardHistory.toList() }
                val json = Json.encodeToString(list).toByteArray()

                if(list == lastClipboardWritten) return@launch

                // Once every day only
                val shouldBackup = (System.currentTimeMillis() - context.getSetting(ClipboardLastBackup)) > 1000L * 60L * 60L * 24L * 1L
                var backupSucceeded = false


                withContext(NonCancellable) {
                    synchronized(atomicClipboardFile) {
                        migrateLegacyClipboardBak()

                        // Produce a backup
                        if (clipboardFile.exists() && shouldBackup) {
                            val existingData = atomicClipboardFile.readFully()

                            val isValid = try {
                                decodeData(existingData).isNotEmpty()
                            } catch (_: Exception) {
                                false
                            }
                            if (isValid) {
                                val atomicBackup = AtomicFile(clipboardFileBak)

                                val stream = atomicBackup.startWrite()
                                try {
                                    stream.write(existingData)
                                    stream.flush()
                                    atomicBackup.finishWrite(stream)
                                    backupSucceeded = true
                                } catch (e: Exception) {
                                    atomicBackup.failWrite(stream)
                                }
                            }
                        }

                        val stream = atomicClipboardFile.startWrite()
                        try {
                            stream.write(json)
                            stream.flush()
                            atomicClipboardFile.finishWrite(stream)
                            lastClipboardWritten = list
                        } catch (e: Exception) {
                            atomicClipboardFile.failWrite(stream)
                            throw e
                        }
                    }
                }

                if(backupSucceeded) {
                    context.setSetting(ClipboardLastBackup, System.currentTimeMillis())
                }

                // Finally validate it can be read
                if (decodeFile(clipboardFile) != list) {
                    throw Exception("Saved file data does not match expected data")
                }

                clipboardIOFailure.value = false
            } catch (e: Exception) {
                if(e is CancellationException) return@launch

                clipboardIOFailure.value = true
                clipboardIOFailureReason = e.toString()
                reportError("saveClipboard", e)
            }
        }
    }

    fun deleteClipboard() {
        listOf(clipboardFile, clipboardFileSwap, clipboardFileBak).forEach {
            if(it.exists()) it.delete()
        }
    }

    private fun decodeData(data: ByteArray): List<ClipboardEntry> =
        Json.decodeFromString<List<ClipboardEntry>>(data.decodeToString())

    private fun decodeFile(file: File): List<ClipboardEntry> =
        decodeData(AtomicFile(file).readFully())

    private fun reportError(during: String, e: Exception) {
        BugViewerState.pushBug(BugInfo("ClipboardHistoryManager", """
Clipboard IO error during $during

Cause: ${e.message}

Stack trace: ${e.stackTrace.map { it.toString() }}

--- main data start --- snip ---
${if(clipboardFile.exists()) { clipboardFile.readText() } else { "File does not exist" }}
--- main data end --- snip ---


--- bak data start --- snip ---
${if(clipboardFileBak.exists()) { clipboardFileBak.readText() } else { "File does not exist" }}
--- bak data end --- snip ---

--- swap data start --- snip ---
${if(clipboardFileSwap.exists()) { clipboardFileSwap.readText() } else { "File does not exist" }}
--- swap data end --- snip ---
"""))
    }

    private suspend fun loadClipboard() = withContext(ClipboardIOContext) {
        if(!context.isDirectBootUnlocked) {
            clipboardIOFailureReason = "Direct Boot not unlocked"
            clipboardIOFailure.value = true
            return@withContext
        }

        val clipboardSetting = context.getUnlockedSetting(ClipboardHistoryEnabled)
        if(clipboardSetting == null) {
            clipboardIOFailureReason = "Settings not unlocked"
            clipboardIOFailure.value = true
            return@withContext
        }

        try {
            if(clipboardSetting == false) {
                deleteClipboard()
            } else if (clipboardFile.exists()) {
                migrateLegacyClipboardBak()
                val data = synchronized(atomicClipboardFile) {
                    try {
                        decodeFile(clipboardFile)
                    } catch(e: Exception) {
                        reportError("loadClipboard main, trying bak", e)
                        if(clipboardFileBak.exists()) {
                            decodeFile(clipboardFileBak)
                        } else {
                            throw e
                        }
                    }
                }

                clipboardHistory.clear()
                clipboardHistory.addAll(data)
                pruneOldItems()
            } else {
                clipboardHistory.add(DefaultClipboardEntry)
            }

            clipboardLoaded = true
            clipboardIOFailureReason = ""
            clipboardIOFailure.value = false
        } catch (e: Exception) {
            e.printStackTrace()
            clipboardIOFailureReason = "Exception: ${e.message}"
            clipboardIOFailure.value = true

            reportError("loadClipboard", e)
        }
    }

    fun onPaste(item: ClipboardEntry) {
        val itemPos = clipboardHistory.indexOf(item).coerceAtLeast(0)
        clipboardHistory.removeAll { it == item }
        clipboardHistory.add(itemPos, item.copy(timestamp = System.currentTimeMillis()))

        saveClipboard()
    }

    fun onTogglePin(item: ClipboardEntry) {
        var itemPos = clipboardHistory.indexOf(item).coerceAtLeast(0)
        clipboardHistory.removeAll { it == item }

        if(context.getSetting(ClipboardShowPinnedOnTop)) {
            // With this setting, unpinning can cause the position to dramatically change, so it's
            // better to just always reinsert into the final position which is more expected.
            // (the final position is visually the first due to reverse iteration)
            itemPos = clipboardHistory.size
        }

        clipboardHistory.add(itemPos, item.copy(
                pinned = !item.pinned,

                // Updating timestamp is necessary to prevent the following situation:
                // 1. Item is past its expiration time but not removed because it's pinned
                // 2. User unpins it (possibly by accident!)
                // 3. Item immediately gets deleted because it's past its expiration time
                timestamp = System.currentTimeMillis(),
            ))

        saveClipboard()
    }

    fun onRemove(item: ClipboardEntry) {
        // Clear the clipboard if the item being removed is the current one
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // TODO: URI
            try {
                if ((item.text != null) && item.text == clipboardManager.primaryClip?.getItemAt(0)
                        ?.coerceToText(context)?.toString()
                ) {
                    clipboardManager.clearPrimaryClip()
                    QuickClip.markQuickClipDismissed()
                }
            } catch(e: Exception) {
                e.printStackTrace()
            }
        }
        clipboardHistory.removeAll { it == item }
        saveClipboard()
    }

    override suspend fun cleanUp() {
        saveClipboard()?.join()
    }

    override fun close() {
        clipboardManager.removePrimaryClipChangedListener(primaryClipChangedListener)
        screenshotHelper.onDestroy()
    }

}

fun String.toFNV1aHash(): Long {
    val fnvPrime: Long = 1099511628211L
    var hash: Long = -3750763034362895579L

    for (byte in this.toByteArray()) {
        hash = hash xor byte.toLong()
        hash *= fnvPrime
    }

    return hash
}

@OptIn(ExperimentalFoundationApi::class)
val ClipboardHistoryAction = Action(
    icon = R.drawable.clipboard_manager,
    name = R.string.action_clipboard_manager_title,
    simplePressImpl = null,
    canShowKeyboard = true,
    persistentState = { manager ->
        ClipboardHistoryManager(manager.getContext(), manager.getLifecycleScope())
    },
    altPressImpl = PasteAction.simplePressImpl,
    persistentStateInitialization = PersistentStateInitialization.OnKeyboardLoad,
    windowImpl = { manager, persistent ->
        val unlocked = !manager.isDeviceLocked()
        val clipboardHistoryManager = persistent as ClipboardHistoryManager

        manager.getLifecycleScope().launch { clipboardHistoryManager.pruneOldItems() }
        object : ActionWindow() {
            @Composable
            override fun windowName(): String {
                return stringResource(R.string.action_clipboard_manager_title)
            }

            @Composable
            override fun WindowTitleBar(rowScope: RowScope) {
                super.WindowTitleBar(rowScope)

                val resources = LocalResources.current

                val clipboardHistory = useDataStore(ClipboardHistoryEnabled, blocking = true)
                if(!clipboardHistory.value) return

                if(unlocked && !clipboardHistoryManager.clipboardIOFailure.value) {
                    IconButton(onClick = {
                        val numUnpinnedItems =
                            clipboardHistoryManager.clipboardHistory.count { !it.pinned }
                        if (clipboardHistoryManager.clipboardHistory.isEmpty()) {
                            manager.requestDialog(
                                resources.getString(R.string.action_clipboard_manager_disable_text),
                                listOf(
                                    DialogRequestItem(resources.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                                    DialogRequestItem(resources.getString(R.string.action_clipboard_manager_disable_button)) {
                                        clipboardHistory.setValue(false)
                                    },
                                ),
                                {}
                            )
                        } else if (numUnpinnedItems == 0) {
                            manager.requestDialog(
                                resources.getString(R.string.action_clipboard_manager_unpin_all_items_text),
                                listOf(
                                    DialogRequestItem(resources.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                                    DialogRequestItem(resources.getString(R.string.action_clipboard_manager_unpin_all_items_button)) {
                                        clipboardHistoryManager.clipboardHistory.toList().forEach {
                                            if (it.pinned) {
                                                clipboardHistoryManager.onTogglePin(it)
                                            }
                                        }
                                    },
                                ),
                                {}
                            )
                        } else {
                            manager.requestDialog(
                                resources.getString(R.string.action_clipboard_manager_clear_unpinned_items_text),
                                listOf(
                                    DialogRequestItem(resources.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                                    DialogRequestItem(resources.getString(R.string.action_clipboard_manager_clear_unpinned_items_button)) {
                                        clipboardHistoryManager.clipboardHistory.toList().forEach {
                                            if (!it.pinned) {
                                                clipboardHistoryManager.onRemove(it)
                                            }
                                        }
                                    },
                                ),
                                {}
                            )
                        }
                    }) {
                        Icon(
                            painterResource(id = R.drawable.close),
                            contentDescription = stringResource(R.string.action_clipboard_manager_clear_clipboard)
                        )
                    }
                }
            }

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                val view = LocalView.current
                val context = LocalContext.current
                val resources = LocalResources.current
                val clipboardHistory = useDataStore(ClipboardHistoryEnabled, blocking = true)
                if(!unlocked) {
                    ScrollableList {
                        PaymentSurface(isPrimary = true) {
                            PaymentSurfaceHeading(title = stringResource(R.string.action_clipboard_manager_error_device_locked_title))

                            ParagraphText(stringResource(R.string.action_clipboard_manager_error_device_locked_text))
                        }
                    }
                } else if(clipboardHistoryManager.clipboardIOFailure.value) {
                    ScrollableList {
                        PaymentSurface(isPrimary = true) {
                            PaymentSurfaceHeading(title = stringResource(R.string.action_clipboard_manager_error_general_title))
                            ParagraphText(
                                stringResource(
                                    R.string.action_clipboard_manager_error_general_text,
                                    clipboardHistoryManager.clipboardIOFailureReason
                                ))
                            Button(onClick = {
                                manager.activateAction(BugViewerAction)
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.action_clipboard_manager_inspect_error_via_bugs_action))
                            }
                            Button(onClick = {
                                clipboardHistoryManager.saveClipboard()
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.action_clipboard_manager_retry_saving_loading))
                            }
                            Button(onClick = {
                                    manager.requestDialog(
                                        resources.getString(R.string.action_clipboard_manager_delete_corrupted_clipboard_text),
                                        listOf(
                                            DialogRequestItem(resources.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                                            DialogRequestItem(resources.getString(R.string.action_clipboard_manager_delete_corrupted_clipboard_button)) {
                                                clipboardHistoryManager.clipboardIOFailure.value = false
                                                clipboardHistory.setValue(false)
                                                clipboardHistoryManager.deleteClipboard()
                                            },
                                        ),
                                        {}
                                    )

                                }, modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                Text(resources.getString(R.string.action_clipboard_manager_delete_corrupted_clipboard_button))
                            }
                        }
                    }
                } else if(!clipboardHistory.value) {
                    ScrollableList {
                        PaymentSurface(isPrimary = true) {
                            PaymentSurfaceHeading(title = stringResource(R.string.action_clipboard_manager_error_clipboard_history_disabled_title))
                            ParagraphText(stringResource(R.string.action_clipboard_manager_error_clipboard_history_disabled_text_v2,
                                    context.getSetting(ClipboardHistoryItemsToKeep),
                                    (context.getSetting(ClipboardHistoryTimeToKeep) / 24.0f).roundToInt()
                                )
                            )
                            Button(onClick = {
                                    clipboardHistory.setValue(true)
                                }, modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.action_clipboard_manager_enable_clipboard_history_button))
                            }
                        }
                    }
                } else {
                    val sortedList = when {
                        useDataStoreValue(ClipboardShowPinnedOnTop) -> clipboardHistoryManager.clipboardHistory
                            .sortedBy { it.pinned }

                        else -> clipboardHistoryManager.clipboardHistory
                    }

                    val useSingleColumn = useDataStoreValue(ClipboardSingleColumn)
                    val columns = if(useSingleColumn) {
                        StaggeredGridCells.Fixed(1)
                    } else {
                        StaggeredGridCells.Adaptive(140.dp)
                    }

                    LazyVerticalStaggeredGrid(
                        modifier = Modifier.fillMaxWidth(),
                        columns = columns,
                        verticalItemSpacing = 4.dp,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(sortedList.size, key = { r_i ->
                            val i = sortedList.size - r_i - 1
                            val entry = sortedList[i]

                            entry.text?.let {
                                if(it.length > 512) {
                                    // Compose really doesn't like extremely long keys, so
                                    // to avoid crashing we just provide a hash
                                    it.toFNV1aHash()
                                } else {
                                    it
                                }
                            } ?: entry.backingFile ?: i
                            i
                        }) { r_i ->
                            val i = sortedList.size - r_i - 1
                            val entry = sortedList[i]
                            ClipboardEntryView(
                                modifier = Modifier.animateItem(),
                                clipboardEntry = entry, onPaste = {
                                    if(it.text != null) {
                                        manager.typeText(it.text)
                                    } else if(it.backingFile != null && it.mimeTypes.isNotEmpty()) {
                                        val request = ClipboardProviderState.addRequest(
                                            ClipboardPasteRequest(
                                                file = File(context.clipboardDir, it.backingFile),
                                                mimeType = it.mimeTypes.first(),
                                                expiration = System.currentTimeMillis() + 5L * 60L * 1000L
                                            )
                                        )

                                        val uri = "content://${CLIPBOARD_AUTHORITY}/clip/$request".toUri()
                                        manager.typeUri(uri, it.mimeTypes, true)
                                    }

                                    clipboardHistoryManager.onPaste(it)
                                    manager.performHapticAndAudioFeedback(Constants.CODE_OUTPUT_TEXT, view)
                                }, onRemove = {
                                    if(context.getSetting(ClipboardSkipDeleteConfirmation) && !it.pinned) {
                                        clipboardHistoryManager.onRemove(it)
                                        manager.performHapticAndAudioFeedback(Constants.CODE_TAB, view)
                                    } else {
                                        manager.requestDialog(
                                            if(it.backingFile != null && it.text == null) {
                                                resources.getString(R.string.action_clipboard_manager_remove_item_confirm_dialog_image)
                                            } else {
                                                resources.getString(
                                                    R.string.action_clipboard_manager_remove_item_confirm_dialog,
                                                    sanitizeClipboardText(it.text ?: "", 24)
                                                )
                                            },
                                            listOf(
                                                DialogRequestItem(
                                                    resources.getString(R.string.action_clipboard_manager_cancel_action_button)
                                                ) { },
                                                DialogRequestItem(
                                                    resources.getString(R.string.action_clipboard_manager_remove_item)
                                                ) {
                                                    clipboardHistoryManager.onRemove(it)
                                                    manager.performHapticAndAudioFeedback(
                                                        Constants.CODE_TAB,
                                                        view
                                                    )
                                                }
                                            )
                                        ) { }
                                    }
                                }, onPin = {
                                    clipboardHistoryManager.onTogglePin(it)
                                    manager.performHapticAndAudioFeedback(Constants.CODE_TAB, view)
                                })
                        }
                    }
                }
            }
        }
    },

    settingsMenu = UserSettingsMenu(
        title = R.string.action_clipboard_manager_settings_title,
        navPath = "actions/clipboard_history",
        registerNavPath = true,
        settings = listOf(
            userSettingToggleDataStore(
                title = R.string.action_clipboard_manager_settings_show_quick_clips,
                setting = ClipboardQuickClipsEnabled
            ),

            userSettingToggleDataStore(
                title = R.string.typing_settings_enable_clipboard_history,
                setting = ClipboardHistoryEnabled
            ).copy(searchTags = R.string.typing_settings_enable_clipboard_history_tags),

            userSettingToggleDataStore(
                title = R.string.action_clipboard_manager_settings_save_images,
                setting = ClipboardSaveImages
            ).copy(visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }),

            UserSetting(
                name = R.string.action_clipboard_manager_settings_save_screenshots,
                component = {
                    val context = LocalContext.current
                    val (enabled, setEnabled) = useDataStore(ClipboardSaveScreenshots)

                    val permission = ScreenshotHelper.permission

                    val launcher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted: Boolean ->
                        if (isGranted) {
                            setEnabled(true)
                        } else {
                            setEnabled(false)
                        }
                    }

                    SettingToggleRaw(
                        title = stringResource(R.string.action_clipboard_manager_settings_save_screenshots),
                        subtitle = if(enabled) null else stringResource(R.string.action_clipboard_manager_settings_save_screenshots_subtitle),
                        enabled = enabled,
                        setValue = {
                            if(!it) setEnabled(false) else {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        permission
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    setEnabled(true)
                                } else {
                                    launcher.launch(permission)
                                }
                            }
                        }
                    )
                },
                visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }
            ),
            UserSetting(
                name = R.string.action_clipboard_manager_settings_maximum_clips,
                component = {
                    SettingSlider(
                        stringResource(R.string.action_clipboard_manager_settings_maximum_clips),
                        ClipboardHistoryItemsToKeep,
                        range = 0.0f..100.0f,
                        hardRange = 0.0f..Float.POSITIVE_INFINITY,
                        transform = { it.toInt() },
                    )
                },
                visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }
            ),

            UserSetting(
                name = R.string.action_clipboard_manager_settings_hours_to_keep_clips,
                component = {
                    SettingSlider(
                        stringResource(R.string.action_clipboard_manager_settings_hours_to_keep_clips),
                        ClipboardHistoryTimeToKeep,
                        range = 1.0f..336.0f,
                        hardRange = 0.0f..Float.POSITIVE_INFINITY,
                        transform = { it.toInt() },
                    )
                },
                visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }
            ),

            UserSetting(
                name = R.string.action_clipboard_manager_settings_save_sensitive_clips,
                subtitle = R.string.action_clipboard_manager_settings_save_sensitive_clips_subtitle,
                component = {
                    SettingToggleDataStore(
                        title = stringResource(R.string.action_clipboard_manager_settings_save_sensitive_clips),
                        subtitle = stringResource(R.string.action_clipboard_manager_settings_save_sensitive_clips_subtitle),
                        setting = ClipboardHistorySaveSensitive
                    )
                },
                visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }
            ),

            userSettingToggleDataStore(
                title = R.string.action_clipboard_manager_settings_show_pinned_above_others,
                setting = ClipboardShowPinnedOnTop
            ).copy(visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }),

            userSettingToggleDataStore(
                title = R.string.action_clipboard_manager_settings_list_layout,
                setting = ClipboardSingleColumn
            ).copy(visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }),

            userSettingToggleDataStore(
                title = R.string.action_clipboard_manager_settings_skip_delete_confirmation,
                setting = ClipboardSkipDeleteConfirmation
            ).copy(visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }),
        )
    )
)