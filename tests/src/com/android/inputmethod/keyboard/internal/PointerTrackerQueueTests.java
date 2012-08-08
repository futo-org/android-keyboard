/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import android.test.AndroidTestCase;

public class PointerTrackerQueueTests extends AndroidTestCase {
    public static class Element implements PointerTrackerQueue.Element {
        public static int sPhantomUpCount;
        public static final long NOT_HAPPENED = -1;

        public final int mId;
        public boolean mIsModifier;
        public boolean mIsInSlidingKeyInput;
        public long mPhantomUpEventTime = NOT_HAPPENED;

        public Element(int id) {
            mId = id;
        }

        @Override
        public boolean isModifier() {
            return mIsModifier;
        }

        @Override
        public boolean isInSlidingKeyInput() {
            return mIsInSlidingKeyInput;
        }

        @Override
        public void onPhantomUpEvent(long eventTime) {
            sPhantomUpCount++;
            mPhantomUpEventTime = eventTime + sPhantomUpCount;
        }

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
        assertEquals("empty queue", 0, mQueue.size());
        assertEquals("empty queue", "[]", mQueue.toString());
    }

    public void testAdd() {
        mQueue.add(mElement1);
        assertEquals("add element1", 1, mQueue.size());
        assertEquals("after adding element1", "[1]", mQueue.toString());
        mQueue.add(mElement2);
        assertEquals("add element2", 2, mQueue.size());
        assertEquals("after adding element2", "[1 2]", mQueue.toString());
        mQueue.add(mElement3);
        assertEquals("add element3", 3, mQueue.size());
        assertEquals("after adding element3", "[1 2 3]", mQueue.toString());
        mQueue.add(mElement4);
        assertEquals("add element4", 4, mQueue.size());
        assertEquals("after adding element4", "[1 2 3 4]", mQueue.toString());
    }

    public void testRemove() {
        Element.sPhantomUpCount = 0;

        mQueue.add(mElement1);
        mQueue.add(mElement2);
        mQueue.add(mElement3);
        mQueue.add(mElement4);

        mQueue.remove(mElement2);
        assertEquals("remove element2", 3, mQueue.size());
        assertEquals("after removing element2", "[1 3 4]", mQueue.toString());
        mQueue.remove(mElement4);
        assertEquals("remove element4", 2, mQueue.size());
        assertEquals("after removing element4", "[1 3]", mQueue.toString());
        mQueue.remove(mElement4);
        assertEquals("remove element4 again", 2, mQueue.size());
        assertEquals("after removing element4 again", "[1 3]", mQueue.toString());
        mQueue.remove(mElement1);
        assertEquals("remove element1", 1, mQueue.size());
        assertEquals("after removing element4", "[3]", mQueue.toString());
        mQueue.remove(mElement3);
        assertEquals("remove element3", 0, mQueue.size());
        assertEquals("after removing element3", "[]", mQueue.toString());
        mQueue.remove(mElement1);
        assertEquals("remove element1 again", 0, mQueue.size());
        assertEquals("after removing element1 again", "[]", mQueue.toString());

        assertEquals("after remove elements", 0, Element.sPhantomUpCount);
        assertEquals("after remove element1",
                Element.NOT_HAPPENED, mElement1.mPhantomUpEventTime);
        assertEquals("after remove element2",
                Element.NOT_HAPPENED, mElement2.mPhantomUpEventTime);
        assertEquals("after remove element3",
                Element.NOT_HAPPENED, mElement3.mPhantomUpEventTime);
        assertEquals("after remove element4",
                Element.NOT_HAPPENED, mElement4.mPhantomUpEventTime);
    }

