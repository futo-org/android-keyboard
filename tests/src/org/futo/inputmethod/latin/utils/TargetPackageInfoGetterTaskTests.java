package org.futo.inputmethod.latin.utils;

import static org.junit.Assert.assertNull;

import androidx.test.InstrumentationRegistry;

import org.junit.Test;

public class TargetPackageInfoGetterTaskTests {
    @Test
    public void missingEditorPackageDoesNotCrashPackageLookup() {
        final TargetPackageInfoGetterTask task = new TargetPackageInfoGetterTask(
                InstrumentationRegistry.getTargetContext(), new AsyncResultHolder<>("test"));
        assertNull(task.doInBackground((String) null));
    }
}
