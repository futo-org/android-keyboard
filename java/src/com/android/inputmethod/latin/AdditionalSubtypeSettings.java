/**
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

import java.util.Locale;
import java.util.TreeSet;

public class AdditionalSubtypeSettings extends PreferenceFragment {
    private SharedPreferences mPrefs;
    private SubtypeLocaleAdapter mSubtypeLocaleAdapter;
    private KeyboardLayoutSetAdapter mKeyboardLayoutSetAdapter;

    private PreferenceGroup mSubtypePrefGroup;

    private static final int MENU_ADD_SUBTYPE = Menu.FIRST;

    static class SubtypeLocaleItem extends Pair<String, String>
            implements Comparable<SubtypeLocaleItem> {
        public SubtypeLocaleItem(String localeString, String displayName) {
            super(localeString, displayName);
        }

        public SubtypeLocaleItem(String localeString) {
            this(localeString, getDisplayName(localeString));
        }

        @Override
        public String toString() {
            return second;
        }

        @Override
        public int compareTo(SubtypeLocaleItem o) {
            return first.compareTo(o.first);
        }

        private static String getDisplayName(String localeString) {
            final Locale locale = LocaleUtils.constructLocaleFromString(localeString);
            return StringUtils.toTitleCase(locale.getDisplayName(locale), locale);
        }
    }

    static class SubtypeLocaleAdapter extends ArrayAdapter<SubtypeLocaleItem> {
        public SubtypeLocaleAdapter(Context context) {
            super(context, android.R.layout.simple_spinner_item);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            final TreeSet<SubtypeLocaleItem> items = new TreeSet<SubtypeLocaleItem>();
            final InputMethodInfo imi = ImfUtils.getInputMethodInfoOfThisIme(context);
            final int count = imi.getSubtypeCount();
            for (int i = 0; i < count; i++) {
                final InputMethodSubtype subtype = imi.getSubtypeAt(i);
                if (subtype.containsExtraValueKey(LatinIME.SUBTYPE_EXTRA_VALUE_ASCII_CAPABLE)) {
                    items.add(createItem(context, subtype.getLocale()));
                }
            }
            // TODO: Should filter out already existing combinations of locale and layout.
            addAll(items);
        }

        public static SubtypeLocaleItem createItem(Context context, String localeString) {
            if (localeString.equals(SubtypeLocale.NO_LANGUAGE)) {
                final String displayName = context.getString(R.string.subtype_no_language);
                return new SubtypeLocaleItem(localeString, displayName);
            } else {
                return new SubtypeLocaleItem(localeString);
            }
        }
    }

    static class KeyboardLayoutSetItem extends Pair<String, String> {
        public KeyboardLayoutSetItem(String keyboardLayoutSetName) {
            super(keyboardLayoutSetName, getDisplayName(keyboardLayoutSetName));
        }

        @Override
        public String toString() {
            return second;
        }

        private static String getDisplayName(String keyboardLayoutSetName) {
            return keyboardLayoutSetName.toUpperCase();
        }
    }

    static class KeyboardLayoutSetAdapter extends ArrayAdapter<KeyboardLayoutSetItem> {
        public KeyboardLayoutSetAdapter(Context context) {
            super(context, android.R.layout.simple_spinner_item);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            // TODO: Should filter out already existing combinations of locale and layout.
            for (final String layout : AdditionalSubtype.PREDEFINED_KEYBOARD_LAYOUT_SET) {
                add(new KeyboardLayoutSetItem(layout));
            }
        }
    }

    private interface SubtypeDialogProxy {
        public void onRemovePressed(SubtypePreference subtypePref);
        public SubtypeLocaleAdapter getSubtypeLocaleAdapter();
        public KeyboardLayoutSetAdapter getKeyboardLayoutSetAdapter();
    }

    static class SubtypePreference extends DialogPreference {
        private InputMethodSubtype mSubtype;

        private final SubtypeDialogProxy mProxy;
        private Spinner mSubtypeLocaleSpinner;
        private Spinner mKeyboardLayoutSetSpinner;

        public SubtypePreference(Context context, InputMethodSubtype subtype,
                    SubtypeDialogProxy proxy) {
            super(context, null);
            setPersistent(false);
            mProxy = proxy;
            setSubtype(subtype);
        }

        public void show() {
            showDialog(null);
        }

        public InputMethodSubtype getSubtype() {
            return mSubtype;
        }

        public void setSubtype(InputMethodSubtype subtype) {
            mSubtype = subtype;
            if (subtype == null) {
                setTitle(null);
                setDialogTitle(R.string.add_style);
            } else {
                final String displayName = SubtypeLocale.getFullDisplayName(subtype);
                setTitle(displayName);
                setDialogTitle(displayName);
            }
        }

        @Override
        protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            final Context context = builder.getContext();
            final View v = LayoutInflater.from(context).inflate(
                    R.layout.additional_subtype_dialog, null);
            builder.setView(v);
            mSubtypeLocaleSpinner = (Spinner) v.findViewById(R.id.subtype_locale_spinner);
            mSubtypeLocaleSpinner.setAdapter(mProxy.getSubtypeLocaleAdapter());
            mKeyboardLayoutSetSpinner = (Spinner) v.findViewById(R.id.keyboard_layout_set_spinner);
            mKeyboardLayoutSetSpinner.setAdapter(mProxy.getKeyboardLayoutSetAdapter());

            if (mSubtype == null) {
                builder.setPositiveButton(R.string.add, this)
                        .setNegativeButton(android.R.string.cancel, this);
            } else {
                builder.setPositiveButton(R.string.save, this)
                        .setNeutralButton(android.R.string.cancel, this)
                        .setNegativeButton(R.string.remove, this);
                final SubtypeLocaleItem localeItem = SubtypeLocaleAdapter.createItem(
                        context, mSubtype.getLocale());
                final KeyboardLayoutSetItem layoutItem = new KeyboardLayoutSetItem(
                        SubtypeLocale.getKeyboardLayoutSetName(mSubtype));
                setSpinnerPosition(mSubtypeLocaleSpinner, localeItem);
                setSpinnerPosition(mKeyboardLayoutSetSpinner, layoutItem);
            }
        }

        private static void setSpinnerPosition(Spinner spinner, Object itemToSelect) {
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
        public void onClick(DialogInterface dialog, int which) {
            super.onClick(dialog, which);
            switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                final SubtypeLocaleItem locale =
                        (SubtypeLocaleItem) mSubtypeLocaleSpinner.getSelectedItem();
                final KeyboardLayoutSetItem layout =
                        (KeyboardLayoutSetItem) mKeyboardLayoutSetSpinner.getSelectedItem();
                final InputMethodSubtype subtype = AdditionalSubtype.createAdditionalSubtype(
                        locale.first, layout.first, LatinIME.SUBTYPE_EXTRA_VALUE_ASCII_CAPABLE);
                setSubtype(subtype);
                notifyChanged();
                break;
            case DialogInterface.BUTTON_NEUTRAL:
                // Nothing to do
                break;
            case DialogInterface.BUTTON_NEGATIVE:
                mProxy.onRemovePressed(this);
                break;
            }
        }

        @Override
        protected Parcelable onSaveInstanceState() {
            final SavedState myState = new SavedState(super.onSaveInstanceState());
            myState.mSubtype = mSubtype;
            return myState;
        }

        @Override
        protected void onRestoreInstanceState(Parcelable state) {
            if (state instanceof SavedState) {
                final SavedState myState = (SavedState) state;
                super.onRestoreInstanceState(state);
                setSubtype(myState.mSubtype);
            } else {
                super.onRestoreInstanceState(state);
            }
        }

        static class SavedState extends Preference.BaseSavedState {
            InputMethodSubtype mSubtype;
            private static final byte VALID = 1;
            private static final byte INVALID = 0;

            public SavedState(Parcelable superState) {
                super(superState);
            }

            @Override
            public void writeToParcel(Parcel dest, int flags) {
                super.writeToParcel(dest, flags);
                if (mSubtype != null) {
                    dest.writeByte(VALID);
                    mSubtype.writeToParcel(dest, 0);
                } else {
                    dest.writeByte(INVALID);
                }
            }

            public SavedState(Parcel source) {
                super(source);
                if (source.readByte() == VALID) {
                    mSubtype = source.readParcelable(null);
                } else {
                    mSubtype = null;
                }
            }

            public static final Parcelable.Creator<SavedState> CREATOR =
                    new Parcelable.Creator<SavedState>() {
                        @Override
                        public SavedState createFromParcel(Parcel source) {
                            return new SavedState(source);
                        }

                        @Override
                        public SavedState[] newArray(int size) {
                            return new SavedState[size];
                        }
                    };
        }
    }

    public AdditionalSubtypeSettings() {
        // Empty constructor for fragment generation.
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.additional_subtype_settings);
        setHasOptionsMenu(true);
        mSubtypePrefGroup = getPreferenceScreen();

        mPrefs = getPreferenceManager().getSharedPreferences();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Context context = getActivity();
        mSubtypeLocaleAdapter = new SubtypeLocaleAdapter(context);
        mKeyboardLayoutSetAdapter = new KeyboardLayoutSetAdapter(context);

        // TODO: Restore editing dialog if any.
    }

    private final SubtypeDialogProxy mSubtypeProxy = new SubtypeDialogProxy() {
        @Override
        public void onRemovePressed(SubtypePreference subtypePref) {
            final PreferenceGroup group = mSubtypePrefGroup;
            if (group != null) {
                group.removePreference(subtypePref);
            }
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

    private void setPrefSubtypes(String prefSubtypes, Context context) {
        final PreferenceGroup group = mSubtypePrefGroup;
        group.removeAll();
        final String[] prefSubtypeArray = prefSubtypes.split(
                AdditionalSubtype.PREF_SUBTYPE_SEPARATOR);
        for (final String prefSubtype : prefSubtypeArray) {
            final InputMethodSubtype subtype =
                    AdditionalSubtype.createAdditionalSubtype(prefSubtype);
            final SubtypePreference pref = new SubtypePreference(
                    context, subtype, mSubtypeProxy);
            group.addPreference(pref);
        }
    }

    private String getPrefSubtypes() {
        final StringBuilder sb = new StringBuilder();
        final int count = mSubtypePrefGroup.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            final Preference pref = mSubtypePrefGroup.getPreference(i);
            if (pref instanceof SubtypePreference) {
                final InputMethodSubtype subtype = ((SubtypePreference)pref).getSubtype();
                if (sb.length() > 0) {
                    sb.append(AdditionalSubtype.PREF_SUBTYPE_SEPARATOR);
                }
                sb.append(AdditionalSubtype.getPrefSubtype(subtype));
            }
        }
        return sb.toString();
    }

    @Override
    public void onResume() {
        super.onResume();

        final String prefSubtypes =
                SettingsValues.getPrefAdditionalSubtypes(mPrefs, getResources());
        setPrefSubtypes(prefSubtypes, getActivity());
    }

    @Override
    public void onPause() {
        super.onPause();
        final String oldSubtypes = SettingsValues.getPrefAdditionalSubtypes(mPrefs, getResources());
        final String prefSubtypes = getPrefSubtypes();
        if (prefSubtypes.equals(oldSubtypes)) {
            return;
        }

        final SharedPreferences.Editor editor = mPrefs.edit();
        try {
            editor.putString(Settings.PREF_CUSTOM_INPUT_STYLES, prefSubtypes);
        } finally {
            editor.apply();
        }
        final InputMethodSubtype[] subtypes =
                AdditionalSubtype.createAdditionalSubtypesArray(prefSubtypes);
        ImfUtils.setAdditionalInputMethodSubtypes(getActivity(), subtypes);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // TODO: save editing dialog state.
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen prefScreen, Preference pref) {
        if (pref instanceof SubtypePreference) {
            return true;
        }
        return super.onPreferenceTreeClick(prefScreen, pref);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        final MenuItem addSubtypeMenu = menu.add(0, MENU_ADD_SUBTYPE, 0, R.string.add_style);
        addSubtypeMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == MENU_ADD_SUBTYPE) {
            final SubtypePreference subtypePref = new SubtypePreference(
                    getActivity(), null, mSubtypeProxy);
            mSubtypePrefGroup.addPreference(subtypePref);
            subtypePref.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
