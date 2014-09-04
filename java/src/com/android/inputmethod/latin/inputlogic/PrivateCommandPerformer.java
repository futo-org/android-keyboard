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

package com.android.inputmethod.latin.inputlogic;

import android.os.Bundle;

/**
 * Provides an interface matching
 * {@link android.view.inputmethod.InputConnection#performPrivateCommand(String,Bundle)}.
 */
public interface PrivateCommandPerformer {
    /**
     * API to send private commands from an input method to its connected
     * editor. This can be used to provide domain-specific features that are
     * only known between certain input methods and their clients.
     *
     * @param action Name of the command to be performed. This must be a scoped
     *            name, i.e. prefixed with a package name you own, so that
     *            different developers will not create conflicting commands.
     * @param data Any data to include with the command.
     * @return true if the command was sent (regardless of whether the
     * associated editor understood it), false if the input connection is no
     * longer valid.
     */
    boolean performPrivateCommand(String action, Bundle data);
}
