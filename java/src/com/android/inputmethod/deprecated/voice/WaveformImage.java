/*
 * Copyright (C) 2008-2009 The Android Open Source Project
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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * Utility class to draw a waveform into a bitmap, given a byte array
 * that represents the waveform as a sequence of 16-bit integers.
 * Adapted from RecognitionActivity.java.
 */
public class WaveformImage {
    private static final int SAMPLING_RATE = 8000;

    private WaveformImage() {
        // Intentional empty constructor.
    }

    public static Bitmap drawWaveform(
        ByteArrayOutputStream waveBuffer, int w, int h, int start, int end) {
        final Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        final Canvas c = new Canvas(b);
        final Paint paint = new Paint();
        paint.setColor(0xFFFFFFFF); // 0xRRGGBBAA
        paint.setAntiAlias(true);
        paint.setStrokeWidth(0);

        final ShortBuffer buf = ByteBuffer
            .wrap(waveBuffer.toByteArray())
            .order(ByteOrder.nativeOrder())
            .asShortBuffer();
        buf.position(0);

        final int numSamples = waveBuffer.size() / 2;
        final int delay = (SAMPLING_RATE * 100 / 1000);
        int endIndex = end / 2 + delay;
        if (end == 0 || endIndex >= numSamples) {
            endIndex = numSamples;
        }
        int index = start / 2 - delay;
        if (index < 0) {
            index = 0;
        }
        final int size = endIndex - index;
        int numSamplePerPixel = 32;
        int delta = size / (numSamplePerPixel * w);
        if (delta == 0) {
            numSamplePerPixel = size / w;
            delta = 1;
        }

        final float scale = 3.5f / 65536.0f;
        // do one less column to make sure we won't read past
        // the buffer.
        try {
            for (int i = 0; i < w - 1 ; i++) {
                final float x = i;
                for (int j = 0; j < numSamplePerPixel; j++) {
                    final short s = buf.get(index);
                    final float y = (h / 2) - (s * h * scale);
                    c.drawPoint(x, y, paint);
                    index += delta;
                }
            }
        } catch (IndexOutOfBoundsException e) {
            // this can happen, but we don't care
        }

        return b;
    }
}
