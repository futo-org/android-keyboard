/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.keyboard.tools;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.jar.JarFile;

public class MakeKeyboardText {
    static class Options {
        private static final String OPTION_JAVA = "-java";

        public final String mJava;

        public static void usage(String message) {
            if (message != null) {
                System.err.println(message);
            }
            System.err.println("usage: make-keyboard-text " + OPTION_JAVA + " <java_output_dir>");
            System.exit(1);
        }

        public Options(final String[] argsArray) {
            final LinkedList<String> args = new LinkedList<String>(Arrays.asList(argsArray));
            String arg = null;
            String java = null;
            try {
                while (!args.isEmpty()) {
                    arg = args.removeFirst();
                    if (arg.equals(OPTION_JAVA)) {
                        java = args.removeFirst();
                    } else {
                        usage("Unknown option: " + arg);
                    }
                }
            } catch (NoSuchElementException e) {
                usage("Option " + arg + " needs argument");
            }

            mJava = java;
        }
    }

    public static void main(final String[] args) {
        final Options options = new Options(args);
        final JarFile jar = JarUtils.getJarFile(MakeKeyboardText.class);
        final MoreKeysResources resources = new MoreKeysResources(jar);
        resources.writeToJava(options.mJava);
    }
}
