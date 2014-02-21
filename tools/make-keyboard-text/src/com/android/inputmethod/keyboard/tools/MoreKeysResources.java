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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.jar.JarFile;

public class MoreKeysResources {
    private static final String TEXT_RESOURCE_NAME = "donottranslate-more-keys.xml";

    private static final String JAVA_TEMPLATE = "KeyboardTextsTable.tmpl";
    private static final String MARK_NAMES = "@NAMES@";
    private static final String MARK_DEFAULT_TEXTS = "@DEFAULT_TEXTS@";
    private static final String MARK_TEXTS = "@TEXTS@";
    private static final String MARK_LANGUAGES_AND_TEXTS = "@LANGUAGES_AND_TEXTS@";
    private static final String DEFAULT_LANGUAGE_NAME = "DEFAULT";
    private static final String EMPTY_STRING_VAR = "EMPTY";

    private static final String NO_LANGUAGE_CODE = "zz";
    private static final String NO_LANGUAGE_DISPLAY_NAME = "Alphabet";

    private final JarFile mJar;
    // Language to string resources map.
    private final HashMap<String, StringResourceMap> mResourcesMap =
            new HashMap<String, StringResourceMap>();
    // Sorted languages list. The language is taken from string resource directories
    // (values-<language>/) or {@link #DEFAULT_LANGUAGE_NAME} for the default string resource
    // directory (values/).
    private final ArrayList<String> mSortedLanguagesList = new ArrayList<String>();
    // Default string resources map.
    private final StringResourceMap mDefaultResourceMap;
    // Histogram of string resource names. This is used to sort {@link #mSortedResourceNames}.
    private final HashMap<String, Integer> mNameHistogram = new HashMap<String, Integer>();
    // Sorted string resource names array; Descending order of histogram count.
    // The string resource name is specified as an attribute "name" in string resource files.
    // The string resource can be accessed by specifying name "!text/<name>"
    // via {@link KeyboardTextsSet#getText(String)}.
    private final String[] mSortedResourceNames;

    public MoreKeysResources(final JarFile jar) {
        mJar = jar;
        final ArrayList<String> resources = JarUtils.getNameListing(jar, TEXT_RESOURCE_NAME);
        for (final String name : resources) {
            final String dirName = name.substring(0, name.lastIndexOf('/'));
            final int pos = dirName.lastIndexOf('/');
            final String parentName = (pos >= 0) ? dirName.substring(pos + 1) : dirName;
            final String language = getLanguageFromResDir(parentName);
            final InputStream stream = JarUtils.openResource(name);
            try {
                mResourcesMap.put(language, new StringResourceMap(stream));
            } finally {
                close(stream);
            }
        }
        mDefaultResourceMap = mResourcesMap.get(DEFAULT_LANGUAGE_NAME);
        mSortedLanguagesList.addAll(mResourcesMap.keySet());
        Collections.sort(mSortedLanguagesList);

        // Initialize name histogram and names list.
        final HashMap<String, Integer> nameHistogram = mNameHistogram;
        final ArrayList<String> resourceNamesList = new ArrayList<String>();
        for (final StringResource res : mDefaultResourceMap.getResources()) {
            nameHistogram.put(res.mName, 0); // Initialize histogram value.
            resourceNamesList.add(res.mName);
        }
        // Make name histogram.
        for (final String language : mResourcesMap.keySet()) {
            final StringResourceMap resMap = mResourcesMap.get(language);
            if (resMap == mDefaultResourceMap) continue;
            for (final StringResource res : resMap.getResources()) {
                if (!mDefaultResourceMap.contains(res.mName)) {
                    throw new RuntimeException(res.mName + " in " + language
                            + " doesn't have default resource");
                }
                final int histogramValue = nameHistogram.get(res.mName);
                nameHistogram.put(res.mName, histogramValue + 1);
            }
        }
        // Sort names list.
        Collections.sort(resourceNamesList, new Comparator<String>() {
            @Override
            public int compare(final String leftName, final String rightName) {
                final int leftCount = nameHistogram.get(leftName);
                final int rightCount = nameHistogram.get(rightName);
                // Descending order of histogram count.
                if (leftCount > rightCount) return -1;
                if (leftCount < rightCount) return 1;
                // TODO: Add further criteria to order the same histogram value names to be able to
                // minimize footprints of string resources arrays.
                return 0;
            }
        });
        mSortedResourceNames = resourceNamesList.toArray(new String[resourceNamesList.size()]);
    }

    private static String getLanguageFromResDir(final String dirName) {
        final int languagePos = dirName.indexOf('-');
        if (languagePos < 0) {
            // Default resource.
            return DEFAULT_LANGUAGE_NAME;
        }
        final String language = dirName.substring(languagePos + 1);
        final int countryPos = language.indexOf("-r");
        if (countryPos < 0) {
            return language;
        }
        return language.replace("-r", "_");
    }

