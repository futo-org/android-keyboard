package org.futo.inputmethod.latin

import android.util.Log
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.TextAttribute
import org.futo.inputmethod.latin.uix.actions.throwIfDebug

interface IBufferedInputConnection {
    fun send()
}

/*
 * Some apps misbehave when they receive multiple calls back to back
 * e.g. delete(4), commit("ello"), commit(" ")
 *
 * This fixes it by queueing all operations and sending them only at the end. A downside is that the
 * commands will not have immediate effect (e.g. getSurroundingText will not show updates until
 * send() is called), so send() should be called at the end to ensure consistent state. A workaround
 * is offered for getText[Before/After]Cursor
 */
class InputConnectionWithBufferingWrapper(target: InputConnection) : InputConnectionWrapper(target, true), IBufferedInputConnection {
    sealed class InputCommand {
        data class Commit(val text: String) : InputCommand()
        data class Delete(val before: Int, val after: Int) : InputCommand()
        data class SetComposingRegion(val start: Int, val end: Int) : InputCommand()
    }

    val commandQueue = mutableListOf<InputCommand>()

    private fun merge(commands: List<InputCommand>): List<InputCommand> {
        var text = ""
        var deletedAmount = 0
        var deletedAfterAmount = 0

        for (cmd in commands) {
            when (cmd) {
                is InputCommand.Commit -> {
                    text += cmd.text
                }

                is InputCommand.Delete -> {
                    val keep = text.length - cmd.before
                    if(keep > 0) {
                        text = text.substring(0, keep)
                    } else {
                        text = ""
                        deletedAmount -= keep
                    }

                    deletedAfterAmount += cmd.after
                }

                is InputCommand.SetComposingRegion -> {}
            }
        }

        return buildList {
            if(deletedAmount > 0 && deletedAfterAmount > 0 && text.isEmpty()) {
                add(InputCommand.Delete(deletedAmount, deletedAfterAmount))
            } else {
                if (deletedAmount > 0) add(InputCommand.Delete(deletedAmount, 0))
                if (text.isNotEmpty()) add(InputCommand.Commit(text))
                if (deletedAfterAmount > 0) add(InputCommand.Delete(0, deletedAfterAmount))
            }

            commands.filterIsInstance<InputCommand.SetComposingRegion>().lastOrNull()?.let {
                add(it)
            }
        }
    }

    private fun applyBefore(beforeTxt: String): String {
        var result = beforeTxt
        commandQueue.forEach { cmd ->
            when(cmd) {
                is InputCommand.Commit -> result += cmd.text
                is InputCommand.Delete -> {
                    result = result.substring(0, result.length - cmd.before)
                }
                else -> {}
            }
        }
        return result
    }

    private fun applyAfter(afterTxt: String): String {
        var result = afterTxt
        commandQueue.forEach { cmd ->
            when(cmd) {
                is InputCommand.Commit -> { }
                is InputCommand.Delete -> {
                    result = result.substring(cmd.after)
                }
                else -> {}
            }
        }
        return result
    }

    private fun extraHeadroom() = 16

    override fun commitText(
        text: CharSequence,
        newCursorPosition: Int,
        textAttribute: TextAttribute?
    ): Boolean {
        if(BuildConfig.DEBUG) Log.d("BufferedInputConnection", "commit (" + text + ")")
        assert(newCursorPosition == 1)
        commandQueue.add(InputCommand.Commit(text.toString()))
        return true
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        assert(newCursorPosition == 1)
        if(text == null) return true
        if(BuildConfig.DEBUG) Log.d("BufferedInputConnection", "commit (" + text + ")")
        commandQueue.add(InputCommand.Commit(text.toString()))
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        commandQueue.add(InputCommand.Delete(beforeLength, afterLength))
        return true
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        throwIfDebug(UnsupportedOperationException("Please use deleteSurroundingText instead"))
        return false
    }

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
        return applyBefore(super.getTextBeforeCursor(n + extraHeadroom(), flags)?.toString() ?: return null).takeLast(n)
    }

    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? {
        return applyAfter(super.getTextAfterCursor(n + extraHeadroom(), flags)?.toString() ?: return null).take(n)
    }

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        commandQueue.add(InputCommand.SetComposingRegion(start, end))
        return true
    }

    override fun setComposingRegion(start: Int, end: Int, textAttribute: TextAttribute?): Boolean {
        return setComposingRegion(start, end)
    }

    override fun send() {
        val mergedList = merge(commandQueue)
        if(BuildConfig.DEBUG) Log.d("BufferedInputConnection", "Command queue: $commandQueue, merged: $mergedList")
        commandQueue.clear()

        mergedList.forEach { when(it) {
            is InputCommand.Commit -> { super.commitText(it.text, 1) }
            is InputCommand.Delete -> { super.deleteSurroundingText(it.before, it.after) }
            is InputCommand.SetComposingRegion -> { super.setComposingRegion(it.start, it.end) }
        } }
    }
}