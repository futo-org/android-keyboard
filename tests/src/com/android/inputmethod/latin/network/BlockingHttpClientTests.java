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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.inputmethod.latin.network.BlockingHttpClient.ResponseProcessor;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Random;

/**
 * Tests for {@link BlockingHttpClient}.
 */
@SmallTest
public class BlockingHttpClientTests extends AndroidTestCase {
    @Mock HttpURLConnection mMockHttpConnection;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
    }

    public void testError_badGateway() throws IOException {
        when(mMockHttpConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_BAD_GATEWAY);
        final BlockingHttpClient client = new BlockingHttpClient(mMockHttpConnection);
        final FakeErrorResponseProcessor processor =
                new FakeErrorResponseProcessor(HttpURLConnection.HTTP_BAD_GATEWAY);

        client.execute(null /* empty request */, processor);
        assertTrue("ResponseProcessor was not invoked", processor.mInvoked);
    }

    public void testError_clientTimeout() throws IOException {
        when(mMockHttpConnection.getResponseCode()).thenReturn(
                HttpURLConnection.HTTP_CLIENT_TIMEOUT);
        final BlockingHttpClient client = new BlockingHttpClient(mMockHttpConnection);
        final FakeErrorResponseProcessor processor =
                new FakeErrorResponseProcessor(HttpURLConnection.HTTP_CLIENT_TIMEOUT);

        client.execute(null /* empty request */, processor);
        assertTrue("ResponseProcessor was not invoked", processor.mInvoked);
    }

    public void testError_forbiddenWithRequest() throws IOException {
        final OutputStream mockOutputStream = Mockito.mock(OutputStream.class);
        when(mMockHttpConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_FORBIDDEN);
        when(mMockHttpConnection.getOutputStream()).thenReturn(mockOutputStream);
        final BlockingHttpClient client = new BlockingHttpClient(mMockHttpConnection);
        final FakeErrorResponseProcessor processor =
                new FakeErrorResponseProcessor(HttpURLConnection.HTTP_FORBIDDEN);

        client.execute(new byte[100], processor);
        verify(mockOutputStream).write(any(byte[].class), eq(0), eq(100));
        assertTrue("ResponseProcessor was not invoked", processor.mInvoked);
    }

    public void testSuccess_emptyRequest() throws IOException {
        final Random rand = new Random();
        byte[] response = new byte[100];
        rand.nextBytes(response);
        when(mMockHttpConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(mMockHttpConnection.getInputStream()).thenReturn(new ByteArrayInputStream(response));
        final BlockingHttpClient client = new BlockingHttpClient(mMockHttpConnection);
        final FakeSuccessResponseProcessor processor =
                new FakeSuccessResponseProcessor(response);

        client.execute(null /* empty request */, processor);
        assertTrue("ResponseProcessor was not invoked", processor.mInvoked);
    }

    public void testSuccess() throws IOException {
        final OutputStream mockOutputStream = Mockito.mock(OutputStream.class);
        final Random rand = new Random();
        byte[] response = new byte[100];
        rand.nextBytes(response);
        when(mMockHttpConnection.getOutputStream()).thenReturn(mockOutputStream);
        when(mMockHttpConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(mMockHttpConnection.getInputStream()).thenReturn(new ByteArrayInputStream(response));
        final BlockingHttpClient client = new BlockingHttpClient(mMockHttpConnection);
        final FakeSuccessResponseProcessor processor =
                new FakeSuccessResponseProcessor(response);

        client.execute(new byte[100], processor);
        assertTrue("ResponseProcessor was not invoked", processor.mInvoked);
    }

    private static class FakeErrorResponseProcessor implements ResponseProcessor {
        private final int mExpectedStatusCode;

        boolean mInvoked;

        FakeErrorResponseProcessor(int expectedStatusCode) {
            mExpectedStatusCode = expectedStatusCode;
        }

        @Override
        public void onError(int httpStatusCode, String message) {
            mInvoked = true;
            assertEquals("onError:", mExpectedStatusCode, httpStatusCode);
        }

        @Override
        public void onSuccess(InputStream response) {
            fail("Expected an error but received success");
        }
    }

    private static class FakeSuccessResponseProcessor implements ResponseProcessor {
        private final byte[] mExpectedResponse;

        boolean mInvoked;

        FakeSuccessResponseProcessor(byte[] expectedResponse) {
            mExpectedResponse = expectedResponse;
        }

        @Override
        public void onError(int httpStatusCode, String message) {
            fail("Expected a response but received an error");
        }

        @Override
        public void onSuccess(InputStream response) {
            try {
                mInvoked = true;
                BufferedInputStream in = new BufferedInputStream(response);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int read = 0;
                while ((read = in.read()) != -1) {
                    buffer.write(read);
                }
                byte[] actualResponse = buffer.toByteArray();
                in.close();
                assertTrue("Response doesn't match",
                        Arrays.equals(mExpectedResponse, actualResponse));
            } catch (IOException ex) {
                fail("IOException in onSuccess");
            }
        }
    }
}
