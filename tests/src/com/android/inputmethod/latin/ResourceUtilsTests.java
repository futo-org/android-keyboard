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

package com.android.inputmethod.latin;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.HashMap;

@SmallTest
public class ResourceUtilsTests extends AndroidTestCase {
    public void testFindDefaultConstant() {
        final String[] nullArray = null;
        assertNull(ResourceUtils.findDefaultConstant(nullArray));

        final String[] emptyArray = {};
        assertNull(ResourceUtils.findDefaultConstant(emptyArray));

        final String[] array = {
            "HARDWARE=grouper,0.3",
            "HARDWARE=mako,0.4",
            ",defaultValue1",
            "HARDWARE=manta,0.2",
            ",defaultValue2",
        };
        assertEquals(ResourceUtils.findDefaultConstant(array), "defaultValue1");
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
        assertEquals(ResourceUtils.findConstantForKeyValuePairs(keyValues, array), "0.3");
        keyValues.put(HARDWARE_KEY, "mako");
        assertEquals(ResourceUtils.findConstantForKeyValuePairs(keyValues, array), "0.4");
        keyValues.put(HARDWARE_KEY, "manta");
        assertEquals(ResourceUtils.findConstantForKeyValuePairs(keyValues, array), "0.2");

        try {
            keyValues.clear();
            keyValues.put("hardware", "grouper");
            final String constant = ResourceUtils.findConstantForKeyValuePairs(keyValues, array);
            fail("condition without HARDWARE must fail: constant=" + constant);
        } catch (final RuntimeException e) {
            assertEquals(e.getMessage(), "Found unknown key: HARDWARE=grouper");
        }
        keyValues.clear();
        keyValues.put(HARDWARE_KEY, "MAKO");
        assertNull(ResourceUtils.findConstantForKeyValuePairs(keyValues, array));
        keyValues.put(HARDWARE_KEY, "mantaray");
        assertNull(ResourceUtils.findConstantForKeyValuePairs(keyValues, array));

        try {
            final String constant = ResourceUtils.findConstantForKeyValuePairs(
                    emptyKeyValue, array);
            fail("emptyCondition shouldn't match: constant=" + constant);
        } catch (final RuntimeException e) {
            assertEquals(e.getMessage(), "Found unknown key: HARDWARE=grouper");
        }
    }

    public void testFindConstantForKeyValuePairsCombined() {
        final String HARDWARE_KEY = "HARDWARE";
        final String MODEL_KEY = "MODEL";
        final String MANUFACTURER_KEY = "MANUFACTURER";
        final String[] array = {
            ",defaultValue",
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
        assertEquals(ResourceUtils.findConstantForKeyValuePairs(keyValues, array), "0.3");
        assertNull(ResourceUtils.findConstantForKeyValuePairs(keyValues, failArray));

        keyValues.clear();
        keyValues.put(HARDWARE_KEY, "mako");
        keyValues.put(MODEL_KEY, "Nexus 4");
        keyValues.put(MANUFACTURER_KEY, "LGE");
        assertEquals(ResourceUtils.findConstantForKeyValuePairs(keyValues, array), "0.4");
        assertNull(ResourceUtils.findConstantForKeyValuePairs(keyValues, failArray));

        keyValues.clear();
        keyValues.put(HARDWARE_KEY, "manta");
        keyValues.put(MODEL_KEY, "Nexus 10");
        keyValues.put(MANUFACTURER_KEY, "samsung");
        assertEquals(ResourceUtils.findConstantForKeyValuePairs(keyValues, array), "0.2");
        assertNull(ResourceUtils.findConstantForKeyValuePairs(keyValues, failArray));
        keyValues.put(HARDWARE_KEY, "mantaray");
        assertNull(ResourceUtils.findConstantForKeyValuePairs(keyValues, array));
        assertEquals(ResourceUtils.findConstantForKeyValuePairs(keyValues, failArray), "0.2");
    }

    public void testFindConstantForKeyValuePairsRegexp() {
        final String HARDWARE_KEY = "HARDWARE";
        final String MODEL_KEY = "MODEL";
        final String MANUFACTURER_KEY = "MANUFACTURER";
        final String[] array = {
            ",defaultValue",
            "HARDWARE=grouper|tilapia:MANUFACTURER=asus,0.3",
            "HARDWARE=[mM][aA][kK][oO]:MODEL=Nexus 4,0.4",
            "HARDWARE=manta.*:MODEL=Nexus 10:MANUFACTURER=samsung,0.2"
        };

        final HashMap<String,String> keyValues = CollectionUtils.newHashMap();
        keyValues.put(HARDWARE_KEY, "grouper");
        keyValues.put(MODEL_KEY, "Nexus 7");
        keyValues.put(MANUFACTURER_KEY, "asus");
        assertEquals(ResourceUtils.findConstantForKeyValuePairs(keyValues, array), "0.3");
        keyValues.put(HARDWARE_KEY, "tilapia");
        assertEquals(ResourceUtils.findConstantForKeyValuePairs(keyValues, array), "0.3");

        keyValues.clear();
        keyValues.put(HARDWARE_KEY, "mako");
        keyValues.put(MODEL_KEY, "Nexus 4");
        keyValues.put(MANUFACTURER_KEY, "LGE");
        assertEquals(ResourceUtils.findConstantForKeyValuePairs(keyValues, array), "0.4");
        keyValues.put(HARDWARE_KEY, "MAKO");
        assertEquals(ResourceUtils.findConstantForKeyValuePairs(keyValues, array), "0.4");

        keyValues.clear();
        keyValues.put(HARDWARE_KEY, "manta");
        keyValues.put(MODEL_KEY, "Nexus 10");
        keyValues.put(MANUFACTURER_KEY, "samsung");
        assertEquals(ResourceUtils.findConstantForKeyValuePairs(keyValues, array), "0.2");
        keyValues.put(HARDWARE_KEY, "mantaray");
        assertEquals(ResourceUtils.findConstantForKeyValuePairs(keyValues, array), "0.2");
    }
}
