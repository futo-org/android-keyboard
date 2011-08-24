/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.inputmethod.deprecated.voice;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.Utils;

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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Locale;

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

    private TextView mText;
    private ImageView mImage;
    private View mProgress;
    private SoundIndicator mSoundIndicator;
    private TextView mLanguage;
    private Button mButton;

    private Drawable mInitializing;
    private Drawable mError;

    private static final int INIT = 0;
    private static final int LISTENING = 1;
    private static final int WORKING = 2;
    private static final int READY = 3;
    
    private int mState = INIT;

    private final View mPopupLayout;

    private final Drawable mListeningBorder;
    private final Drawable mWorkingBorder;
    private final Drawable mErrorBorder;

    public RecognitionView(Context context, OnClickListener clickListener) {
        mUiHandler = new Handler();

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        mView = inflater.inflate(R.layout.recognition_status, null);

        mPopupLayout= mView.findViewById(R.id.popup_layout);

        // Pre-load volume level images
        Resources r = context.getResources();

        mListeningBorder = r.getDrawable(R.drawable.vs_dialog_red);
        mWorkingBorder = r.getDrawable(R.drawable.vs_dialog_blue);
        mErrorBorder = r.getDrawable(R.drawable.vs_dialog_yellow);

        mInitializing = r.getDrawable(R.drawable.mic_slash);
        mError = r.getDrawable(R.drawable.caution);

        mImage = (ImageView) mView.findViewById(R.id.image);
        mProgress = mView.findViewById(R.id.progress);
        mSoundIndicator = (SoundIndicator) mView.findViewById(R.id.sound_indicator);

        mButton = (Button) mView.findViewById(R.id.button);
        mButton.setOnClickListener(clickListener);
        mText = (TextView) mView.findViewById(R.id.text);
        mLanguage = (TextView) mView.findViewById(R.id.language);

        mContext = context;
    }

    public View getView() {
        return mView;
    }

    public void restoreState() {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                // Restart the spinner
                if (mState == WORKING) {
                    ((ProgressBar) mProgress).setIndeterminate(false);
                    ((ProgressBar) mProgress).setIndeterminate(true);
                }
            }
        });
    }

    public void showInitializing() {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                mState = INIT;
                prepareDialog(mContext.getText(R.string.voice_initializing), mInitializing,
                        mContext.getText(R.string.cancel));
            }
          });
    }

    public void showListening() {
        Log.d(TAG, "#showListening");
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                mState = LISTENING;
                prepareDialog(mContext.getText(R.string.voice_listening), null,
                        mContext.getText(R.string.cancel));
            }
          });
    }

    public void updateVoiceMeter(float rmsdB) {
        mSoundIndicator.setRmsdB(rmsdB);
    }

    public void showError(final String message) {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                mState = READY;
                prepareDialog(message, mError, mContext.getText(R.string.ok));
            }
        });
    }

    public void showWorking(
        final ByteArrayOutputStream waveBuffer,
        final int speechStartPosition,
        final int speechEndPosition) {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                mState = WORKING;
                prepareDialog(mContext.getText(R.string.voice_working), null, mContext
                        .getText(R.string.cancel));
                final ShortBuffer buf = ByteBuffer.wrap(waveBuffer.toByteArray()).order(
                        ByteOrder.nativeOrder()).asShortBuffer();
                buf.position(0);
                waveBuffer.reset();
                showWave(buf, speechStartPosition / 2, speechEndPosition / 2);
            }
        });
    }
    
    private void prepareDialog(CharSequence text, Drawable image,
            CharSequence btnTxt) {

        /*
         * The mic of INIT and of LISTENING has to be displayed in the same position. To accomplish
         * that, some text visibility are not set as GONE but as INVISIBLE.
         */
        switch (mState) {
            case INIT:
                mText.setVisibility(View.INVISIBLE);

                mProgress.setVisibility(View.GONE);

                mImage.setVisibility(View.VISIBLE);
                mImage.setImageResource(R.drawable.mic_slash);

                mSoundIndicator.setVisibility(View.GONE);
                mSoundIndicator.stop();

                mLanguage.setVisibility(View.INVISIBLE);

                mPopupLayout.setBackgroundDrawable(mListeningBorder);
                break;
            case LISTENING:
                mText.setVisibility(View.VISIBLE);
                mText.setText(text);

                mProgress.setVisibility(View.GONE);

                mImage.setVisibility(View.GONE);

                mSoundIndicator.setVisibility(View.VISIBLE);
                mSoundIndicator.start();

                Locale locale = SubtypeSwitcher.getInstance().getInputLocale();

                mLanguage.setVisibility(View.VISIBLE);
                mLanguage.setText(Utils.getFullDisplayName(locale, true));

                mPopupLayout.setBackgroundDrawable(mListeningBorder);
                break;
            case WORKING:

                mText.setVisibility(View.VISIBLE);
                mText.setText(text);

                mProgress.setVisibility(View.VISIBLE);

                mImage.setVisibility(View.VISIBLE);

                mSoundIndicator.setVisibility(View.GONE);
                mSoundIndicator.stop();

                mLanguage.setVisibility(View.GONE);

                mPopupLayout.setBackgroundDrawable(mWorkingBorder);
                break;
            case READY:
                mText.setVisibility(View.VISIBLE);
                mText.setText(text);

                mProgress.setVisibility(View.GONE);

                mImage.setVisibility(View.VISIBLE);
                mImage.setImageResource(R.drawable.caution);

                mSoundIndicator.setVisibility(View.GONE);
                mSoundIndicator.stop();

                mLanguage.setVisibility(View.GONE);

                mPopupLayout.setBackgroundDrawable(mErrorBorder);
                break;
             default:
                 Log.w(TAG, "Unknown state " + mState);
        }
        mPopupLayout.requestLayout();
        mButton.setText(btnTxt);
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
        final int h = ((View) mImage.getParent()).getHeight();
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
        paint.setAlpha(80);

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
        int yMax = h / 2;
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
            paint.setStrokeWidth(2);
        } else {
            paint.setStrokeWidth(Math.max(0, (int) (deltaX -.05)));
        }
        c.drawPath(path, paint);
        mImage.setImageBitmap(b);
    }

    public void finish() {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                mSoundIndicator.stop();
            }
        });
    }
}
