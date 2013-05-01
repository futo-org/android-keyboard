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

package com.android.inputmethod.latin.setup;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import com.android.inputmethod.compat.TextViewCompatUtils;
import com.android.inputmethod.compat.ViewCompatUtils;
import com.android.inputmethod.latin.CollectionUtils;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.SettingsActivity;
import com.android.inputmethod.latin.StaticInnerHandlerWrapper;

import java.util.ArrayList;

// TODO: Use Fragment to implement welcome screen and setup steps.
public final class SetupActivity extends Activity implements View.OnClickListener {
    private static final String TAG = SetupActivity.class.getSimpleName();

    private View mWelcomeScreen;
    private View mSetupScreen;
    private Uri mWelcomeVideoUri;
    private VideoView mWelcomeVideoView;
    private View mActionStart;
    private View mActionNext;
    private TextView mStep1Bullet;
    private TextView mActionFinish;
    private SetupStepGroup mSetupStepGroup;
    private static final String STATE_STEP = "step";
    private int mStepNumber;
    private static final int STEP_0 = 0;
    private static final int STEP_1 = 1;
    private static final int STEP_2 = 2;
    private static final int STEP_3 = 3;
    private boolean mWasLanguageAndInputSettingsInvoked;

    private final SettingsPoolingHandler mHandler = new SettingsPoolingHandler(this);

    static final class SettingsPoolingHandler extends StaticInnerHandlerWrapper<SetupActivity> {
        private static final int MSG_POLLING_IME_SETTINGS = 0;
        private static final long IME_SETTINGS_POLLING_INTERVAL = 200;

        public SettingsPoolingHandler(final SetupActivity outerInstance) {
            super(outerInstance);
        }

        @Override
        public void handleMessage(final Message msg) {
            final SetupActivity setupActivity = getOuterInstance();
            if (setupActivity == null) {
                return;
            }
            switch (msg.what) {
            case MSG_POLLING_IME_SETTINGS:
                if (SetupActivity.isThisImeEnabled(setupActivity)) {
                    setupActivity.invokeSetupWizardOfThisIme();
                    return;
                }
                startPollingImeSettings();
                break;
            }
        }

        public void startPollingImeSettings() {
            sendMessageDelayed(obtainMessage(MSG_POLLING_IME_SETTINGS),
                    IME_SETTINGS_POLLING_INTERVAL);
        }

