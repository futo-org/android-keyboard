/*
 * Copyright (C) 2012, The Android Open Source Project
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

import com.android.inputmethod.latin.utils.JniUtils;

import java.util.Locale;

public final class DicTraverseSession {
    static {
        JniUtils.loadNativeLibrary();
    }

    private static native long setDicTraverseSessionNative(String locale, long dictSize);
    private static native void initDicTraverseSessionNative(long nativeDicTraverseSession,
            long dictionary, int[] previousWord, int previousWordLength);
    private static native void releaseDicTraverseSessionNative(long nativeDicTraverseSession);

    private long mNativeDicTraverseSession;

    public DicTraverseSession(Locale locale, long dictionary, long dictSize) {
        mNativeDicTraverseSession = createNativeDicTraverseSession(
                locale != null ? locale.toString() : "", dictSize);
        initSession(dictionary);
    }

    public long getSession() {
        return mNativeDicTraverseSession;
    }

    public void initSession(long dictionary) {
        initSession(dictionary, null, 0);
    }

    public void initSession(long dictionary, int[] previousWord, int previousWordLength) {
        initDicTraverseSessionNative(
                mNativeDicTraverseSession, dictionary, previousWord, previousWordLength);
    }

    private final long createNativeDicTraverseSession(String locale, long dictSize) {
        return setDicTraverseSessionNative(locale, dictSize);
    }

    private void closeInternal() {
        if (mNativeDicTraverseSession != 0) {
            releaseDicTraverseSessionNative(mNativeDicTraverseSession);
            mNativeDicTraverseSession = 0;
        }
    }

    public void close() {
        closeInternal();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            closeInternal();
        } finally {
            super.finalize();
        }
    }
}
