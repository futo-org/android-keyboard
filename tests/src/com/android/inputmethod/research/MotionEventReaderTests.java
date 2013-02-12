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

package com.android.inputmethod.research;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.JsonReader;

import com.android.inputmethod.research.MotionEventReader.ReplayData;

import java.io.IOException;
import java.io.StringReader;

@SmallTest
public class MotionEventReaderTests extends AndroidTestCase {
    private MotionEventReader mMotionEventReader = new MotionEventReader();
    private ReplayData mReplayData;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mReplayData = new ReplayData();
    }

    private JsonReader jsonReaderForString(final String s) {
        return new JsonReader(new StringReader(s));
    }

    public void testTopLevelDataVariant() {
        final JsonReader jsonReader = jsonReaderForString(
                "{"
                + "\"_ct\": 1359590400000,"
                + "\"_ut\": 4381933,"
                + "\"_ty\": \"MotionEvent\","
                + "\"action\": \"UP\","
                + "\"isLoggingRelated\": false,"
                + "\"x\": 100.0,"
                + "\"y\": 200.0"
                + "}"
                );
        try {
            mMotionEventReader.readLogStatement(jsonReader, mReplayData);
        } catch (IOException e) {
            e.printStackTrace();
            fail("IOException thrown");
        }
        assertEquals("x set correctly", (int) mReplayData.mPointerCoordsArrays.get(0)[0].x, 100);
        assertEquals("y set correctly", (int) mReplayData.mPointerCoordsArrays.get(0)[0].y, 200);
        assertEquals("only one pointer", mReplayData.mPointerCoordsArrays.get(0).length, 1);
        assertEquals("only one MotionEvent", mReplayData.mPointerCoordsArrays.size(), 1);
    }

    public void testNestedDataVariant() {
        final JsonReader jsonReader = jsonReaderForString(
                "{"
                + "  \"_ct\": 135959040000,"
                + "  \"_ut\": 4382702,"
                + "  \"_ty\": \"MotionEvent\","
                + "  \"action\": \"MOVE\","
                + "  \"isLoggingRelated\": false,"
                + "  \"motionEvent\": {"
                + "    \"pointerIds\": ["
                + "      0"
                + "    ],"
                + "    \"xyt\": ["
                + "      {"
                + "        \"t\": 4382551,"
                + "        \"d\": ["
                + "          {"
                + "            \"x\": 100.0,"
                + "            \"y\": 200.0,"
                + "            \"toma\": 999.0,"
                + "            \"tomi\": 999.0,"
                + "            \"o\": 0.0"
                + "          }"
                + "        ]"
                + "      },"
                + "      {"
                + "        \"t\": 4382559,"
                + "        \"d\": ["
                + "          {"
                + "            \"x\": 300.0,"
                + "            \"y\": 400.0,"
                + "            \"toma\": 999.0,"
                + "            \"tomi\": 999.0,"
                + "            \"o\": 0.0"
                + "          }"
                + "        ]"
                + "      }"
                + "    ]"
                + "  }"
                + "}"
                );
        try {
            mMotionEventReader.readLogStatement(jsonReader, mReplayData);
        } catch (IOException e) {
            e.printStackTrace();
            fail("IOException thrown");
        }
        assertEquals("x1 set correctly", (int) mReplayData.mPointerCoordsArrays.get(0)[0].x, 100);
        assertEquals("y1 set correctly", (int) mReplayData.mPointerCoordsArrays.get(0)[0].y, 200);
        assertEquals("x2 set correctly", (int) mReplayData.mPointerCoordsArrays.get(1)[0].x, 300);
        assertEquals("y2 set correctly", (int) mReplayData.mPointerCoordsArrays.get(1)[0].y, 400);
        assertEquals("only one pointer", mReplayData.mPointerCoordsArrays.get(0).length, 1);
        assertEquals("two MotionEvents", mReplayData.mPointerCoordsArrays.size(), 2);
    }

    public void testNestedDataVariantMultiPointer() {
        final JsonReader jsonReader = jsonReaderForString(
                "{"
                + "  \"_ct\": 135959040000,"
                + "  \"_ut\": 4382702,"
                + "  \"_ty\": \"MotionEvent\","
                + "  \"action\": \"MOVE\","
                + "  \"isLoggingRelated\": false,"
                + "  \"motionEvent\": {"
                + "    \"pointerIds\": ["
                + "      1"
                + "    ],"
                + "    \"xyt\": ["
                + "      {"
                + "        \"t\": 4382551,"
                + "        \"d\": ["
                + "          {"
                + "            \"x\": 100.0,"
                + "            \"y\": 200.0,"
                + "            \"toma\": 999.0,"
                + "            \"tomi\": 999.0,"
                + "            \"o\": 0.0"
                + "          },"
                + "          {"
                + "            \"x\": 300.0,"
                + "            \"y\": 400.0,"
                + "            \"toma\": 999.0,"
                + "            \"tomi\": 999.0,"
                + "            \"o\": 0.0"
                + "          }"
                + "        ]"
                + "      }"
                + "    ]"
                + "  }"
                + "}"
                );
        try {
            mMotionEventReader.readLogStatement(jsonReader, mReplayData);
        } catch (IOException e) {
            e.printStackTrace();
            fail("IOException thrown");
        }
        assertEquals("x1 set correctly", (int) mReplayData.mPointerCoordsArrays.get(0)[0].x, 100);
        assertEquals("y1 set correctly", (int) mReplayData.mPointerCoordsArrays.get(0)[0].y, 200);
        assertEquals("x2 set correctly", (int) mReplayData.mPointerCoordsArrays.get(0)[1].x, 300);
        assertEquals("y2 set correctly", (int) mReplayData.mPointerCoordsArrays.get(0)[1].y, 400);
        assertEquals("two pointers", mReplayData.mPointerCoordsArrays.get(0).length, 2);
        assertEquals("one MotionEvent", mReplayData.mPointerCoordsArrays.size(), 1);
    }
}
