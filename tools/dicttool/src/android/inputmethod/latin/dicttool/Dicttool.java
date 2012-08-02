/**
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

package com.android.inputmethod.latin.dicttool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class Dicttool {

    public static abstract class Command {
        protected String[] mArgs;
        public void setArgs(String[] args) throws IllegalArgumentException {
            mArgs = args;
        }
        abstract public String getHelp();
        abstract public void run() throws Exception;
    }
    static HashMap<String, Class<? extends Command>> sCommands =
            new HashMap<String, Class<? extends Command>>();
    static {
        sCommands.put("info", Info.class);
        sCommands.put("compress", Compress.Compressor.class);
        sCommands.put("uncompress", Compress.Uncompressor.class);
    }

    private static Command getCommandInstance(final String commandName) {
        try {
            return sCommands.get(commandName).newInstance();
        } catch (InstantiationException e) {
            throw new RuntimeException(commandName + " is not installed");
        } catch (IllegalAccessException e) {
            throw new RuntimeException(commandName + " is not installed");
        }
    }

    private static void help() {
        System.out.println("Syntax: dicttool <command [arguments]>\nAvailable commands:\n");
        for (final String commandName : sCommands.keySet()) {
            System.out.println("*** " + commandName);
            System.out.println(getCommandInstance(commandName).getHelp());
            System.out.println("");
        }
    }

    private static boolean isCommand(final String commandName) {
        return sCommands.containsKey(commandName);
    }

    private Command getCommand(final ArrayList<String> arguments) {
        final String firstArgument = arguments.get(0);
        final String commandName;
        if (isCommand(firstArgument)) {
            commandName = firstArgument;
            arguments.remove(0);
        } else {
            throw new RuntimeException("Unknown command : " + firstArgument);
        }
        final Command command = getCommandInstance(commandName);
        final String[] argsArray = arguments.toArray(new String[arguments.size()]);
        command.setArgs(argsArray);
        return command;
    }

    private void execute(final ArrayList<String> arguments) {
        final Command command = getCommand(arguments);
        try {
            command.run();
        } catch (Exception e) {
            System.out.println("Exception while processing command "
                    + command.getClass().getSimpleName() + " : " + e);
            return;
        }
    }

    public static void main(final String[] args) {
        if (0 == args.length) {
            help();
            return;
        }
        if (!isCommand(args[0])) throw new RuntimeException("Unknown command : " + args[0]);

        final ArrayList<String> arguments = new ArrayList<String>(args.length);
        arguments.addAll(Arrays.asList(args));
        new Dicttool().execute(arguments);
    }
}
