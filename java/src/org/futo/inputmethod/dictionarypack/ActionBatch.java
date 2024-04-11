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

package org.futo.inputmethod.dictionarypack;

import android.content.Context;

import org.futo.inputmethod.latin.utils.DebugLogUtils;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Object representing an upgrade from one state to another.
 *
 * This implementation basically encapsulates a list of Runnable objects. In the future
 * it may manage dependencies between them. Concretely, it does not use Runnable because the
 * actions need an argument.
 */
/*

The state of a word list follows the following scheme.

       |                                   ^
  MakeAvailable                            |
       |        .------------Forget--------'
       V        |
 STATUS_AVAILABLE  <-------------------------.
       |                                     |
StartDownloadAction                  FinishDeleteAction
       |                                     |
       V                                     |
STATUS_DOWNLOADING      EnableAction-- STATUS_DELETING
       |                     |               ^
InstallAfterDownloadAction   |               |
       |     .---------------'        StartDeleteAction
       |     |                               |
       V     V                               |
 STATUS_INSTALLED  <--EnableAction--   STATUS_DISABLED
                    --DisableAction-->

  It may also be possible that DisableAction or StartDeleteAction or
  DownloadAction run when the file is still downloading.  This cancels
  the download and returns to STATUS_AVAILABLE.
  Also, an UpdateDataAction may apply in any state. It does not affect
  the state in any way (nor type, local filename, id or version) but
  may update other attributes like description or remote filename.

  Forget is an DB maintenance action that removes the entry if it is not installed or disabled.
  This happens when the word list information disappeared from the server, or when a new version
  is available and we should forget about the old one.
*/
public final class ActionBatch {
    /**
     * A piece of update.
     *
     * Action is basically like a Runnable that takes an argument.
     */
    public interface Action {
        /**
         * Execute this action NOW.
         * @param context the context to get system services, resources, databases
         */
        void execute(final Context context);
    }

    // An action batch consists of an ordered queue of Actions that can execute.
    private final Queue<Action> mActions;

    public ActionBatch() {
        mActions = new LinkedList<>();
    }

    public void add(final Action a) {
        mActions.add(a);
    }

    /**
     * Append all the actions of another action batch.
     * @param that the upgrade to merge into this one.
     */
    public void append(final ActionBatch that) {
        for (final Action a : that.mActions) {
            add(a);
        }
    }

    /**
     * Execute this batch.
     *
     * @param context the context for getting resources, databases, system services.
     * @param reporter a Reporter to send errors to.
     */
    public void execute(final Context context, final ProblemReporter reporter) {
        DebugLogUtils.l("Executing a batch of actions");
        Queue<Action> remainingActions = mActions;
        while (!remainingActions.isEmpty()) {
            final Action a = remainingActions.poll();
            try {
                a.execute(context);
            } catch (Exception e) {
                if (null != reporter)
                    reporter.report(e);
            }
        }
    }
}
