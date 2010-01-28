/*
 * Copyright (C) 2008-2009 Google Inc.
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

import java.util.Locale;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Paint.Align;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.Keyboard;
import android.view.inputmethod.EditorInfo;

public class LatinKeyboard extends Keyboard {

    private Drawable mShiftLockIcon;
    private Drawable mShiftLockPreviewIcon;
    private Drawable mOldShiftIcon;
    private Drawable mOldShiftPreviewIcon;
    private Drawable mSpaceIcon;
    private Drawable mMicIcon;
    private Drawable mMicPreviewIcon;
    private Drawable m123MicIcon;
    private Drawable m123MicPreviewIcon;
    private Key mShiftKey;
    private Key mEnterKey;
    private Key mF1Key;
    private Key mSpaceKey;
    /* package */ Locale mLocale;
    private Resources mRes;
    private int mMode;
    private boolean mHasVoice;

    private int mExtensionResId; 
    
    private static final int SHIFT_OFF = 0;
    private static final int SHIFT_ON = 1;
    private static final int SHIFT_LOCKED = 2;
    
    private int mShiftState = SHIFT_OFF;

    static int sSpacebarVerticalCorrection;

    public LatinKeyboard(Context context, int xmlLayoutResId) {
        this(context, xmlLayoutResId, 0, false);
    }

    public LatinKeyboard(Context context, int xmlLayoutResId, int mode, boolean hasVoice) {
        super(context, xmlLayoutResId, mode);
        final Resources res = context.getResources();
        mMode = mode;
        mRes = res;
        mHasVoice = hasVoice;
        mShiftLockIcon = res.getDrawable(R.drawable.sym_keyboard_shift_locked);
        mShiftLockPreviewIcon = res.getDrawable(R.drawable.sym_keyboard_feedback_shift_locked);
        mShiftLockPreviewIcon.setBounds(0, 0, 
                mShiftLockPreviewIcon.getIntrinsicWidth(),
                mShiftLockPreviewIcon.getIntrinsicHeight());
        mSpaceIcon = res.getDrawable(R.drawable.sym_keyboard_space);
        mMicIcon = res.getDrawable(R.drawable.sym_keyboard_mic);
        mMicPreviewIcon = res.getDrawable(R.drawable.sym_keyboard_feedback_mic);
        sSpacebarVerticalCorrection = res.getDimensionPixelOffset(
                R.dimen.spacebar_vertical_correction);
        setF1Key();
    }

    public LatinKeyboard(Context context, int layoutTemplateResId, 
            CharSequence characters, int columns, int horizontalPadding) {
        super(context, layoutTemplateResId, characters, columns, horizontalPadding);
    }

    @Override
    protected Key createKeyFromXml(Resources res, Row parent, int x, int y, 
            XmlResourceParser parser) {
        Key key = new LatinKey(res, parent, x, y, parser);
        switch (key.codes[0]) {
        case 10:
            mEnterKey = key;
            break;
        case LatinKeyboardView.KEYCODE_F1:
            mF1Key = key;
            break;
        case 32:
            mSpaceKey = key;
            break;
        }
        return key;
    }

    void setImeOptions(Resources res, int mode, int options) {
        if (mEnterKey != null) {
            // Reset some of the rarely used attributes.
            mEnterKey.popupCharacters = null;
            mEnterKey.popupResId = 0;
            mEnterKey.text = null;
            switch (options&(EditorInfo.IME_MASK_ACTION|EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
                case EditorInfo.IME_ACTION_GO:
                    mEnterKey.iconPreview = null;
                    mEnterKey.icon = null;
                    mEnterKey.label = res.getText(R.string.label_go_key);
                    break;
                case EditorInfo.IME_ACTION_NEXT:
                    mEnterKey.iconPreview = null;
                    mEnterKey.icon = null;
                    mEnterKey.label = res.getText(R.string.label_next_key);
                    break;
                case EditorInfo.IME_ACTION_DONE:
                    mEnterKey.iconPreview = null;
                    mEnterKey.icon = null;
                    mEnterKey.label = res.getText(R.string.label_done_key);
                    break;
                case EditorInfo.IME_ACTION_SEARCH:
                    mEnterKey.iconPreview = res.getDrawable(
                            R.drawable.sym_keyboard_feedback_search);
                    mEnterKey.icon = res.getDrawable(
                            R.drawable.sym_keyboard_search);
                    mEnterKey.label = null;
                    break;
                case EditorInfo.IME_ACTION_SEND:
                    mEnterKey.iconPreview = null;
                    mEnterKey.icon = null;
                    mEnterKey.label = res.getText(R.string.label_send_key);
                    break;
                default:
                    if (mode == KeyboardSwitcher.MODE_IM) {
                        mEnterKey.icon = null;
                        mEnterKey.iconPreview = null;
                        mEnterKey.label = ":-)";
                        mEnterKey.text = ":-) ";
                        mEnterKey.popupResId = R.xml.popup_smileys;
                    } else {
                        mEnterKey.iconPreview = res.getDrawable(
                                R.drawable.sym_keyboard_feedback_return);
                        mEnterKey.icon = res.getDrawable(
                                R.drawable.sym_keyboard_return);
                        mEnterKey.label = null;
                    }
                    break;
            }
            // Set the initial size of the preview icon
            if (mEnterKey.iconPreview != null) {
                mEnterKey.iconPreview.setBounds(0, 0, 
                        mEnterKey.iconPreview.getIntrinsicWidth(),
                        mEnterKey.iconPreview.getIntrinsicHeight());
            }
        }
    }
    
    void enableShiftLock() {
        int index = getShiftKeyIndex();
        if (index >= 0) {
            mShiftKey = getKeys().get(index);
            if (mShiftKey instanceof LatinKey) {
                ((LatinKey)mShiftKey).enableShiftLock();
            }
            mOldShiftIcon = mShiftKey.icon;
            mOldShiftPreviewIcon = mShiftKey.iconPreview;
        }
    }

    void setShiftLocked(boolean shiftLocked) {
        if (mShiftKey != null) {
            if (shiftLocked) {
                mShiftKey.on = true;
                mShiftKey.icon = mShiftLockIcon;
                mShiftState = SHIFT_LOCKED;
            } else {
                mShiftKey.on = false;
                mShiftKey.icon = mShiftLockIcon;
                mShiftState = SHIFT_ON;
            }
        }
    }

    boolean isShiftLocked() {
        return mShiftState == SHIFT_LOCKED;
    }
    
    @Override
    public boolean setShifted(boolean shiftState) {
        boolean shiftChanged = false;
        if (mShiftKey != null) {
            if (shiftState == false) {
                shiftChanged = mShiftState != SHIFT_OFF;
                mShiftState = SHIFT_OFF;
                mShiftKey.on = false;
                mShiftKey.icon = mOldShiftIcon;
            } else {
                if (mShiftState == SHIFT_OFF) {
                    shiftChanged = mShiftState == SHIFT_OFF;
                    mShiftState = SHIFT_ON;
                    mShiftKey.icon = mShiftLockIcon;
                }
            }
        } else {
            return super.setShifted(shiftState);
        }
        return shiftChanged;
    }

    @Override
    public boolean isShifted() {
        if (mShiftKey != null) {
            return mShiftState != SHIFT_OFF;
        } else {
            return super.isShifted();
        }
    }

    public void setExtension(int resId) {
        mExtensionResId = resId;
    }

    public int getExtension() {
        return mExtensionResId;
    }

    private void setF1Key() {
        if (mF1Key == null) return;
        System.err.println("Setting F1 key");
        if (!mHasVoice) {
            mF1Key.label = ",";
            mF1Key.codes = new int[] { ',' };
            mF1Key.icon = null;
            mF1Key.iconPreview = null;
        } else {
            mF1Key.codes = new int[] { LatinKeyboardView.KEYCODE_VOICE };
            mF1Key.label = null;
            mF1Key.icon = mMicIcon;
            mF1Key.iconPreview = mMicPreviewIcon;
        }
    }

    private void updateSpaceBarForLocale() {
        if (mLocale != null) {
            // Create the graphic for spacebar
            Bitmap buffer = Bitmap.createBitmap(mSpaceKey.width, mSpaceIcon.getIntrinsicHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(buffer);
            canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            // TODO: Make the text size a customizable attribute
            paint.setTextSize(18);
            paint.setTextAlign(Align.CENTER);
            // Draw a drop shadow for the text
            paint.setShadowLayer(1f, 0, 0, 0xFF000000);
            paint.setColor(0xFF808080);
            canvas.drawText(mLocale.getDisplayLanguage(mLocale),
                    buffer.getWidth() / 2, - paint.ascent() + 2, paint);
            int x = (buffer.getWidth() - mSpaceIcon.getIntrinsicWidth()) / 2;
            int y = buffer.getHeight() - mSpaceIcon.getIntrinsicHeight();
            mSpaceIcon.setBounds(x, y, 
                    x + mSpaceIcon.getIntrinsicWidth(), y + mSpaceIcon.getIntrinsicHeight());
            mSpaceIcon.draw(canvas);
            mSpaceKey.icon = new BitmapDrawable(mRes, buffer);
            mSpaceKey.repeatable = false;
        } else {
            mSpaceKey.icon = mRes.getDrawable(R.drawable.sym_keyboard_space);
            mSpaceKey.repeatable = true;
        }
    }

    public void setLanguage(Locale locale) {
        if (mLocale != null && mLocale.equals(locale)) return;
        mLocale = locale;
        updateSpaceBarForLocale();
    }

    static class LatinKey extends Keyboard.Key {
        
        private boolean mShiftLockEnabled;
        
        public LatinKey(Resources res, Keyboard.Row parent, int x, int y, 
                XmlResourceParser parser) {
            super(res, parent, x, y, parser);
            if (popupCharacters != null && popupCharacters.length() == 0) {
                // If there is a keyboard with no keys specified in popupCharacters
                popupResId = 0;
            }
        }
        
        void enableShiftLock() {
            mShiftLockEnabled = true;
        }

        @Override
        public void onReleased(boolean inside) {
            if (!mShiftLockEnabled) {
                super.onReleased(inside);
            } else {
                pressed = !pressed;
            }
        }

        /**
         * Overriding this method so that we can reduce the target area for certain keys.
         */
        @Override
        public boolean isInside(int x, int y) {
            final int code = codes[0];
            if (code == KEYCODE_SHIFT ||
                    code == KEYCODE_DELETE) {
                y -= height / 10;
                if (code == KEYCODE_SHIFT) x += width / 6;
                if (code == KEYCODE_DELETE) x -= width / 6;
            } else if (code == LatinIME.KEYCODE_SPACE) {
                y += LatinKeyboard.sSpacebarVerticalCorrection;
            }
            return super.isInside(x, y);
        }
    }
}
