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
package org.futo.inputmethod.keyboard.internal

import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import org.futo.inputmethod.latin.uix.DynamicThemeProvider
import org.futo.inputmethod.latin.uix.actions.AllActionsMap

class KeyboardIconsSet {
    private var provider: DynamicThemeProvider? = null
    fun loadIcons(keyboardAttrs: TypedArray?, provider: DynamicThemeProvider?) {
        this.provider = provider
    }

    fun getIconDrawable(iconId: String?): Drawable? {
        return provider!!.getIcon(iconId!!)
    }

    companion object {
        private val TAG: String = KeyboardIconsSet::class.java.simpleName

        const val PREFIX_ICON: String = "!icon/"
        const val ICON_UNDEFINED: String = ""

        const val NAME_UNDEFINED = "undefined"
        const val NAME_SHIFT_KEY: String = "shift_key"
        const val NAME_SHIFT_KEY_SHIFTED: String = "shift_key_shifted"
        const val NAME_DELETE_KEY: String = "delete_key"
        const val NAME_SETTINGS_KEY: String = "settings_key"
        const val NAME_SPACE_KEY: String = "space_key"
        const val NAME_SPACE_KEY_FOR_NUMBER_LAYOUT: String = "space_key_for_number_layout"
        const val NAME_ENTER_KEY: String = "enter_key"
        const val NAME_GO_KEY: String = "go_key"
        const val NAME_SEARCH_KEY: String = "search_key"
        const val NAME_SEND_KEY: String = "send_key"
        const val NAME_NEXT_KEY: String = "next_key"
        const val NAME_DONE_KEY: String = "done_key"
        const val NAME_PREVIOUS_KEY: String = "previous_key"
        const val NAME_TAB_KEY: String = "tab_key"
        const val NAME_ZWNJ_KEY: String = "zwnj_key"
        const val NAME_ZWJ_KEY: String = "zwj_key"
        const val NAME_EMOJI_ACTION_KEY: String = "emoji_action_key"
        const val NAME_EMOJI_NORMAL_KEY: String = "emoji_normal_key"
        const val NAME_NUMPAD: String = "numpad"
        const val NAME_JAPANESE_KEY: String = "japanese_key"

        val validIcons = mutableListOf(
            NAME_SHIFT_KEY,
            NAME_SHIFT_KEY_SHIFTED,
            NAME_DELETE_KEY,
            NAME_SETTINGS_KEY,
            NAME_SPACE_KEY,
            NAME_SPACE_KEY_FOR_NUMBER_LAYOUT,
            NAME_ENTER_KEY,
            NAME_GO_KEY,
            NAME_SEARCH_KEY,
            NAME_SEND_KEY,
            NAME_NEXT_KEY,
            NAME_DONE_KEY,
            NAME_PREVIOUS_KEY,
            NAME_TAB_KEY,
            NAME_ZWNJ_KEY,
            NAME_ZWJ_KEY,
            NAME_EMOJI_ACTION_KEY,
            NAME_EMOJI_NORMAL_KEY,
            NAME_NUMPAD,
            NAME_JAPANESE_KEY
        ).apply {
            AllActionsMap.keys.forEachIndexed { i, it ->
                // by number (action_0)
                add("action_${i}")

                // by key (action_copy)
                add("action_${it}")
            }
        }.toSet()

        @JvmStatic
        fun iconExists(iconId: String?): Boolean {
            return validIcons.contains(iconId ?: return false)
        }
    }
}
