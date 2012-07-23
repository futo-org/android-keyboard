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

package com.android.inputmethod.research;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import com.android.inputmethod.latin.R;

public class FeedbackFragment extends Fragment {
    private EditText mEditText;
    private CheckBox mCheckBox;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.research_feedback_fragment_layout, container,
                false);
        mEditText = (EditText) view.findViewById(R.id.research_feedback_contents);
        mCheckBox = (CheckBox) view.findViewById(R.id.research_feedback_include_history);

        final Button sendButton = (Button) view.findViewById(
                R.id.research_feedback_send_button);
        sendButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final Editable editable = mEditText.getText();
                final String feedbackContents = editable.toString();
                final boolean includeHistory = mCheckBox.isChecked();
                ResearchLogger.getInstance().sendFeedback(feedbackContents, includeHistory);
                final Activity activity = FeedbackFragment.this.getActivity();
                activity.finish();
                ResearchLogger.getInstance().onLeavingSendFeedbackDialog();
            }
        });

        final Button cancelButton = (Button) view.findViewById(
                R.id.research_feedback_cancel_button);
        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final Activity activity = FeedbackFragment.this.getActivity();
                activity.finish();
                ResearchLogger.getInstance().onLeavingSendFeedbackDialog();
            }
        });

        return view;
    }
}
