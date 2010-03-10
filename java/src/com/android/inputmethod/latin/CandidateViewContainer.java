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

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.LinearLayout;

public class CandidateViewContainer extends LinearLayout implements OnTouchListener {

    private View mButtonLeft;
    private View mButtonRight;
    private View mButtonLeftLayout;
    private View mButtonRightLayout;
    private CandidateView mCandidates;
    
    public CandidateViewContainer(Context screen, AttributeSet attrs) {
        super(screen, attrs);
    }

    public void initViews() {
        if (mCandidates == null) {
            mButtonLeftLayout = findViewById(R.id.candidate_left_parent);
            mButtonLeft = findViewById(R.id.candidate_left);
            if (mButtonLeft != null) {
                mButtonLeft.setOnTouchListener(this);
            }
            mButtonRightLayout = findViewById(R.id.candidate_right_parent);
            mButtonRight = findViewById(R.id.candidate_right);
            if (mButtonRight != null) {
                mButtonRight.setOnTouchListener(this);
            }
            mCandidates = (CandidateView) findViewById(R.id.candidates);
        }
    }

    @Override
    public void requestLayout() {
        if (mCandidates != null) {
            int availableWidth = mCandidates.getWidth();
            int neededWidth = mCandidates.computeHorizontalScrollRange();
            int x = mCandidates.getScrollX();
            boolean leftVisible = x > 0;
            boolean rightVisible = x + availableWidth < neededWidth;
            if (mButtonLeftLayout != null) {
                mButtonLeftLayout.setVisibility(leftVisible ? VISIBLE : GONE);
            }
            if (mButtonRightLayout != null) {
                mButtonRightLayout.setVisibility(rightVisible ? VISIBLE : GONE);
            }
        }
        super.requestLayout();
    }

    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (v == mButtonRight) {
                mCandidates.scrollNext();
            } else if (v == mButtonLeft) {
                mCandidates.scrollPrev();
            }
        }
        return false;
    }
    
}
