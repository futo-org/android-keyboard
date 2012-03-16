/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.content.res.TypedArray;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

public class XmlParseUtils {
    private XmlParseUtils() {
        // This utility class is not publicly instantiable.
    }

    @SuppressWarnings("serial")
    public static class ParseException extends XmlPullParserException {
        public ParseException(String msg, XmlPullParser parser) {
            super(msg + " at " + parser.getPositionDescription());
        }
    }

    @SuppressWarnings("serial")
    public static class IllegalStartTag extends ParseException {
        public IllegalStartTag(XmlPullParser parser, String parent) {
            super("Illegal start tag " + parser.getName() + " in " + parent, parser);
        }
    }

    @SuppressWarnings("serial")
    public static class IllegalEndTag extends ParseException {
        public IllegalEndTag(XmlPullParser parser, String parent) {
            super("Illegal end tag " + parser.getName() + " in " + parent, parser);
        }
    }

    @SuppressWarnings("serial")
    public static class IllegalAttribute extends ParseException {
        public IllegalAttribute(XmlPullParser parser, String attribute) {
            super("Tag " + parser.getName() + " has illegal attribute " + attribute, parser);
        }
    }

    @SuppressWarnings("serial")
    public static class NonEmptyTag extends ParseException{
        public NonEmptyTag(String tag, XmlPullParser parser) {
            super(tag + " must be empty tag", parser);
        }
    }

    public static void checkEndTag(String tag, XmlPullParser parser)
            throws XmlPullParserException, IOException {
        if (parser.next() == XmlPullParser.END_TAG && tag.equals(parser.getName()))
            return;
        throw new NonEmptyTag(tag, parser);
    }

    public static void checkAttributeExists(TypedArray attr, int attrId, String attrName,
            String tag, XmlPullParser parser) throws XmlPullParserException {
        if (attr.hasValue(attrId))
            return;
        throw new ParseException(
                "No " + attrName + " attribute found in <" + tag + "/>", parser);
    }
}
