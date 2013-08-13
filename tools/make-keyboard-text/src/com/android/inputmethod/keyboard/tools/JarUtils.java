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

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class JarUtils {
    private JarUtils() {
        // This utility class is not publicly instantiable.
    }

    public static JarFile getJarFile(final Class<?> mainClass) {
        final String mainClassPath = "/" + mainClass.getName().replace('.', '/') + ".class";
        final URL resUrl = mainClass.getResource(mainClassPath);
        if (!resUrl.getProtocol().equals("jar")) {
            throw new RuntimeException("Should run as jar");
        }
        final String path = resUrl.getPath();
        if (!path.startsWith("file:")) {
            throw new RuntimeException("Unknown jar path: " + path);
        }
        final String jarPath = path.substring("file:".length(), path.indexOf('!'));
        try {
            return new JarFile(URLDecoder.decode(jarPath, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
        } catch (IOException e) {
        }
        return null;
    }

    public static InputStream openResource(final String name) {
        return JarUtils.class.getResourceAsStream("/" + name);
    }

    public interface JarFilter {
        public boolean accept(String dirName, String name);
    }

    public static ArrayList<String> getNameListing(final JarFile jar, final JarFilter filter) {
        final ArrayList<String> result = new ArrayList<String>();
        final Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            final String path = entry.getName();
            final int pos = path.lastIndexOf('/');
            final String dirName = (pos >= 0) ? path.substring(0, pos) : "";
            final String name = (pos >= 0) ? path.substring(pos + 1) : path;
            if (filter.accept(dirName, name)) {
                result.add(path);
            }
        }
        return result;
    }

    public static ArrayList<String> getNameListing(final JarFile jar, final String filterName) {
        return getNameListing(jar, new JarFilter() {
            @Override
            public boolean accept(final String dirName, final String name) {
                return name.equals(filterName);
            }
        });
    }
}
