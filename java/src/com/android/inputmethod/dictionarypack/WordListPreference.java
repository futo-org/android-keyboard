/**
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.android.inputmethod.dictionarypack;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ListView;
import android.widget.TextView;

import com.android.inputmethod.latin.R;

import java.util.Locale;

/**
 * A preference for one word list.
 *
 * This preference refers to a single word list, as available in the dictionary
 * pack. Upon being pressed, it displays a menu to allow the user to install, disable,
 * enable or delete it as appropriate for the current state of the word list.
 */
public final class WordListPreference extends Preference {
    static final private String TAG = WordListPreference.class.getSimpleName();

    // What to display in the "status" field when we receive unknown data as a status from
    // the content provider. Empty string sounds sensible.
    static final private String NO_STATUS_MESSAGE = "";

    /// Actions
    static final private int ACTION_UNKNOWN = 0;
    static final private int ACTION_ENABLE_DICT = 1;
    static final private int ACTION_DISABLE_DICT = 2;
    static final private int ACTION_DELETE_DICT = 3;

    // Members
    // The context to get resources
    final Context mContext;
    // The id of the client for which this preference is.
    final String mClientId;
    // The metadata word list id and version of this word list.
    public final String mWordlistId;
    public final int mVersion;
    public final Locale mLocale;
    public final String mDescription;
    // The status
    private int mStatus;
    // The size of the dictionary file
    private final int mFilesize;

    private final DictionaryListInterfaceState mInterfaceState;
    private final OnWordListPreferenceClick mPreferenceClickHandler =
            new OnWordListPreferenceClick();
    private final OnActionButtonClick mActionButtonClickHandler =
            new OnActionButtonClick();

    public WordListPreference(final Context context,
            final DictionaryListInterfaceState dictionaryListInterfaceState, final String clientId,
            final String wordlistId, final int version, final Locale locale,
            final String description, final int status, final int filesize) {
        super(context, null);
        mContext = context;
        mInterfaceState = dictionaryListInterfaceState;
        mClientId = clientId;
        mVersion = version;
        mWordlistId = wordlistId;
        mFilesize = filesize;
        mLocale = locale;
        mDescription = description;

        setLayoutResource(R.layout.dictionary_line);

        setTitle(description);
        setStatus(status);
        setKey(wordlistId);
    }

    public void setStatus(final int status) {
        if (status == mStatus) return;
        mStatus = status;
        setSummary(getSummary(status));
    }

    @Override
    public View onCreateView(final ViewGroup parent) {
        final View orphanedView = mInterfaceState.findFirstOrphanedView();
        if (null != orphanedView) return orphanedView; // Will be sent to onBindView
        final View newView = super.onCreateView(parent);
        return mInterfaceState.addToCacheAndReturnView(newView);
    }

    public boolean hasPriorityOver(final int otherPrefStatus) {
        // Both of these should be one of MetadataDbHelper.STATUS_*
        return mStatus > otherPrefStatus;
    }

    private String getSummary(final int status) {
        switch (status) {
            // If we are deleting the word list, for the user it's like it's already deleted.
            // It should be reinstallable. Exposing to the user the whole complexity of
            // the delayed deletion process between the dictionary pack and Android Keyboard
            // would only be confusing.
            case MetadataDbHelper.STATUS_DELETING:
            case MetadataDbHelper.STATUS_AVAILABLE:
                return mContext.getString(R.string.dictionary_available);
            case MetadataDbHelper.STATUS_DOWNLOADING:
                return mContext.getString(R.string.dictionary_downloading);
            case MetadataDbHelper.STATUS_INSTALLED:
                return mContext.getString(R.string.dictionary_installed);
            case MetadataDbHelper.STATUS_DISABLED:
                return mContext.getString(R.string.dictionary_disabled);
            default:
                return NO_STATUS_MESSAGE;
        }
    }