    public void testAddAndRemove() {
        Element.sPhantomUpCount = 0;

        mQueue.add(mElement1);
        mQueue.add(mElement2);
        mQueue.add(mElement3);
        mQueue.add(mElement4);

        mQueue.remove(mElement2);
        assertEquals("remove element2", 3, mQueue.size());
        assertEquals("after removing element2", "[1 3 4]", mQueue.toString());
        mQueue.remove(mElement4);
        assertEquals("remove element4", 2, mQueue.size());
        assertEquals("after removing element4", "[1 3]", mQueue.toString());
        mQueue.add(mElement2);
        assertEquals("add element2", 3, mQueue.size());
        assertEquals("after adding element2", "[1 3 2]", mQueue.toString());
        mQueue.remove(mElement4);
        assertEquals("remove element4 again", 3, mQueue.size());
        assertEquals("after removing element4 again", "[1 3 2]", mQueue.toString());
        mQueue.remove(mElement1);
        assertEquals("remove element1", 2, mQueue.size());
        assertEquals("after removing element4", "[3 2]", mQueue.toString());
        mQueue.add(mElement1);
        assertEquals("add element1", 3, mQueue.size());
        assertEquals("after adding element1", "[3 2 1]", mQueue.toString());
        mQueue.remove(mElement3);
        assertEquals("remove element3", 2, mQueue.size());
        assertEquals("after removing element3", "[2 1]", mQueue.toString());
        mQueue.remove(mElement1);
        assertEquals("remove element1 again", 1, mQueue.size());
        assertEquals("after removing element1 again", "[2]", mQueue.toString());

        assertEquals("after remove element1",
                Element.NOT_HAPPENED, mElement1.mPhantomUpEventTime);
        assertEquals("after remove element2",
                Element.NOT_HAPPENED, mElement2.mPhantomUpEventTime);
        assertEquals("after remove element3",
                Element.NOT_HAPPENED, mElement3.mPhantomUpEventTime);
        assertEquals("after remove element4",
                Element.NOT_HAPPENED, mElement4.mPhantomUpEventTime);
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
        assertEquals("after releaseAllPointers", 4, Element.sPhantomUpCount);
        assertEquals("after releaseAllPointers", 0, mQueue.size());
        assertEquals("after releaseAllPointers", "[]", mQueue.toString());
        assertEquals("after releaseAllPointers element1",
                eventTime + 1, mElement1.mPhantomUpEventTime);
        assertEquals("after releaseAllPointers element2",
                eventTime + 2, mElement2.mPhantomUpEventTime);
        assertEquals("after releaseAllPointers element3",
                eventTime + 3, mElement3.mPhantomUpEventTime);
        assertEquals("after releaseAllPointers element4",
                eventTime + 4, mElement4.mPhantomUpEventTime);
    }

    public void testReleaseAllPointersOlderThan() {
        mElement2.mIsModifier = true;
        mQueue.add(mElement1);
        mQueue.add(mElement2);
        mQueue.add(mElement3);
        mQueue.add(mElement4);

        final long eventTime = 123;
        Element.sPhantomUpCount = 0;
        mQueue.releaseAllPointersOlderThan(mElement4, eventTime);
        assertEquals("after releaseAllPointersOlderThan", 2, Element.sPhantomUpCount);
        assertEquals("after releaseAllPointersOlderThan", 2, mQueue.size());
        assertEquals("after releaseAllPointersOlderThan", "[2 4]", mQueue.toString());
        assertEquals("after releaseAllPointersOlderThan element1",
                eventTime + 1, mElement1.mPhantomUpEventTime);
        assertEquals("after releaseAllPointersOlderThan element2",
                Element.NOT_HAPPENED, mElement2.mPhantomUpEventTime);
        assertEquals("after releaseAllPointersOlderThan element3",
                eventTime + 2, mElement3.mPhantomUpEventTime);
        assertEquals("after releaseAllPointersOlderThan element4",
                Element.NOT_HAPPENED, mElement4.mPhantomUpEventTime);
    }

    public void testReleaseAllPointersOlderThanWithoutModifier() {
        mQueue.add(mElement1);
        mQueue.add(mElement2);
        mQueue.add(mElement3);
        mQueue.add(mElement4);

        final long eventTime = 123;
        Element.sPhantomUpCount = 0;
        mQueue.releaseAllPointersOlderThan(mElement4, eventTime);
        assertEquals("after releaseAllPointersOlderThan without modifier",
                3, Element.sPhantomUpCount);
        assertEquals("after releaseAllPointersOlderThan without modifier", 1, mQueue.size());
        assertEquals("after releaseAllPointersOlderThan without modifier",
                "[4]", mQueue.toString());
        assertEquals("after releaseAllPointersOlderThan without modifier element1",
                eventTime + 1, mElement1.mPhantomUpEventTime);
        assertEquals("after releaseAllPointersOlderThan without modifier element2",
                eventTime + 2, mElement2.mPhantomUpEventTime);
        assertEquals("after releaseAllPointersOlderThan without modifier element3",
                eventTime + 3, mElement3.mPhantomUpEventTime);
        assertEquals("after releaseAllPointersOlderThan without modifier element4",
                Element.NOT_HAPPENED, mElement4.mPhantomUpEventTime);
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
        assertEquals("after releaseAllPointersExcept", 3, Element.sPhantomUpCount);
        assertEquals("after releaseAllPointersExcept", 1, mQueue.size());
        assertEquals("after releaseAllPointersExcept", "[3]", mQueue.toString());
        assertEquals("after releaseAllPointersExcept element1",
                eventTime + 1, mElement1.mPhantomUpEventTime);
        assertEquals("after releaseAllPointersExcept element2",
                eventTime + 2, mElement2.mPhantomUpEventTime);
        assertEquals("after releaseAllPointersExcept element3",
                Element.NOT_HAPPENED, mElement3.mPhantomUpEventTime);
        assertEquals("after releaseAllPointersExcept element4",
                eventTime + 3, mElement4.mPhantomUpEventTime);
    }

