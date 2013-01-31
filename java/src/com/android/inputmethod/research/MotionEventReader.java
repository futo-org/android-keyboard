/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.research;

import android.util.JsonReader;
import android.util.Log;
import android.view.MotionEvent;

import com.android.inputmethod.latin.define.ProductionFlag;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class MotionEventReader {
    private static final String TAG = MotionEventReader.class.getSimpleName();
    private static final boolean DEBUG = false && ProductionFlag.IS_EXPERIMENTAL_DEBUG;

    public ReplayData readMotionEventData(final File file) {
        final ReplayData replayData = new ReplayData();
        try {
            // Read file
            final JsonReader jsonReader = new JsonReader(new BufferedReader(new InputStreamReader(
                    new FileInputStream(file))));
            jsonReader.beginArray();
            while (jsonReader.hasNext()) {
                readLogStatement(jsonReader, replayData);
            }
            jsonReader.endArray();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return replayData;
    }

    static class ReplayData {
        final ArrayList<Integer> mActions = new ArrayList<Integer>();
        final ArrayList<Integer> mXCoords = new ArrayList<Integer>();
        final ArrayList<Integer> mYCoords = new ArrayList<Integer>();
        final ArrayList<Long> mTimes = new ArrayList<Long>();
    }

    private void readLogStatement(final JsonReader jsonReader, final ReplayData replayData)
            throws IOException {
        String logStatementType = null;
        Integer actionType = null;
        Integer x = null;
        Integer y = null;
        Long time = null;
        boolean loggingRelated = false;

        jsonReader.beginObject();
        while (jsonReader.hasNext()) {
            final String key = jsonReader.nextName();
            if (key.equals("_ty")) {
                logStatementType = jsonReader.nextString();
            } else if (key.equals("_ut")) {
                time = jsonReader.nextLong();
            } else if (key.equals("x")) {
                x = jsonReader.nextInt();
            } else if (key.equals("y")) {
                y = jsonReader.nextInt();
            } else if (key.equals("action")) {
                final String s = jsonReader.nextString();
                if (s.equals("UP")) {
                    actionType = MotionEvent.ACTION_UP;
                } else if (s.equals("DOWN")) {
                    actionType = MotionEvent.ACTION_DOWN;
                } else if (s.equals("MOVE")) {
                    actionType = MotionEvent.ACTION_MOVE;
                }
            } else if (key.equals("loggingRelated")) {
                loggingRelated = jsonReader.nextBoolean();
            } else {
                if (DEBUG) {
                    Log.w(TAG, "Unknown JSON key in LogStatement: " + key);
                }
                jsonReader.skipValue();
            }
        }
        jsonReader.endObject();

        if (logStatementType != null && time != null && x != null && y != null && actionType != null
                && logStatementType.equals("MotionEvent")
                && !loggingRelated) {
            replayData.mActions.add(actionType);
            replayData.mXCoords.add(x);
            replayData.mYCoords.add(y);
            replayData.mTimes.add(time);
        }
    }

}
