/*
 * Copyright (C) 2010 Google Inc.
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

import com.android.inputmethod.latin.SwipeTracker.EventRingBuffer;

import android.test.AndroidTestCase;

public class EventRingBufferTests extends AndroidTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private static float X_BASE = 1000f;

    private static float Y_BASE = 2000f;

    private static long TIME_BASE = 3000l;

    private static float x(int id) {
        return X_BASE + id;
    }

    private static float y(int id) {
        return Y_BASE + id;
    }

    private static long time(int id) {
        return TIME_BASE + id;
    }

    private static void addEvent(EventRingBuffer buf, int id) {
        buf.add(x(id), y(id), time(id));
    }

    private static void assertEventSize(EventRingBuffer buf, int size) {
        assertEquals(size, buf.size());
    }

    private static void assertEvent(EventRingBuffer buf, int pos, int id) {
        assertEquals(x(id), buf.getX(pos), 0f);
        assertEquals(y(id), buf.getY(pos), 0f);
        assertEquals(time(id), buf.getTime(pos));
    }

    public void testClearBuffer() {
        EventRingBuffer buf = new EventRingBuffer(4);
        assertEventSize(buf, 0);

        addEvent(buf, 0);
        addEvent(buf, 1);
        addEvent(buf, 2);
        addEvent(buf, 3);
        addEvent(buf, 4);
        assertEventSize(buf, 4);

        buf.clear();
        assertEventSize(buf, 0);
    }

    public void testRingBuffer() {
        EventRingBuffer buf = new EventRingBuffer(4);
        assertEventSize(buf, 0); // [0]

        addEvent(buf, 0);
        assertEventSize(buf, 1); // [1] 0
        assertEvent(buf, 0, 0);

        addEvent(buf, 1);
        addEvent(buf, 2);
        assertEventSize(buf, 3); // [3] 2 1 0
        assertEvent(buf, 0, 0);
        assertEvent(buf, 1, 1);
        assertEvent(buf, 2, 2);

        addEvent(buf, 3);
        assertEventSize(buf, 4); // [4] 3 2 1 0
        assertEvent(buf, 0, 0);
        assertEvent(buf, 1, 1);
        assertEvent(buf, 2, 2);
        assertEvent(buf, 3, 3);

        addEvent(buf, 4);
        addEvent(buf, 5);
        assertEventSize(buf, 4); // [4] 5 4|3 2(1 0)
        assertEvent(buf, 0, 2);
        assertEvent(buf, 1, 3);
        assertEvent(buf, 2, 4);
        assertEvent(buf, 3, 5);

        addEvent(buf, 6);
        addEvent(buf, 7);
        addEvent(buf, 8);
        assertEventSize(buf, 4); // [4] 8 7 6 5|(4 3 2)1|0
        assertEvent(buf, 0, 5);
        assertEvent(buf, 1, 6);
        assertEvent(buf, 2, 7);
        assertEvent(buf, 3, 8);
    }

    public void testDropOldest() {
        EventRingBuffer buf = new EventRingBuffer(4);

        addEvent(buf, 0);
        assertEventSize(buf, 1); // [1] 0
        assertEvent(buf, 0, 0);

        buf.dropOldest();
        assertEventSize(buf, 0); // [0] (0)

        addEvent(buf, 1);
        addEvent(buf, 2);
        addEvent(buf, 3);
        addEvent(buf, 4);
        assertEventSize(buf, 4); // [4] 4|3 2 1(0)
        assertEvent(buf, 0, 1);

        buf.dropOldest();
        assertEventSize(buf, 3); // [3] 4|3 2(1)0
        assertEvent(buf, 0, 2);

        buf.dropOldest();
        assertEventSize(buf, 2); // [2] 4|3(2)10
        assertEvent(buf, 0, 3);

        buf.dropOldest();
        assertEventSize(buf, 1); // [1] 4|(3)210
        assertEvent(buf, 0, 4);

        buf.dropOldest();
        assertEventSize(buf, 0); // [0] (4)|3210
    }
}
