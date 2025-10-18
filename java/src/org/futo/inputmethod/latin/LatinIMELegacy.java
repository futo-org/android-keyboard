/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.futo.inputmethod.latin;

import static org.futo.inputmethod.latin.common.Constants.ImeOption.FORCE_ASCII;
import static org.futo.inputmethod.latin.common.Constants.ImeOption.NO_MICROPHONE;
import static org.futo.inputmethod.latin.common.Constants.ImeOption.NO_MICROPHONE_COMPAT;

import android.Manifest.permission;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.Debug;
import android.os.IBinder;
import android.text.InputType;
import android.util.Log;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.util.SparseArray;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.futo.inputmethod.accessibility.AccessibilityUtils;
import org.futo.inputmethod.annotations.UsedForTesting;
import org.futo.inputmethod.compat.ViewOutlineProviderCompatUtils;
import org.futo.inputmethod.compat.ViewOutlineProviderCompatUtils.InsetsUpdater;
import org.futo.inputmethod.engine.IMEInterface;
import org.futo.inputmethod.engine.IMEManager;
import org.futo.inputmethod.event.Event;
import org.futo.inputmethod.event.HardwareEventDecoder;
import org.futo.inputmethod.event.HardwareKeyboardEventDecoder;
import org.futo.inputmethod.event.InputTransaction;
import org.futo.inputmethod.keyboard.Keyboard;
import org.futo.inputmethod.keyboard.KeyboardActionListener;
import org.futo.inputmethod.keyboard.KeyboardSwitcher;
import org.futo.inputmethod.keyboard.MainKeyboardView;
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import org.futo.inputmethod.latin.common.Constants;
import org.futo.inputmethod.latin.common.CoordinateUtils;
import org.futo.inputmethod.latin.common.InputPointers;
import org.futo.inputmethod.latin.define.DebugFlags;
import org.futo.inputmethod.latin.define.ProductionFlags;
import org.futo.inputmethod.latin.permissions.PermissionsManager;
import org.futo.inputmethod.latin.personalization.PersonalizationHelper;
import org.futo.inputmethod.latin.settings.Settings;
import org.futo.inputmethod.latin.settings.SettingsValues;
import org.futo.inputmethod.latin.suggestions.SuggestionStripViewAccessor;
import org.futo.inputmethod.latin.suggestions.SuggestionStripViewListener;
import org.futo.inputmethod.latin.utils.ApplicationUtils;
import org.futo.inputmethod.latin.utils.JniUtils;
import org.futo.inputmethod.latin.utils.StatsUtils;
import org.futo.inputmethod.latin.utils.ViewLayoutUtils;
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2;
import org.jetbrains.annotations.NotNull;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
public class LatinIMELegacy implements KeyboardActionListener,
        SuggestionStripViewListener, SuggestionStripViewAccessor,
        PermissionsManager.PermissionsResultCallback {

    public interface SuggestionStripController {
        public void updateVisibility(boolean shouldShowSuggestionsStrip, boolean fullscreenMode);
        public void setSuggestions(SuggestedWords suggestedWords, boolean rtlSubtype, boolean useExpandableUi);
    }
    
    private final InputMethodService mInputMethodService;
    private final IMEManager mImeManager;

    static final String TAG = LatinIMELegacy.class.getSimpleName();
    private static final boolean TRACE = false;

    private static final int PERIOD_FOR_AUDIO_AND_HAPTIC_FEEDBACK_IN_KEY_REPEAT = 2;

    /**
     * The name of the scheme used by the Package Manager to warn of a new package installation,
     * replacement or removal.
     */
    private static final String SCHEME_PACKAGE = "package";

    public final Settings mSettings;
    private Locale mLocale;
    // We expect to have only one decoder in almost all cases, hence the default capacity of 1.
    // If it turns out we need several, it will get grown seamlessly.
    final SparseArray<HardwareEventDecoder> mHardwareEventDecoders = new SparseArray<>(1);

    // TODO: Move these {@link View}s to {@link KeyboardSwitcher}.
    private View mInputView;
    private View mComposeInputView;
    private InsetsUpdater mInsetsUpdater;

    private RichInputMethodManager mRichImm;
    public final KeyboardSwitcher mKeyboardSwitcher;
    private EmojiAltPhysicalKeyDetector mEmojiAltPhysicalKeyDetector;

    // Used for re-initialize keyboard layout after onConfigurationChange.
    @Nullable private Context mDisplayContext;

    private final BroadcastReceiver mDictionaryDumpBroadcastReceiver =
            new DictionaryDumpBroadcastReceiver(this);

    private final boolean mIsHardwareAcceleratedDrawingEnabled;

    // Loading the native library eagerly to avoid unexpected UnsatisfiedLinkError at the initial
    // JNI call as much as possible.
    static {
        JniUtils.loadNativeLibrary();
    }

    public LatinIMELegacy(InputMethodService inputMethodService, SuggestionStripController suggestionStripController) {
        super();
        mInputMethodService = inputMethodService;
        mSettings = Settings.getInstance();
        mKeyboardSwitcher = KeyboardSwitcher.getInstance();
        mIsHardwareAcceleratedDrawingEnabled = true;
        Log.i(TAG, "Hardware accelerated drawing: " + mIsHardwareAcceleratedDrawingEnabled);

        mImeManager = ((LatinIME)inputMethodService).getImeManager();
    }

    public void onCreate() {
        Settings.init(mInputMethodService);
        RichInputMethodManager.init(mInputMethodService);
        mRichImm = RichInputMethodManager.getInstance();
        AudioAndHapticFeedbackManager.init(mInputMethodService);
        AccessibilityUtils.init(mInputMethodService);
        final WindowManager wm = mInputMethodService.getSystemService(WindowManager.class);
        mDisplayContext = getDisplayContext();
        KeyboardSwitcher.init(this);

        // TODO: Resolve mutual dependencies of {@link #loadSettings()} and
        // {@link #resetDictionaryFacilitatorIfNecessary()}.
        loadSettings();

        // Register to receive ringer mode change.
        final IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        ContextCompat.registerReceiver(mInputMethodService, mRingerModeChangeReceiver, filter, ContextCompat.RECEIVER_EXPORTED);

        final IntentFilter dictDumpFilter = new IntentFilter();
        dictDumpFilter.addAction(DictionaryDumpBroadcastReceiver.DICTIONARY_DUMP_INTENT_ACTION);
        ContextCompat.registerReceiver(mInputMethodService, mDictionaryDumpBroadcastReceiver, dictDumpFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        StatsUtils.onCreate(mSettings.getCurrent(), mRichImm);
    }

    public void loadSettings() {
        mLocale = mRichImm.getCurrentSubtypeLocale();
        final EditorInfo editorInfo = mInputMethodService.getCurrentInputEditorInfo();
        final InputAttributes inputAttributes = new InputAttributes(
                editorInfo, mInputMethodService.isFullscreenMode(), mInputMethodService.getPackageName());
        mSettings.loadSettings(mInputMethodService, mLocale, inputAttributes);
        final SettingsValues currentSettingsValues = mSettings.getCurrent();
        AudioAndHapticFeedbackManager.getInstance().onSettingsChanged(currentSettingsValues);
        refreshPersonalizationDictionarySession(currentSettingsValues);
    }

    private boolean mLastPersonalizedDicts = true;
    private void refreshPersonalizationDictionarySession(
            final SettingsValues currentSettingsValues) {
        if (!currentSettingsValues.mUsePersonalizedDicts && mLastPersonalizedDicts) {
            // Remove user history dictionaries.
            PersonalizationHelper.removeAllUserHistoryDictionaries(mInputMethodService);
            mImeManager.clearUserHistoryDictionaries();
        }

        mLastPersonalizedDicts = currentSettingsValues.mUsePersonalizedDicts;
    }

    public void onDestroy() {
        mSettings.onDestroy();
        mInputMethodService.unregisterReceiver(mRingerModeChangeReceiver);
        mInputMethodService.unregisterReceiver(mDictionaryDumpBroadcastReceiver);
    }

    @UsedForTesting
    public void recycle() {
        mInputMethodService.unregisterReceiver(mDictionaryDumpBroadcastReceiver);
        mInputMethodService.unregisterReceiver(mRingerModeChangeReceiver);
        mImeManager.recycle();
    }

    public boolean isImeSuppressedByHardwareKeyboard() {
        if(true) return false; // TODO: This function returning true causes some initialization issues

        final KeyboardSwitcher switcher = KeyboardSwitcher.getInstance();
        return !onEvaluateInputViewShown() && switcher.isImeSuppressedByHardwareKeyboard(
                mSettings.getCurrent(), switcher.getKeyboardSwitchState());
    }

    public void onConfigurationChanged(final Configuration conf) {
        SettingsValues settingsValues = mSettings.getCurrent();
        if (settingsValues.mDisplayOrientation != conf.orientation) {
            // Originally debounce mHandler.startOrientationChanging();
            if(mInputMethodService.isInputViewShown()) mKeyboardSwitcher.saveKeyboardState();
        }
        if (settingsValues.mHasHardwareKeyboard != Settings.readHasHardwareKeyboard(conf)) {
            // If the state of having a hardware keyboard changed, then we want to reload the
            // settings to adjust for that.
            // TODO: we should probably do this unconditionally here, rather than only when we
            // have a change in hardware keyboard configuration.
            loadSettings();
        }
    }

    public void onInitializeInterface() {
        mDisplayContext = getDisplayContext();
        mKeyboardSwitcher.updateKeyboardTheme(mDisplayContext);
    }

    /**
     * Returns the context object whose resources are adjusted to match the metrics of the display.
     *
     * Note that before {@link android.os.Build.VERSION_CODES#KITKAT}, there is no way to support
     * multi-display scenarios, so the context object will just return the IME context itself.
     *
     * With initiating multi-display APIs from {@link android.os.Build.VERSION_CODES#KITKAT}, the
     * context object has to return with re-creating the display context according the metrics
     * of the display in runtime.
     *
     * Starts from {@link android.os.Build.VERSION_CODES#S_V2}, the returning context object has
     * became to IME context self since it ends up capable of updating its resources internally.
     *
     * @see android.content.Context#createDisplayContext(Display)
     */
    private @NonNull Context getDisplayContext() {
        // TODO: We need to pass LatinIME for theming
        /*
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            // createDisplayContext is not available.
            return mInputMethodService;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            // IME context sources is now managed by WindowProviderService from Android 12L.
            return mInputMethodService;
        }
        // An issue in Q that non-activity components Resources / DisplayMetrics in
        // Context doesn't well updated when the IME window moving to external display.
        // Currently we do a workaround is to create new display context directly and re-init
        // keyboard layout with this context.
        final WindowManager wm = (WindowManager) mInputMethodService.getSystemService(Context.WINDOW_SERVICE);
        return mInputMethodService.createDisplayContext(wm.getDefaultDisplay());
        */
        return mInputMethodService;
    }

    public View onCreateInputView() {
        StatsUtils.onCreateInputView();
        assert mDisplayContext != null;
        return mKeyboardSwitcher.onCreateInputView(mDisplayContext,
                mIsHardwareAcceleratedDrawingEnabled);
    }

    public void updateTheme() {
        mKeyboardSwitcher.queueThemeSwitch();
        mKeyboardSwitcher.updateKeyboardTheme(mDisplayContext);
    }


    public void setComposeInputView(final View view) {
        mComposeInputView = view;
        mInsetsUpdater = ViewOutlineProviderCompatUtils.setInsetsOutlineProvider(view);
        updateSoftInputWindowLayoutParameters();
    }

    public void setInputView(final View view) {
        mInputView = view;
    }

    public void setCandidatesView(final View view) {
        // To ensure that CandidatesView will never be set.
    }

    public void onStartInput(final EditorInfo editorInfo, final boolean restarting) {
        onStartInputInternal(editorInfo, restarting);
    }

    public void onStartInputView(final EditorInfo editorInfo, final boolean restarting) {
        onStartInputViewInternal(editorInfo, restarting);
    }

    public void onFinishInputView(final boolean finishingInput) {
        StatsUtils.onFinishInputView();
        onFinishInputViewInternal(finishingInput);
    }

    public void onFinishInput() {
        onFinishInputInternal();
    }

    public void onCurrentInputMethodSubtypeChanged(final InputMethodSubtype subtype) {
        // Note that the calling sequence of onCreate() and onCurrentInputMethodSubtypeChanged()
        // is not guaranteed. It may even be called at the same time on a different thread.
        mImeManager.onFinishInput();
        InputMethodSubtype oldSubtype = mRichImm.getCurrentSubtype().getRawSubtype();
        StatsUtils.onSubtypeChanged(oldSubtype, subtype);
        mRichImm.onSubtypeChanged(subtype);
        loadSettings();
        mImeManager.onStartInput();
        loadKeyboard();
    }

    void onStartInputInternal(final EditorInfo editorInfo, final boolean restarting) {

    }

    public void updateMainKeyboardViewSettings() {
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        SettingsValues currentSettingsValues = mSettings.getCurrent();

        mainKeyboardView.setImeAllowsGestureInput(
                mImeManager.getActiveIME(currentSettingsValues).isGestureHandlingAvailable());
        mainKeyboardView.setKeyPreviewPopupEnabled(currentSettingsValues.mKeyPreviewPopupOn,
                currentSettingsValues.mKeyPreviewPopupDismissDelay);
        mainKeyboardView.setSlidingKeyInputPreviewEnabled(
                currentSettingsValues.mSlidingKeyInputPreviewEnabled);
        mainKeyboardView.setGestureHandlingEnabledByUser(
                currentSettingsValues.mGestureInputEnabled,
                currentSettingsValues.mGestureTrailEnabled,
                currentSettingsValues.mGestureFloatingPreviewTextEnabled);
    }

    @SuppressWarnings("deprecation")
    void onStartInputViewInternal(final EditorInfo editorInfo, final boolean restarting) {
        mRichImm.refreshSubtypeCaches();
        final KeyboardSwitcher switcher = mKeyboardSwitcher;
        switcher.updateKeyboardTheme(mDisplayContext);
        final MainKeyboardView mainKeyboardView = switcher.getMainKeyboardView();
        // If we are starting input in a different text field from before, we'll have to reload
        // settings, so currentSettingsValues can't be final.
        SettingsValues currentSettingsValues = mSettings.getCurrent();

        if (editorInfo == null) {
            Log.e(TAG, "Null EditorInfo in onStartInputView()");
            if (DebugFlags.DEBUG_ENABLED) {
                throw new NullPointerException("Null EditorInfo in onStartInputView()");
            }
            return;
        }
        if (DebugFlags.DEBUG_ENABLED) {
            Log.d(TAG, "onStartInputView: editorInfo:"
                    + String.format("inputType=0x%08x imeOptions=0x%08x",
                            editorInfo.inputType, editorInfo.imeOptions));
            Log.d(TAG, "All caps = "
                    + ((editorInfo.inputType & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0)
                    + ", sentence caps = "
                    + ((editorInfo.inputType & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0)
                    + ", word caps = "
                    + ((editorInfo.inputType & InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0));
        }
        Log.i(TAG, "Starting input. Cursor position = "
                + editorInfo.initialSelStart + "," + editorInfo.initialSelEnd);
        // TODO: Consolidate these checks with {@link InputAttributes}.
        if (InputAttributes.inPrivateImeOptions(null, NO_MICROPHONE_COMPAT, editorInfo)) {
            Log.w(TAG, "Deprecated private IME option specified: " + editorInfo.privateImeOptions);
            Log.w(TAG, "Use " + mInputMethodService.getPackageName() + "." + NO_MICROPHONE + " instead");
        }
        if (InputAttributes.inPrivateImeOptions(mInputMethodService.getPackageName(), FORCE_ASCII, editorInfo)) {
            Log.w(TAG, "Deprecated private IME option specified: " + editorInfo.privateImeOptions);
            Log.w(TAG, "Use EditorInfo.IME_FLAG_FORCE_ASCII flag instead");
        }

        // In landscape mode, this method gets called without the input view being created.
        if (mainKeyboardView == null) {
            return;
        }

        // Forward this event to the accessibility utilities, if enabled.
        final AccessibilityUtils accessUtils = AccessibilityUtils.getInstance();
        if (accessUtils.isTouchExplorationEnabled()) {
            accessUtils.onStartInputViewInternal(mainKeyboardView, editorInfo, restarting);
        }

        final boolean inputTypeChanged = !currentSettingsValues.isSameInputType(editorInfo);
        final boolean isDifferentTextField = !restarting || inputTypeChanged;

        // The EditorInfo might have a flag that affects fullscreen mode.
        // Note: This call should be done by InputMethodService?
        updateFullscreenMode();

        // ALERT: settings have not been reloaded and there is a chance they may be stale.
        // In the practice, if it is, we should have gotten onConfigurationChanged so it should
        // be fine, but this is horribly confusing and must be fixed AS SOON AS POSSIBLE.

        if (isDifferentTextField ||
                !currentSettingsValues.hasSameOrientation(mInputMethodService.getResources().getConfiguration())) {
            loadSettings();
        }
        if (isDifferentTextField) {
            mainKeyboardView.closing();
            currentSettingsValues = mSettings.getCurrent();

            switcher.loadKeyboard(editorInfo, currentSettingsValues, getCurrentAutoCapsState());
        } else if (restarting) {
            switcher.resetKeyboardStateToAlphabet(editorInfo, getCurrentAutoCapsState());
            // In apps like Talk, we come here when the text is sent and the field gets emptied and
            // we need to re-evaluate the shift state, but not the whole layout which would be
            // disruptive.
            // Space state must be updated before calling updateShiftState
            switcher.requestUpdatingShiftState(getCurrentAutoCapsState());
        }

        updateMainKeyboardViewSettings();

        if (TRACE) Debug.startMethodTracing("/data/trace/latinime");
    }

    public void onWindowShown() {
        setNavigationBarVisibility(mInputMethodService.isInputViewShown());
    }

    public void onWindowHidden() {
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (mainKeyboardView != null) {
            mainKeyboardView.closing();
        }
        setNavigationBarVisibility(false);
    }

    void onFinishInputInternal() {
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (mainKeyboardView != null) {
            mainKeyboardView.closing();
        }
    }

    void onFinishInputViewInternal(final boolean finishingInput) {

    }

    protected void deallocateMemory() {
        mKeyboardSwitcher.deallocateMemory();
    }

    public void onUpdateSelection(final int oldSelStart, final int oldSelEnd,
            final int newSelStart, final int newSelEnd,
            final int composingSpanStart, final int composingSpanEnd) {
        if (DebugFlags.DEBUG_ENABLED) {
            Log.i(TAG, "onUpdateSelection: oss=" + oldSelStart + ", ose=" + oldSelEnd
                    + ", nss=" + newSelStart + ", nse=" + newSelEnd
                    + ", cs=" + composingSpanStart + ", ce=" + composingSpanEnd);
        }

        // This call happens whether our view is displayed or not, but if it's not then we should
        // not attempt recorrection. This is true even with a hardware keyboard connected: if the
        // view is not displayed we have no means of showing suggestions anyway, and if it is then
        // we want to show suggestions anyway.
        final SettingsValues settingsValues = mSettings.getCurrent();
        if (mInputMethodService.isInputViewShown()) {
            mImeManager.onUpdateSelection(
                    oldSelStart, oldSelEnd,
                    newSelStart, newSelEnd,
                    composingSpanStart, composingSpanEnd
            );
        }
    }

    /**
     * This is called when the user has clicked on the extracted text view,
     * when running in fullscreen mode.  The default implementation hides
     * the suggestions view when this happens, but only if the extracted text
     * editor has a vertical scroll bar because its text doesn't fit.
     * Here we override the behavior due to the possibility that a re-correction could
     * cause the suggestions strip to disappear and re-appear.
     */
    public void onExtractedTextClicked() {
        if (mSettings.getCurrent().needsToLookupSuggestions()) {
            return;
        }
    }

    /**
     * This is called when the user has performed a cursor movement in the
     * extracted text view, when it is running in fullscreen mode.  The default
     * implementation hides the suggestions view when a vertical movement
     * happens, but only if the extracted text editor has a vertical scroll bar
     * because its text doesn't fit.
     * Here we override the behavior due to the possibility that a re-correction could
     * cause the suggestions strip to disappear and re-appear.
     */
    public void onExtractedCursorMovement(final int dx, final int dy) {
        if (mSettings.getCurrent().needsToLookupSuggestions()) {
            return;
        }
    }

    public void hideWindow() {
        mKeyboardSwitcher.onHideWindow();

        if (TRACE) Debug.stopMethodTracing();
    }

    public void setInsets(final InputMethodService.Insets insets) {
        mInsetsUpdater.setInsets(insets);
    }

    public boolean onShowInputRequested(final int flags, final boolean configChange) {
        if (isImeSuppressedByHardwareKeyboard()) {
            return true;
        }
        return false;
    }

    public boolean onEvaluateInputViewShown() {
        return false;
    }

    public void updateFullscreenMode() {
        updateSoftInputWindowLayoutParameters();
    }

    private void updateSoftInputWindowLayoutParameters() {
        // Override layout parameters to expand {@link SoftInputWindow} to the entire screen.
        // See {@link InputMethodService#setinputView(View)} and
        // {@link SoftInputWindow#updateWidthHeight(WindowManager.LayoutParams)}.
        final Window window = mInputMethodService.getWindow().getWindow();
        ViewLayoutUtils.updateLayoutHeightOf(window, LayoutParams.MATCH_PARENT);
        // This method may be called before {@link #setInputView(View)}.
        if (mComposeInputView != null) {
            // In non-fullscreen mode, {@link InputView} and its parent inputArea should expand to
            // the entire screen and be placed at the bottom of {@link SoftInputWindow}.
            // In fullscreen mode, these shouldn't expand to the entire screen and should be
            // coexistent with {@link #mExtractedArea} above.
            // See {@link InputMethodService#setInputView(View) and
            // com.android.internal.R.layout.input_method.xml.
            final int layoutHeight = mInputMethodService.isFullscreenMode()
                    ? LayoutParams.WRAP_CONTENT : LayoutParams.MATCH_PARENT;
            final View inputArea = window.findViewById(android.R.id.inputArea);
            ViewLayoutUtils.updateLayoutHeightOf(inputArea, layoutHeight);
            ViewLayoutUtils.updateLayoutGravityOf(inputArea, Gravity.BOTTOM);
            ViewLayoutUtils.updateLayoutHeightOf(mComposeInputView, layoutHeight);
        }
    }

    int getCurrentAutoCapsState() {
        return getActiveIME().getCurrentAutoCapsState();
    }

    /**
     * @param codePoints code points to get coordinates for.
     * @return x,y coordinates for this keyboard, as a flattened array.
     */
    public int[] getCoordinatesForCurrentKeyboard(final int[] codePoints) {
        final Keyboard keyboard = mKeyboardSwitcher.getKeyboard();
        if (null == keyboard) {
            return CoordinateUtils.newCoordinateArray(codePoints.length,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE);
        }
        return keyboard.getCoordinates(codePoints);
    }

    // Callback for the {@link SuggestionStripView}, to call when the important notice strip is
    // pressed.
    @Override
    public void showImportantNoticeContents() {
        PermissionsManager.get(mInputMethodService).requestPermissions(
                this /* PermissionsResultCallback */,
                null /* activity */, permission.READ_CONTACTS);
    }

    @Override
    public void onRequestPermissionsResult(boolean allGranted) {

    }

    @Override
    public boolean onCustomRequest(final int requestCode) {
        switch (requestCode) {
        case Constants.CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER:
            getLatinIME().getUixManager().showLanguageSwitcher();
            return true;
        }
        return false;
    }

    @Override
    public void onMovePointer(int steps) {
        mImeManager.getActiveIME(
                mSettings.getCurrent()
        ).onMovePointer(steps, false, null);
    }

    @Override
    public void onMoveDeletePointer(int steps) {
        mImeManager.getActiveIME(
                mSettings.getCurrent()
        ).onMoveDeletePointer(steps);
    }

    @Override
    public void onUpWithDeletePointerActive() {
        mImeManager.getActiveIME(
                mSettings.getCurrent()
        ).onUpWithDeletePointerActive();
    }

    @Override
    public void onUpWithPointerActive() {
        mImeManager.getActiveIME(
                mSettings.getCurrent()
        ).onUpWithPointerActive();
    }

    @Override
    public void onSwipeLanguage(int direction) {
        Subtypes.INSTANCE.switchToNextLanguage(mInputMethodService, direction);
    }

    @Override
    public void onMovingCursorLockEvent(boolean canMoveCursor) {
        if(canMoveCursor) {
            hapticAndAudioFeedback(Constants.CODE_UNSPECIFIED, 0);
        }
    }

    // TODO: Instead of checking for alphabetic keyboard here, separate keycodes for
    // alphabetic shift and shift while in symbol layout and get rid of this method.
    private int getCodePointForKeyboard(final int codePoint) {
        if (Constants.CODE_SHIFT == codePoint) {
            final Keyboard currentKeyboard = mKeyboardSwitcher.getKeyboard();
            if (null != currentKeyboard && currentKeyboard.mId.isAlphabetKeyboard()) {
                return codePoint;
            }
            return Constants.CODE_SYMBOL_SHIFT;
        }
        return codePoint;
    }

    // Implementation of {@link KeyboardActionListener}.
    @Override
    public void onCodeInput(final int codePoint, final int x, final int y,
            final boolean isKeyRepeat) {
        // TODO: this processing does not belong inside LatinIME, the caller should be doing this.
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        // x and y include some padding, but everything down the line (especially native
        // code) needs the coordinates in the keyboard frame.
        // TODO: We should reconsider which coordinate system should be used to represent
        // keyboard event. Also we should pull this up -- LatinIME has no business doing
        // this transformation, it should be done already before calling onEvent.
        final int keyX = mainKeyboardView.getKeyX(x);
        final int keyY = mainKeyboardView.getKeyY(y);
        final Event event = createSoftwareKeypressEvent(getCodePointForKeyboard(codePoint),
                keyX, keyY, isKeyRepeat);
        onEvent(event);
    }

    // This method is public for testability of LatinIME, but also in the future it should
    // completely replace #onCodeInput.
    public void onEvent(@Nonnull final Event event) {
        if (Constants.CODE_SHORTCUT == event.mKeyCode) {
            mRichImm.switchToShortcutIme(mInputMethodService);
        }
        /*final InputTransaction completeInputTransaction =
                mInputLogic.onCodeInput(mSettings.getCurrent(), event,
                        mKeyboardSwitcher.getKeyboardShiftMode(),
                        mKeyboardSwitcher.getCurrentKeyboardScriptId(), mHandler);
        updateStateAfterInputTransaction(completeInputTransaction);*/
        getActiveIME().onEvent(event);
        mKeyboardSwitcher.onEvent(event, getCurrentAutoCapsState());
    }

    // A helper method to split the code point and the key code. Ultimately, they should not be
    // squashed into the same variable, and this method should be removed.
    // public for testing, as we don't want to copy the same logic into test code
    @Nonnull
    public static Event createSoftwareKeypressEvent(final int keyCodeOrCodePoint, final int keyX,
             final int keyY, final boolean isKeyRepeat) {
        final int keyCode;
        final int codePoint;
        if (keyCodeOrCodePoint <= 0) {
            keyCode = keyCodeOrCodePoint;
            codePoint = Event.NOT_A_CODE_POINT;
        } else {
            keyCode = Event.NOT_A_KEY_CODE;
            codePoint = keyCodeOrCodePoint;
        }
        return Event.createSoftwareKeypressEvent(codePoint, keyCode, keyX, keyY, isKeyRepeat);
    }

    // Called from PointerTracker through the KeyboardActionListener interface
    @Override
    public void onTextInput(final String rawText) {
        final Event event = Event.createSoftwareTextEvent(rawText, Constants.CODE_OUTPUT_TEXT);
        getActiveIME().onEvent(event);
        mKeyboardSwitcher.onEvent(event, getCurrentAutoCapsState());
    }

    public void onTextInputWithSpace(final String rawText) {
        final Event event = Event.createSoftwareTextEvent(rawText, Constants.CODE_OUTPUT_TEXT_WITH_SPACES);
        getActiveIME().onEvent(event);
        mKeyboardSwitcher.onEvent(event, getCurrentAutoCapsState());
    }

    private IMEInterface getActiveIME() {
        return mImeManager.getActiveIME(mSettings.getCurrent());
    }

    @Override
    public void onStartBatchInput() {
        getActiveIME().onStartBatchInput();
    }

    @Override
    public void onUpdateBatchInput(final InputPointers batchPointers) {
        getActiveIME().onUpdateBatchInput(batchPointers);
    }

    @Override
    public void onEndBatchInput(final InputPointers batchPointers) {
        getActiveIME().onEndBatchInput(batchPointers);
    }

    @Override
    public void onCancelBatchInput() {
        getActiveIME().onCancelBatchInput();
    }

    // Called from PointerTracker through the KeyboardActionListener interface
    @Override
    public void onFinishSlidingInput() {
        // User finished sliding input.
        mKeyboardSwitcher.onFinishSlidingInput(getCurrentAutoCapsState());
    }

    // Called from PointerTracker through the KeyboardActionListener interface
    @Override
    public void onCancelInput() {
        // User released a finger outside any key
        // Nothing to do so far.
    }

    @Override
    public void showSuggestionStrip(SuggestedWords suggestedWords) {
        // Does not get called anymore.
    }

    // Called from {@link SuggestionStripView} through the {@link SuggestionStripView#Listener}
    // interface
    @Override
    public void pickSuggestionManually(final SuggestedWordInfo suggestionInfo) {
        final Event event = Event.createSuggestionPickedEvent(suggestionInfo);
        mImeManager.getActiveIME(
                mSettings.getCurrent()
        ).onEvent(event);

        if(suggestionInfo.isKindOf(SuggestedWordInfo.KIND_EMOJI_SUGGESTION)) {
            getLatinIME().rememberEmojiSuggestion(suggestionInfo);
        }
    }

    @Override
    public void requestForgetWord(SuggestedWordInfo word) {
        getLatinIME().requestForgetWord(word);
    }

    @Override
    public void setNeutralSuggestionStrip() {

    }

    // Outside LatinIME, only used by the {@link InputTestsBase} test suite.
    @UsedForTesting
    void loadKeyboard() {
        // Since we are switching languages, the most urgent thing is to let the keyboard graphics
        // update. LoadKeyboard does that, but we need to wait for buffer flip for it to be on
        // the screen. Anything we do right now will delay this, so wait until the next frame
        // before we do the rest, like reopening dictionaries and updating suggestions. So we
        // post a message.
        // TODO: mHandler.postReopenDictionaries();
        loadSettings();
        if (mKeyboardSwitcher.getMainKeyboardView() != null) {
            // Reload keyboard because the current language has been changed.
            mKeyboardSwitcher.loadKeyboard(mInputMethodService.getCurrentInputEditorInfo(), mSettings.getCurrent(),
                    getCurrentAutoCapsState());
        }
    }

    private void hapticAndAudioFeedback(final int code, final int repeatCount) {
        final MainKeyboardView keyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (keyboardView != null && keyboardView.isInDraggingFinger()) {
            // No need to feedback while finger is dragging.
            return;
        }
        final AudioAndHapticFeedbackManager feedbackManager =
                AudioAndHapticFeedbackManager.getInstance();
        feedbackManager.performHapticFeedback(keyboardView, repeatCount > 0);
        feedbackManager.performAudioFeedback(code);
    }

    // Callback of the {@link KeyboardActionListener}. This is called when a key is depressed;
    // release matching call is {@link #onReleaseKey(int,boolean)} below.
    @Override
    public void onPressKey(final int primaryCode, final int repeatCount,
            final boolean isSinglePointer) {
        mKeyboardSwitcher.onPressKey(primaryCode, isSinglePointer, getCurrentAutoCapsState());
        hapticAndAudioFeedback(primaryCode, repeatCount);
    }

    // Callback of the {@link KeyboardActionListener}. This is called when a key is released;
    // press matching call is {@link #onPressKey(int,int,boolean)} above.
    @Override
    public void onReleaseKey(final int primaryCode, final boolean withSliding) {
        mKeyboardSwitcher.onReleaseKey(primaryCode, withSliding, getCurrentAutoCapsState());
    }

    private HardwareEventDecoder getHardwareKeyEventDecoder(final int deviceId) {
        final HardwareEventDecoder decoder = mHardwareEventDecoders.get(deviceId);
        if (null != decoder) return decoder;
        // TODO: create the decoder according to the specification
        final HardwareEventDecoder newDecoder = new HardwareKeyboardEventDecoder(deviceId);
        mHardwareEventDecoders.put(deviceId, newDecoder);
        return newDecoder;
    }

    // Hooks for hardware keyboard
    public boolean onKeyDown(final int keyCode, final KeyEvent keyEvent) {
        if (mEmojiAltPhysicalKeyDetector == null) {
            mEmojiAltPhysicalKeyDetector = new EmojiAltPhysicalKeyDetector(
                    mInputMethodService.getApplicationContext().getResources());
        }
        mEmojiAltPhysicalKeyDetector.onKeyDown(keyEvent);
        if (!ProductionFlags.IS_HARDWARE_KEYBOARD_SUPPORTED) {
            return false;
        }
        final Event event = getHardwareKeyEventDecoder(
                keyEvent.getDeviceId()).decodeHardwareKey(keyEvent);
        // If the event is not handled by LatinIME, we just pass it to the parent implementation.
        // If it's handled, we return true because we did handle it.
        if (event.isHandled()) {
            mImeManager.getActiveIME(
                    mSettings.getCurrent()
            ).onEvent(event);
            return true;
        }
        return false;
    }

    public boolean onKeyUp(final int keyCode, final KeyEvent keyEvent) {
        if (mEmojiAltPhysicalKeyDetector == null) {
            mEmojiAltPhysicalKeyDetector = new EmojiAltPhysicalKeyDetector(
                    mInputMethodService.getApplicationContext().getResources());
        }
        mEmojiAltPhysicalKeyDetector.onKeyUp(keyEvent);
        if (!ProductionFlags.IS_HARDWARE_KEYBOARD_SUPPORTED) {
            return false;
        }
        final long keyIdentifier = keyEvent.getDeviceId() << 32 + keyEvent.getKeyCode();
        // TODO: Seems unused, nothing is ever even added to this set, so this is always false
        //if (mInputLogic.mCurrentlyPressedHardwareKeys.remove(keyIdentifier)) {
        //    return true;
        //}
        return false;
    }

    // onKeyDown and onKeyUp are the main events we are interested in. There are two more events
    // related to handling of hardware key events that we may want to implement in the future:
    // boolean onKeyLongPress(final int keyCode, final KeyEvent event);
    // boolean onKeyMultiple(final int keyCode, final int count, final KeyEvent event);

    // receive ringer mode change.
    private final BroadcastReceiver mRingerModeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            if (action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                AudioAndHapticFeedbackManager.getInstance().onRingerModeChanged();
            }
        }
    };

    /**
     * Starts {@link Activity} on the same display where the IME is shown.
     *
     * @param intent {@link Intent} to be used to start {@link Activity}.
     */
    private void startActivityOnTheSameDisplay(Intent intent) {
        // Note that WindowManager#getDefaultDisplay() returns the display ID associated with the
        // Context from which the WindowManager instance was obtained. Therefore the following code
        // returns the display ID for the window where the IME is shown.
        final int currentDisplayId = ((WindowManager) mInputMethodService.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getDisplayId();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mInputMethodService.startActivity(intent,
                    ActivityOptions.makeBasic().setLaunchDisplayId(currentDisplayId).toBundle());
        }
    }

    @UsedForTesting
    SuggestedWords getSuggestedWordsForTest() {
        // You may not use this method for anything else than debug
        // TODO: return DebugFlags.DEBUG_ENABLED ? mInputLogic.mSuggestedWords : null;
        return null;
    }

    // DO NOT USE THIS for any other purpose than testing. This is information private to LatinIME.
    @UsedForTesting
    void waitForLoadingDictionaries(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        // TODO: mDictionaryFacilitator.waitForLoadingDictionariesForTesting(timeout, unit);
    }

    // DO NOT USE THIS for any other purpose than testing. This can break the keyboard badly.
    @UsedForTesting
    void replaceDictionariesForTest(final Locale locale) {
        final ArrayList<Locale> locales = new ArrayList<>();
        locales.add(locale);

        final SettingsValues settingsValues = mSettings.getCurrent();
        // TODO: mDictionaryFacilitator.resetDictionaries(mInputMethodService, locales,
        //    settingsValues.mUseContactsDict, settingsValues.mUsePersonalizedDicts,
        //    false /* forceReloadMainDictionary */,
        //    settingsValues.mAccount, "", /* dictionaryNamePrefix */
        //    this /* DictionaryInitializationListener */);
    }

    // DO NOT USE THIS for any other purpose than testing.
    @UsedForTesting
    void clearPersonalizedDictionariesForTest() {
        // TODO: mDictionaryFacilitator.clearUserHistoryDictionary(mInputMethodService);
    }

    public void dumpDictionaryForDebug(final String dictName) {
        // TODO: Remove?
        //if (!mDictionaryFacilitator.isActive()) {
        //    resetDictionaryFacilitatorIfNecessary();
        //}
        //mDictionaryFacilitator.dumpDictionaryForDebug(dictName);
    }

    public void debugDumpStateAndCrashWithException(final String context) {
        final SettingsValues settingsValues = mSettings.getCurrent();
        final StringBuilder s = new StringBuilder(settingsValues.toString());
        s.append("\nAttributes : ").append(settingsValues.mInputAttributes)
                .append("\nContext : ").append(context);
        throw new RuntimeException(s.toString());
    }

    protected void dump(final FileDescriptor fd, final PrintWriter fout, final String[] args) {
        final Printer p = new PrintWriterPrinter(fout);
        p.println("LatinIME state :");
        p.println("  VersionCode = " + ApplicationUtils.getVersionCode(mInputMethodService));
        p.println("  VersionName = " + ApplicationUtils.getVersionName(mInputMethodService));
        final Keyboard keyboard = mKeyboardSwitcher.getKeyboard();
        final int keyboardMode = keyboard != null ? keyboard.mId.mMode : -1;
        p.println("  Keyboard mode = " + keyboardMode);
        final SettingsValues settingsValues = mSettings.getCurrent();
        p.println(settingsValues.dump());
        //p.println(mDictionaryFacilitator.dump(mInputMethodService));
        // TODO: Dump all settings values
    }

    public boolean shouldSwitchToOtherInputMethods() {
        // TODO: Revisit here to reorganize the settings. Probably we can/should use different
        // strategy once the implementation of
        // {@link InputMethodManager#shouldOfferSwitchingToNextInputMethod} is defined well.
        final boolean fallbackValue = mSettings.getCurrent().mIncludesOtherImesInLanguageSwitchList;
        final IBinder token = mInputMethodService.getWindow().getWindow().getAttributes().token;
        if (token == null) {
            return fallbackValue;
        }
        return mRichImm.shouldOfferSwitchingToNextInputMethod(token, fallbackValue);
    }

    private void setNavigationBarVisibility(final boolean visible) {
        getLatinIME().updateNavigationBarVisibility(visible);
    }

    public InputMethodService getInputMethodService() {
        return mInputMethodService;
    }

    public LatinIME getLatinIME() {
        return (LatinIME)(mInputMethodService);
    }

    public Locale getLocale() {
        return mLocale;
    }

    public void setLayout(@NotNull KeyboardLayoutSetV2 layout) {
        mImeManager.setLayout(layout);
    }
}
