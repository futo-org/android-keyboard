/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.res.TypedArray;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

public final class XmlParseUtils {
    private XmlParseUtils() {
        // This utility class is not publicly instantiable.
    }

    @SuppressWarnings("serial")
    public static class ParseException extends XmlPullParserException {
        public ParseException(final String msg, final XmlPullParser parser) {
            super(msg + " at " + parser.getPositionDescription());
        }
    }

    @SuppressWarnings("serial")
    public static final class IllegalStartTag extends ParseException {
        public IllegalStartTag(final XmlPullParser parser, final String tag, final String parent) {
            super("Illegal start tag " + tag + " in " + parent, parser);
        }
    }

    @SuppressWarnings("serial")
    public static final class IllegalEndTag extends ParseException {
        public IllegalEndTag(final XmlPullParser parser, final String tag, final String parent) {
            super("Illegal end tag " + tag + " in " + parent, parser);
        }
    }

    @SuppressWarnings("serial")
    public static final class IllegalAttribute extends ParseException {
        public IllegalAttribute(final XmlPullParser parser, final String tag,
                final String attribute) {
            super("Tag " + tag + " has illegal attribute " + attribute, parser);
        }
    }

    @SuppressWarnings("serial")
    public static final class NonEmptyTag extends ParseException{
        public NonEmptyTag(final XmlPullParser parser, final String tag) {
            super(tag + " must be empty tag", parser);
        }
    }

    public static void checkEndTag(final String tag, final XmlPullParser parser)
            throws XmlPullParserException, IOException {
        if (parser.next() == XmlPullParser.END_TAG && tag.equals(parser.getName()))
            return;
        throw new NonEmptyTag(parser, tag);
    }

    public static void checkAttributeExists(final TypedArray attr, final int attrId,
            final String attrName, final String tag, final XmlPullParser parser)
                    throws XmlPullParserException {
        if (attr.hasValue(attrId)) {
            return;
        }
        throw new ParseException(
                "No " + attrName + " attribute found in <" + tag + "/>", parser);
    }
}
