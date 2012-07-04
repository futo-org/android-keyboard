/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.compat;

import android.media.AudioManager;

import java.lang.reflect.Method;

public class AudioManagerCompatWrapper {
    private static final Method METHOD_isWiredHeadsetOn = CompatUtils.getMethod(
            AudioManager.class, "isWiredHeadsetOn");
    private static final Method METHOD_isBluetoothA2dpOn = CompatUtils.getMethod(
            AudioManager.class, "isBluetoothA2dpOn");

    private final AudioManager mManager;

    public AudioManagerCompatWrapper(AudioManager manager) {
        mManager = manager;
    }

    /**
     * Checks whether audio routing to the wired headset is on or off.
     *
     * @return true if audio is being routed to/from wired headset;
     *         false if otherwise
     */
    public boolean isWiredHeadsetOn() {
        return (Boolean) CompatUtils.invoke(mManager, false, METHOD_isWiredHeadsetOn);
    }

    /**
     * Checks whether A2DP audio routing to the Bluetooth headset is on or off.
     *
     * @return true if A2DP audio is being routed to/from Bluetooth headset;
     *         false if otherwise
     */
    public boolean isBluetoothA2dpOn() {
        return (Boolean) CompatUtils.invoke(mManager, false, METHOD_isBluetoothA2dpOn);
    }
}
