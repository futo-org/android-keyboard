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

package com.android.inputmethod.keyboard.tools;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.DefaultHandler2;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class StringResourceMap {
    // String resource list.
    private final List<StringResource> mResources;
    // Name to string resource map.
    private final Map<String, StringResource> mResourcesMap;

    public StringResourceMap(final InputStream is) {
        final StringResourceHandler handler = new StringResourceHandler();
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            final SAXParser parser = factory.newSAXParser();
            // In order to get comment tag.
            parser.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
            parser.parse(is, handler);
        } catch (ParserConfigurationException e) {
        } catch (SAXParseException e) {
            throw new RuntimeException(e.getMessage() + " at line " + e.getLineNumber()
                    + ", column " + e.getColumnNumber());
        } catch (SAXException e) {
            throw new RuntimeException(e.getMessage());
        } catch (IOException e) {
        }

        mResources = Collections.unmodifiableList(handler.mResources);
        final HashMap<String,StringResource> map = new HashMap<String,StringResource>();
        for (final StringResource res : mResources) {
            map.put(res.mName, res);
        }
        mResourcesMap = map;
    }

    public List<StringResource> getResources() {
        return mResources;
    }

    public boolean contains(final String name) {
        return mResourcesMap.containsKey(name);
    }

    public StringResource get(final String name) {
        return mResourcesMap.get(name);
    }

    static class StringResourceHandler extends DefaultHandler2 {
        private static final String TAG_RESOURCES = "resources";
        private static final String TAG_STRING = "string";
        private static final String ATTR_NAME = "name";

        final ArrayList<StringResource> mResources = new ArrayList<StringResource>();

        private String mName;
        private final StringBuilder mValue = new StringBuilder();
        private final StringBuilder mComment = new StringBuilder();

        private void init() {
            mName = null;
            mComment.setLength(0);
        }

        @Override
        public void comment(char[] ch, int start, int length) {
            mComment.append(ch, start, length);
            if (ch[start + length - 1] != '\n') {
                mComment.append('\n');
            }
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attr) {
            if (TAG_RESOURCES.equals(localName)) {
                init();
            } else if (TAG_STRING.equals(localName)) {
                mName = attr.getValue(ATTR_NAME);
                mValue.setLength(0);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            mValue.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (TAG_STRING.equals(localName)) {
                if (mName == null)
                    throw new SAXException(TAG_STRING + " doesn't have name");
                final String comment = mComment.length() > 0 ? mComment.toString() : null;
                String value = mValue.toString();
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    // Trim surroundings double quote.
                    value = value.substring(1, value.length() - 1);
                }
                mResources.add(new StringResource(mName, value, comment));
                init();
            }
        }
    }
}
