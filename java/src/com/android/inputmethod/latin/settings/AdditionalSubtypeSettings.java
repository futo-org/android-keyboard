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

package com.android.inputmethod.latin.settings;

import static com.android.inputmethod.latin.Constants.Subtype.ExtraValue.ASCII_CAPABLE;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Toast;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.utils.AdditionalSubtypeUtils;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.IntentUtils;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.ArrayList;
import java.util.TreeSet;

public final class AdditionalSubtypeSettings extends PreferenceFragment {
    private RichInputMethodManager mRichImm;
    private SharedPreferences mPrefs;
    private SubtypeLocaleAdapter mSubtypeLocaleAdapter;
    private KeyboardLayoutSetAdapter mKeyboardLayoutSetAdapter;

    private boolean mIsAddingNewSubtype;
    private AlertDialog mSubtypeEnablerNotificationDialog;
    private String mSubtypePreferenceKeyForSubtypeEnabler;

    private static final int MENU_ADD_SUBTYPE = Menu.FIRST;
    private static final String KEY_IS_ADDING_NEW_SUBTYPE = "is_adding_new_subtype";
    private static final String KEY_IS_SUBTYPE_ENABLER_NOTIFICATION_DIALOG_OPEN =
            "is_subtype_enabler_notification_dialog_open";
    private static final String KEY_SUBTYPE_FOR_SUBTYPE_ENABLER = "subtype_for_subtype_enabler";

    static final class SubtypeLocaleItem extends Pair<String, String>
            implements Comparable<SubtypeLocaleItem> {
        public SubtypeLocaleItem(final String localeString, final String displayName) {
            super(localeString, displayName);
        }

        public SubtypeLocaleItem(final String localeString) {
            this(localeString,
                    SubtypeLocaleUtils.getSubtypeLocaleDisplayNameInSystemLocale(localeString));
        }

        @Override
        public String toString() {
            return second;
        }

        @Override
        public int compareTo(final SubtypeLocaleItem o) {
            return first.compareTo(o.first);
        }
    }

    static final class SubtypeLocaleAdapter extends ArrayAdapter<SubtypeLocaleItem> {
        private static final String TAG = SubtypeLocaleAdapter.class.getSimpleName();
        private static final boolean DEBUG_SUBTYPE_ID = false;

        public SubtypeLocaleAdapter(final Context context) {
            super(context, android.R.layout.simple_spinner_item);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            final TreeSet<SubtypeLocaleItem> items = CollectionUtils.newTreeSet();
            final InputMethodInfo imi = RichInputMethodManager.getInstance()
                    .getInputMethodInfoOfThisIme();
            final int count = imi.getSubtypeCount();
            for (int i = 0; i < count; i++) {
                final InputMethodSubtype subtype = imi.getSubtypeAt(i);
                if (DEBUG_SUBTYPE_ID) {
                    android.util.Log.d(TAG, String.format("%-6s 0x%08x %11d %s",
                            subtype.getLocale(), subtype.hashCode(), subtype.hashCode(),
                            SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype)));
                }
                if (subtype.containsExtraValueKey(ASCII_CAPABLE)) {
                    items.add(createItem(context, subtype.getLocale()));
                }
            }
            // TODO: Should filter out already existing combinations of locale and layout.
            addAll(items);
        }

