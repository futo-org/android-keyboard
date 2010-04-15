/*
 * Copyright (C) 2009 Google Inc.
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

package com.android.inputmethod.voice;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.inputmethod.latin.R;

/**
 * The user interface for the "Speak now" and "working" states.
 * Displays a recognition dialog (with waveform, voice meter, etc.),
 * plays beeps, shows errors, etc.
 */
public class RecognitionView {
    private static final String TAG = "RecognitionView";

    private Handler mUiHandler;  // Reference to UI thread
    private View mView;
    private Context mContext;

    private ImageView mImage;
    private TextView mText;
    private View mButton;
    private TextView mButtonText;
    private View mProgress;

    private Drawable mInitializing;
    private Drawable mError;
    private List<Drawable> mSpeakNow;

    private float mVolume = 0.0f;
    private int mLevel = 0;

    private enum State {LISTENING, WORKING, READY}
    private State mState = State.READY;

    private float mMinMicrophoneLevel;
    private float mMaxMicrophoneLevel;

    /** Updates the microphone icon to show user their volume.*/
    private Runnable mUpdateVolumeRunnable = new Runnable() {
        public void run() {
            if (mState != State.LISTENING) {
                return;
            }

            final float min = mMinMicrophoneLevel;
            final float max = mMaxMicrophoneLevel;
            final int maxLevel = mSpeakNow.size() - 1;

            int index = (int) ((mVolume - min) / (max - min) * maxLevel);
            final int level = Math.min(Math.max(0, index), maxLevel);

            if (level != mLevel) {
                mImage.setImageDrawable(mSpeakNow.get(level));
                mLevel = level;
            }
            mUiHandler.postDelayed(mUpdateVolumeRunnable, 50);
        }
      };

    public RecognitionView(Context context, OnClickListener clickListener) {
        mUiHandler = new Handler();

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(
            Context.LAYOUT_INFLATER_SERVICE);
        mView = inflater.inflate(R.layout.recognition_status, null);
        ContentResolver cr = context.getContentResolver();
        mMinMicrophoneLevel = SettingsUtil.getSettingsFloat(
                cr, SettingsUtil.LATIN_IME_MIN_MICROPHONE_LEVEL, 15.f);
        mMaxMicrophoneLevel = SettingsUtil.getSettingsFloat(
                cr, SettingsUtil.LATIN_IME_MAX_MICROPHONE_LEVEL, 30.f);

        // Pre-load volume level images
        Resources r = context.getResources();

        mSpeakNow = new ArrayList<Drawable>();
        mSpeakNow.add(r.getDrawable(R.drawable.speak_now_level0));
        mSpeakNow.add(r.getDrawable(R.drawable.speak_now_level1));
        mSpeakNow.add(r.getDrawable(R.drawable.speak_now_level2));
        mSpeakNow.add(r.getDrawable(R.drawable.speak_now_level3));
        mSpeakNow.add(r.getDrawable(R.drawable.speak_now_level4));
        mSpeakNow.add(r.getDrawable(R.drawable.speak_now_level5));
        mSpeakNow.add(r.getDrawable(R.drawable.speak_now_level6));

        mInitializing = r.getDrawable(R.drawable.mic_slash);
        mError = r.getDrawable(R.drawable.caution);

        mImage = (ImageView) mView.findViewById(R.id.image);
        mButton = mView.findViewById(R.id.button);
        mButton.setOnClickListener(clickListener);
        mText = (TextView) mView.findViewById(R.id.text);
        mButtonText = (TextView) mView.findViewById(R.id.button_text);
        mProgress = mView.findViewById(R.id.progress);

        mContext = context;
    }

    public View getView() {
        return mView;
    }

    public void restoreState() {
        mUiHandler.post(new Runnable() {
            public void run() {
                // Restart the spinner
                if (mState == State.WORKING) {
                    ((ProgressBar)mProgress).setIndeterminate(false);
                    ((ProgressBar)mProgress).setIndeterminate(true);
                }
            }
        });
    }

    public void showInitializing() {
        mUiHandler.post(new Runnable() {
            public void run() {
                prepareDialog(false, mContext.getText(R.string.voice_initializing), mInitializing,
                        mContext.getText(R.string.cancel)); 
            }
          });
    }

    public void showListening() {
        mUiHandler.post(new Runnable() {
            public void run() {
                mState = State.LISTENING;
                prepareDialog(false, mContext.getText(R.string.voice_listening), mSpeakNow.get(0),
                        mContext.getText(R.string.cancel));
            }
          });
        mUiHandler.postDelayed(mUpdateVolumeRunnable, 50);
    }

    public void updateVoiceMeter(final float rmsdB) {
        mVolume = rmsdB;
    }

