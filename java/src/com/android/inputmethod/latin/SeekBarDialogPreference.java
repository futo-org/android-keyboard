/*
 * Copyright (C) 2013 The Android Open Source Project
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

    private TextView mValueView;
    private SeekBar mSeekBar;

    private ValueProxy mValueProxy;

    public SeekBarDialogPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.SeekBarDialogPreference, 0, 0);
        mValueFormatResId = a.getResourceId(R.styleable.SeekBarDialogPreference_valueFormatText, 0);
        mMaxValue = a.getInt(R.styleable.SeekBarDialogPreference_maxValue, 0);
        a.recycle();
        setDialogLayoutResource(R.layout.seek_bar_dialog);
    }

    public void setInterface(final ValueProxy proxy) {
        mValueProxy = proxy;
        setSummary(getValueText(proxy.readValue(getKey())));
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
        mSeekBar.setMax(mMaxValue);
        mSeekBar.setOnSeekBarChangeListener(this);
        mValueView = (TextView)view.findViewById(R.id.seek_bar_dialog_value);
        return view;
    }

    private void setValue(final int value, final boolean fromUser) {
        mValueView.setText(getValueText(value));
        if (!fromUser) {
            mSeekBar.setProgress(value);
        }
    }

    @Override
    protected void onBindDialogView(final View view) {
        setValue(mValueProxy.readValue(getKey()), false /* fromUser */);
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
            setValue(mValueProxy.readDefaultValue(getKey()), false /* fromUser */);
        }
        if (which != DialogInterface.BUTTON_NEGATIVE) {
            setSummary(mValueView.getText());
            mValueProxy.writeValue(mSeekBar.getProgress(), getKey());
        }
    }

    @Override
    public void onProgressChanged(final SeekBar seekBar, final int progress,
            final boolean fromUser) {
        setValue(progress, fromUser);
    }

    @Override
    public void onStartTrackingTouch(final SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(final SeekBar seekBar) {
        mValueProxy.feedbackValue(seekBar.getProgress());
    }
}