        public static SubtypeLocaleItem createItem(final Context context,
                final String localeString) {
            if (localeString.equals(SubtypeLocaleUtils.NO_LANGUAGE)) {
                final String displayName = context.getString(R.string.subtype_no_language);
                return new SubtypeLocaleItem(localeString, displayName);
            } else {
                return new SubtypeLocaleItem(localeString);
            }
        }
    }

    static final class KeyboardLayoutSetItem extends Pair<String, String> {
        public KeyboardLayoutSetItem(final InputMethodSubtype subtype) {
            super(SubtypeLocaleUtils.getKeyboardLayoutSetName(subtype),
                    SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(subtype));
        }

        @Override
        public String toString() {
            return second;
        }
    }

    static final class KeyboardLayoutSetAdapter extends ArrayAdapter<KeyboardLayoutSetItem> {
        public KeyboardLayoutSetAdapter(final Context context) {
            super(context, android.R.layout.simple_spinner_item);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            // TODO: Should filter out already existing combinations of locale and layout.
            for (final String layout : SubtypeLocaleUtils.getPredefinedKeyboardLayoutSet()) {
                // This is a dummy subtype with NO_LANGUAGE, only for display.
                final InputMethodSubtype subtype = AdditionalSubtypeUtils.createAdditionalSubtype(
                        SubtypeLocaleUtils.NO_LANGUAGE, layout, null);
                add(new KeyboardLayoutSetItem(subtype));
            }
        }
    }

    private interface SubtypeDialogProxy {
        public void onRemovePressed(SubtypePreference subtypePref);
        public void onSavePressed(SubtypePreference subtypePref);
        public void onAddPressed(SubtypePreference subtypePref);
        public SubtypeLocaleAdapter getSubtypeLocaleAdapter();
        public KeyboardLayoutSetAdapter getKeyboardLayoutSetAdapter();
    }

    static final class SubtypePreference extends DialogPreference
            implements DialogInterface.OnCancelListener {
        private static final String KEY_PREFIX = "subtype_pref_";
        private static final String KEY_NEW_SUBTYPE = KEY_PREFIX + "new";

        private InputMethodSubtype mSubtype;
        private InputMethodSubtype mPreviousSubtype;

        private final SubtypeDialogProxy mProxy;
        private Spinner mSubtypeLocaleSpinner;
        private Spinner mKeyboardLayoutSetSpinner;

        public static SubtypePreference newIncompleteSubtypePreference(final Context context,
                final SubtypeDialogProxy proxy) {
            return new SubtypePreference(context, null, proxy);
        }

        public SubtypePreference(final Context context, final InputMethodSubtype subtype,
                final SubtypeDialogProxy proxy) {
            super(context, null);
            setDialogLayoutResource(R.layout.additional_subtype_dialog);
            setPersistent(false);
            mProxy = proxy;
            setSubtype(subtype);
        }

        public void show() {
            showDialog(null);
        }

        public final boolean isIncomplete() {
            return mSubtype == null;
        }

        public InputMethodSubtype getSubtype() {
            return mSubtype;
        }

        public void setSubtype(final InputMethodSubtype subtype) {
            mPreviousSubtype = mSubtype;
            mSubtype = subtype;
            if (isIncomplete()) {
                setTitle(null);
                setDialogTitle(R.string.add_style);
                setKey(KEY_NEW_SUBTYPE);
            } else {
                final String displayName =
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype);
                setTitle(displayName);
                setDialogTitle(displayName);
                setKey(KEY_PREFIX + subtype.getLocale() + "_"
                        + SubtypeLocaleUtils.getKeyboardLayoutSetName(subtype));
            }
        }

        public void revert() {
            setSubtype(mPreviousSubtype);
        }

        public boolean hasBeenModified() {
            return mSubtype != null && !mSubtype.equals(mPreviousSubtype);
        }

        @Override
        protected View onCreateDialogView() {
            final View v = super.onCreateDialogView();
            mSubtypeLocaleSpinner = (Spinner) v.findViewById(R.id.subtype_locale_spinner);
            mSubtypeLocaleSpinner.setAdapter(mProxy.getSubtypeLocaleAdapter());
            mKeyboardLayoutSetSpinner = (Spinner) v.findViewById(R.id.keyboard_layout_set_spinner);
            mKeyboardLayoutSetSpinner.setAdapter(mProxy.getKeyboardLayoutSetAdapter());
            return v;
        }

        @Override
        protected void onPrepareDialogBuilder(final AlertDialog.Builder builder) {
            final Context context = builder.getContext();
            builder.setCancelable(true).setOnCancelListener(this);
            if (isIncomplete()) {
                builder.setPositiveButton(R.string.add, this)
                        .setNegativeButton(android.R.string.cancel, this);
            } else {
                builder.setPositiveButton(R.string.save, this)
                        .setNeutralButton(android.R.string.cancel, this)
                        .setNegativeButton(R.string.remove, this);
                final SubtypeLocaleItem localeItem = SubtypeLocaleAdapter.createItem(
                        context, mSubtype.getLocale());
                final KeyboardLayoutSetItem layoutItem = new KeyboardLayoutSetItem(mSubtype);
                setSpinnerPosition(mSubtypeLocaleSpinner, localeItem);
                setSpinnerPosition(mKeyboardLayoutSetSpinner, layoutItem);
            }
        }

        private static void setSpinnerPosition(final Spinner spinner, final Object itemToSelect) {
            final SpinnerAdapter adapter = spinner.getAdapter();
            final int count = adapter.getCount();
            for (int i = 0; i < count; i++) {
                final Object item = spinner.getItemAtPosition(i);
                if (item.equals(itemToSelect)) {
                    spinner.setSelection(i);
                    return;
                }
            }
        }

        @Override
        public void onCancel(final DialogInterface dialog) {
            if (isIncomplete()) {
                mProxy.onRemovePressed(this);
            }
        }

        @Override
        public void onClick(final DialogInterface dialog, final int which) {
            super.onClick(dialog, which);
            switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                final boolean isEditing = !isIncomplete();
                final SubtypeLocaleItem locale =
                        (SubtypeLocaleItem) mSubtypeLocaleSpinner.getSelectedItem();
                final KeyboardLayoutSetItem layout =
                        (KeyboardLayoutSetItem) mKeyboardLayoutSetSpinner.getSelectedItem();
                final InputMethodSubtype subtype = AdditionalSubtypeUtils.createAdditionalSubtype(
                        locale.first, layout.first, ASCII_CAPABLE);
                setSubtype(subtype);
                notifyChanged();
                if (isEditing) {
                    mProxy.onSavePressed(this);
                } else {
                    mProxy.onAddPressed(this);
                }
                break;
            case DialogInterface.BUTTON_NEUTRAL:
                // Nothing to do
                break;
            case DialogInterface.BUTTON_NEGATIVE:
                mProxy.onRemovePressed(this);
                break;
            }
        }

        private static int getSpinnerPosition(final Spinner spinner) {
            if (spinner == null) return -1;
            return spinner.getSelectedItemPosition();
        }

        private static void setSpinnerPosition(final Spinner spinner, final int position) {
            if (spinner == null || position < 0) return;
            spinner.setSelection(position);
        }

        @Override
        protected Parcelable onSaveInstanceState() {
            final Parcelable superState = super.onSaveInstanceState();
            final Dialog dialog = getDialog();
            if (dialog == null || !dialog.isShowing()) {
                return superState;
            }

            final SavedState myState = new SavedState(superState);
            myState.mSubtype = mSubtype;
            myState.mSubtypeLocaleSelectedPos = getSpinnerPosition(mSubtypeLocaleSpinner);
            myState.mKeyboardLayoutSetSelectedPos = getSpinnerPosition(mKeyboardLayoutSetSpinner);
            return myState;
        }

        @Override
        protected void onRestoreInstanceState(final Parcelable state) {
            if (!(state instanceof SavedState)) {
                super.onRestoreInstanceState(state);
                return;
            }

            final SavedState myState = (SavedState) state;
            super.onRestoreInstanceState(myState.getSuperState());
            setSpinnerPosition(mSubtypeLocaleSpinner, myState.mSubtypeLocaleSelectedPos);
            setSpinnerPosition(mKeyboardLayoutSetSpinner, myState.mKeyboardLayoutSetSelectedPos);
            setSubtype(myState.mSubtype);
        }

        static final class SavedState extends Preference.BaseSavedState {
            InputMethodSubtype mSubtype;
            int mSubtypeLocaleSelectedPos;
            int mKeyboardLayoutSetSelectedPos;

            public SavedState(final Parcelable superState) {
                super(superState);
            }

            @Override
            public void writeToParcel(final Parcel dest, final int flags) {
                super.writeToParcel(dest, flags);
                dest.writeInt(mSubtypeLocaleSelectedPos);
                dest.writeInt(mKeyboardLayoutSetSelectedPos);
                dest.writeParcelable(mSubtype, 0);
            }

            public SavedState(final Parcel source) {
                super(source);
                mSubtypeLocaleSelectedPos = source.readInt();
                mKeyboardLayoutSetSelectedPos = source.readInt();
                mSubtype = (InputMethodSubtype)source.readParcelable(null);
            }

            @SuppressWarnings("hiding")
            public static final Parcelable.Creator<SavedState> CREATOR =
                    new Parcelable.Creator<SavedState>() {
                        @Override
                        public SavedState createFromParcel(final Parcel source) {
                            return new SavedState(source);
                        }

                        @Override
                        public SavedState[] newArray(final int size) {
                            return new SavedState[size];
                        }
                    };
        }
    }

    public AdditionalSubtypeSettings() {
        // Empty constructor for fragment generation.
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = getPreferenceManager().getSharedPreferences();
        RichInputMethodManager.init(getActivity());
        mRichImm = RichInputMethodManager.getInstance();
        addPreferencesFromResource(R.xml.additional_subtype_settings);
        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        final Context context = getActivity();
        mSubtypeLocaleAdapter = new SubtypeLocaleAdapter(context);
        mKeyboardLayoutSetAdapter = new KeyboardLayoutSetAdapter(context);

        final String prefSubtypes =
                Settings.readPrefAdditionalSubtypes(mPrefs, getResources());
        setPrefSubtypes(prefSubtypes, context);

        mIsAddingNewSubtype = (savedInstanceState != null)
                && savedInstanceState.containsKey(KEY_IS_ADDING_NEW_SUBTYPE);
        if (mIsAddingNewSubtype) {
            getPreferenceScreen().addPreference(
                    SubtypePreference.newIncompleteSubtypePreference(context, mSubtypeProxy));
        }

        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null && savedInstanceState.containsKey(
                KEY_IS_SUBTYPE_ENABLER_NOTIFICATION_DIALOG_OPEN)) {
            mSubtypePreferenceKeyForSubtypeEnabler = savedInstanceState.getString(
                    KEY_SUBTYPE_FOR_SUBTYPE_ENABLER);
            final SubtypePreference subtypePref = (SubtypePreference)findPreference(
                    mSubtypePreferenceKeyForSubtypeEnabler);
            mSubtypeEnablerNotificationDialog = createDialog(subtypePref);
            mSubtypeEnablerNotificationDialog.show();
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mIsAddingNewSubtype) {
            outState.putBoolean(KEY_IS_ADDING_NEW_SUBTYPE, true);
        }
        if (mSubtypeEnablerNotificationDialog != null
                && mSubtypeEnablerNotificationDialog.isShowing()) {
            outState.putBoolean(KEY_IS_SUBTYPE_ENABLER_NOTIFICATION_DIALOG_OPEN, true);
            outState.putString(
                    KEY_SUBTYPE_FOR_SUBTYPE_ENABLER, mSubtypePreferenceKeyForSubtypeEnabler);
        }
    }

    private final SubtypeDialogProxy mSubtypeProxy = new SubtypeDialogProxy() {
        @Override
        public void onRemovePressed(final SubtypePreference subtypePref) {
            mIsAddingNewSubtype = false;
            final PreferenceGroup group = getPreferenceScreen();
            group.removePreference(subtypePref);
            mRichImm.setAdditionalInputMethodSubtypes(getSubtypes());
        }

        @Override
        public void onSavePressed(final SubtypePreference subtypePref) {
            final InputMethodSubtype subtype = subtypePref.getSubtype();
            if (!subtypePref.hasBeenModified()) {
                return;
            }
            if (findDuplicatedSubtype(subtype) == null) {
                mRichImm.setAdditionalInputMethodSubtypes(getSubtypes());
                return;
            }

            // Saved subtype is duplicated.
            final PreferenceGroup group = getPreferenceScreen();
            group.removePreference(subtypePref);
            subtypePref.revert();
            group.addPreference(subtypePref);
            showSubtypeAlreadyExistsToast(subtype);
        }

        @Override
        public void onAddPressed(final SubtypePreference subtypePref) {
            mIsAddingNewSubtype = false;
            final InputMethodSubtype subtype = subtypePref.getSubtype();
            if (findDuplicatedSubtype(subtype) == null) {
                mRichImm.setAdditionalInputMethodSubtypes(getSubtypes());
                mSubtypePreferenceKeyForSubtypeEnabler = subtypePref.getKey();
                mSubtypeEnablerNotificationDialog = createDialog(subtypePref);
                mSubtypeEnablerNotificationDialog.show();
                return;
            }

            // Newly added subtype is duplicated.
            final PreferenceGroup group = getPreferenceScreen();
            group.removePreference(subtypePref);
            showSubtypeAlreadyExistsToast(subtype);
        }

        @Override
        public SubtypeLocaleAdapter getSubtypeLocaleAdapter() {
            return mSubtypeLocaleAdapter;
        }

        @Override
        public KeyboardLayoutSetAdapter getKeyboardLayoutSetAdapter() {
            return mKeyboardLayoutSetAdapter;
        }
    };

    private void showSubtypeAlreadyExistsToast(final InputMethodSubtype subtype) {
        final Context context = getActivity();
        final Resources res = context.getResources();
        final String message = res.getString(R.string.custom_input_style_already_exists,
                SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype));
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    private InputMethodSubtype findDuplicatedSubtype(final InputMethodSubtype subtype) {
        final String localeString = subtype.getLocale();
        final String keyboardLayoutSetName = SubtypeLocaleUtils.getKeyboardLayoutSetName(subtype);
        return mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                localeString, keyboardLayoutSetName);
    }

    private AlertDialog createDialog(
            @SuppressWarnings("unused") final SubtypePreference subtypePref) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.custom_input_styles_title)
                .setMessage(R.string.custom_input_style_note_message)
                .setNegativeButton(R.string.not_now, null)
                .setPositiveButton(R.string.enable, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final Intent intent = IntentUtils.getInputLanguageSelectionIntent(
                                mRichImm.getInputMethodIdOfThisIme(),
                                Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        // TODO: Add newly adding subtype to extra value of the intent as a hint
                        // for the input language selection activity.
                        // intent.putExtra("newlyAddedSubtype", subtypePref.getSubtype());
                        startActivity(intent);
                    }
                });

        return builder.create();
    }

    private void setPrefSubtypes(final String prefSubtypes, final Context context) {
        final PreferenceGroup group = getPreferenceScreen();
        group.removeAll();
        final InputMethodSubtype[] subtypesArray =
                AdditionalSubtypeUtils.createAdditionalSubtypesArray(prefSubtypes);
        for (final InputMethodSubtype subtype : subtypesArray) {
            final SubtypePreference pref = new SubtypePreference(
                    context, subtype, mSubtypeProxy);
            group.addPreference(pref);
        }
    }

    private InputMethodSubtype[] getSubtypes() {
        final PreferenceGroup group = getPreferenceScreen();
        final ArrayList<InputMethodSubtype> subtypes = CollectionUtils.newArrayList();
        final int count = group.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            final Preference pref = group.getPreference(i);
            if (pref instanceof SubtypePreference) {
                final SubtypePreference subtypePref = (SubtypePreference)pref;
                // We should not save newly adding subtype to preference because it is incomplete.
                if (subtypePref.isIncomplete()) continue;
                subtypes.add(subtypePref.getSubtype());
            }
        }
        return subtypes.toArray(new InputMethodSubtype[subtypes.size()]);
    }

    @Override
    public void onPause() {
        super.onPause();
        final String oldSubtypes = Settings.readPrefAdditionalSubtypes(mPrefs, getResources());
        final InputMethodSubtype[] subtypes = getSubtypes();
        final String prefSubtypes = AdditionalSubtypeUtils.createPrefSubtypes(subtypes);
        if (prefSubtypes.equals(oldSubtypes)) {
            return;
        }
        Settings.writePrefAdditionalSubtypes(mPrefs, prefSubtypes);
        mRichImm.setAdditionalInputMethodSubtypes(subtypes);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        final MenuItem addSubtypeMenu = menu.add(0, MENU_ADD_SUBTYPE, 0, R.string.add_style);
        addSubtypeMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == MENU_ADD_SUBTYPE) {
            final SubtypePreference newSubtype =
                    SubtypePreference.newIncompleteSubtypePreference(getActivity(), mSubtypeProxy);
            getPreferenceScreen().addPreference(newSubtype);
            newSubtype.show();
            mIsAddingNewSubtype = true;
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
