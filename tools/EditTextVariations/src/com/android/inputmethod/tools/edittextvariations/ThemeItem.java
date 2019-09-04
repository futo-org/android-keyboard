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

package com.android.inputmethod.tools.edittextvariations;

import android.os.Build;

import com.android.inputmethod.tools.edittextvariations.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ThemeItem {
    public final int id;
    public final String name;

    private ThemeItem(final String name, final int resId) {
        this.id = resId;
        this.name = name;
    }

    private static final String THEME_DEFAULT = "Default";
    private static final String THEME_HOLO = "Theme_Holo";
    private static final String THEME_HOLO_LIGHT = "Theme_Holo_Light";
    private static final String THEME_DEVICE_DEFAULT = "Theme_DeviceDefault";
    private static final String THEME_DEVICE_DEFAULT_LIGHT = "Theme_DeviceDefault_Light";
    private static final String THEME_MATERIAL = "Theme_Material";
    private static final String THEME_MATERIAL_LIGHT = "Theme_Material_Light";

    public static String getDefaultThemeName() {
        return THEME_DEFAULT;
    }

    public static final List<ThemeItem> THEME_LIST = createThemeList(
            THEME_HOLO, THEME_HOLO_LIGHT, THEME_DEVICE_DEFAULT, THEME_DEVICE_DEFAULT_LIGHT,
            THEME_MATERIAL, THEME_MATERIAL_LIGHT);

    private static List<ThemeItem> createThemeList(final String... candidateList) {
        final ArrayList<ThemeItem> list = new ArrayList<>();

        // Default theme is always available as it's defined in our resource.
        list.add(new ThemeItem(THEME_DEFAULT, R.style.defaultActivityTheme));

        for (final String name : candidateList) {
            final FinalClassField<Integer> constant =
                    FinalClassField.newInstance(android.R.style.class, name, 0);
            if (constant.defined) {
                list.add(new ThemeItem(name, constant.value));
            }
        }

        return Collections.unmodifiableList(list);
    }
}
