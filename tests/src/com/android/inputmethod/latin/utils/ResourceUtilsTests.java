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

package com.android.inputmethod.latin.utils;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.inputmethod.latin.utils.ResourceUtils.DeviceOverridePatternSyntaxError;

import java.util.HashMap;

@SmallTest
public class ResourceUtilsTests extends AndroidTestCase {
    public void testFindDefaultConstant() {
        final String[] nullArray = null;
        final String[] emptyArray = {};
        final String[] array = {
                "HARDWARE=grouper,0.3",
                "HARDWARE=mako,0.4",
                ",defaultValue1",
                "HARDWARE=manta,0.2",
                ",defaultValue2",
        };

        try {
            assertNull(ResourceUtils.findDefaultConstant(nullArray));
            assertNull(ResourceUtils.findDefaultConstant(emptyArray));
            assertEquals(ResourceUtils.findDefaultConstant(array), "defaultValue1");
        } catch (final DeviceOverridePatternSyntaxError e) {
            fail(e.getMessage());
        }

        final String[] errorArray = {
            "HARDWARE=grouper,0.3",
            "no_comma"
        };
        try {
            final String defaultValue = ResourceUtils.findDefaultConstant(errorArray);
            fail("exception should be thrown: defaultValue=" + defaultValue);
        } catch (final DeviceOverridePatternSyntaxError e) {
            assertEquals("Array element has no comma: no_comma", e.getMessage());
        }
    }

    public void testFindConstantForKeyValuePairsSimple() {
        final HashMap<String,String> anyKeyValue = CollectionUtils.newHashMap();
        anyKeyValue.put("anyKey", "anyValue");
        final HashMap<String,String> nullKeyValue = null;
        final HashMap<String,String> emptyKeyValue = CollectionUtils.newHashMap();

        final String[] nullArray = null;
        assertNull(ResourceUtils.findConstantForKeyValuePairs(anyKeyValue, nullArray));
        assertNull(ResourceUtils.findConstantForKeyValuePairs(emptyKeyValue, nullArray));
        assertNull(ResourceUtils.findConstantForKeyValuePairs(nullKeyValue, nullArray));

        final String[] emptyArray = {};
        assertNull(ResourceUtils.findConstantForKeyValuePairs(anyKeyValue, emptyArray));
        assertNull(ResourceUtils.findConstantForKeyValuePairs(emptyKeyValue, emptyArray));
        assertNull(ResourceUtils.findConstantForKeyValuePairs(nullKeyValue, emptyArray));

        final String HARDWARE_KEY = "HARDWARE";
        final String[] array = {
            ",defaultValue",
            "HARDWARE=grouper,0.3",
            "HARDWARE=mako,0.4",
            "HARDWARE=manta,0.2",
            "HARDWARE=mako,0.5",
        };

        final HashMap<String,String> keyValues = CollectionUtils.newHashMap();
        keyValues.put(HARDWARE_KEY, "grouper");
        assertEquals("0.3", ResourceUtils.findConstantForKeyValuePairs(keyValues, array));
        keyValues.put(HARDWARE_KEY, "mako");
        assertEquals("0.4", ResourceUtils.findConstantForKeyValuePairs(keyValues, array));
        keyValues.put(HARDWARE_KEY, "manta");
        assertEquals("0.2", ResourceUtils.findConstantForKeyValuePairs(keyValues, array));

        keyValues.clear();
        keyValues.put("hardware", "grouper");
        assertNull(ResourceUtils.findConstantForKeyValuePairs(keyValues, array));

        keyValues.clear();
        keyValues.put(HARDWARE_KEY, "MAKO");
        assertNull(ResourceUtils.findConstantForKeyValuePairs(keyValues, array));
        keyValues.put(HARDWARE_KEY, "mantaray");
        assertNull(ResourceUtils.findConstantForKeyValuePairs(keyValues, array));

        assertNull(ResourceUtils.findConstantForKeyValuePairs(emptyKeyValue, array));
    }

