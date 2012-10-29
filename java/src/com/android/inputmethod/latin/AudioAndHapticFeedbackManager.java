/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin;

import android.content.Context;
import android.media.AudioManager;
import android.view.HapticFeedbackConstants;
import android.view.View;

import com.android.inputmethod.latin.VibratorUtils;

/**
 * This class gathers audio feedback and haptic feedback functions.
 *
 * It offers a consistent and simple interface that allows LatinIME to forget about the
 * complexity of settings and the like.
 */
public final class AudioAndHapticFeedbackManager {
    private final AudioManager mAudioManager;
    private final VibratorUtils mVibratorUtils;

    private SettingsValues mSettingsValues;
    private boolean mSoundOn;

    public AudioAndHapticFeedbackManager(final LatinIME latinIme) {
        mVibratorUtils = VibratorUtils.getInstance(latinIme);
        mAudioManager = (AudioManager) latinIme.getSystemService(Context.AUDIO_SERVICE);
    }

    public void hapticAndAudioFeedback(final int primaryCode,
            final View viewToPerformHapticFeedbackOn) {
        vibrate(viewToPerformHapticFeedbackOn);
        playKeyClick(primaryCode);
    }

    private boolean reevaluateIfSoundIsOn() {
        if (mSettingsValues == null || !mSettingsValues.mSoundOn || mAudioManager == null) {
            return false;
        } else {
            return mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
        }
    }

    private void playKeyClick(int primaryCode) {
        // if mAudioManager is null, we can't play a sound anyway, so return
        if (mAudioManager == null) return;
        if (mSoundOn) {
            final int sound;
            switch (primaryCode) {
            case Constants.CODE_DELETE:
                sound = AudioManager.FX_KEYPRESS_DELETE;
                break;
            case Constants.CODE_ENTER:
                sound = AudioManager.FX_KEYPRESS_RETURN;
                break;
            case Constants.CODE_SPACE:
                sound = AudioManager.FX_KEYPRESS_SPACEBAR;
                break;
            default:
                sound = AudioManager.FX_KEYPRESS_STANDARD;
                break;
            }
            mAudioManager.playSoundEffect(sound, mSettingsValues.mFxVolume);
        }
    }

    private void vibrate(final View viewToPerformHapticFeedbackOn) {
        if (!mSettingsValues.mVibrateOn) {
            return;
        }
        if (mSettingsValues.mKeypressVibrationDuration < 0) {
            // Go ahead with the system default
            if (viewToPerformHapticFeedbackOn != null) {
                viewToPerformHapticFeedbackOn.performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }
        } else if (mVibratorUtils != null) {
            mVibratorUtils.vibrate(mSettingsValues.mKeypressVibrationDuration);
        }
    }

    public void onSettingsChanged(final SettingsValues settingsValues) {
        mSettingsValues = settingsValues;
        mSoundOn = reevaluateIfSoundIsOn();
    }

    public void onRingerModeChanged() {
        mSoundOn = reevaluateIfSoundIsOn();
    }
}
