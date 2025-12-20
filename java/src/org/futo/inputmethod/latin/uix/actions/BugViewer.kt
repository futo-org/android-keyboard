package org.futo.inputmethod.latin.uix.actions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.updates.openURI


data class BugInfo(val name: String, val details: String)

public object BugViewerState {
    val bugs = mutableListOf<BugInfo>()

    fun pushBug(bug: BugInfo) {
        Log.e("BugViewerState", "Bug pushed: $bug")
        bugs.add(0, bug)
    }

    fun pushException(name: String, exception: Exception) {
        pushBug(BugInfo(name, """
Cause: ${exception.message}

Stack trace: ${exception.stackTrace.map { it.toString() }}
"""))
    }

    var isBugViewerPendingOpen = false
        private set

    fun triggerOpen() {
        isBugViewerPendingOpen = true
    }

    fun clearPendingOpen() {
        isBugViewerPendingOpen = false
    }

    fun clearBugs() {
        bugs.clear()
    }
}

private val Throwable.rootCause: Throwable?
    get() = generateSequence(this) { it.cause }.lastOrNull()?.takeIf { it !== this }

var CanThrowIfDebug = true

val IsDebug = BuildConfig.DEBUG || (BuildConfig.FLAVOR == "unstable" && CanThrowIfDebug)
fun throwIfDebug(ex: Exception) {
    if(IsDebug) {
        throw ex
    } else {
        val sw = java.io.StringWriter()
        ex.printStackTrace(java.io.PrintWriter(sw))

        BugViewerState.pushBug(
            BugInfo(
                name = ex.javaClass.simpleName,
                details = buildString {
                    appendLine("Message: ${ex.message ?: "<no message>"}")
                    appendLine()
                    appendLine("Stack-trace:")
                    append(sw.toString().trim())
                    ex.rootCause?.let { root ->
                        appendLine()
                        appendLine()
                        appendLine("Root cause: ${root.javaClass.simpleName}")
                        appendLine("Root message: ${root.message ?: "<no message>"}")
                    }
                }
            )
        )
    }
}

val BugViewerAction = Action(
    icon = R.drawable.code,
    name = R.string.action_bug_viewer_title,
    simplePressImpl = null,
    canShowKeyboard = true,
    windowImpl = { manager, _ ->
        val latinIme = manager.getLatinIMEForDebug()
        object : ActionWindow() {
            @Composable
            override fun windowName(): String {
                return stringResource(R.string.action_bug_viewer_title)
            }

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                ScrollableList {
                    if(BugViewerState.bugs.isEmpty()) {
                        Text("No errors have been reported yet", style = DebugLabel)
                    }

                    BugViewerState.bugs.forEach {
                        val name = "Bug in ${it.name} (${BuildConfig.VERSION_NAME})"
                        Text(name, style = DebugTitle)
                        Row {
                            TextButton(onClick = {
                                val clipboardManager = manager.getContext()
                                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip =
                                    ClipData.newPlainText("label", "$name\n\n${it.details}")
                                clipboardManager.setPrimaryClip(clip)
                            }) {
                                Text("Copy to clipboard")
                            }

                            TextButton(onClick = {
                                manager.getContext().openURI("mailto:keyboard@futo.org", newTask = true)
                            }) {
                                Text("Email us (include the copy)")
                            }
                        }
                        Text(it.details, style = DebugLabel)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    },
    shownInEditor = false
)