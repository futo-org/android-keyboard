/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.keyboard.internal;

import android.graphics.Path;
import android.graphics.RectF;

public final class RoundedLine {
    // Start point (P1) coordinates and trail radius.
    public float p1x, p1y;
    public float r1;
    // End point (P2) coordinates and trail radius.
    public float p2x, p2y;
    public float r2;

    // Closing point of arc at P1.
    private float p1ax, p1ay;
    // Opening point of arc at P1.
    private float p1bx, p1by;
    // Opening point of arc at P2.
    private float p2ax, p2ay;
    // Closing point of arc at P2.
    private float p2bx, p2by;
    // Start angle of the trail arcs.
    private float angle;
    // Sweep angle of the trail arc at P1.
    private float a1;
    private final RectF arc1 = new RectF();
    // Sweep angle of the trail arc at P2.
    private float a2;
    private final RectF arc2 = new RectF();
    private final Path path = new Path();

    private static final float RADIAN_TO_DEGREE = (float)(180.0d / Math.PI);
    private static final float RIGHT_ANGLE = (float)(Math.PI / 2.0d);

    public Path makePath() {
        final float dx = p2x - p1x;
        final float dy = p2y - p1y;
        // Distance of the points.
        final double l = Math.hypot(dx, dy);
        if (Double.compare(0.0d, l) == 0) {
            return null;
        }
        // Angle of the line p1-p2
        final float a = (float)Math.atan2(dy, dx);
        // Difference of trail cap radius.
        final float dr = r2 - r1;
        // Variation of angle at trail cap.
        final float ar = (float)Math.asin(dr / l);
        // The start angle of trail cap arc at P1.
        final float aa = a - (RIGHT_ANGLE + ar);
        // The end angle of trail cap arc at P2.
        final float ab = a + (RIGHT_ANGLE + ar);
        final float cosa = (float)Math.cos(aa);
        final float sina = (float)Math.sin(aa);
        final float cosb = (float)Math.cos(ab);
        final float sinb = (float)Math.sin(ab);
        p1ax = p1x + r1 * cosa;
        p1ay = p1y + r1 * sina;
        p1bx = p1x + r1 * cosb;
        p1by = p1y + r1 * sinb;
        p2ax = p2x + r2 * cosa;
        p2ay = p2y + r2 * sina;
        p2bx = p2x + r2 * cosb;
        p2by = p2y + r2 * sinb;
        angle = aa * RADIAN_TO_DEGREE;
        final float ar2degree = ar * 2.0f * RADIAN_TO_DEGREE;
        a1 = -180.0f + ar2degree;
        a2 = 180.0f + ar2degree;
        arc1.set(p1x, p1y, p1x, p1y);
        arc1.inset(-r1, -r1);
        arc2.set(p2x, p2y, p2x, p2y);
        arc2.inset(-r2, -r2);

        path.rewind();
        // Trail cap at P1.
        path.moveTo(p1x, p1y);
        path.arcTo(arc1, angle, a1);
        // Trail cap at P2.
        path.moveTo(p2x, p2y);
        path.arcTo(arc2, angle, a2);
        // Two trapezoids connecting P1 and P2.
        path.moveTo(p1ax, p1ay);
        path.lineTo(p1x, p1y);
        path.lineTo(p1bx, p1by);
        path.lineTo(p2bx, p2by);
        path.lineTo(p2x, p2y);
        path.lineTo(p2ax, p2ay);
        path.close();
        return path;
    }
}