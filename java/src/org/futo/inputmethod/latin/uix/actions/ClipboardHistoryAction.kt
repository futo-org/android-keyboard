package org.futo.inputmethod.latin.uix.actions

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.getUnlockedSetting
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.pages.ParagraphText
import org.futo.inputmethod.latin.uix.settings.pages.PaymentSurface
import org.futo.inputmethod.latin.uix.settings.pages.PaymentSurfaceHeading
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.Typography
import java.io.File

val ClipboardHistoryEnabled = SettingsKey(
    booleanPreferencesKey("enableClipboardHistory"),
    false
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
    val mimeTypes: List<String>
)


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardEntryView(modifier: Modifier, clipboardEntry: ClipboardEntry, onPaste: (ClipboardEntry) -> Unit, onRemove: (ClipboardEntry) -> Unit, onPin: (ClipboardEntry) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .padding(2.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material.ripple.rememberRipple(),
                enabled = true,
                onClick = { onPaste(clipboardEntry) },
                onLongClick = { onPin(clipboardEntry) }
            ),
        shape = RoundedCornerShape(8.dp),
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
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1.0f))

                IconButton(onClick = {
                    onRemove(clipboardEntry)
                }, modifier = Modifier.size(32.dp), enabled = !clipboardEntry.pinned) {
                    Icon(
                        painterResource(id = R.drawable.close),
                        contentDescription = stringResource(R.string.action_clipboard_manager_remove_item),
                        tint = if(clipboardEntry.pinned) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            val text = (clipboardEntry.text ?: "").let {
                if(it.length > 256) {
                    it.substring(0, 256) + "..."
                } else {
                    it
                }
            }

            Text(text, modifier = Modifier.padding(8.dp, 2.dp), style = Typography.SmallMl)

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
        items(sampleText.size) {
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

class ClipboardHistoryManager(val context: Context, val coroutineScope: LifecycleCoroutineScope) : PersistentActionState {
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val clipboardHistory = mutableStateListOf<ClipboardEntry>()

    var clipboardLoaded = false
    
    override suspend fun onDeviceUnlocked() {
        loadClipboard()
    }

    init {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                loadClipboard()
            }

            withContext(Dispatchers.Main) {
                clipboardManager.addPrimaryClipChangedListener {
                    if(!context.getSettingBlocking(ClipboardHistoryEnabled)) return@addPrimaryClipChangedListener

                    val clip = clipboardManager.primaryClip

                    val text = clip?.getItemAt(0)?.coerceToText(context)?.toString()
                    val uri = clip?.getItemAt(0)?.uri

                    val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        clip?.description?.timestamp
                    } else {
                        null
                    } ?: System.currentTimeMillis()

                    val mimeTypes = List(clip?.description?.mimeTypeCount ?: 0) {
                        clip?.description?.getMimeType(it)
                    }.filterNotNull()

                    val isSensitive = clip?.description?.extras?.getBoolean(
                        ClipDescription.EXTRA_IS_SENSITIVE, false) ?: false

                    // TODO: Support images and other non-text media
                    if (text != null && uri == null && !isSensitive) {
                        val isAlreadyPinned = clipboardHistory.firstOrNull {
                            ((it.text != null && it.text == text) || (it.uri != null && it.uri == uri)) && it.pinned
                        }?.pinned ?: false

                        clipboardHistory.removeAll {
                            (it.text != null && it.text == text) || (it.uri != null && it.uri == uri)
                        }

                        val newEntry = ClipboardEntry(
                            timestamp = timestamp,
                            pinned = isAlreadyPinned,
                            text = text,
                            uri = uri,
                            mimeTypes = mimeTypes
                        )
                        clipboardHistory.add(newEntry)

                        saveClipboard()
                    }
                }
            }
        }
    }

    suspend fun pruneOldItems() = withContext(Dispatchers.Main) {
        val maxDays = 3L
        val minimumTimestamp = System.currentTimeMillis() - (maxDays * 24L * 60L * 60L * 1000L)
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

        val maxItems = 25
        val numUnpinnedItems = clipboardHistory.filter { !it.pinned }.size

        val numItemsToRemove = numUnpinnedItems - maxItems
        if(numItemsToRemove > 0) {
            for(i in 0 until numItemsToRemove) {
                val idx = clipboardHistory.indexOfFirst { !it.pinned }
                if(idx == -1) break
                clipboardHistory.removeAt(idx)
            }
        }
    }

    val clipboardSaveFailure = mutableStateOf(false)
    var saveClipboardLoadJob: Job? = null
    private fun saveClipboard() {
        if(!context.isDirectBootUnlocked) return
        if(!clipboardLoaded) {
            if(saveClipboardLoadJob?.isActive == true) return

            val currentEntries = clipboardHistory.toList()
            saveClipboardLoadJob = coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    loadClipboard()
                }

                if(clipboardLoaded) {
                    clipboardHistory.addAll(currentEntries)
                    saveClipboard()
                } else {
                    clipboardSaveFailure.value = true
                }
            }

            return
        }

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                pruneOldItems()

                val json = Json.encodeToString(clipboardHistory.toList())

                val file = File(context.filesDir, "clipboard.json")
                file.writeText(json)

                clipboardSaveFailure.value = false
            }
        }
    }

    fun deleteClipboard() {
        val file = File(context.filesDir, "clipboard.json")
        file.delete()
    }

    var clipboardLoadFailureReason = ""
    private suspend fun loadClipboard() {
        if(!context.isDirectBootUnlocked) {
            clipboardLoadFailureReason = "Direct Boot not unlocked"
            clipboardSaveFailure.value = true
            return
        }

        val clipboardSetting = context.getUnlockedSetting(ClipboardHistoryEnabled)
        if(clipboardSetting == null) {
            clipboardLoadFailureReason = "Settings not unlocked"
            clipboardSaveFailure.value = true
            return
        }

        var inputString = ""
        try {
            val file = File(context.filesDir, "clipboard.json")

            if(clipboardSetting == false) {
                file.delete()
            } else if (file.exists()) {
                val reader = file.bufferedReader()
                inputString = reader.use { it.readText() }

                val data = Json.decodeFromString<List<ClipboardEntry>>(inputString)

                clipboardHistory.clear()
                clipboardHistory.addAll(data)
                pruneOldItems()
            } else {
                clipboardHistory.add(DefaultClipboardEntry)
            }

            clipboardLoaded = true
            clipboardLoadFailureReason = ""
            clipboardSaveFailure.value = false
        } catch (e: Exception) {
            e.printStackTrace()
            clipboardLoadFailureReason = "Exception: ${e.message}"
            clipboardSaveFailure.value = true

            BugViewerState.pushBug(BugInfo("ClipboardHistoryManager", """
Clipboard history could not be loaded

Cause: ${e.message}

Stack trace: ${e.stackTrace.map { it.toString() }}

--- data --- snip ---
$inputString
--- data --- snip ---
"""))
        }
    }

    fun onPaste(item: ClipboardEntry) {
        val itemPos = clipboardHistory.indexOf(item)
        clipboardHistory.removeAll { it == item }

        clipboardHistory.add(itemPos,
            ClipboardEntry(
                timestamp = System.currentTimeMillis(),
                pinned = item.pinned,
                text = item.text,
                uri = item.uri,
                mimeTypes = item.mimeTypes
            )
        )

        saveClipboard()
    }

    fun onPin(item: ClipboardEntry) {
        clipboardHistory.removeAll { it == item }

        clipboardHistory.add(
            ClipboardEntry(
                timestamp = System.currentTimeMillis(),
                pinned = !item.pinned,
                text = item.text,
                uri = item.uri,
                mimeTypes = item.mimeTypes
            )
        )

        saveClipboard()
    }

    fun onRemove(item: ClipboardEntry) {
        // Clear the clipboard if the item being removed is the current one
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // TODO: URI
            if((item.text != null) && item.text == clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()) {
                clipboardManager.clearPrimaryClip()
            }
        }
        clipboardHistory.removeAll { it == item }
        saveClipboard()
    }

    override suspend fun cleanUp() {
        saveClipboard()
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

                val context = LocalContext.current

                val clipboardHistory = useDataStore(ClipboardHistoryEnabled, blocking = true)
                if(!clipboardHistory.value) return

                if(unlocked && !clipboardHistoryManager.clipboardSaveFailure.value) {
                    IconButton(onClick = {
                        val numUnpinnedItems =
                            clipboardHistoryManager.clipboardHistory.count { !it.pinned }
                        if (clipboardHistoryManager.clipboardHistory.size == 0) {
                            manager.requestDialog(
                                context.getString(R.string.action_clipboard_manager_disable_text),
                                listOf(
                                    DialogRequestItem(context.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                                    DialogRequestItem(context.getString(R.string.action_clipboard_manager_disable_button)) {
                                        clipboardHistory.setValue(false)
                                    },
                                ),
                                {}
                            )
                        } else if (numUnpinnedItems == 0) {
                            manager.requestDialog(
                                context.getString(R.string.action_clipboard_manager_unpin_all_items_text),
                                listOf(
                                    DialogRequestItem(context.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                                    DialogRequestItem(context.getString(R.string.action_clipboard_manager_unpin_all_items_button)) {
                                        clipboardHistoryManager.clipboardHistory.toList().forEach {
                                            if (it.pinned) {
                                                clipboardHistoryManager.onPin(it)
                                            }
                                        }
                                    },
                                ),
                                {}
                            )
                        } else {
                            manager.requestDialog(
                                context.getString(R.string.action_clipboard_manager_clear_unpinned_items_text),
                                listOf(
                                    DialogRequestItem(context.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                                    DialogRequestItem(context.getString(R.string.action_clipboard_manager_clear_unpinned_items_button)) {
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
                val clipboardHistory = useDataStore(ClipboardHistoryEnabled, blocking = true)
                if(!unlocked) {
                    ScrollableList {
                        PaymentSurface(isPrimary = true) {
                            PaymentSurfaceHeading(title = stringResource(R.string.action_clipboard_manager_error_device_locked_title))

                            ParagraphText(stringResource(R.string.action_clipboard_manager_error_device_locked_text))
                        }
                    }
                } else if(clipboardHistoryManager.clipboardSaveFailure.value) {
                    ScrollableList {
                        PaymentSurface(isPrimary = true) {
                            PaymentSurfaceHeading(title = stringResource(R.string.action_clipboard_manager_error_general_title))
                            ParagraphText(
                                stringResource(
                                    R.string.action_clipboard_manager_error_general_text,
                                    clipboardHistoryManager.clipboardLoadFailureReason
                                ))
                            Button(onClick = {
                                manager.requestDialog(
                                    context.getString(R.string.action_clipboard_manager_delete_corrupted_clipboard_text),
                                    listOf(
                                        DialogRequestItem(context.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                                        DialogRequestItem(context.getString(R.string.action_clipboard_manager_delete_corrupted_clipboard_button)) {
                                            clipboardHistoryManager.clipboardSaveFailure.value = false
                                            clipboardHistory.setValue(false)
                                            clipboardHistoryManager.deleteClipboard()
                                        },
                                    ),
                                    {}
                                )

                            }, modifier = Modifier
                                .fillMaxWidth()) {
                                Text(context.getString(R.string.action_clipboard_manager_delete_corrupted_clipboard_button))
                            }

                            Button(onClick = {
                                manager.activateAction(BugViewerAction)
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.action_clipboard_manager_inspect_error_via_bugs_action))
                            }
                        }
                    }
                } else if(!clipboardHistory.value) {
                    ScrollableList {
                        PaymentSurface(isPrimary = true) {
                            PaymentSurfaceHeading(title = stringResource(R.string.action_clipboard_manager_error_clipboard_history_disabled_title))
                            ParagraphText(stringResource(R.string.action_clipboard_manager_error_clipboard_history_disabled_text))
                            Button(onClick = {
                                clipboardHistory.setValue(true)
                            }, modifier = Modifier
                                .fillMaxWidth()) {
                                Text(stringResource(R.string.action_clipboard_manager_enable_clipboard_history_button))
                            }
                        }
                    }
                } else {
                    LazyVerticalStaggeredGrid(
                        modifier = Modifier.fillMaxWidth(),
                        columns = StaggeredGridCells.Adaptive(140.dp),
                        verticalItemSpacing = 4.dp,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(clipboardHistoryManager.clipboardHistory.size, key = { r_i ->
                            val i = clipboardHistoryManager.clipboardHistory.size - r_i - 1
                            val entry = clipboardHistoryManager.clipboardHistory[i]

                            entry.text?.let {
                                if(it.length > 512) {
                                    // Compose really doesn't like extremely long keys, so
                                    // to avoid crashing we just provide a hash
                                    it.toFNV1aHash()
                                } else {
                                    it
                                }
                            } ?: i
                            i
                        }) { r_i ->
                            val i = clipboardHistoryManager.clipboardHistory.size - r_i - 1
                            val entry = clipboardHistoryManager.clipboardHistory[i]
                            ClipboardEntryView(
                                modifier = Modifier.animateItemPlacement(),
                                clipboardEntry = entry, onPaste = {
                                    if (it.uri != null) {
                                        manager.typeUri(it.uri, it.mimeTypes)
                                    } else if (it.text != null) {
                                        manager.typeText(it.text)
                                    }
                                    clipboardHistoryManager.onPaste(it)
                                    manager.performHapticAndAudioFeedback(Constants.CODE_OUTPUT_TEXT, view)
                                }, onRemove = {
                                    clipboardHistoryManager.onRemove(it)
                                    manager.performHapticAndAudioFeedback(Constants.CODE_TAB, view)
                                }, onPin = {
                                    clipboardHistoryManager.onPin(it)
                                    manager.performHapticAndAudioFeedback(Constants.CODE_TAB, view)
                                })
                        }
                    }
                }
            }
        }
    }
)