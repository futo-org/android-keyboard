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

import android.app.Activity;
import android.os.Bundle;

import com.android.inputmethod.latin.R;

public class FeedbackActivity extends Activity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.research_feedback_activity);
        final FeedbackLayout layout = (FeedbackLayout) findViewById(R.id.research_feedback_layout);
        layout.setActivity(this);
    }

    @Override
    public void onBackPressed() {
        ResearchLogger.getInstance().onLeavingSendFeedbackDialog();
        super.onBackPressed();
    }
}
