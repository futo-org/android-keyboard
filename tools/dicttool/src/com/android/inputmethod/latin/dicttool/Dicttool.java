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
        CommandList.populate();
    }
    public static void addCommand(final String commandName, final Class<? extends Command> cls) {
        sCommands.put(commandName, cls);
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

    private Command getCommand(final String[] arguments) {
        final String commandName = arguments[0];
        if (!isCommand(commandName)) {
            throw new RuntimeException("Unknown command : " + commandName);
        }
        final Command command = getCommandInstance(commandName);
        final String[] argsArray = Arrays.copyOfRange(arguments, 1, arguments.length);
        command.setArgs(argsArray);
        return command;
    }

    /**
     * Executes the specified command with the specified arguments.
     * @param arguments the arguments passed to dicttool.
     * @return 0 for success, an error code otherwise (always 1 at the moment)
     */
    private int execute(final String[] arguments) {
        final Command command = getCommand(arguments);
        try {
            command.run();
            return 0;
        } catch (Exception e) {
            System.out.println("Exception while processing command "
                    + command.getClass().getSimpleName() + " : " + e);
            e.printStackTrace();
            return 1;
        }
    }

    public static void main(final String[] arguments) {
        if (0 == arguments.length) {
            help();
            return;
        }
        // Exit with the success/error code from #execute() as status.
        System.exit(new Dicttool().execute(arguments));
    }
}