        public void cancelPollingImeSettings() {
            removeMessages(MSG_POLLING_IME_SETTINGS);
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        setTheme(android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.setup_wizard);

        RichInputMethodManager.init(this);

        if (savedInstanceState == null) {
            mStepNumber = determineSetupStepNumber();
        } else {
            mStepNumber = savedInstanceState.getInt(STATE_STEP);
        }

        if (mStepNumber == STEP_3) {
            // This IME already has been enabled and set as current IME.
            // TODO: Implement tutorial.
            invokeSettingsOfThisIme();
            finish();
            return;
        }

        final String applicationName = getResources().getString(getApplicationInfo().labelRes);
        mWelcomeScreen = findViewById(R.id.setup_welcome_screen);
        final TextView welcomeTitle = (TextView)findViewById(R.id.setup_welcome_title);
        welcomeTitle.setText(getString(R.string.setup_welcome_title, applicationName));

        mSetupScreen = findViewById(R.id.setup_steps_screen);
        final TextView stepsTitle = (TextView)findViewById(R.id.setup_title);
        stepsTitle.setText(getString(R.string.setup_steps_title, applicationName));

        final SetupStepIndicatorView indicatorView =
                (SetupStepIndicatorView)findViewById(R.id.setup_step_indicator);
        mSetupStepGroup = new SetupStepGroup(indicatorView);

        mStep1Bullet = (TextView)findViewById(R.id.setup_step1_bullet);
        mStep1Bullet.setOnClickListener(this);
        final SetupStep step1 = new SetupStep(STEP_1, applicationName,
                mStep1Bullet, findViewById(R.id.setup_step1),
                R.string.setup_step1_title, R.string.setup_step1_instruction,
                R.string.setup_step1_finished_instruction, R.drawable.ic_setup_step1,
                R.string.setup_step1_action);
        step1.setAction(new Runnable() {
            @Override
            public void run() {
                invokeLanguageAndInputSettings();
                mHandler.startPollingImeSettings();
            }
        });
        mSetupStepGroup.addStep(step1);

        final SetupStep step2 = new SetupStep(STEP_2, applicationName,
                (TextView)findViewById(R.id.setup_step2_bullet), findViewById(R.id.setup_step2),
                R.string.setup_step2_title, R.string.setup_step2_instruction,
                0 /* finishedInstruction */, R.drawable.ic_setup_step2,
                R.string.setup_step2_action);
        step2.setAction(new Runnable() {
            @Override
            public void run() {
                // Invoke input method picker.
                RichInputMethodManager.getInstance().getInputMethodManager()
                        .showInputMethodPicker();
            }
        });
        mSetupStepGroup.addStep(step2);

        final SetupStep step3 = new SetupStep(STEP_3, applicationName,
                (TextView)findViewById(R.id.setup_step3_bullet), findViewById(R.id.setup_step3),
                R.string.setup_step3_title, R.string.setup_step3_instruction,
                0 /* finishedInstruction */, R.drawable.ic_setup_step3,
                R.string.setup_step3_action);
        step3.setAction(new Runnable() {
            @Override
            public void run() {
                invokeSubtypeEnablerOfThisIme();
            }
        });
        mSetupStepGroup.addStep(step3);

        mWelcomeVideoUri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(getPackageName())
                .path(Integer.toString(R.raw.setup_welcome_video))
                .build();
        mWelcomeVideoView = (VideoView)findViewById(R.id.setup_welcome_video);
        mWelcomeVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(final MediaPlayer mp) {
                mp.start();
            }
        });
        mWelcomeVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(final MediaPlayer mp) {
                // Now VideoView has been laid-out and ready to play, remove background of it to
                // reveal the video.
                mWelcomeVideoView.setBackgroundResource(0);
            }
        });
        final ImageView welcomeImageView = (ImageView)findViewById(R.id.setup_welcome_image);
        mWelcomeVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(final MediaPlayer mp, final int what, final int extra) {
                Log.e(TAG, "Playing welcome video causes error: what=" + what + " extra=" + extra);
                mWelcomeVideoView.setVisibility(View.GONE);
                welcomeImageView.setImageResource(R.raw.setup_welcome_image);
                welcomeImageView.setVisibility(View.VISIBLE);
                return true;
            }
        });

        mActionStart = findViewById(R.id.setup_start_label);
        mActionStart.setOnClickListener(this);
        mActionNext = findViewById(R.id.setup_next);
        mActionNext.setOnClickListener(this);
        mActionFinish = (TextView)findViewById(R.id.setup_finish);
        TextViewCompatUtils.setCompoundDrawablesRelativeWithIntrinsicBounds(mActionFinish,
                getResources().getDrawable(R.drawable.ic_setup_finish), null, null, null);
        mActionFinish.setOnClickListener(this);
    }

    @Override
    public void onClick(final View v) {
        if (v == mActionFinish) {
            finish();
            return;
        }
        final int stepState = determineSetupState();
        final int nextStep;
        if (v == mActionStart) {
            nextStep = STEP_1;
        } else if (v == mActionNext) {
            nextStep = mStepNumber + 1;
        } else if (v == mStep1Bullet && stepState == STEP_2) {
            nextStep = STEP_1;
        } else {
            nextStep = mStepNumber;
        }
        if (mStepNumber != nextStep) {
            mStepNumber = nextStep;
            updateSetupStepView();
        }
    }

    private void invokeSetupWizardOfThisIme() {
        final Intent intent = new Intent();
        intent.setClass(this, SetupActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void invokeSettingsOfThisIme() {
        final Intent intent = new Intent();
        intent.setClass(this, SettingsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void invokeLanguageAndInputSettings() {
        final Intent intent = new Intent();
        intent.setAction(Settings.ACTION_INPUT_METHOD_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        startActivity(intent);
        mWasLanguageAndInputSettingsInvoked = true;
    }

    private void invokeSubtypeEnablerOfThisIme() {
        final InputMethodInfo imi =
                RichInputMethodManager.getInstance().getInputMethodInfoOfThisIme();
        final Intent intent = new Intent();
        intent.setAction(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.putExtra(Settings.EXTRA_INPUT_METHOD_ID, imi.getId());
        startActivity(intent);
    }

    /**
     * Check if the IME specified by the context is enabled.
     * Note that {@link RichInputMethodManager} must have been initialized before calling this
     * method.
     *
     * @param context package context of the IME to be checked.
     * @return true if this IME is enabled.
     */
    public static boolean isThisImeEnabled(final Context context) {
        final String packageName = context.getPackageName();
        final InputMethodManager imm = RichInputMethodManager.getInstance().getInputMethodManager();
        for (final InputMethodInfo imi : imm.getEnabledInputMethodList()) {
            if (packageName.equals(imi.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the IME specified by the context is the current IME.
     * Note that {@link RichInputMethodManager} must have been initialized before calling this
     * method.
     *
     * @param context package context of the IME to be checked.
     * @return true if this IME is the current IME.
     */
    public static boolean isThisImeCurrent(final Context context) {
        final InputMethodInfo myImi =
                RichInputMethodManager.getInstance().getInputMethodInfoOfThisIme();
        final String currentImeId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return myImi.getId().equals(currentImeId);
    }

    private int determineSetupState() {
        mHandler.cancelPollingImeSettings();
        if (!isThisImeEnabled(this)) {
            return STEP_1;
        }
        if (!isThisImeCurrent(this)) {
            return STEP_2;
        }
        return STEP_3;
    }

    private int determineSetupStepNumber() {
        final int stepState = determineSetupState();
        if (stepState == STEP_1) {
            return mWasLanguageAndInputSettingsInvoked ? STEP_1 : STEP_0;
        }
        return stepState;
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_STEP, mStepNumber);
    }

    @Override
    protected void onRestoreInstanceState(final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mStepNumber = savedInstanceState.getInt(STATE_STEP);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mStepNumber = determineSetupStepNumber();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        mStepNumber = determineSetupStepNumber();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSetupStepView();
    }

    @Override
    public void onBackPressed() {
        if (mStepNumber == STEP_1) {
            mStepNumber = STEP_0;
            updateSetupStepView();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        mWelcomeVideoView.stopPlayback();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(final boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            return;
        }
        mStepNumber = determineSetupStepNumber();
        updateSetupStepView();
    }

    private void updateSetupStepView() {
        final boolean welcomeScreen = (mStepNumber == STEP_0);
        mWelcomeScreen.setVisibility(welcomeScreen ? View.VISIBLE : View.GONE);
        mSetupScreen.setVisibility(welcomeScreen ? View.GONE: View.VISIBLE);
        if (welcomeScreen) {
            mWelcomeVideoView.setVideoURI(mWelcomeVideoUri);
            mWelcomeVideoView.start();
            return;
        }
        mWelcomeVideoView.stopPlayback();
        final boolean isStepActionAlreadyDone = mStepNumber < determineSetupState();
        mSetupStepGroup.enableStep(mStepNumber, isStepActionAlreadyDone);
        mActionNext.setVisibility(isStepActionAlreadyDone ? View.VISIBLE : View.GONE);
        mActionFinish.setVisibility((mStepNumber == STEP_3) ? View.VISIBLE : View.GONE);
    }

    static final class SetupStep implements View.OnClickListener {
        public final int mStepNo;
        private final View mStepView;
        private final TextView mBulletView;
        private final int mActivatedColor;
        private final int mDeactivatedColor;
        private final String mInstruction;
        private final String mFinishedInstruction;
        private final TextView mActionLabel;
        private Runnable mAction;

        public SetupStep(final int stepNo, final String applicationName, final TextView bulletView,
                final View stepView, final int title, final int instruction,
                final int finishedInstruction,final int actionIcon, final int actionLabel) {
            mStepNo = stepNo;
            mStepView = stepView;
            mBulletView = bulletView;
            final Resources res = stepView.getResources();
            mActivatedColor = res.getColor(R.color.setup_text_action);
            mDeactivatedColor = res.getColor(R.color.setup_text_dark);

            final TextView titleView = (TextView)mStepView.findViewById(R.id.setup_step_title);
            titleView.setText(res.getString(title, applicationName));
            mInstruction = (instruction == 0) ? null
                    : res.getString(instruction, applicationName);
            mFinishedInstruction = (finishedInstruction == 0) ? null
                    : res.getString(finishedInstruction, applicationName);

            mActionLabel = (TextView)mStepView.findViewById(R.id.setup_step_action_label);
            mActionLabel.setText(res.getString(actionLabel));
            if (actionIcon == 0) {
                final int paddingEnd = ViewCompatUtils.getPaddingEnd(mActionLabel);
                ViewCompatUtils.setPaddingRelative(mActionLabel, paddingEnd, 0, paddingEnd, 0);
            } else {
                TextViewCompatUtils.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        mActionLabel, res.getDrawable(actionIcon), null, null, null);
            }
        }

        public void setEnabled(final boolean enabled, final boolean isStepActionAlreadyDone) {
            mStepView.setVisibility(enabled ? View.VISIBLE : View.GONE);
            mBulletView.setTextColor(enabled ? mActivatedColor : mDeactivatedColor);
            final TextView instructionView = (TextView)mStepView.findViewById(
                    R.id.setup_step_instruction);
            instructionView.setText(isStepActionAlreadyDone ? mFinishedInstruction : mInstruction);
            mActionLabel.setVisibility(isStepActionAlreadyDone ? View.GONE : View.VISIBLE);
        }

        public void setAction(final Runnable action) {
            mActionLabel.setOnClickListener(this);
            mAction = action;
        }

        @Override
        public void onClick(final View v) {
            if (v == mActionLabel && mAction != null) {
                mAction.run();
                return;
            }
        }
    }

    static final class SetupStepGroup {
        private final SetupStepIndicatorView mIndicatorView;
        private final ArrayList<SetupStep> mGroup = CollectionUtils.newArrayList();

        public SetupStepGroup(final SetupStepIndicatorView indicatorView) {
            mIndicatorView = indicatorView;
        }

        public void addStep(final SetupStep step) {
            mGroup.add(step);
        }

        public void enableStep(final int enableStepNo, final boolean isStepActionAlreadyDone) {
            for (final SetupStep step : mGroup) {
                step.setEnabled(step.mStepNo == enableStepNo, isStepActionAlreadyDone);
            }
            mIndicatorView.setIndicatorPosition(enableStepNo - STEP_1, mGroup.size());
        }
    }
}
