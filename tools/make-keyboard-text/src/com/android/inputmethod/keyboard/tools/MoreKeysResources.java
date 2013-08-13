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
import java.util.HashMap;
import java.util.Locale;
import java.util.jar.JarFile;

public class MoreKeysResources {
    private static final String TEXT_RESOURCE_NAME = "donottranslate-more-keys.xml";

    private static final String JAVA_TEMPLATE = "KeyboardTextsSet.tmpl";
    private static final String MARK_NAMES = "@NAMES@";
    private static final String MARK_DEFAULT_TEXTS = "@DEFAULT_TEXTS@";
    private static final String MARK_TEXTS = "@TEXTS@";
    private static final String MARK_LANGUAGES_AND_TEXTS = "@LANGUAGES_AND_TEXTS@";
    private static final String DEFAUT_LANGUAGE_NAME = "DEFAULT";
    private static final String ARRAY_NAME_FOR_LANGUAGE = "LANGUAGE_%s";
    private static final String EMPTY_STRING_VAR = "EMPTY";

    private static final String NO_LANGUAGE_CODE = "zz";
    private static final String NO_LANGUAGE_DISPLAY_NAME = "Alphabet";

    private final JarFile mJar;
    // Language to string resources map.
    private final HashMap<String, StringResourceMap> mResourcesMap =
            new HashMap<String, StringResourceMap>();
    // Name to id map.
    private final HashMap<String, Integer> mNameToIdMap = new HashMap<String,Integer>();

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
    }

    private static String getLanguageFromResDir(final String dirName) {
        final int languagePos = dirName.indexOf('-');
        if (languagePos < 0) {
            // Default resource.
            return DEFAUT_LANGUAGE_NAME;
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
        if (list.isEmpty())
            throw new RuntimeException("Can't find java template " + JAVA_TEMPLATE);
        if (list.size() > 1)
            throw new RuntimeException("Found multiple java template " + JAVA_TEMPLATE);
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
        final StringResourceMap defaultResMap = mResourcesMap.get(DEFAUT_LANGUAGE_NAME);
        int id = 0;
        for (final StringResource res : defaultResMap.getResources()) {
            out.format("        /* %2d */ \"%s\",\n", id, res.mName);
            mNameToIdMap.put(res.mName, id);
            id++;
        }
    }

    private void dumpDefaultTexts(final PrintStream out) {
        final StringResourceMap defaultResMap = mResourcesMap.get(DEFAUT_LANGUAGE_NAME);
        dumpTextsInternal(out, defaultResMap, defaultResMap);
    }

    private void dumpTexts(final PrintStream out) {
        final StringResourceMap defaultResMap = mResourcesMap.get(DEFAUT_LANGUAGE_NAME);
        final ArrayList<String> allLanguages = new ArrayList<String>();
        allLanguages.addAll(mResourcesMap.keySet());
        Collections.sort(allLanguages);
        for (final String language : allLanguages) {
            if (language.equals(DEFAUT_LANGUAGE_NAME)) {
                continue;
            }
            out.format("    /* Language %s: %s */\n", language, getLanguageDisplayName(language));
            out.format("    private static final String[] " + ARRAY_NAME_FOR_LANGUAGE + " = {\n",
                    language);
            final StringResourceMap resMap = mResourcesMap.get(language);
            for (final StringResource res : resMap.getResources()) {
                if (!defaultResMap.contains(res.mName)) {
                    throw new RuntimeException(res.mName + " in " + language
                            + " doesn't have default resource");
                }
            }
            dumpTextsInternal(out, resMap, defaultResMap);
            out.format("    };\n\n");
        }
    }

    private void dumpLanguageMap(final PrintStream out) {
        final ArrayList<String> allLanguages = new ArrayList<String>();
        allLanguages.addAll(mResourcesMap.keySet());
        Collections.sort(allLanguages);
        for (final String language : allLanguages) {
            out.format("        \"%s\", " + ARRAY_NAME_FOR_LANGUAGE + ", /* %s */\n",
                    language, language, getLanguageDisplayName(language));
        }
    }

    private static String getLanguageDisplayName(final String language) {
        if (language.equals(NO_LANGUAGE_CODE)) {
            return NO_LANGUAGE_DISPLAY_NAME;
        } else {
            return new Locale(language).getDisplayLanguage();
        }
    }

    private static void dumpTextsInternal(final PrintStream out, final StringResourceMap resMap,
            final StringResourceMap defaultResMap) {
        final ArrayInitializerFormatter formatter =
                new ArrayInitializerFormatter(out, 100, "        ");
        boolean successiveNull = false;
        for (final StringResource defaultRes : defaultResMap.getResources()) {
            if (resMap.contains(defaultRes.mName)) {
                final StringResource res = resMap.get(defaultRes.mName);
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
            } else {
                formatter.outElement("null,");
                successiveNull = true;
            }
        }
        if (!successiveNull) {
            formatter.flush();
        }
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
        return replaceIncompatibleEscape(sb.toString());
    }

    private static String replaceIncompatibleEscape(final String text) {
        String t = text;
        t = replaceAll(t, "\\?", "?");
        t = replaceAll(t, "\\@", "@");
        t = replaceAll(t, "@string/", "!text/");
        return t;
    }

    private static String replaceAll(final String text, final String target, final String replace) {
        String t = text;
        while (t.indexOf(target) >= 0) {
            t = t.replace(target, replace);
        }
        return t;
    }

    private static void close(Closeable stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException e) {
        }
    }
}
