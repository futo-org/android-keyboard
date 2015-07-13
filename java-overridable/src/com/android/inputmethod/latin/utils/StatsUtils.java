/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.inputmethod.latin.utils;

import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.DictionaryFacilitator;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.settings.SettingsValues;

import javax.annotation.Nullable;

@SuppressWarnings("unused")
public final class StatsUtils {

    private StatsUtils() {
        // Intentional empty constructor.
    }

    public static void onCreate(final SettingsValues settingsValues,
            RichInputMethodManager richImm) {
    }

    public static void onPickSuggestionManually(final SuggestedWords suggestedWords,
            final SuggestedWords.SuggestedWordInfo suggestionInfo,
            final DictionaryFacilitator dictionaryFacilitator) {
    }

    public static void onBackspaceWordDelete(int wordLength) {
    }

    public static void onBackspacePressed(int lengthToDelete) {
    }

    public static void onBackspaceSelectedText(int selectedTextLength) {
    }

    public static void onDeleteMultiCharInput(int multiCharLength) {
    }

    public static void onRevertAutoCorrect() {
    }

    public static void onRevertDoubleSpacePeriod() {
    }

    public static void onRevertSwapPunctuation() {
    }

    public static void onFinishInputView() {
    }

    public static void onCreateInputView() {
    }

    public static void onStartInputView(int inputType, int displayOrientation, boolean restarting) {
    }

    public static void onAutoCorrection(final String typedWord, final String autoCorrectionWord,
            final boolean isBatchInput, final DictionaryFacilitator dictionaryFacilitator,
            final String prevWordsContext) {
    }

    public static void onWordCommitUserTyped(final String commitWord, final boolean isBatchMode) {
    }

    public static void onWordCommitAutoCorrect(final String commitWord, final boolean isBatchMode) {
    }

    public static void onWordCommitSuggestionPickedManually(
            final String commitWord, final boolean isBatchMode) {
    }

    public static void onDoubleSpacePeriod() {
    }

    public static void onLoadSettings(SettingsValues settingsValues) {
    }

    public static void onInvalidWordIdentification(final String invalidWord) {
    }

    public static void onSubtypeChanged(final InputMethodSubtype oldSubtype,
            final InputMethodSubtype newSubtype) {
    }

    public static void onSettingsActivity(final String entryPoint) {
    }

    public static void onInputConnectionLaggy(final int operation, final long duration) {
    }

    public static void onDecoderLaggy(final int operation, final long duration) {
    }
}
