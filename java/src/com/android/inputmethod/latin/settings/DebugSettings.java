/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.latin.settings;

public final class DebugSettings {
    public static final String PREF_DEBUG_MODE = "debug_mode";
    public static final String PREF_FORCE_NON_DISTINCT_MULTITOUCH = "force_non_distinct_multitouch";
    public static final String PREF_FORCE_PHYSICAL_KEYBOARD_SPECIAL_KEY =
            "force_physical_keyboard_special_key";
    public static final String PREF_SHOW_UI_TO_ACCEPT_TYPED_WORD =
            "pref_show_ui_to_accept_typed_word";
    public static final String PREF_HAS_CUSTOM_KEY_PREVIEW_ANIMATION_PARAMS =
            "pref_has_custom_key_preview_animation_params";
    public static final String PREF_KEY_PREVIEW_SHOW_UP_START_X_SCALE =
            "pref_key_preview_show_up_start_x_scale";
    public static final String PREF_KEY_PREVIEW_SHOW_UP_START_Y_SCALE =
            "pref_key_preview_show_up_start_y_scale";
    public static final String PREF_KEY_PREVIEW_DISMISS_END_X_SCALE =
            "pref_key_preview_dismiss_end_x_scale";
    public static final String PREF_KEY_PREVIEW_DISMISS_END_Y_SCALE =
            "pref_key_preview_dismiss_end_y_scale";
    public static final String PREF_KEY_PREVIEW_SHOW_UP_DURATION =
            "pref_key_preview_show_up_duration";
    public static final String PREF_KEY_PREVIEW_DISMISS_DURATION =
            "pref_key_preview_dismiss_duration";
    public static final String PREF_SLIDING_KEY_INPUT_PREVIEW = "pref_sliding_key_input_preview";
    public static final String PREF_KEY_LONGPRESS_TIMEOUT = "pref_key_longpress_timeout";

    private DebugSettings() {
        // This class is not publicly instantiable.
    }
}
