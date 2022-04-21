/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.tools.edittextvariations;

import static android.graphics.Color.BLUE;
import static android.view.Gravity.LEFT;
import static android.view.Gravity.TOP;
import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class EditTextVariations extends Activity implements TextView.OnEditorActionListener,
        DialogInterface.OnClickListener {
    private static final String TAG = EditTextVariations.class.getSimpleName();
    private static final boolean DEBUG_INPUT_TEXT = false;

    private static final int MENU_CHANGE_THEME = 0;
    private static final int MENU_VERSION = 1;
    private static final int MENU_NAVIGATE_ON = 2;
    private static final int MENU_NAVIGATE_OFF = 3;
    private static final int MENU_SOFTINPUT_VISIBLE = 4;
    private static final int MENU_SOFTINPUT_HIDDEN = 5;
    private static final int MENU_DIRECT_REPLY = 6;
    private static final int MENU_TOGGLE_IME_FOCUSABLE_OVERLAY = 7;
    private static final String PREF_THEME = "theme";
    private static final String PREF_NAVIGATE = "navigate";
    private static final String PREF_SOFTINPUT = "softinput";
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 0;

    private SharedPreferences prefs;
    private View[] fields;

    private static final FinalClassField<Integer> ApplicationInfo_FLAG_SUPPORTS_RTL =
            FinalClassField.newInstance(ApplicationInfo.class, "FLAG_SUPPORTS_RTL", 1 << 22);

    // This flag should be defined IceCreamSandwich and later.
    // Note that Froyo and Gingerbread have hidden IME_FLAG_NO_FULLSCREEN as
    // value 0x80000000.
    private static final FinalClassField<Integer> EditorInfo_IME_FLAG_FORCE_ASCII =
            FinalClassField.newInstance(EditorInfo.class, "IME_FLAG_FORCE_ASCII",
                    Build.VERSION.SDK_INT >= /* ICE_CREAM_SANDWICH */14 ? 0x80000000 : 0);

    private ArrayAdapter<String> mAutoCompleteAdapter;

    private TextView mOverlayTextView;
    private boolean mShowOverlay = true;

    /** Called when the activity is first created. */
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        getApplicationInfo().flags |= ApplicationInfo_FLAG_SUPPORTS_RTL.value;
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        loadTheme();
        loadSoftInputMode();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final String[] countries = getResources().getStringArray(R.array.countries_array);
        mAutoCompleteAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, countries);

        final boolean navigateMode = getNavigateMode();
        final ViewGroup vg = (ViewGroup) findViewById(R.id.edit_text_list);
        final int n = vg.getChildCount();
        fields = new View[n];
        for (int i = 0; i < n; i++) {
            final View v = vg.getChildAt(i);
            if (v instanceof EditText) {
                final int id = v.getId();
                final EditText e = (EditText) v;
                int inputType = e.getInputType();
                int imeOptions = e.getImeOptions();
                if (id == R.id.text_auto_correct_previous) {
                    imeOptions &= ~EditorInfo.IME_MASK_ACTION;
                    imeOptions |= EditorInfo.IME_ACTION_PREVIOUS;
                }
                if (id == R.id.text_force_ascii_flag) {
                    imeOptions |= EditorInfo_IME_FLAG_FORCE_ASCII.value;
                }
                if (id == R.id.text_null) {
                    inputType = InputType.TYPE_NULL;
                }
                if (id == R.id.text_restarting) {
                    EchoingTextWatcher.attachTo(e);
                }
                if (navigateMode && i > 0) {
                    imeOptions |= EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS;
                }
                if (navigateMode && i < n - 1) {
                    imeOptions |= EditorInfo.IME_FLAG_NAVIGATE_NEXT;
                }

                e.setInputType(inputType);
                e.setImeOptions(imeOptions);
                setupHintText(e);
                if (navigateMode) {
                    e.setOnEditorActionListener(this);
                }
            }
            if (v instanceof AutoCompleteTextView) {
                final AutoCompleteTextView e = (AutoCompleteTextView) v;
                e.setAdapter(mAutoCompleteAdapter);
                e.setThreshold(1);
            }
            if (v instanceof WebView) {
                final WebView wv = (WebView) v;
                wv.getSettings().setJavaScriptEnabled(true);
                wv.addJavascriptInterface(new Object() {
                    @JavascriptInterface
                    public String name() {
                        return getThemeName();
                    }
                }, "theme");
                wv.loadUrl("file:///android_asset/webview.html");
            }
            fields[i] = v;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(Menu.NONE, MENU_NAVIGATE_ON, 0, getString(R.string.menu_navigate_on));
        menu.add(Menu.NONE, MENU_NAVIGATE_OFF, 1, getString(R.string.menu_navigate_off));
        menu.add(Menu.NONE, MENU_SOFTINPUT_VISIBLE, 2, getString(R.string.menu_softinput_visible));
        menu.add(Menu.NONE, MENU_SOFTINPUT_HIDDEN, 3, getString(R.string.menu_softinput_hidden));
        menu.add(Menu.NONE, MENU_CHANGE_THEME, 4, R.string.menu_change_theme);
        if (NotificationUtils.DIRECT_REPLY_SUPPORTED) {
            menu.add(Menu.NONE, MENU_DIRECT_REPLY, 5, R.string.menu_direct_reply);
        }
        menu.add(Menu.NONE, MENU_TOGGLE_IME_FOCUSABLE_OVERLAY, 6,
                mShowOverlay ? getString(R.string.menu_show_ime_focusable_overlay)
                        : getString(R.string.menu_hide_ime_focusable_overlay));
        try {
            final PackageInfo pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            menu.add(Menu.NONE, MENU_VERSION, 7,
                    getString(R.string.menu_version, pinfo.versionName))
                    .setEnabled(false);
        } catch (NameNotFoundException e) {
            return false;
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == MENU_CHANGE_THEME) {
            final List<CharSequence> items = new ArrayList<>();
            for (final ThemeItem theme : ThemeItem.THEME_LIST) {
                items.add(theme.name);
            }
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.menu_change_theme);
            builder.setCancelable(true);
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.setItems(items.toArray(new CharSequence[items.size()]), this);
            builder.show();
        } else if (itemId == MENU_NAVIGATE_ON || itemId == MENU_NAVIGATE_OFF) {
            saveNavigateMode(itemId == MENU_NAVIGATE_ON);
            restartActivity();
        } else if (itemId == MENU_SOFTINPUT_VISIBLE || itemId == MENU_SOFTINPUT_HIDDEN) {
            saveSoftInputMode(itemId == MENU_SOFTINPUT_VISIBLE);
            restartActivity();
        } else if (itemId == MENU_DIRECT_REPLY) {
            final boolean needPermissionCheck = isNeedNotificationPermission()
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED;
            if (needPermissionCheck) {
                requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS },
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
            } else {
                NotificationUtils.sendDirectReplyNotification(this);
            }
        } else if (itemId == MENU_TOGGLE_IME_FOCUSABLE_OVERLAY) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this,
                        "Not allowed to show overlay.\nCheck \"Settings > "
                                + "Display over other apps\"", Toast.LENGTH_LONG).show();
            } else {
                toggleOverlayView(true /* needsIme */);
                item.setTitle(mShowOverlay ? getString(R.string.menu_show_ime_focusable_overlay)
                        : getString(R.string.menu_hide_ime_focusable_overlay));
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
                if (grantResults.length == 1 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission is granted. Continue to send the notification.
                    NotificationUtils.sendDirectReplyNotification(this);
                }  else {
                    Log.d(TAG, "POST_NOTIFICATIONS Permissions denied.");
                    Toast.makeText(this, "Required permission has denied",
                            Toast.LENGTH_LONG).show();
                }
        }
    }

    @Override
    protected void onDestroy() {
        if (mOverlayTextView != null) {
            getWindowManager().removeView(mOverlayTextView);
            mOverlayTextView = null;
        }
    }

    @Override
    public void onClick(final DialogInterface dialog, final int which) {
        saveTheme(ThemeItem.THEME_LIST.get(which));
        restartActivity();
    }

    private void restartActivity() {
        final Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    private static void setupHintText(final EditText e) {
        final int imeOptions = e.getImeOptions();
        String hint = (e instanceof MultiLineShortMessageEditText) ? "*" : "";
        hint += inputTypeToString(e.getInputType());
        String text;
        if (e.getImeActionLabel() != null) {
            text = "actionLabel<" + e.getImeActionLabel() + ":" + e.getImeActionId() + ">";
        } else {
            text = actionName(imeOptions & EditorInfo.IME_MASK_ACTION);
        }
        text = appendFlagText(text,
                (imeOptions & EditorInfo.IME_FLAG_NO_EXTRACT_UI) != 0, "flagNoExtractUi");
        text = appendFlagText(text,
                (imeOptions & EditorInfo.IME_FLAG_NO_FULLSCREEN) != 0, "flagNoFullscreen");
        text = appendFlagText(text,
                (imeOptions & EditorInfo_IME_FLAG_FORCE_ASCII.value) != 0, "flagForceAscii");
        text = appendFlagText(text,
                (imeOptions & EditorInfo.IME_FLAG_NAVIGATE_NEXT) != 0, ">");
        text = appendFlagText(text,
                (imeOptions & EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS) != 0, "<");
        if (text.length() > 0)
            hint += " " + text;
        final String privateOptions = e.getPrivateImeOptions();
        if (!TextUtils.isEmpty(privateOptions)) {
            hint += " (";
            String sep = "";
            for (final String opt : privateOptions.trim().split(",")) {
                final String[] elem = opt.trim().split("\\.");
                hint += sep + elem[elem.length - 1];
                sep = ",";
            }
            hint += ")";
        }
        if (DEBUG_INPUT_TEXT) {
            Log.d(TAG, String.format("class=0x%08x variation=0x%08x flags=0x%08x hint=%s",
                    e.getInputType() & InputType.TYPE_MASK_CLASS,
                    e.getInputType() & InputType.TYPE_MASK_VARIATION,
                    e.getInputType() & InputType.TYPE_MASK_FLAGS, hint));
        }
        if (e.getId() == R.id.text_restarting) {
            hint += " restarting";
        }
        e.setHint(hint);
    }

    private void saveBooleanPreference(final String key, final boolean value) {
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    private void saveStringPreference(final String key, final String value) {
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key, value);
        editor.apply();
    }

    private void saveNavigateMode(final boolean enabled) {
        saveBooleanPreference(PREF_NAVIGATE, enabled);
    }

    private boolean getNavigateMode() {
        return prefs.getBoolean(PREF_NAVIGATE, false);
    }

    private void saveSoftInputMode(final boolean visible) {
        saveBooleanPreference(PREF_SOFTINPUT, visible);
    }

    private void loadSoftInputMode() {
        final boolean visible = prefs.getBoolean(PREF_SOFTINPUT, false);
        final Window w = getWindow();
        w.setSoftInputMode(visible
                ? WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                : WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    private void saveTheme(final ThemeItem theme) {
        saveStringPreference(PREF_THEME, theme.name);
    }

    String getThemeName() {
        return prefs.getString(PREF_THEME, ThemeItem.getDefaultThemeName());
    }

    private void loadTheme() {
        final String themeName = getThemeName();
        for (final ThemeItem theme : ThemeItem.THEME_LIST) {
            if (themeName.equals(theme.name)) {
                setTheme(theme.id);
                return;
            }
        }
    }

    @Override
    public boolean onEditorAction(final TextView v, final int action, final KeyEvent event) {
        for (int i = 0; i < fields.length; i++) {
            if (v == fields[i]) {
                final int direction;
                if (action == EditorInfo.IME_ACTION_PREVIOUS) {
                    direction = -1;
                } else {
                    direction = +1;
                }

                final int target = i + direction;
                if (target < 0 || target >= fields.length)
                    return false;

                final View targetView = fields[target];
                targetView.requestFocus();
                return true;
            }
        }
        return false;
    }

    private static String actionName(final int action) {
        switch (action & EditorInfo.IME_MASK_ACTION) {
        case EditorInfo.IME_ACTION_UNSPECIFIED:
            return "actionUnspecified";
        case EditorInfo.IME_ACTION_NONE:
            return "actionNone";
        case EditorInfo.IME_ACTION_GO:
            return "actionGo";
        case EditorInfo.IME_ACTION_SEARCH:
            return "actionSearch";
        case EditorInfo.IME_ACTION_SEND:
            return "actionSend";
        case EditorInfo.IME_ACTION_NEXT:
            return "actionNext";
        case EditorInfo.IME_ACTION_DONE:
            return "actionDone";
        case EditorInfo.IME_ACTION_PREVIOUS:
            return "actionPrevious";
        default:
            return "actionUnknown(" + action + ")";
        }
    }

    private static String inputTypeToString(final int inputType) {
        if (inputType == InputType.TYPE_NULL) {
            return "TYPE_NULL";
        }
        final int clazz = inputType & InputType.TYPE_MASK_CLASS;
        final int variation = inputType & InputType.TYPE_MASK_VARIATION;
        final int flags = inputType & InputType.TYPE_MASK_FLAGS;
        String base = "unknown(class=" + clazz + " variation=" + variation + " flag=0x"
                + Integer.toHexString(flags);

        switch (clazz) {
        case InputType.TYPE_CLASS_TEXT:
            switch (variation) {
            case InputType.TYPE_TEXT_VARIATION_NORMAL:
                base = "text";
                break;
            case InputType.TYPE_TEXT_VARIATION_URI:
                base = "textUri";
                break;
            case InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS:
                base = "textEmailAddress";
                break;
            case InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT:
                base = "textEmailSubject";
                break;
            case InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE:
                base = "textShortMessage";
                break;
            case InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE:
                base = "textLongMessage";
                break;
            case InputType.TYPE_TEXT_VARIATION_PERSON_NAME:
                base = "textPersonName";
                break;
            case InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS:
                base = "textPostalAddress";
                break;
            case InputType.TYPE_TEXT_VARIATION_PASSWORD:
                base = "textPassword";
                break;
            case InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
                base = "textVisiblePassword";
                break;
            case InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT:
                base = "textWebEditText";
                break;
            case InputType.TYPE_TEXT_VARIATION_FILTER:
                base = "textFilter";
                break;
            case InputType.TYPE_TEXT_VARIATION_PHONETIC:
                base = "textPhonetic";
                break;
            case InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS:
                base = "textWebEmailAddress";
                break;
            case InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
                base = "textWebPassword";
                break;
            }
            base = appendFlagText(base, (flags & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0,
                    "textCapCharacters");
            base = appendFlagText(base, (flags & InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0,
                    "textCapWords");
            base = appendFlagText(base, (flags & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0,
                    "textCapSentences");
            base = appendFlagText(base, (flags & InputType.TYPE_TEXT_FLAG_AUTO_CORRECT) != 0,
                    "textAutoCorrect");
            base = appendFlagText(base, (flags & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0,
                    "textAutoComplete");
            base = appendFlagText(base, (flags & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0,
                    "textMultiLine");
            base = appendFlagText(base, (flags & InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE) != 0,
                    "textImeMultiLine");
            base = appendFlagText(base, (flags & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0,
                    "textNoSuggestions");
            break;

        case InputType.TYPE_CLASS_NUMBER:
            if (variation == InputType.TYPE_NUMBER_VARIATION_NORMAL) {
                base = "number";
            } else if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
                base = "numberPassword";
            }
            base = appendFlagText(base, (flags & InputType.TYPE_NUMBER_FLAG_SIGNED) != 0,
                    "numberSigned");
            base = appendFlagText(base, (flags & InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0,
                    "numberDecimal");
            break;

        case InputType.TYPE_CLASS_PHONE:
            base = "phone";
            break;

        case InputType.TYPE_CLASS_DATETIME:
            switch (variation) {
            case InputType.TYPE_DATETIME_VARIATION_NORMAL:
                base = "datetime";
                break;
            case InputType.TYPE_DATETIME_VARIATION_DATE:
                base = "date";
                break;
            case InputType.TYPE_DATETIME_VARIATION_TIME:
                base = "time";
                break;
            }
            break;
        }

        return base;
    }

    private static String appendFlagText(final String text, final boolean flag, final String name) {
        if (flag) {
            if (text.length() == 0 || name.startsWith(text))
                return name;
            return text + "|" + name;
        }
        return text;
    }

    private static boolean isNeedNotificationPermission() {
        for(Field field : Manifest.permission.class.getFields()) {
            if (field.getName().equals("POST_NOTIFICATIONS")) {
                Log.d(TAG, "Need notification permission.");
                return true;
            }
        }
        return false;
    }

    private void toggleOverlayView(boolean needsIme) {
        if (mOverlayTextView == null) {
            Context overlayContext = createDisplayContext(getDisplay())
                    .createWindowContext(TYPE_APPLICATION_OVERLAY, null /* options */);
            int focusableFlags = FLAG_NOT_FOCUSABLE | (needsIme ? FLAG_ALT_FOCUSABLE_IM : 0);
            final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    TYPE_APPLICATION_OVERLAY, FLAG_WATCH_OUTSIDE_TOUCH | focusableFlags);
            final Rect windowBounds = getWindowManager().getCurrentWindowMetrics().getBounds();
            params.width = windowBounds.width() / 3;
            params.height = windowBounds.height() / 3;
            params.gravity = TOP | LEFT;

            mOverlayTextView = new TextView(overlayContext);
            mOverlayTextView.setText("I'm an IME focusable overlay");
            mOverlayTextView.setBackgroundColor(BLUE);
            getWindowManager().addView(mOverlayTextView, params);
        }
        mOverlayTextView.setVisibility(mShowOverlay ? View.VISIBLE : View.GONE);
        // Toggle the overlay visibility after the call.
        mShowOverlay = !mShowOverlay;
    }
}
