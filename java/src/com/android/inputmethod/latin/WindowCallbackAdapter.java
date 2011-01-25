/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;

public class WindowCallbackAdapter implements Window.Callback {
    private final Window.Callback mPreviousCallback;

    public WindowCallbackAdapter(Window.Callback previousCallback) {
        mPreviousCallback = previousCallback;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (mPreviousCallback != null)
            return mPreviousCallback.dispatchGenericMotionEvent(event);
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mPreviousCallback != null)
            return mPreviousCallback.dispatchKeyEvent(event);
        return false;
    }

    @Override
    public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        if (mPreviousCallback != null)
            return mPreviousCallback.dispatchKeyShortcutEvent(event);
        return false;
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (mPreviousCallback != null)
            return mPreviousCallback.dispatchPopulateAccessibilityEvent(event);
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mPreviousCallback != null)
            return mPreviousCallback.dispatchTouchEvent(event);
        return false;
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent event) {
        if (mPreviousCallback != null)
            return mPreviousCallback.dispatchTrackballEvent(event);
        return false;
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        if (mPreviousCallback != null)
            mPreviousCallback.onActionModeFinished(mode);
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        if (mPreviousCallback != null)
            mPreviousCallback.onActionModeStarted(mode);
    }

    @Override
    public void onAttachedToWindow() {
        if (mPreviousCallback != null)
            mPreviousCallback.onAttachedToWindow();
    }

    @Override
    public void onContentChanged() {
        if (mPreviousCallback != null)
            mPreviousCallback.onContentChanged();
    }

    @Override
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        if (mPreviousCallback != null)
            return mPreviousCallback.onCreatePanelMenu(featureId, menu);
        return false;
    }

    @Override
    public View onCreatePanelView(int featureId) {
        if (mPreviousCallback != null)
            return mPreviousCallback.onCreatePanelView(featureId);
        return null;
    }

    @Override
    public void onDetachedFromWindow() {
        if (mPreviousCallback != null)
            mPreviousCallback.onDetachedFromWindow();
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        if (mPreviousCallback != null)
            return mPreviousCallback.onMenuItemSelected(featureId, item);
        return false;
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        if (mPreviousCallback != null)
            return mPreviousCallback.onMenuOpened(featureId, menu);
        return false;
    }

    @Override
    public void onPanelClosed(int featureId, Menu menu) {
        if (mPreviousCallback != null)
            mPreviousCallback.onPanelClosed(featureId, menu);
    }

    @Override
    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        if (mPreviousCallback != null)
            return mPreviousCallback.onPreparePanel(featureId, view, menu);
        return false;
    }

    @Override
    public boolean onSearchRequested() {
        if (mPreviousCallback != null)
            return mPreviousCallback.onSearchRequested();
        return false;
    }

    @Override
    public void onWindowAttributesChanged(LayoutParams attrs) {
        if (mPreviousCallback != null)
            mPreviousCallback.onWindowAttributesChanged(attrs);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (mPreviousCallback != null)
            mPreviousCallback.onWindowFocusChanged(hasFocus);
    }

    @Override
    public ActionMode onWindowStartingActionMode(Callback callback) {
        if (mPreviousCallback != null)
            return mPreviousCallback.onWindowStartingActionMode(callback);
        return null;
    }
}
