/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.research;

import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.android.inputmethod.latin.R;

public class FeedbackFragment extends Fragment implements OnClickListener {
    private static final String TAG = FeedbackFragment.class.getSimpleName();

    public static final String KEY_FEEDBACK_STRING = "FeedbackString";
    public static final String KEY_INCLUDE_ACCOUNT_NAME = "IncludeAccountName";
    public static final String KEY_HAS_USER_RECORDING = "HasRecording";

    private EditText mEditText;
    private CheckBox mIncludingAccountNameCheckBox;
    private CheckBox mIncludingUserRecordingCheckBox;
    private Button mSendButton;
    private Button mCancelButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.research_feedback_fragment_layout, container,
                false);
        mEditText = (EditText) view.findViewById(R.id.research_feedback_contents);
        mEditText.requestFocus();
        mIncludingAccountNameCheckBox = (CheckBox) view.findViewById(
                R.id.research_feedback_include_account_name);
        mIncludingUserRecordingCheckBox = (CheckBox) view.findViewById(
                R.id.research_feedback_include_recording_checkbox);
        mIncludingUserRecordingCheckBox.setOnClickListener(this);

        mSendButton = (Button) view.findViewById(R.id.research_feedback_send_button);
        mSendButton.setOnClickListener(this);
        mCancelButton = (Button) view.findViewById(R.id.research_feedback_cancel_button);
        mCancelButton.setOnClickListener(this);

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else {
            final Bundle bundle = getActivity().getIntent().getExtras();
            if (bundle != null) {
                restoreState(bundle);
            }
        }
        return view;
    }

    @Override
    public void onClick(final View view) {
        final ResearchLogger researchLogger = ResearchLogger.getInstance();
        if (view == mIncludingUserRecordingCheckBox) {
            if (mIncludingUserRecordingCheckBox.isChecked()) {
                final Bundle bundle = new Bundle();
                onSaveInstanceState(bundle);

                // Let the user make a recording
                getActivity().finish();

                researchLogger.setFeedbackDialogBundle(bundle);
                researchLogger.onLeavingSendFeedbackDialog();
                researchLogger.startRecording();
            }
        } else if (view == mSendButton) {
            final Editable editable = mEditText.getText();
            final String feedbackContents = editable.toString();
            if (TextUtils.isEmpty(feedbackContents)) {
                Toast.makeText(getActivity(),
                        R.string.research_feedback_empty_feedback_error_message,
                        Toast.LENGTH_LONG).show();
            } else {
                final boolean isIncludingAccountName = mIncludingAccountNameCheckBox.isChecked();
                researchLogger.sendFeedback(feedbackContents, false /* isIncludingHistory */,
                        isIncludingAccountName, mIncludingUserRecordingCheckBox.isChecked());
                getActivity().finish();
                researchLogger.setFeedbackDialogBundle(null);
                researchLogger.onLeavingSendFeedbackDialog();
            }
        } else if (view == mCancelButton) {
            Log.d(TAG, "Finishing");
            getActivity().finish();
            researchLogger.setFeedbackDialogBundle(null);
            researchLogger.onLeavingSendFeedbackDialog();
        } else {
            Log.e(TAG, "Unknown view passed to FeedbackFragment.onClick()");
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle bundle) {
        final String savedFeedbackString = mEditText.getText().toString();

        bundle.putString(KEY_FEEDBACK_STRING, savedFeedbackString);
        bundle.putBoolean(KEY_INCLUDE_ACCOUNT_NAME, mIncludingAccountNameCheckBox.isChecked());
        bundle.putBoolean(KEY_HAS_USER_RECORDING, mIncludingUserRecordingCheckBox.isChecked());
    }

    private void restoreState(final Bundle bundle) {
        mEditText.setText(bundle.getString(KEY_FEEDBACK_STRING));
        mIncludingAccountNameCheckBox.setChecked(bundle.getBoolean(KEY_INCLUDE_ACCOUNT_NAME));
        mIncludingUserRecordingCheckBox.setChecked(bundle.getBoolean(KEY_HAS_USER_RECORDING));
    }
}
