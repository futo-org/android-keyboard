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

package org.futo.inputmethod.keyboard;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;

import org.futo.inputmethod.event.Event;
import org.futo.inputmethod.keyboard.internal.KeyboardLayoutElement;
import org.futo.inputmethod.keyboard.internal.KeyboardState;
import org.futo.inputmethod.keyboard.internal.KeyboardTextsSet;
import org.futo.inputmethod.keyboard.internal.SwitchActions;
import org.futo.inputmethod.latin.InputView;
import org.futo.inputmethod.latin.LatinIME;
import org.futo.inputmethod.latin.LatinIMELegacy;
import org.futo.inputmethod.latin.R;
import org.futo.inputmethod.latin.RichInputMethodManager;
import org.futo.inputmethod.latin.RichInputMethodSubtype;
import org.futo.inputmethod.latin.Subtypes;
import org.futo.inputmethod.latin.WordComposer;
import org.futo.inputmethod.latin.common.Constants;
import org.futo.inputmethod.latin.settings.LongPressKeySettings;
import org.futo.inputmethod.latin.settings.Settings;
import org.futo.inputmethod.latin.settings.SettingsValues;
import org.futo.inputmethod.latin.utils.LanguageOnSpacebarUtils;
import org.futo.inputmethod.latin.utils.ScriptUtils;
import org.futo.inputmethod.v2keyboard.ComputedKeyboardSize;
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetKt;
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2;
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2Params;
import org.futo.inputmethod.v2keyboard.KeyboardSizingCalculator;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class KeyboardSwitcher implements SwitchActions {
    private static final String TAG = KeyboardSwitcher.class.getSimpleName();

    private InputView mCurrentInputView;
    private View mMainKeyboardFrame;
    private MainKeyboardView mKeyboardView;
    private LatinIMELegacy mLatinIMELegacy;
    private RichInputMethodManager mRichImm;
    private boolean mIsHardwareAcceleratedDrawingEnabled;

    public KeyboardState mState;

    private KeyboardLayoutSetV2 mKeyboardLayoutSet;
    // TODO: The following {@link KeyboardTextsSet} should be in {@link KeyboardLayoutSet}.
    private final KeyboardTextsSet mKeyboardTextsSet = new KeyboardTextsSet();

    private KeyboardTheme mKeyboardTheme;
    private Context mThemeContext;

    private static final KeyboardSwitcher sInstance = new KeyboardSwitcher();

    public static KeyboardSwitcher getInstance() {
        return sInstance;
    }

    private KeyboardSwitcher() {
        // Intentional empty constructor for singleton.
    }

    public static void init(final LatinIMELegacy latinImeLegacy) {
        sInstance.initInternal(latinImeLegacy);
    }

    private void initInternal(final LatinIMELegacy latinImeLegacy) {
        mLatinIMELegacy = latinImeLegacy;
        mRichImm = RichInputMethodManager.getInstance();
        mState = new KeyboardState(this);
        mIsHardwareAcceleratedDrawingEnabled = true;
    }

    public void updateKeyboardTheme(@NonNull Context displayContext) {
        final boolean themeUpdated = updateKeyboardThemeAndContextThemeWrapper(
                displayContext, KeyboardTheme.getKeyboardTheme(displayContext /* context */));
        if (themeUpdated && mKeyboardView != null) {
            mLatinIMELegacy.getLatinIME().updateLegacyView(onCreateInputView(
                    displayContext, mIsHardwareAcceleratedDrawingEnabled));
            mLatinIMELegacy.updateMainKeyboardViewSettings();
        }
    }

    private boolean themeSwitchPending = false;
    public void queueThemeSwitch() {
        themeSwitchPending = true;
    }

    private boolean updateKeyboardThemeAndContextThemeWrapper(final Context context,
            final KeyboardTheme keyboardTheme) {
        if (themeSwitchPending || mThemeContext == null || !keyboardTheme.equals(mKeyboardTheme)
                || !mThemeContext.getResources().equals(context.getResources())) {
            mKeyboardTheme = keyboardTheme;
            mThemeContext = new ContextThemeWrapper(context, keyboardTheme.mStyleId);
            KeyboardLayoutSetV2.onKeyboardThemeChanged(context);
            themeSwitchPending = false;
            return true;
        }
        return false;
    }

    public void loadKeyboard(final EditorInfo editorInfo, final SettingsValues settingsValues,
            final int currentAutoCapsState) {

        final Resources res = mThemeContext.getResources();

        final RichInputMethodSubtype subtype = mRichImm.getCurrentSubtype();

        String layoutSetName = subtype.getKeyboardLayoutSetName();
        List<Locale> multilingualTypingLanguages = subtype.getMultilingualTypingLanguages(mThemeContext);
        String overrideLayoutSet = KeyboardLayoutSetKt.getPrimaryLayoutOverride(editorInfo);
        if(overrideLayoutSet != null) {
            layoutSetName = overrideLayoutSet;
            multilingualTypingLanguages = null;
        }


        final KeyboardSizingCalculator sizingCalculator = mLatinIMELegacy.getLatinIME().getSizingCalculator();
        final ComputedKeyboardSize computedSize = sizingCalculator.calculate(layoutSetName, settingsValues);
        if(computedSize == null) {
            Log.e(TAG, "Unable to compute size right now.");
            return;
        }

        final KeyboardLayoutSetV2Params params = new KeyboardLayoutSetV2Params(
                computedSize,
                layoutSetName,
                subtype.getLocale(),
                multilingualTypingLanguages,
                editorInfo == null ? new EditorInfo() : editorInfo,
                settingsValues.mIsNumberRowEnabled,
                settingsValues.mNumberRowMode,
                settingsValues.mUseLocalNumbers,
                settingsValues.mIsArrowRowEnabled,
                settingsValues.mIsUsingAlternativePeriodKey,
                sizingCalculator.calculateGap(),
                settingsValues.mShowsActionKey ? settingsValues.mActionKeyId : null,
                LongPressKeySettings.load(mThemeContext)
        );

        try {
            mKeyboardLayoutSet = new KeyboardLayoutSetV2(
                    mThemeContext,
                    params
            );

            mState.onLoadKeyboard(editorInfo, transformAutoCapsState(currentAutoCapsState),
                    layoutSetName);
            mLatinIMELegacy.setLayout(mKeyboardLayoutSet);
            mKeyboardTextsSet.setLocale(mRichImm.getCurrentSubtypeLocale(), mThemeContext);
        } catch (Exception e) {
            Log.e(TAG, "loading keyboard failed: ", e);
        }
    }

    private int transformAutoCapsState(int state) {
        if(mKeyboardLayoutSet == null) return state;
        final org.futo.inputmethod.v2keyboard.Keyboard keyboard = mKeyboardLayoutSet.getMainLayout();
        if(keyboard == null) return state;

        if(!keyboard.getAutoShift())
            return Constants.TextUtils.CAP_MODE_OFF;
        else
            return state;
    }

    public void saveKeyboardState() {
        if (getKeyboard() != null || isShowingEmojiPalettes()) {
            mState.onSaveKeyboardState();
        }
    }

    public void onHideWindow() {
        if (mKeyboardView != null) {
            mKeyboardView.onHideWindow();
        }
    }

    private void setKeyboard(
            @Nonnull final KeyboardLayoutElement element,
            @Nonnull final KeyboardSwitchState toggleState) {
        // Make {@link MainKeyboardView} visible and hide {@link EmojiPalettesView}.
        final SettingsValues currentSettingsValues = Settings.getInstance().getCurrent();
        setMainKeyboardFrame(currentSettingsValues, toggleState);
        // TODO: pass this object to setKeyboard instead of getting the current values.
        final MainKeyboardView keyboardView = mKeyboardView;
        final Keyboard oldKeyboard = keyboardView.getKeyboard();
        final Keyboard newKeyboard = mKeyboardLayoutSet.getKeyboard(element);
        keyboardView.setKeyboard(newKeyboard);
        keyboardView.setKeyPreviewPopupEnabled(
                currentSettingsValues.mKeyPreviewPopupOn,
                currentSettingsValues.mKeyPreviewPopupDismissDelay);
        keyboardView.setKeyPreviewAnimationParams(
                currentSettingsValues.mHasCustomKeyPreviewAnimationParams,
                currentSettingsValues.mKeyPreviewShowUpStartXScale,
                currentSettingsValues.mKeyPreviewShowUpStartYScale,
                currentSettingsValues.mKeyPreviewShowUpDuration,
                currentSettingsValues.mKeyPreviewDismissEndXScale,
                currentSettingsValues.mKeyPreviewDismissEndYScale,
                currentSettingsValues.mKeyPreviewDismissDuration);
        keyboardView.updateShortcutKey(mRichImm.isShortcutImeReady());
        final boolean subtypeChanged = (oldKeyboard == null)
                || !newKeyboard.mId.mLocale.equals(oldKeyboard.mId.mLocale)
                || !newKeyboard.mId.mKeyboardLayoutSetName.equals(oldKeyboard.mId.mKeyboardLayoutSetName);
        final int languageOnSpacebarFormatType = LanguageOnSpacebarUtils
                .getLanguageOnSpacebarFormatType(newKeyboard.mId.mLocale, newKeyboard.mId.mKeyboardLayoutSetName);
        final boolean hasMultipleEnabledIMEsOrSubtypes = Subtypes.INSTANCE.hasMultipleEnabledSubtypes(mThemeContext);
        keyboardView.startDisplayLanguageOnSpacebar(subtypeChanged, languageOnSpacebarFormatType,
                hasMultipleEnabledIMEsOrSubtypes);
    }

    @Nullable
    public Keyboard getKeyboard() {
        if (mKeyboardView != null) {
            return mKeyboardView.getKeyboard();
        }
        return null;
    }

    public void resetKeyboardStateToAlphabet(final EditorInfo editorInfo, final int currentAutoCapsState) {
        mState.onResetKeyboardStateToAlphabet(editorInfo, transformAutoCapsState(currentAutoCapsState));
    }

    public void onPressKey(final int code, final boolean isSinglePointer,
            final int currentAutoCapsState) {
        mState.onPressKey(code, isSinglePointer, transformAutoCapsState(currentAutoCapsState));
    }

    public void onReleaseKey(final int code, final boolean withSliding,
            final int currentAutoCapsState) {
        mState.onReleaseKey(code, withSliding, transformAutoCapsState(currentAutoCapsState));
    }

    public void onFinishSlidingInput(final int currentAutoCapsState) {
        mState.onFinishSlidingInput(transformAutoCapsState(currentAutoCapsState));
    }

    public boolean isImeSuppressedByHardwareKeyboard(
            @Nonnull final SettingsValues settingsValues,
            @Nonnull final KeyboardSwitchState toggleState) {
        return settingsValues.mHasHardwareKeyboard && toggleState == KeyboardSwitchState.HIDDEN;
    }

    private void setMainKeyboardFrame(
            @Nonnull final SettingsValues settingsValues,
            @Nonnull final KeyboardSwitchState toggleState) {
        final int visibility =  isImeSuppressedByHardwareKeyboard(settingsValues, toggleState)
                ? View.GONE : View.VISIBLE;
        mKeyboardView.setVisibility(visibility);
        // The visibility of {@link #mKeyboardView} must be aligned with {@link #MainKeyboardFrame}.
        // @see #getVisibleKeyboardView() and
        // @see LatinIME#onComputeInset(android.inputmethodservice.InputMethodService.Insets)
        mMainKeyboardFrame.setVisibility(visibility);
    }

    @Override
    public void setKeyboard(@NonNull KeyboardLayoutElement element) {
        if(mKeyboardView == null) return;
        setKeyboard(element, KeyboardSwitchState.OTHER);
    }

    @Override
    public void requestUpdatingShiftState(int autoCapsFlags) {
        mState.onUpdateShiftState(transformAutoCapsState(autoCapsFlags));
    }

    public enum KeyboardSwitchState {
        HIDDEN(-1),
        SYMBOLS_SHIFTED(KeyboardId.ELEMENT_SYMBOLS_SHIFTED),
        EMOJI(-1),
        OTHER(-1);

        final int mKeyboardId;

        KeyboardSwitchState(int keyboardId) {
            mKeyboardId = keyboardId;
        }
    }

    public KeyboardSwitchState getKeyboardSwitchState() {
        boolean hidden = !isShowingEmojiPalettes()
                && (mKeyboardLayoutSet == null
                || mKeyboardView == null
                || !mKeyboardView.isShown());
        KeyboardSwitchState state;
        if (hidden) {
            return KeyboardSwitchState.HIDDEN;
        } else if (isShowingEmojiPalettes()) {
            return KeyboardSwitchState.EMOJI;
        } else if (isShowingKeyboardId(KeyboardId.ELEMENT_SYMBOLS_SHIFTED)) {
            return KeyboardSwitchState.SYMBOLS_SHIFTED;
        }
        return KeyboardSwitchState.OTHER;
    }

    public void onToggleKeyboard(@Nonnull final KeyboardSwitchState toggleState) {
        // TODO: only used by EmojiAltPhysicalKeyDetector
    }

    /**
     * Updates state machine to figure out when to automatically switch back to the previous mode.
     */
    public void onEvent(final Event event, final int currentAutoCapsState) {
        mState.onEvent(event, transformAutoCapsState(currentAutoCapsState));
    }

    public boolean isShowingKeyboardId(@Nonnull int... keyboardIds) {
        if (mKeyboardView == null || !mKeyboardView.isShown()) {
            return false;
        }
        int activeKeyboardId = mKeyboardView.getKeyboard().mId.mElementId;
        for (int keyboardId : keyboardIds) {
            if (activeKeyboardId == keyboardId) {
                return true;
            }
        }
        return false;
    }

    public boolean isShowingEmojiPalettes() {
        return false;
    }

    public boolean isShowingMoreKeysPanel() {
        if(mKeyboardView == null) return false;
        return mKeyboardView.isShowingMoreKeysPanel();
    }

    public View getVisibleKeyboardView() {
        return mKeyboardView;
    }

    public MainKeyboardView getMainKeyboardView() {
        return mKeyboardView;
    }

    public void deallocateMemory() {
        if (mKeyboardView != null) {
            mKeyboardView.cancelAllOngoingEvents();
            mKeyboardView.deallocateMemory();
        }
    }

    public View onCreateInputView(@NonNull Context displayContext,
            final boolean isHardwareAcceleratedDrawingEnabled) {
        if (mKeyboardView != null) {
            mKeyboardView.closing();
        }

        updateKeyboardThemeAndContextThemeWrapper(
                displayContext, KeyboardTheme.getKeyboardTheme(displayContext /* context */));
        mCurrentInputView = (InputView)LayoutInflater.from(mThemeContext).inflate(
                R.layout.input_view, null, false);
        mMainKeyboardFrame = mCurrentInputView.findViewById(R.id.main_keyboard_frame);
        mKeyboardView = (MainKeyboardView) mCurrentInputView.findViewById(R.id.keyboard_view);
        mKeyboardView.setHardwareAcceleratedDrawingEnabled(isHardwareAcceleratedDrawingEnabled);
        mKeyboardView.setKeyboardActionListener(mLatinIMELegacy);
        return mCurrentInputView;
    }

    public int getKeyboardShiftMode() {
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return WordComposer.CAPS_MODE_OFF;
        }
        switch (keyboard.mId.mElementId) {
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED:
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED:
            return WordComposer.CAPS_MODE_MANUAL_SHIFT_LOCKED;
        case KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED:
            return WordComposer.CAPS_MODE_MANUAL_SHIFTED;
        case KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED:
            return WordComposer.CAPS_MODE_AUTO_SHIFTED;
        default:
            return WordComposer.CAPS_MODE_OFF;
        }
    }

    public int getCurrentKeyboardScriptId() {
        if (null == mKeyboardLayoutSet) {
            return ScriptUtils.SCRIPT_UNKNOWN;
        }
        return mKeyboardLayoutSet.getScriptId();
    }
}