    public void showError(final String message) {
        mUiHandler.post(new Runnable() {
            public void run() {
                mState = State.READY;
                prepareDialog(false, message, mError, mContext.getText(R.string.ok));
            }
          });
    }

    public void showWorking(
        final ByteArrayOutputStream waveBuffer,
        final int speechStartPosition,
        final int speechEndPosition) {

        mUiHandler.post(new Runnable() {
            public void run() {
                mState = State.WORKING;
                prepareDialog(true, mContext.getText(R.string.voice_working), null, mContext
                        .getText(R.string.cancel));
                final ShortBuffer buf = ByteBuffer.wrap(waveBuffer.toByteArray()).order(
                        ByteOrder.nativeOrder()).asShortBuffer();
                buf.position(0);
                waveBuffer.reset();
                showWave(buf, speechStartPosition / 2, speechEndPosition / 2);
            }
          });
    }
    
    private void prepareDialog(boolean spinVisible, CharSequence text, Drawable image,
            CharSequence btnTxt) {
        if (spinVisible) {
            mProgress.setVisibility(View.VISIBLE);
            mImage.setVisibility(View.GONE);
        } else {
            mProgress.setVisibility(View.GONE);
            mImage.setImageDrawable(image);
            mImage.setVisibility(View.VISIBLE);
        }
        mText.setText(text);
        mButtonText.setText(btnTxt);
    }

    /**
     * @return an average abs of the specified buffer.
     */
    private static int getAverageAbs(ShortBuffer buffer, int start, int i, int npw) {
        int from = start + i * npw;
        int end = from + npw;
        int total = 0;
        for (int x = from; x < end; x++) {
            total += Math.abs(buffer.get(x));
        }
        return total / npw;
    }


    /**
     * Shows waveform of input audio.
     *
     * Copied from version in VoiceSearch's RecognitionActivity.
     *
     * TODO: adjust stroke width based on the size of data.
     * TODO: use dip rather than pixels.
     */
    private void showWave(ShortBuffer waveBuffer, int startPosition, int endPosition) {
        final int w = ((View) mImage.getParent()).getWidth();
        final int h = mImage.getHeight();
        if (w <= 0 || h <= 0) {
            // view is not visible this time. Skip drawing.
            return;
        }
        final Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        final Canvas c = new Canvas(b);
        final Paint paint = new Paint();
        paint.setColor(0xFFFFFFFF); // 0xAARRGGBB
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAlpha(0x90);

        final PathEffect effect = new CornerPathEffect(3);
        paint.setPathEffect(effect);

        final int numSamples = waveBuffer.remaining();
        int endIndex;
        if (endPosition == 0) {
            endIndex = numSamples;
        } else {
            endIndex = Math.min(endPosition, numSamples);
        }

        int startIndex = startPosition - 2000; // include 250ms before speech
        if (startIndex < 0) {
            startIndex = 0;
        }
        final int numSamplePerWave = 200;  // 8KHz 25ms = 200 samples
        final float scale = 10.0f / 65536.0f;

        final int count = (endIndex - startIndex) / numSamplePerWave;
        final float deltaX = 1.0f * w / count;
        int yMax = h / 2 - 8;
        Path path = new Path();
        c.translate(0, yMax);
        float x = 0;
        path.moveTo(x, 0);
        for (int i = 0; i < count; i++) {
            final int avabs = getAverageAbs(waveBuffer, startIndex, i , numSamplePerWave);
            int sign = ( (i & 01) == 0) ? -1 : 1;
            final float y = Math.min(yMax, avabs * h * scale) * sign;
            path.lineTo(x, y);
            x += deltaX;
            path.lineTo(x, y);
        }
        if (deltaX > 4) {
            paint.setStrokeWidth(3);
        } else {
            paint.setStrokeWidth(Math.max(1, (int) (deltaX -.05)));
        }
        c.drawPath(path, paint);
        mImage.setImageBitmap(b);
        mImage.setVisibility(View.VISIBLE);
        MarginLayoutParams mProgressParams = (MarginLayoutParams)mProgress.getLayoutParams();
        mProgressParams.topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                -h , mContext.getResources().getDisplayMetrics());

        // Tweak the padding manually to fill out the whole view horizontally.
        // TODO: Do this in the xml layout instead.
        ((View) mImage.getParent()).setPadding(4, ((View) mImage.getParent()).getPaddingTop(), 3,
                ((View) mImage.getParent()).getPaddingBottom());
        mProgress.setLayoutParams(mProgressParams);
    }


    public void finish() {
        mUiHandler.post(new Runnable() {
            public void run() {
                mState = State.READY;
                exitWorking();
            }
          });
    }

    private void exitWorking() {
        mProgress.setVisibility(View.GONE);
        mImage.setVisibility(View.VISIBLE);
    }
}
