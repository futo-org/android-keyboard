package org.futo.inputmethod.latin.uix.settings.pages.buggyeditors

data class BuggyEditorConfiguration(
    // does not set composing region,
    // setComposingText("xyz"), setComposingText("xyzw") produces xyzxyzw
    val setComposingTextDoesNotCompose: Boolean = false,

    // after this amount of ms, resets composing
    val composingTextResetsAfter: Long? = null,

    // like setComposingTextDoesNotCompose but not even setComposingRegion works
    val noComposing: Boolean = false,

    // after each symbol change, cursor gets moved to the end of text then back to where it was
    // composing region remains same
    val cursorMovesToEndAndBack: Boolean = false,

    // does not send cursor updates when typing
    val doesNotSendCursorUpdates: Boolean = false,

    // cursor updates are sent but the composing span is -1, even if composing is handled properly otherwise
    val alwaysSendsNoComposing: Boolean = false,

    // all cursor updates are delayed
    val delayCursorUpdates: Long? = null
)