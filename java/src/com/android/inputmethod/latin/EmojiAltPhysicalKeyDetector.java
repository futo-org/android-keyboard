/*
 * Copyright (C) 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin;

import android.content.res.Resources;
import android.util.Log;
import android.view.KeyEvent;

import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.latin.settings.Settings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * A class for detecting Emoji-Alt physical key.
 */
final class EmojiAltPhysicalKeyDetector {
    private static final String TAG = "EmojiAltPhysicalKeyDetector";

    private final Map<Integer, Integer> mEmojiSwitcherMap;
    private final Map<Integer, Integer> mSymbolsShiftedSwitcherMap;
    private final Map<Integer, Integer> mCombinedSwitcherMap;

    // Set of keys codes that have been used as modifiers.
    private Set<Integer> mActiveModifiers;

    public EmojiAltPhysicalKeyDetector(@Nonnull final Resources resources) {
        mEmojiSwitcherMap = parseSwitchDefinition(resources, R.array.keyboard_switcher_emoji);
        mSymbolsShiftedSwitcherMap = parseSwitchDefinition(
                resources, R.array.keyboard_switcher_symbols_shifted);
        mCombinedSwitcherMap = new HashMap<>();
        mCombinedSwitcherMap.putAll(mEmojiSwitcherMap);
        mCombinedSwitcherMap.putAll(mSymbolsShiftedSwitcherMap);
        mActiveModifiers = new HashSet<>();
    }

    private static Map<Integer, Integer> parseSwitchDefinition(
            @Nonnull final Resources resources,
            final int resourceId) {
        final Map<Integer, Integer> definition = new HashMap<>();
        final String name = resources.getResourceEntryName(resourceId);
        final String[] values = resources.getStringArray(resourceId);
        for (int i = 0; values != null && i < values.length; i++) {
            String[] valuePair = values[i].split(",");
            if (valuePair.length != 2) {
                Log.w(TAG, "Expected 2 integers in " + name + "[" + i + "] : " + values[i]);
            }
            try {
                definition.put(Integer.parseInt(valuePair[0]), Integer.parseInt(valuePair[1]));
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed to parse " + name + "[" + i + "] : " + values[i], e);
            }
        }
        return definition;
    }

    /**
     * Determine whether an up key event came from a mapped modifier key.
     *
     * @param keyEvent an up key event.
     */
    public void onKeyUp(@Nonnull final KeyEvent keyEvent) {
        Log.d(TAG, "onKeyUp() : " + keyEvent);
        if (!Settings.getInstance().getCurrent().mEnableEmojiAltPhysicalKey) {
            // The feature is disabled.
            Log.d(TAG, "onKeyUp() : Disabled");
            return;
        }
        if (keyEvent.isCanceled()) {
            // This key up event was a part of key combinations and should be ignored.
            Log.d(TAG, "onKeyUp() : Canceled");
            return;
        }
        final Integer mappedModifier = getMappedModifier(keyEvent);
        if (mappedModifier != null) {
            // If the key was modified by a mapped key, then ignore the next time
            // the same modifier key comes up.
            Log.d(TAG, "onKeyUp() : Using Modifier: " + mappedModifier);
            mActiveModifiers.add(mappedModifier);
            return;
        }
        final int keyCode = keyEvent.getKeyCode();
        if (mActiveModifiers.contains(keyCode)) {
            // Used as a modifier, not a standalone key press.
            Log.d(TAG, "onKeyUp() : Used as Modifier: " + keyCode);
            mActiveModifiers.remove(keyCode);
            return;
        }
        if (!isMappedKeyCode(keyEvent)) {
            // Nothing special about this key.
            Log.d(TAG, "onKeyUp() : Not Mapped: " + keyCode);
            return;
        }
        final KeyboardSwitcher switcher = KeyboardSwitcher.getInstance();
        if (mEmojiSwitcherMap.keySet().contains(keyCode)) {
            switcher.onToggleKeyboard(KeyboardSwitcher.KeyboardSwitchState.EMOJI);
        } else if (mSymbolsShiftedSwitcherMap.keySet().contains(keyCode)) {
            switcher.onToggleKeyboard(KeyboardSwitcher.KeyboardSwitchState.SYMBOLS_SHIFTED);
        } else {
            Log.w(TAG, "Cannot toggle on keyCode: " + keyCode);
        }
    }

    /**
     * @param keyEvent pressed key event
     * @return true iff the user pressed a mapped modifier key.
     */
    private boolean isMappedKeyCode(@Nonnull final KeyEvent keyEvent) {
        return mCombinedSwitcherMap.get(keyEvent.getKeyCode()) != null;
    }

    /**
     * @param keyEvent pressed key event
     * @return the mapped modifier used with this key opress, if any.
     */
    private Integer getMappedModifier(@Nonnull final KeyEvent keyEvent) {
        final int keyCode = keyEvent.getKeyCode();
        final int metaState = keyEvent.getMetaState();
        for (int mappedKeyCode : mCombinedSwitcherMap.keySet()) {
            if (keyCode == mappedKeyCode) {
                Log.d(TAG, "getMappedModifier() : KeyCode = MappedKeyCode = " + mappedKeyCode);
                continue;
            }
            final Integer mappedMeta = mCombinedSwitcherMap.get(mappedKeyCode);
            if (mappedMeta == null || mappedMeta.intValue() == -1) {
                continue;
            }
            if ((metaState & mappedMeta) != 0) {
                Log.d(TAG, "getMappedModifier() : MetaState(" + metaState
                        + ") contains MappedMeta(" + mappedMeta + ")");
                return mappedKeyCode;
            }
        }
        return null;
    }
}
