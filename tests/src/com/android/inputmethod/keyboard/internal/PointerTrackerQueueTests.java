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

package com.android.inputmethod.keyboard.internal;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

@SmallTest
public class PointerTrackerQueueTests extends AndroidTestCase {
    public static class Element implements PointerTrackerQueue.Element {
        public static int sPhantomUpCount;
        public static final long NOT_HAPPENED = -1;

        public final int mId;
        public boolean mIsModifier;
        public boolean mIsInDraggingFinger;
        public long mPhantomUpEventTime = NOT_HAPPENED;

        public Element(int id) {
            mId = id;
        }

        @Override
        public boolean isModifier() {
            return mIsModifier;
        }

        @Override
        public boolean isInDraggingFinger() {
            return mIsInDraggingFinger;
        }

        @Override
        public void onPhantomUpEvent(long eventTime) {
            sPhantomUpCount++;
            mPhantomUpEventTime = eventTime + sPhantomUpCount;
        }

        @Override
        public void cancelTrackingForAction() {}

        @Override
        public String toString() {
            return Integer.toString(mId);
        }
    }

    private final Element mElement1 = new Element(1);
    private final Element mElement2 = new Element(2);
    private final Element mElement3 = new Element(3);
    private final Element mElement4 = new Element(4);
    private final PointerTrackerQueue mQueue = new PointerTrackerQueue();

    public void testEmpty() {
        assertEquals(0, mQueue.size());
        assertEquals("[]", mQueue.toString());
    }

    public void testAdd() {
        mQueue.add(mElement1);
        assertEquals(1, mQueue.size());
        assertEquals("[1]", mQueue.toString());
        mQueue.add(mElement2);
        assertEquals(2, mQueue.size());
        assertEquals("[1 2]", mQueue.toString());
        mQueue.add(mElement3);
        assertEquals(3, mQueue.size());
        assertEquals("[1 2 3]", mQueue.toString());
        mQueue.add(mElement4);
        assertEquals(4, mQueue.size());
        assertEquals("[1 2 3 4]", mQueue.toString());
    }

    public void testRemove() {
        Element.sPhantomUpCount = 0;

        mQueue.add(mElement1);
        mQueue.add(mElement2);
        mQueue.add(mElement3);
        mQueue.add(mElement4);

        mQueue.remove(mElement2);
        assertEquals(3, mQueue.size());
        assertEquals("[1 3 4]", mQueue.toString());
        mQueue.remove(mElement4);
        assertEquals(2, mQueue.size());
        assertEquals("[1 3]", mQueue.toString());
        mQueue.remove(mElement4);
        assertEquals(2, mQueue.size());
        assertEquals("[1 3]", mQueue.toString());
        mQueue.remove(mElement1);
        assertEquals(1, mQueue.size());
        assertEquals("[3]", mQueue.toString());
        mQueue.remove(mElement3);
        assertEquals(0, mQueue.size());
        assertEquals("[]", mQueue.toString());
        mQueue.remove(mElement1);
        assertEquals(0, mQueue.size());
        assertEquals("[]", mQueue.toString());

        assertEquals(0, Element.sPhantomUpCount);
        assertEquals(Element.NOT_HAPPENED, mElement1.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement2.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement3.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement4.mPhantomUpEventTime);
    }

    public void testAddAndRemove() {
        Element.sPhantomUpCount = 0;

        mQueue.add(mElement1);
        mQueue.add(mElement2);
        mQueue.add(mElement3);
        mQueue.add(mElement4);

        mQueue.remove(mElement2);
        assertEquals(3, mQueue.size());
        assertEquals("[1 3 4]", mQueue.toString());
        mQueue.remove(mElement4);
        assertEquals(2, mQueue.size());
        assertEquals("[1 3]", mQueue.toString());
        mQueue.add(mElement2);
        assertEquals(3, mQueue.size());
        assertEquals("[1 3 2]", mQueue.toString());
        mQueue.remove(mElement4);
        assertEquals(3, mQueue.size());
        assertEquals("[1 3 2]", mQueue.toString());
        mQueue.remove(mElement1);
        assertEquals(2, mQueue.size());
        assertEquals("[3 2]", mQueue.toString());
        mQueue.add(mElement1);
        assertEquals(3, mQueue.size());
        assertEquals("[3 2 1]", mQueue.toString());
        mQueue.remove(mElement3);
        assertEquals(2, mQueue.size());
        assertEquals("[2 1]", mQueue.toString());
        mQueue.remove(mElement1);
        assertEquals(1, mQueue.size());
        assertEquals("[2]", mQueue.toString());

        assertEquals(Element.NOT_HAPPENED, mElement1.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement2.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement3.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement4.mPhantomUpEventTime);
    }

    public void testReleaseAllPointers() {
        mElement2.mIsModifier = true;
        mQueue.add(mElement1);
        mQueue.add(mElement2);
        mQueue.add(mElement3);
        mQueue.add(mElement4);

        final long eventTime = 123;
        Element.sPhantomUpCount = 0;
        mQueue.releaseAllPointers(eventTime);
        assertEquals(4, Element.sPhantomUpCount);
        assertEquals(0, mQueue.size());
        assertEquals("[]", mQueue.toString());
        assertEquals(eventTime + 1, mElement1.mPhantomUpEventTime);
        assertEquals(eventTime + 2, mElement2.mPhantomUpEventTime);
        assertEquals(eventTime + 3, mElement3.mPhantomUpEventTime);
        assertEquals(eventTime + 4, mElement4.mPhantomUpEventTime);
    }

