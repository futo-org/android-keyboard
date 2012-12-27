/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public final class SeekBarDialog implements DialogInterface.OnClickListener,
        OnSeekBarChangeListener {
    public interface Listener {
        public void onPositiveButtonClick(final SeekBarDialog dialog);
        public void onNegativeButtonClick(final SeekBarDialog dialog);
        public void onProgressChanged(final SeekBarDialog dialog);
        public void onStartTrackingTouch(final SeekBarDialog dialog);
        public void onStopTrackingTouch(final SeekBarDialog dialog);
    }

    public static class Adapter implements Listener {
        @Override
        public void onPositiveButtonClick(final SeekBarDialog dialog) {}
        @Override
        public void onNegativeButtonClick(final SeekBarDialog dialog) { dialog.dismiss(); }
        @Override
        public void onProgressChanged(final SeekBarDialog dialog) {}
        @Override
        public void onStartTrackingTouch(final SeekBarDialog dialog) {}
        @Override
        public void onStopTrackingTouch(final SeekBarDialog dialog) {}
    }

    private static final Listener EMPTY_ADAPTER = new Adapter();

    private final AlertDialog mDialog;
    private final Listener mListener;
    private final TextView mValueView;
    private final SeekBar mSeekBar;
    private final String mValueFormat;

    private int mValue;

    private SeekBarDialog(final Builder builder) {
        final AlertDialog.Builder dialogBuilder = builder.mDialogBuilder;
        dialogBuilder.setView(builder.mView);
        dialogBuilder.setPositiveButton(android.R.string.ok, this);
        dialogBuilder.setNegativeButton(android.R.string.cancel, this);
        mDialog = dialogBuilder.create();
        mListener = (builder.mListener == null) ? EMPTY_ADAPTER : builder.mListener;
        mValueView = (TextView)builder.mView.findViewById(R.id.seek_bar_dialog_value);
        mSeekBar = (SeekBar)builder.mView.findViewById(R.id.seek_bar_dialog_bar);
        mSeekBar.setMax(builder.mMaxValue);
        mSeekBar.setOnSeekBarChangeListener(this);
        if (builder.mValueFormatResId == 0) {
            mValueFormat = "%s";
        } else {
            mValueFormat = mDialog.getContext().getString(builder.mValueFormatResId);
        }
    }

    public void setValue(final int value, final boolean fromUser) {
        mValue = value;
        mValueView.setText(String.format(mValueFormat, value));
        if (!fromUser) {
            mSeekBar.setProgress(value);
        }
    }

    public int getValue() {
        return mValue;
    }

    public CharSequence getValueText() {
        return mValueView.getText();
    }

    public void show() {
        mDialog.show();
    }

    public void dismiss() {
        mDialog.dismiss();
    }

    @Override
    public void onClick(final DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            mListener.onPositiveButtonClick(this);
            return;
        }
        if (which == DialogInterface.BUTTON_NEGATIVE) {
            mListener.onNegativeButtonClick(this);
            return;
        }
    }

    @Override
    public void onProgressChanged(final SeekBar seekBar, final int progress,
            final boolean fromUser) {
        setValue(progress, fromUser);
        if (fromUser) {
            mListener.onProgressChanged(this);
        }
    }

    @Override
    public void onStartTrackingTouch(final SeekBar seekBar) {
        mListener.onStartTrackingTouch(this);
    }

    @Override
    public void onStopTrackingTouch(final SeekBar seekBar) {
        mListener.onStopTrackingTouch(this);
    }

    public static final class Builder {
        final AlertDialog.Builder mDialogBuilder;
        final View mView;

        int mMaxValue;
        int mValueFormatResId;
        int mValue;
        Listener mListener;

        public Builder(final Context context) {
            mDialogBuilder = new AlertDialog.Builder(context);
            mView = LayoutInflater.from(context).inflate(R.layout.seek_bar_dialog, null);
        }

        public Builder setTitle(final int resId) {
            mDialogBuilder.setTitle(resId);
            return this;
        }

        public Builder setMaxValue(final int max) {
            mMaxValue = max;
            return this;
        }

        public Builder setValueFromat(final int resId) {
            mValueFormatResId = resId;
            return this;
        }

        public Builder setValue(final int value) {
            mValue = value;
            return this;
        }

        public Builder setListener(final Listener listener) {
            mListener = listener;
            return this;
        }

        public SeekBarDialog create() {
            final SeekBarDialog dialog = new SeekBarDialog(this);
            dialog.setValue(mValue, false /* fromUser */);
            return dialog;
        }
    }
}
