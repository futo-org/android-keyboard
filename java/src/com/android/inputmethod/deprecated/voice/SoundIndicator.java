/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.android.inputmethod.latin.R;

/**
 * A widget which shows the volume of audio using a microphone icon
 */
public class SoundIndicator extends ImageView {
    @SuppressWarnings("unused")
    private static final String TAG = "SoundIndicator";

    private static final float UP_SMOOTHING_FACTOR = 0.9f;
    private static final float DOWN_SMOOTHING_FACTOR = 0.4f;

    private static final float AUDIO_METER_MIN_DB = 7.0f;
    private static final float AUDIO_METER_DB_RANGE = 20.0f;

    private static final long FRAME_DELAY = 50;

    private Bitmap mDrawingBuffer;
    private Canvas mBufferCanvas;
    private Bitmap mEdgeBitmap;
    private float mLevel = 0.0f;
    private Drawable mFrontDrawable;
    private Paint mClearPaint;
    private Paint mMultPaint;
    private int mEdgeBitmapOffset;

    private Handler mHandler;

    private Runnable mDrawFrame = new Runnable() {
        public void run() {
            invalidate();
            mHandler.postDelayed(mDrawFrame, FRAME_DELAY);
        }
    };

    public SoundIndicator(Context context) {
        this(context, null);
    }

    public SoundIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);

        mFrontDrawable = getDrawable();
        BitmapDrawable edgeDrawable =
                (BitmapDrawable) context.getResources().getDrawable(R.drawable.vs_popup_mic_edge);
        mEdgeBitmap = edgeDrawable.getBitmap();
        mEdgeBitmapOffset = mEdgeBitmap.getHeight() / 2;

        mDrawingBuffer =
                Bitmap.createBitmap(mFrontDrawable.getIntrinsicWidth(),
                        mFrontDrawable.getIntrinsicHeight(), Config.ARGB_8888);

        mBufferCanvas = new Canvas(mDrawingBuffer);

        // Initialize Paints.
        mClearPaint = new Paint();
        mClearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        mMultPaint = new Paint();
        mMultPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));

        mHandler = new Handler();
    }

    @Override
    public void onDraw(Canvas canvas) {
        //super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();

        // Clear the buffer canvas
        mBufferCanvas.drawRect(0, 0, w, h, mClearPaint);

        // Set its clip so we don't draw the front image all the way to the top
        Rect clip = new Rect(0,
                (int) ((1.0 - mLevel) * (h + mEdgeBitmapOffset)) - mEdgeBitmapOffset,
                (int) w,
                (int) h);

        mBufferCanvas.save();
        mBufferCanvas.clipRect(clip);

        // Draw the front image
        mFrontDrawable.setBounds(new Rect(0, 0, (int) w, (int) h));
        mFrontDrawable.draw(mBufferCanvas);

        mBufferCanvas.restore();

        // Draw the edge image on top of the buffer image with a multiply mode
        mBufferCanvas.drawBitmap(mEdgeBitmap, 0, clip.top, mMultPaint);

        // Draw the buffer image (on top of the background image)
        canvas.drawBitmap(mDrawingBuffer, 0, 0, null);
    }

    /**
     * Sets the sound level
     *
     * @param rmsdB The level of the sound, in dB.
     */
    public void setRmsdB(float rmsdB) {
        float level = ((rmsdB - AUDIO_METER_MIN_DB) / AUDIO_METER_DB_RANGE);

        level = Math.min(Math.max(0.0f, level), 1.0f);

        // We smooth towards the new level
        if (level > mLevel) {
            mLevel = (level - mLevel) * UP_SMOOTHING_FACTOR + mLevel;
        } else {
            mLevel = (level - mLevel) * DOWN_SMOOTHING_FACTOR + mLevel;
        }
        invalidate();
    }

    public void start() {
        mHandler.post(mDrawFrame);
    }

    public void stop() {
        mHandler.removeCallbacks(mDrawFrame);
    }
}