    public void testHasModifierKeyOlderThan() {
        Element.sPhantomUpCount = 0;
        assertFalse("hasModifierKeyOlderThan empty", mQueue.hasModifierKeyOlderThan(mElement1));

        mQueue.add(mElement1);
        mQueue.add(mElement2);
        mQueue.add(mElement3);
        mQueue.add(mElement4);

        assertFalse("hasModifierKeyOlderThan element1", mQueue.hasModifierKeyOlderThan(mElement1));
        assertFalse("hasModifierKeyOlderThan element2", mQueue.hasModifierKeyOlderThan(mElement2));
        assertFalse("hasModifierKeyOlderThan element3", mQueue.hasModifierKeyOlderThan(mElement3));
        assertFalse("hasModifierKeyOlderThan element4", mQueue.hasModifierKeyOlderThan(mElement4));

        mElement2.mIsModifier = true;
        assertFalse("hasModifierKeyOlderThan element1", mQueue.hasModifierKeyOlderThan(mElement1));
        assertFalse("hasModifierKeyOlderThan element2", mQueue.hasModifierKeyOlderThan(mElement2));
        assertTrue("hasModifierKeyOlderThan element3", mQueue.hasModifierKeyOlderThan(mElement3));
        assertTrue("hasModifierKeyOlderThan element4", mQueue.hasModifierKeyOlderThan(mElement4));

        assertEquals("after hasModifierKeyOlderThan", 0, Element.sPhantomUpCount);
        assertEquals("after hasModifierKeyOlderThan", 4, mQueue.size());
        assertEquals("after hasModifierKeyOlderThan", "[1 2 3 4]", mQueue.toString());
        assertEquals("after hasModifierKeyOlderThan element1",
                Element.NOT_HAPPENED, mElement1.mPhantomUpEventTime);
        assertEquals("after hasModifierKeyOlderThan element2",
                Element.NOT_HAPPENED, mElement2.mPhantomUpEventTime);
        assertEquals("after hasModifierKeyOlderThan element3",
                Element.NOT_HAPPENED, mElement3.mPhantomUpEventTime);
        assertEquals("after hasModifierKeyOlderThan element4",
                Element.NOT_HAPPENED, mElement4.mPhantomUpEventTime);
    }

    public void testIsAnyInSlidingKeyInput() {
        Element.sPhantomUpCount = 0;
        assertFalse("isAnyInSlidingKeyInput empty", mQueue.isAnyInSlidingKeyInput());

        mQueue.add(mElement1);
        mQueue.add(mElement2);
        mQueue.add(mElement3);
        mQueue.add(mElement4);

        assertFalse("isAnyInSlidingKeyInput element1", mQueue.isAnyInSlidingKeyInput());

        mElement3.mIsInSlidingKeyInput = true;
        assertTrue("isAnyInSlidingKeyInput element1", mQueue.isAnyInSlidingKeyInput());

        assertEquals("after isAnyInSlidingKeyInput", 0, Element.sPhantomUpCount);
        assertEquals("after isAnyInSlidingKeyInput", 4, mQueue.size());
        assertEquals("after isAnyInSlidingKeyInput", "[1 2 3 4]", mQueue.toString());
        assertEquals("after isAnyInSlidingKeyInput element1",
                Element.NOT_HAPPENED, mElement1.mPhantomUpEventTime);
        assertEquals("after isAnyInSlidingKeyInput element2",
                Element.NOT_HAPPENED, mElement2.mPhantomUpEventTime);
        assertEquals("after isAnyInSlidingKeyInput element3",
                Element.NOT_HAPPENED, mElement3.mPhantomUpEventTime);
        assertEquals("after isAnyInSlidingKeyInput element4",
                Element.NOT_HAPPENED, mElement4.mPhantomUpEventTime);
    }
}
