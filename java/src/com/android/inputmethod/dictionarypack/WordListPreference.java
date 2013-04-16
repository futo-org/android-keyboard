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

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.DialogPreference;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.ListView;

import com.android.inputmethod.latin.R;

import java.util.Locale;

/**
 * A preference for one word list.
 *
 * This preference refers to a single word list, as available in the dictionary
 * pack. Upon being pressed, it displays a menu to allow the user to install, disable,
 * enable or delete it as appropriate for the current state of the word list.
 */
public final class WordListPreference extends DialogPreference {
    static final private String TAG = WordListPreference.class.getSimpleName();

    // What to display in the "status" field when we receive unknown data as a status from
    // the content provider. Empty string sounds sensible.
    static final private String NO_STATUS_MESSAGE = "";
    static final private int NOT_AN_INDEX = -1;

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
    // The status
    public int mStatus;

    // Animation directions
    static final private int ANIMATION_IN = 1;
    static final private int ANIMATION_OUT = 2;

    private static int sLastClickedIndex = NOT_AN_INDEX;
    private final OnWordListPreferenceClick mPreferenceClickHandler =
            new OnWordListPreferenceClick();
    private final OnActionButtonClick mActionButtonClickHandler =
            new OnActionButtonClick();

    public WordListPreference(final Context context, final String clientId, final String wordlistId,
            final int version, final Locale locale, final String description, final int status) {
        super(context, null);
        mContext = context;
        mClientId = clientId;
        mVersion = version;
        mWordlistId = wordlistId;

        setLayoutResource(R.layout.dictionary_line);

        setTitle(description);
        setStatus(status);
        setKey(wordlistId);
    }

    private void setStatus(final int status) {
        if (status == mStatus) return;
        mStatus = status;
        setSummary(getSummary(status));
        // If we are currently displaying the dialog, we should update it, or at least
        // dismiss it.
        final Dialog dialog = getDialog();
        if (null != dialog) {
            dialog.dismiss();
        }
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

    private static final int sStatusActionList[][] = {
        // MetadataDbHelper.STATUS_UNKNOWN
        {},
        // MetadataDbHelper.STATUS_AVAILABLE
        { R.string.install_dict, ACTION_ENABLE_DICT },
        // MetadataDbHelper.STATUS_DOWNLOADING
        { R.string.cancel_download_dict, ACTION_DISABLE_DICT },
        // MetadataDbHelper.STATUS_INSTALLED
        { R.string.delete_dict, ACTION_DELETE_DICT },
        // MetadataDbHelper.STATUS_DISABLED
        { R.string.delete_dict, ACTION_DELETE_DICT },
        // MetadataDbHelper.STATUS_DELETING
        // We show 'install' because the file is supposed to be deleted.
        // The user may reinstall it.
        { R.string.install_dict, ACTION_ENABLE_DICT }
    };

    private CharSequence getButtonLabel(final int status) {
        if (status >= sStatusActionList.length) {
            Log.e(TAG, "Unknown status " + status);
            return "";
        }
        return mContext.getString(sStatusActionList[status][0]);
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
        final Button button = (Button)view.findViewById(R.id.wordlist_button);
        button.setText(getButtonLabel(mStatus));
        button.setVisibility(View.INVISIBLE);
        button.setOnClickListener(mActionButtonClickHandler);
        view.setOnClickListener(mPreferenceClickHandler);
    }

    private class OnWordListPreferenceClick implements View.OnClickListener {
        @Override
        public void onClick(final View v) {
            final Button button = (Button)v.findViewById(R.id.wordlist_button);
            animateButton(button, ANIMATION_IN);
            final ViewParent parent = v.getParent();
            // Just in case something changed in the framework, test for the concrete class
            if (!(parent instanceof ListView)) return;
            final ListView listView = (ListView)parent;
            final int myIndex = listView.indexOfChild(v) + listView.getFirstVisiblePosition();
            if (NOT_AN_INDEX != sLastClickedIndex) {
                animateButton(getButtonForIndex(listView, sLastClickedIndex), ANIMATION_OUT);
            }
            sLastClickedIndex = myIndex;
        }
    }

    private Button getButtonForIndex(final ListView listView, final int index) {
        final int indexInChildren = index - listView.getFirstVisiblePosition();
        if (indexInChildren < 0 || index > listView.getLastVisiblePosition()) {
            // The view is offscreen.
            return null;
        }
        return (Button)listView.getChildAt(indexInChildren).findViewById(R.id.wordlist_button);
    }

    private void animateButton(final Button button, final int direction) {
        if (null == button) return;
        final float outerX = ((View)button.getParent()).getWidth();
        final float innerX = button.getX() - button.getTranslationX();
        if (View.INVISIBLE == button.getVisibility()) {
            button.setTranslationX(outerX - innerX);
            button.setVisibility(View.VISIBLE);
        }
        if (ANIMATION_IN == direction) {
            button.animate().translationX(0);
        } else {
            button.animate().translationX(outerX - innerX);
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