    public void testReleaseAllPointersOlderThanFirst() {
        mElement2.mIsModifier = true;
        mQueue.add(mElement1);
        mQueue.add(mElement2);
        mQueue.add(mElement3);

        final long eventTime = 123;
        Element.sPhantomUpCount = 0;
        mQueue.releaseAllPointersOlderThan(mElement1, eventTime);
        assertEquals(0, Element.sPhantomUpCount);
        assertEquals(3, mQueue.size());
        assertEquals("[1 2 3]", mQueue.toString());
        assertEquals(Element.NOT_HAPPENED, mElement1.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement2.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement3.mPhantomUpEventTime);
    }

    public void testReleaseAllPointersOlderThanLast() {
        mElement2.mIsModifier = true;
        mQueue.add(mElement1);
        mQueue.add(mElement2);
        mQueue.add(mElement3);
        mQueue.add(mElement4);

        final long eventTime = 123;
        Element.sPhantomUpCount = 0;
        mQueue.releaseAllPointersOlderThan(mElement4, eventTime);
        assertEquals(2, Element.sPhantomUpCount);
        assertEquals(2, mQueue.size());
        assertEquals("[2 4]", mQueue.toString());
        assertEquals(eventTime + 1, mElement1.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement2.mPhantomUpEventTime);
        assertEquals(eventTime + 2, mElement3.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement4.mPhantomUpEventTime);
    }

    public void testReleaseAllPointersOlderThanWithoutModifierMiddle() {
        mQueue.add(mElement1);
        mQueue.add(mElement2);
        mQueue.add(mElement3);
        mQueue.add(mElement4);

        final long eventTime = 123;
        Element.sPhantomUpCount = 0;
        mQueue.releaseAllPointersOlderThan(mElement3, eventTime);
        assertEquals(2, Element.sPhantomUpCount);
        assertEquals(2, mQueue.size());
        assertEquals("[3 4]", mQueue.toString());
        assertEquals(eventTime + 1, mElement1.mPhantomUpEventTime);
        assertEquals(eventTime + 2, mElement2.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement3.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement4.mPhantomUpEventTime);
    }

    public void testReleaseAllPointersOlderThanWithoutModifierLast() {
        mQueue.add(mElement1);
        mQueue.add(mElement2);
        mQueue.add(mElement3);
        mQueue.add(mElement4);

        final long eventTime = 123;
        Element.sPhantomUpCount = 0;
        mQueue.releaseAllPointersOlderThan(mElement4, eventTime);
        assertEquals(3, Element.sPhantomUpCount);
        assertEquals(1, mQueue.size());
        assertEquals("[4]", mQueue.toString());
        assertEquals(eventTime + 1, mElement1.mPhantomUpEventTime);
        assertEquals(eventTime + 2, mElement2.mPhantomUpEventTime);
        assertEquals(eventTime + 3, mElement3.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement4.mPhantomUpEventTime);
    }

    public void testReleaseAllPointersExcept() {
        mElement2.mIsModifier = true;
        mQueue.add(mElement1);
        mQueue.add(mElement2);
        mQueue.add(mElement3);
        mQueue.add(mElement4);

        final long eventTime = 123;
        Element.sPhantomUpCount = 0;
        mQueue.releaseAllPointersExcept(mElement3, eventTime);
        assertEquals(3, Element.sPhantomUpCount);
        assertEquals(1, mQueue.size());
        assertEquals("[3]", mQueue.toString());
        assertEquals(eventTime + 1, mElement1.mPhantomUpEventTime);
        assertEquals(eventTime + 2, mElement2.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement3.mPhantomUpEventTime);
        assertEquals(eventTime + 3, mElement4.mPhantomUpEventTime);
    }

    public void testHasModifierKeyOlderThan() {
        Element.sPhantomUpCount = 0;
        assertFalse("hasModifierKeyOlderThan empty", mQueue.hasModifierKeyOlderThan(mElement1));

        mQueue.add(mElement1);
        mQueue.add(mElement2);
        mQueue.add(mElement3);
        mQueue.add(mElement4);

        assertFalse(mQueue.hasModifierKeyOlderThan(mElement1));
        assertFalse(mQueue.hasModifierKeyOlderThan(mElement2));
        assertFalse(mQueue.hasModifierKeyOlderThan(mElement3));
        assertFalse(mQueue.hasModifierKeyOlderThan(mElement4));

        mElement2.mIsModifier = true;
        assertFalse(mQueue.hasModifierKeyOlderThan(mElement1));
        assertFalse(mQueue.hasModifierKeyOlderThan(mElement2));
        assertTrue(mQueue.hasModifierKeyOlderThan(mElement3));
        assertTrue(mQueue.hasModifierKeyOlderThan(mElement4));

        assertEquals(0, Element.sPhantomUpCount);
        assertEquals(4, mQueue.size());
        assertEquals("[1 2 3 4]", mQueue.toString());
        assertEquals(Element.NOT_HAPPENED, mElement1.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement2.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement3.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement4.mPhantomUpEventTime);
    }

    public void testIsAnyInDraggingFinger() {
        Element.sPhantomUpCount = 0;
        assertFalse(mQueue.isAnyInDraggingFinger());

        mQueue.add(mElement1);
        mQueue.add(mElement2);
        mQueue.add(mElement3);
        mQueue.add(mElement4);

        assertFalse(mQueue.isAnyInDraggingFinger());

        mElement3.mIsInDraggingFinger = true;
        assertTrue(mQueue.isAnyInDraggingFinger());

        assertEquals(0, Element.sPhantomUpCount);
        assertEquals(4, mQueue.size());
        assertEquals("[1 2 3 4]", mQueue.toString());
        assertEquals(Element.NOT_HAPPENED, mElement1.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement2.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement3.mPhantomUpEventTime);
        assertEquals(Element.NOT_HAPPENED, mElement4.mPhantomUpEventTime);
    }
}