    // The table below needs to be kept in sync with MetadataDbHelper.STATUS_* since it uses
    // the values as indices.
    private static final int sStatusActionList[][] = {
        // MetadataDbHelper.STATUS_UNKNOWN
        {},
        // MetadataDbHelper.STATUS_AVAILABLE
        { ButtonSwitcher.STATUS_INSTALL, ACTION_ENABLE_DICT },
        // MetadataDbHelper.STATUS_DOWNLOADING
        { ButtonSwitcher.STATUS_CANCEL, ACTION_DISABLE_DICT },
        // MetadataDbHelper.STATUS_INSTALLED
        { ButtonSwitcher.STATUS_DELETE, ACTION_DELETE_DICT },
        // MetadataDbHelper.STATUS_DISABLED
        { ButtonSwitcher.STATUS_DELETE, ACTION_DELETE_DICT },
        // MetadataDbHelper.STATUS_DELETING
        // We show 'install' because the file is supposed to be deleted.
        // The user may reinstall it.
        { ButtonSwitcher.STATUS_INSTALL, ACTION_ENABLE_DICT }
    };

    private int getButtonSwitcherStatus(final int status) {
        if (status >= sStatusActionList.length) {
            Log.e(TAG, "Unknown status " + status);
            return ButtonSwitcher.STATUS_NO_BUTTON;
        }
        return sStatusActionList[status][0];
    }

    private static int getActionIdFromStatusAndMenuEntry(final int status) {
        if (status >= sStatusActionList.length) {
            Log.e(TAG, "Unknown status " + status);
            return ACTION_UNKNOWN;
        }
        return sStatusActionList[status][1];
    }

    private void disableDict() {
        SharedPreferences prefs = CommonPreferences.getCommonPreferences(mContext);
        CommonPreferences.disable(prefs, mWordlistId);
        UpdateHandler.markAsUnused(mContext, mClientId, mWordlistId, mVersion, mStatus);
        if (MetadataDbHelper.STATUS_DOWNLOADING == mStatus) {
            setStatus(MetadataDbHelper.STATUS_AVAILABLE);
        } else if (MetadataDbHelper.STATUS_INSTALLED == mStatus) {
            // Interface-wise, we should no longer be able to come here. However, this is still
            // the right thing to do if we do come here.
            setStatus(MetadataDbHelper.STATUS_DISABLED);
        } else {
            Log.e(TAG, "Unexpected state of the word list for disabling " + mStatus);
        }
    }
    private void enableDict() {
        SharedPreferences prefs = CommonPreferences.getCommonPreferences(mContext);
        CommonPreferences.enable(prefs, mWordlistId);
        // Explicit enabling by the user : allow downloading on metered data connection.
        UpdateHandler.markAsUsed(mContext, mClientId, mWordlistId, mVersion, mStatus, true);
        if (MetadataDbHelper.STATUS_AVAILABLE == mStatus) {
            setStatus(MetadataDbHelper.STATUS_DOWNLOADING);
        } else if (MetadataDbHelper.STATUS_DISABLED == mStatus
                || MetadataDbHelper.STATUS_DELETING == mStatus) {
            // If the status is DELETING, it means Android Keyboard
            // has not deleted the word list yet, so we can safely
            // turn it to 'installed'. The status DISABLED is still supported internally to
            // avoid breaking older installations and all but there should not be a way to
            // disable a word list through the interface any more.
            setStatus(MetadataDbHelper.STATUS_INSTALLED);
        } else {
            Log.e(TAG, "Unexpected state of the word list for enabling " + mStatus);
        }
    }
    private void deleteDict() {
        SharedPreferences prefs = CommonPreferences.getCommonPreferences(mContext);
        CommonPreferences.disable(prefs, mWordlistId);
        setStatus(MetadataDbHelper.STATUS_DELETING);
        UpdateHandler.markAsDeleting(mContext, mClientId, mWordlistId, mVersion, mStatus);
    }

