/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.inputmethod.deprecated.voice;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/**
 * A set of text fields where speech has been explicitly enabled.
 */
public class Whitelist {
    private List<Bundle> mConditions;

    public Whitelist() {
        mConditions = new ArrayList<Bundle>();
    }

    public Whitelist(List<Bundle> conditions) {
        this.mConditions = conditions;
    }

    public void addApp(String app) {
        Bundle bundle = new Bundle();
        bundle.putString("packageName", app);
        mConditions.add(bundle);
    }

    /**
     * @return true if the field is a member of the whitelist.
     */
    public boolean matches(FieldContext context) {
        for (Bundle condition : mConditions) {
            if (matches(condition, context.getBundle())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true of all values in condition are matched by a value
     *     in target.
     */
    private boolean matches(Bundle condition, Bundle target) {
        for (String key : condition.keySet()) {
          if (!condition.getString(key).equals(target.getString(key))) {
            return false;
          }
        }
        return true;
    }
}