    public void testFindConstantForKeyValuePairsCombined() {
        final String HARDWARE_KEY = "HARDWARE";
        final String MODEL_KEY = "MODEL";
        final String MANUFACTURER_KEY = "MANUFACTURER";
        final String[] array = {
            ",defaultValue",
            "no_comma",
            "error_pattern,0.1",
            "HARDWARE=grouper:MANUFACTURER=asus,0.3",
            "HARDWARE=mako:MODEL=Nexus 4,0.4",
            "HARDWARE=manta:MODEL=Nexus 10:MANUFACTURER=samsung,0.2"
        };
        final String[] failArray = {
            ",defaultValue",
            "HARDWARE=grouper:MANUFACTURER=ASUS,0.3",
            "HARDWARE=mako:MODEL=Nexus_4,0.4",
            "HARDWARE=mantaray:MODEL=Nexus 10:MANUFACTURER=samsung,0.2"
        };

        final HashMap<String,String> keyValues = CollectionUtils.newHashMap();
        keyValues.put(HARDWARE_KEY, "grouper");
        keyValues.put(MODEL_KEY, "Nexus 7");
        keyValues.put(MANUFACTURER_KEY, "asus");
        assertEquals("0.3", ResourceUtils.findConstantForKeyValuePairs(keyValues, array));
        assertNull(ResourceUtils.findConstantForKeyValuePairs(keyValues, failArray));

        keyValues.clear();
        keyValues.put(HARDWARE_KEY, "mako");
        keyValues.put(MODEL_KEY, "Nexus 4");
        keyValues.put(MANUFACTURER_KEY, "LGE");
        assertEquals("0.4", ResourceUtils.findConstantForKeyValuePairs(keyValues, array));
        assertNull(ResourceUtils.findConstantForKeyValuePairs(keyValues, failArray));

        keyValues.clear();
        keyValues.put(HARDWARE_KEY, "manta");
        keyValues.put(MODEL_KEY, "Nexus 10");
        keyValues.put(MANUFACTURER_KEY, "samsung");
        assertEquals("0.2", ResourceUtils.findConstantForKeyValuePairs(keyValues, array));
        assertNull(ResourceUtils.findConstantForKeyValuePairs(keyValues, failArray));
        keyValues.put(HARDWARE_KEY, "mantaray");
        assertNull(ResourceUtils.findConstantForKeyValuePairs(keyValues, array));
        assertEquals("0.2", ResourceUtils.findConstantForKeyValuePairs(keyValues, failArray));
    }

    public void testFindConstantForKeyValuePairsRegexp() {
        final String HARDWARE_KEY = "HARDWARE";
        final String MODEL_KEY = "MODEL";
        final String MANUFACTURER_KEY = "MANUFACTURER";
        final String[] array = {
            ",defaultValue",
            "no_comma",
            "HARDWARE=error_regexp:MANUFACTURER=error[regexp,0.1",
            "HARDWARE=grouper|tilapia:MANUFACTURER=asus,0.3",
            "HARDWARE=[mM][aA][kK][oO]:MODEL=Nexus 4,0.4",
            "HARDWARE=manta.*:MODEL=Nexus 10:MANUFACTURER=samsung,0.2"
        };

        final HashMap<String,String> keyValues = CollectionUtils.newHashMap();
        keyValues.put(HARDWARE_KEY, "grouper");
        keyValues.put(MODEL_KEY, "Nexus 7");
        keyValues.put(MANUFACTURER_KEY, "asus");
        assertEquals("0.3", ResourceUtils.findConstantForKeyValuePairs(keyValues, array));
        keyValues.put(HARDWARE_KEY, "tilapia");
        assertEquals("0.3", ResourceUtils.findConstantForKeyValuePairs(keyValues, array));

        keyValues.clear();
        keyValues.put(HARDWARE_KEY, "mako");
        keyValues.put(MODEL_KEY, "Nexus 4");
        keyValues.put(MANUFACTURER_KEY, "LGE");
        assertEquals("0.4", ResourceUtils.findConstantForKeyValuePairs(keyValues, array));
        keyValues.put(HARDWARE_KEY, "MAKO");
        assertEquals("0.4", ResourceUtils.findConstantForKeyValuePairs(keyValues, array));

        keyValues.clear();
        keyValues.put(HARDWARE_KEY, "manta");
        keyValues.put(MODEL_KEY, "Nexus 10");
        keyValues.put(MANUFACTURER_KEY, "samsung");
        assertEquals("0.2", ResourceUtils.findConstantForKeyValuePairs(keyValues, array));
        keyValues.put(HARDWARE_KEY, "mantaray");
        assertEquals("0.2", ResourceUtils.findConstantForKeyValuePairs(keyValues, array));
    }
}
