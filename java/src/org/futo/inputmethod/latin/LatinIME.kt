package org.futo.inputmethod.latin

import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class LatinIME : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val latinIMELegacy = LatinIMELegacy(this as InputMethodService)

    private val mSavedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = mSavedStateRegistryController.savedStateRegistry

    private val mLifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle
        get() = mLifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore
        get() = store

    private fun handleLifecycleEvent(event: Lifecycle.Event) =
        mLifecycleRegistry.handleLifecycleEvent(event)

    private val inputMethodManager: InputMethodManager
        get() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    override fun onCreate() {
        super.onCreate()
        mSavedStateRegistryController.performRestore(null)
        handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        latinIMELegacy.onCreate()
    }

    private fun setOwners() {
        val decorView = window.window?.decorView
        if (decorView?.findViewTreeLifecycleOwner() == null) {
            decorView?.setViewTreeLifecycleOwner(this)
        }
        if (decorView?.findViewTreeViewModelStoreOwner() == null) {
            decorView?.setViewTreeViewModelStoreOwner(this)
        }
        if (decorView?.findViewTreeSavedStateRegistryOwner() == null) {
            decorView?.setViewTreeSavedStateRegistryOwner(this)
        }
    }


    private var composeView: ComposeView? = null

    override fun onDestroy() {
        latinIMELegacy.onDestroy()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        latinIMELegacy.onConfigurationChanged(newConfig)
        super.onConfigurationChanged(newConfig)
    }

    override fun onInitializeInterface() {
        latinIMELegacy.onInitializeInterface()
    }

    private var legacyInputView: View? = null
    private var touchableHeight: Int = 0
    override fun onCreateInputView(): View {
        legacyInputView = latinIMELegacy.onCreateInputView()
        composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setParentCompositionContext(null)

            this@LatinIME.setOwners()
        }

        setContent()

        return composeView!!
    }

    private fun setContent() {
        composeView?.setContent {
            Column {
                Spacer(modifier = Modifier.weight(1.0f))
                Surface(modifier = Modifier.onSizeChanged {
                    touchableHeight = it.height
                }, color = MaterialTheme.colorScheme.surface) {
                    Column {
                        key(legacyInputView) {
                            AndroidView(factory = {
                                legacyInputView!!
                            }, update = { })
                        }
                    }
                }
            }
        }
    }

    // necessary for when KeyboardSwitcher updates the theme
    fun updateLegacyView(newView: View) {
        legacyInputView = newView
        setContent()

        latinIMELegacy.setComposeInputView(composeView!!)
        latinIMELegacy.setInputView(legacyInputView)
    }

    override fun setInputView(view: View?) {
        super.setInputView(view)
        latinIMELegacy.setComposeInputView(composeView!!)
        latinIMELegacy.setInputView(legacyInputView)
    }

    override fun setCandidatesView(view: View?) {
        return latinIMELegacy.setCandidatesView(view)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        latinIMELegacy.onStartInput(attribute, restarting)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        latinIMELegacy.onStartInputView(info, restarting)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        latinIMELegacy.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        latinIMELegacy.onFinishInput()
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        latinIMELegacy.onCurrentInputMethodSubtypeChanged(newSubtype)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        latinIMELegacy.onWindowShown()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        latinIMELegacy.onWindowHidden()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)

        latinIMELegacy.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    override fun onExtractedTextClicked() {
        latinIMELegacy.onExtractedTextClicked()
        super.onExtractedTextClicked()
    }

    override fun onExtractedCursorMovement(dx: Int, dy: Int) {
        latinIMELegacy.onExtractedCursorMovement(dx, dy)
        super.onExtractedCursorMovement(dx, dy)
    }

    override fun hideWindow() {
        latinIMELegacy.hideWindow()
        super.hideWindow()
    }

    override fun onDisplayCompletions(completions: Array<out CompletionInfo>?) {
        latinIMELegacy.onDisplayCompletions(completions)
    }

    override fun onComputeInsets(outInsets: Insets?) {
        // This method may be called before {@link #setInputView(View)}.
        if (legacyInputView == null) {
            return
        }

        val inputHeight: Int = composeView!!.height
        if (latinIMELegacy.isImeSuppressedByHardwareKeyboard && !legacyInputView!!.isShown) {
            // If there is a hardware keyboard and a visible software keyboard view has been hidden,
            // no visual element will be shown on the screen.
            latinIMELegacy.setInsets(outInsets!!.apply {
                contentTopInsets = inputHeight
                visibleTopInsets = inputHeight
            })
            return
        }

        val visibleTopY = inputHeight - touchableHeight

        val touchLeft = 0
        val touchTop = visibleTopY
        val touchRight = legacyInputView!!.width
        val touchBottom = inputHeight

        latinIMELegacy.setInsets(outInsets!!.apply {
            touchableInsets = Insets.TOUCHABLE_INSETS_REGION;
            touchableRegion.set(touchLeft, touchTop, touchRight, touchBottom);
            contentTopInsets = visibleTopY
            visibleTopInsets = visibleTopY
        })
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        return latinIMELegacy.onShowInputRequested(flags, configChange) || super.onShowInputRequested(flags, configChange)
    }

    override fun onEvaluateInputViewShown(): Boolean {
        return latinIMELegacy.onEvaluateInputViewShown() || super.onEvaluateInputViewShown()
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return latinIMELegacy.onEvaluateFullscreenMode(super.onEvaluateFullscreenMode())
    }

    override fun updateFullscreenMode() {
        super.updateFullscreenMode()
        latinIMELegacy.updateFullscreenMode()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return latinIMELegacy.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return latinIMELegacy.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)
    }
}