package org.futo.inputmethod.latin

import android.os.Build
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

object InputConnectionUtil {
    /**
     * Tries to extract the selection from getSurroundingText or getExtractedText.
     *
     * The return value is Pair(selectionStart, selectionEnd). If it's unknown, will return -1 for
     * both.
     */
    fun extractSelection(ic: InputConnection, minValue: Int = 0): Pair<Int, Int> {
        if(!SupportsNonComposing) return -1 to -1

        var selStart = -1
        var selEnd = -1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val surroundingText = ic.getSurroundingText(1, 1, 0)
            if(surroundingText != null && surroundingText.offset != -1) {
                selStart = surroundingText.offset + surroundingText.selectionStart
                selEnd = surroundingText.offset + surroundingText.selectionEnd

                if(selStart < minValue || selStart < 0) selStart = -1
                if(selEnd   < minValue || selEnd   < 0) selEnd   = -1
            }
        }

        if(selStart == -1 || selEnd == -1) {
            val extracted = ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 1 }, 0)
            if(extracted != null) {
                selStart = extracted.selectionStart + extracted.startOffset
                selStart = extracted.selectionEnd   + extracted.startOffset

                if(selStart < minValue || selStart < 0) selStart = -1
                if(selEnd   < minValue || selEnd   < 0) selEnd   = -1
            }
        }

        if(selStart == -1 || selEnd == -1) {
            return -1 to -1
        } else {
            return selStart to selEnd
        }
    }
}