    public void writeToJava(final String outDir) {
        final ArrayList<String> list = JarUtils.getNameListing(mJar, JAVA_TEMPLATE);
        if (list.isEmpty()) {
            throw new RuntimeException("Can't find java template " + JAVA_TEMPLATE);
        }
        if (list.size() > 1) {
            throw new RuntimeException("Found multiple java template " + JAVA_TEMPLATE);
        }
        final String template = list.get(0);
        final String javaPackage = template.substring(0, template.lastIndexOf('/'));
        PrintStream ps = null;
        LineNumberReader lnr = null;
        try {
            if (outDir == null) {
                ps = System.out;
            } else {
                final File outPackage = new File(outDir, javaPackage);
                final File outputFile = new File(outPackage,
                        JAVA_TEMPLATE.replace(".tmpl", ".java"));
                outPackage.mkdirs();
                ps = new PrintStream(outputFile, "UTF-8");
            }
            lnr = new LineNumberReader(new InputStreamReader(JarUtils.openResource(template)));
            inflateTemplate(lnr, ps);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            close(lnr);
            close(ps);
        }
    }

    private void inflateTemplate(final LineNumberReader in, final PrintStream out)
            throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (line.contains(MARK_NAMES)) {
                dumpNames(out);
            } else if (line.contains(MARK_DEFAULT_TEXTS)) {
                dumpDefaultTexts(out);
            } else if (line.contains(MARK_TEXTS)) {
                dumpTexts(out);
            } else if (line.contains(MARK_LANGUAGES_AND_TEXTS)) {
                dumpLanguageMap(out);
            } else {
                out.println(line);
            }
        }
    }

    private void dumpNames(final PrintStream out) {
        final int namesCount = mSortedResourceNames.length;
        for (int index = 0; index < namesCount; index++) {
            final String name = mSortedResourceNames[index];
            final int histogramValue = mNameHistogram.get(name);
            out.format("        /* %3d:%2d */ \"%s\",\n", index, histogramValue, name);
        }
    }

    private void dumpDefaultTexts(final PrintStream out) {
        final int outputArraySize = dumpTextsInternal(out, mDefaultResourceMap);
        mDefaultResourceMap.setOutputArraySize(outputArraySize);
    }

    private static String getArrayNameForLanguage(final String language) {
        return "LANGUAGE_" + language;
    }

    private void dumpTexts(final PrintStream out) {
        for (final String language : mSortedLanguagesList) {
            final StringResourceMap resMap = mResourcesMap.get(language);
            if (resMap == mDefaultResourceMap) continue;
            out.format("    /* Language %s: %s */\n", language, getLanguageDisplayName(language));
            out.format("    private static final String[] " + getArrayNameForLanguage(language)
                    + " = {\n");
            final int outputArraySize = dumpTextsInternal(out, resMap);
            resMap.setOutputArraySize(outputArraySize);
            out.format("    };\n\n");
        }
    }

    private void dumpLanguageMap(final PrintStream out) {
        for (final String language : mSortedLanguagesList) {
            final StringResourceMap resMap = mResourcesMap.get(language);
            final Locale locale = LocaleUtils.constructLocaleFromString(language);
            final String languageKeyToDump = locale.getCountry().isEmpty()
                    ? String.format("\"%s\"", language)
                    : String.format("\"%s\"", locale.getLanguage());
            out.format("        %s, %-15s /* %3d/%3d %s */\n",
                    languageKeyToDump, getArrayNameForLanguage(language) + ",",
                    resMap.getResources().size(), resMap.getOutputArraySize(),
                    getLanguageDisplayName(language));
        }
    }

    private static String getLanguageDisplayName(final String language) {
        final Locale locale = LocaleUtils.constructLocaleFromString(language);
        if (locale.getLanguage().equals(NO_LANGUAGE_CODE)) {
            return NO_LANGUAGE_DISPLAY_NAME;
        }
        return locale.getDisplayName(Locale.ENGLISH);
    }

    private int dumpTextsInternal(final PrintStream out, final StringResourceMap resMap) {
        final ArrayInitializerFormatter formatter =
                new ArrayInitializerFormatter(out, 100, "        ", mSortedResourceNames);
        int outputArraySize = 0;
        boolean successiveNull = false;
        final int namesCount = mSortedResourceNames.length;
        for (int index = 0; index < namesCount; index++) {
            final String name = mSortedResourceNames[index];
            final StringResource res = resMap.get(name);
            if (res != null) {
                // TODO: Check whether the resource value is equal to the default.
                if (res.mComment != null) {
                    formatter.outCommentLines(addPrefix("        // ", res. mComment));
                }
                final String escaped = escapeNonAscii(res.mValue);
                if (escaped.length() == 0) {
                    formatter.outElement(EMPTY_STRING_VAR + ",");
                } else {
                    formatter.outElement(String.format("\"%s\",", escaped));
                }
                successiveNull = false;
                outputArraySize = formatter.getCurrentIndex();
            } else {
                formatter.outElement("null,");
                successiveNull = true;
            }
        }
        if (!successiveNull) {
            formatter.flush();
        }
        return outputArraySize;
    }

    private static String addPrefix(final String prefix, final String lines) {
        final StringBuilder sb = new StringBuilder();
        for (final String line : lines.split("\n")) {
            sb.append(prefix + line.trim() + "\n");
        }
        return sb.toString();
    }

    private static String escapeNonAscii(final String text) {
        final StringBuilder sb = new StringBuilder();
        final int length = text.length();
        for (int i = 0; i < length; i++) {
            final char c = text.charAt(i);
            if (c >= ' ' && c < 0x7f) {
                sb.append(c);
            } else {
                sb.append(String.format("\\u%04X", (int)c));
            }
        }
        return sb.toString();
    }

    private static void close(final Closeable stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException e) {
        }
    }
}
