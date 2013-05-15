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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

public final class SeekBarDialogPreference extends DialogPreference
        implements SeekBar.OnSeekBarChangeListener {
    public interface ValueProxy {
        public int readValue(final String key);
        public int readDefaultValue(final String key);
        public void writeValue(final int value, final String key);
        public void feedbackValue(final int value);
    }

    private final int mValueFormatResId;
    private final int mMaxValue;
    private final int mMinValue;
    private final int mStepValue;

    private TextView mValueView;
    private SeekBar mSeekBar;

    private ValueProxy mValueProxy;

    public SeekBarDialogPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.SeekBarDialogPreference, 0, 0);
        mValueFormatResId = a.getResourceId(R.styleable.SeekBarDialogPreference_valueFormatText, 0);
        mMaxValue = a.getInt(R.styleable.SeekBarDialogPreference_maxValue, 0);
        mMinValue = a.getInt(R.styleable.SeekBarDialogPreference_minValue, 0);
        mStepValue = a.getInt(R.styleable.SeekBarDialogPreference_stepValue, 0);
        a.recycle();
        setDialogLayoutResource(R.layout.seek_bar_dialog);
    }

    public void setInterface(final ValueProxy proxy) {
        mValueProxy = proxy;
        setSummary(getValueText(clipValue(proxy.readValue(getKey()))));
    }

    private String getValueText(final int value) {
        if (mValueFormatResId == 0) {
            return Integer.toString(value);
        } else {
            return getContext().getString(mValueFormatResId, value);
        }
    }

    @Override
    protected View onCreateDialogView() {
        final View view = super.onCreateDialogView();
        mSeekBar = (SeekBar)view.findViewById(R.id.seek_bar_dialog_bar);
        mSeekBar.setMax(mMaxValue - mMinValue);
        mSeekBar.setOnSeekBarChangeListener(this);
        mValueView = (TextView)view.findViewById(R.id.seek_bar_dialog_value);
        return view;
    }

    private int getProgressFromValue(final int value) {
        return value - mMinValue;
    }

    private int getValueFromProgress(final int progress) {
        return progress + mMinValue;
    }

    private int clipValue(final int value) {
        final int clippedValue = Math.min(mMaxValue, Math.max(mMinValue, value));
        if (mStepValue <= 1) {
            return clippedValue;
        }
        return clippedValue - (clippedValue % mStepValue);
    }

    private int getClippedValueFromProgress(final int progress) {
        return clipValue(getValueFromProgress(progress));
    }

    private void setValue(final int value, final boolean fromUser) {
        mValueView.setText(getValueText(value));
        if (!fromUser) {
            mSeekBar.setProgress(getProgressFromValue(value));
        }
    }

    @Override
    protected void onBindDialogView(final View view) {
        setValue(clipValue(mValueProxy.readValue(getKey())), false /* fromUser */);
    }

    @Override
    protected void onPrepareDialogBuilder(final AlertDialog.Builder builder) {
        builder.setPositiveButton(android.R.string.ok, this)
            .setNegativeButton(android.R.string.cancel, this)
            .setNeutralButton(R.string.button_default, this);
    }

    @Override
    public void onClick(final DialogInterface dialog, final int which) {
        super.onClick(dialog, which);
        if (which == DialogInterface.BUTTON_NEUTRAL) {
            setValue(clipValue(mValueProxy.readDefaultValue(getKey())), false /* fromUser */);
        }
        if (which != DialogInterface.BUTTON_NEGATIVE) {
            setSummary(mValueView.getText());
            mValueProxy.writeValue(getClippedValueFromProgress(mSeekBar.getProgress()), getKey());
        }
    }

    @Override
    public void onProgressChanged(final SeekBar seekBar, final int progress,
            final boolean fromUser) {
        setValue(getClippedValueFromProgress(progress), fromUser);
    }

    @Override
    public void onStartTrackingTouch(final SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(final SeekBar seekBar) {
        mValueProxy.feedbackValue(getClippedValueFromProgress(seekBar.getProgress()));
    }
}
