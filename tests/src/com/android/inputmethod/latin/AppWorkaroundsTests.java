/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.latin;

import com.android.inputmethod.latin.settings.Settings;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build.VERSION_CODES;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.inputmethod.EditorInfo;

@LargeTest
public class AppWorkaroundsTests extends InputTestsBase {
    String packageNameOfAppBeforeJellyBean;
    String packageNameOfAppAfterJellyBean;

    @Override
    protected void setUp() throws Exception {
        // NOTE: this will fail if there is no app installed that targets an SDK
        // before Jelly Bean. For the moment, it's fine.
        final PackageManager pm = getContext().getPackageManager();
        for (ApplicationInfo ai : pm.getInstalledApplications(0 /* flags */)) {
            if (ai.targetSdkVersion < VERSION_CODES.JELLY_BEAN) {
                packageNameOfAppBeforeJellyBean = ai.packageName;
            } else {
                packageNameOfAppAfterJellyBean = ai.packageName;
            }
        }
        super.setUp();
    }

    // We want to test if the app package info is correctly retrieved by LatinIME. Since it
    // asks this information to the package manager from the package name, and that it takes
    // the package name from the EditorInfo, all we have to do it put the correct package
    // name in the editor info.
    // To this end, our base class InputTestsBase offers a hook for us to touch the EditorInfo.
    // We override this hook to write the package name that we need.
    @Override
    protected EditorInfo enrichEditorInfo(final EditorInfo ei) {
        if ("testBeforeJellyBeanTrue".equals(getName())) {
            ei.packageName = packageNameOfAppBeforeJellyBean;
        } else if ("testBeforeJellyBeanFalse".equals(getName())) {
            ei.packageName = packageNameOfAppAfterJellyBean;
        }
        return ei;
    }

    public void testBeforeJellyBeanTrue() {
        assertTrue("Couldn't successfully detect this app targets < Jelly Bean (package is "
                + packageNameOfAppBeforeJellyBean + ")",
                Settings.getInstance().getCurrent().isBeforeJellyBean());
    }

    public void testBeforeJellyBeanFalse() {
        assertFalse("Couldn't successfully detect this app targets >= Jelly Bean (package is "
                + packageNameOfAppAfterJellyBean + ")",
                Settings.getInstance().getCurrent().isBeforeJellyBean());
    }
}