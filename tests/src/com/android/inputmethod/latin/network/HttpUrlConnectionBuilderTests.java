/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.inputmethod.latin.network;

import static com.android.inputmethod.latin.network.HttpUrlConnectionBuilder.MODE_BI_DIRECTIONAL;
import static com.android.inputmethod.latin.network.HttpUrlConnectionBuilder.MODE_DOWNLOAD_ONLY;
import static com.android.inputmethod.latin.network.HttpUrlConnectionBuilder.MODE_UPLOAD_ONLY;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;


/**
 * Tests for {@link HttpUrlConnectionBuilder}.
 */
@SmallTest
public class HttpUrlConnectionBuilderTests extends AndroidTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testSetUrl_malformed() {
        HttpUrlConnectionBuilder builder = new HttpUrlConnectionBuilder();
        try {
            builder.setUrl("dadasd!@%@!:11");
            fail("Expected a MalformedURLException.");
        } catch (MalformedURLException e) {
            // Expected
        }
    }

    public void testSetConnectTimeout_invalid() {
        HttpUrlConnectionBuilder builder = new HttpUrlConnectionBuilder();
        try {
            builder.setConnectTimeout(-1);
            fail("Expected an IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    public void testSetConnectTimeout() throws IOException {
        HttpUrlConnectionBuilder builder = new HttpUrlConnectionBuilder();
        builder.setUrl("https://www.example.com");
        builder.setConnectTimeout(8765);
        HttpURLConnection connection = builder.build();
        assertEquals(8765, connection.getConnectTimeout());
    }

    public void testSetReadTimeout_invalid() {
        HttpUrlConnectionBuilder builder = new HttpUrlConnectionBuilder();
        try {
            builder.setReadTimeout(-1);
            fail("Expected an IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    public void testSetReadTimeout() throws IOException {
        HttpUrlConnectionBuilder builder = new HttpUrlConnectionBuilder();
        builder.setUrl("https://www.example.com");
        builder.setReadTimeout(8765);
        HttpURLConnection connection = builder.build();
        assertEquals(8765, connection.getReadTimeout());
    }

    public void testAddHeader() throws IOException {
        HttpUrlConnectionBuilder builder = new HttpUrlConnectionBuilder();
        builder.setUrl("http://www.example.com");
        builder.addHeader("some-random-key", "some-random-value");
        HttpURLConnection connection = builder.build();
        assertEquals("some-random-value", connection.getRequestProperty("some-random-key"));
    }

    public void testSetUseCache_notSet() throws IOException {
        HttpUrlConnectionBuilder builder = new HttpUrlConnectionBuilder();
        builder.setUrl("http://www.example.com");
        HttpURLConnection connection = builder.build();
        assertFalse(connection.getUseCaches());
    }

    public void testSetUseCache_false() throws IOException {
        HttpUrlConnectionBuilder builder = new HttpUrlConnectionBuilder();
        builder.setUrl("http://www.example.com");
        HttpURLConnection connection = builder.build();
        connection.setUseCaches(false);
        assertFalse(connection.getUseCaches());
    }

    public void testSetUseCache_true() throws IOException {
        HttpUrlConnectionBuilder builder = new HttpUrlConnectionBuilder();
        builder.setUrl("http://www.example.com");
        HttpURLConnection connection = builder.build();
        connection.setUseCaches(true);
        assertTrue(connection.getUseCaches());
    }

    public void testSetMode_uploadOnly() throws IOException {
        HttpUrlConnectionBuilder builder = new HttpUrlConnectionBuilder();
        builder.setUrl("http://www.example.com");
        builder.setMode(MODE_UPLOAD_ONLY);
        HttpURLConnection connection = builder.build();
        assertTrue(connection.getDoInput());
        assertFalse(connection.getDoOutput());
    }

    public void testSetMode_downloadOnly() throws IOException {
        HttpUrlConnectionBuilder builder = new HttpUrlConnectionBuilder();
        builder.setUrl("https://www.example.com");
        builder.setMode(MODE_DOWNLOAD_ONLY);
        HttpURLConnection connection = builder.build();
        assertFalse(connection.getDoInput());
        assertTrue(connection.getDoOutput());
    }

    public void testSetMode_bidirectional() throws IOException {
        HttpUrlConnectionBuilder builder = new HttpUrlConnectionBuilder();
        builder.setUrl("https://www.example.com");
        builder.setMode(MODE_BI_DIRECTIONAL);
        HttpURLConnection connection = builder.build();
        assertTrue(connection.getDoInput());
        assertTrue(connection.getDoOutput());
    }

    public void testSetAuthToken() throws IOException {
        HttpUrlConnectionBuilder builder = new HttpUrlConnectionBuilder();
        builder.setUrl("https://www.example.com");
        builder.setAuthToken("some-random-auth-token");
        HttpURLConnection connection = builder.build();
        assertEquals("some-random-auth-token",
                connection.getRequestProperty(HttpUrlConnectionBuilder.HTTP_HEADER_AUTHORIZATION));
    }
}