    @Override
    protected void onBindView(final View view) {
        super.onBindView(view);
        ((ViewGroup)view).setLayoutTransition(null);

        final DictionaryDownloadProgressBar progressBar =
                (DictionaryDownloadProgressBar)view.findViewById(R.id.dictionary_line_progress_bar);
        final TextView status = (TextView)view.findViewById(android.R.id.summary);
        progressBar.setIds(mClientId, mWordlistId);
        progressBar.setMax(mFilesize);
        final boolean showProgressBar = (MetadataDbHelper.STATUS_DOWNLOADING == mStatus);
        status.setVisibility(showProgressBar ? View.INVISIBLE : View.VISIBLE);
        progressBar.setVisibility(showProgressBar ? View.VISIBLE : View.INVISIBLE);

        final ButtonSwitcher buttonSwitcher =
                (ButtonSwitcher)view.findViewById(R.id.wordlist_button_switcher);
        // We need to clear the state of the button switcher, because we reuse views; if we didn't
        // reset it would animate from whatever its old state was.
        buttonSwitcher.reset(mInterfaceState);
        if (mInterfaceState.isOpen(mWordlistId)) {
            // The button is open.
            final int previousStatus = mInterfaceState.getStatus(mWordlistId);
            buttonSwitcher.setStatusAndUpdateVisuals(getButtonSwitcherStatus(previousStatus));
            if (previousStatus != mStatus) {
                // We come here if the status has changed since last time. We need to animate
                // the transition.
                buttonSwitcher.setStatusAndUpdateVisuals(getButtonSwitcherStatus(mStatus));
                mInterfaceState.setOpen(mWordlistId, mStatus);
            }
        } else {
            // The button is closed.
            buttonSwitcher.setStatusAndUpdateVisuals(ButtonSwitcher.STATUS_NO_BUTTON);
        }
        buttonSwitcher.setInternalOnClickListener(mActionButtonClickHandler);
        view.setOnClickListener(mPreferenceClickHandler);
    }

    private class OnWordListPreferenceClick implements View.OnClickListener {
        @Override
        public void onClick(final View v) {
            // Note : v is the preference view
            final ViewParent parent = v.getParent();
            // Just in case something changed in the framework, test for the concrete class
            if (!(parent instanceof ListView)) return;
            final ListView listView = (ListView)parent;
            final int indexToOpen;
            // Close all first, we'll open back any item that needs to be open.
            final boolean wasOpen = mInterfaceState.isOpen(mWordlistId);
            mInterfaceState.closeAll();
            if (wasOpen) {
                // This button being shown. Take note that we don't want to open any button in the
                // loop below.
                indexToOpen = -1;
            } else {
                // This button was not being shown. Open it, and remember the index of this
                // child as the one to open in the following loop.
                mInterfaceState.setOpen(mWordlistId, mStatus);
                indexToOpen = listView.indexOfChild(v);
            }
            final int lastDisplayedIndex =
                    listView.getLastVisiblePosition() - listView.getFirstVisiblePosition();
            // The "lastDisplayedIndex" is actually displayed, hence the <=
            for (int i = 0; i <= lastDisplayedIndex; ++i) {
                final ButtonSwitcher buttonSwitcher = (ButtonSwitcher)listView.getChildAt(i)
                        .findViewById(R.id.wordlist_button_switcher);
                if (i == indexToOpen) {
                    buttonSwitcher.setStatusAndUpdateVisuals(getButtonSwitcherStatus(mStatus));
                } else {
                    buttonSwitcher.setStatusAndUpdateVisuals(ButtonSwitcher.STATUS_NO_BUTTON);
                }
            }
        }
    }

    private class OnActionButtonClick implements View.OnClickListener {
        @Override
        public void onClick(final View v) {
            switch (getActionIdFromStatusAndMenuEntry(mStatus)) {
            case ACTION_ENABLE_DICT:
                enableDict();
                break;
            case ACTION_DISABLE_DICT:
                disableDict();
                break;
            case ACTION_DELETE_DICT:
                deleteDict();
                break;
            default:
                Log.e(TAG, "Unknown menu item pressed");
            }
        }
    }
}
