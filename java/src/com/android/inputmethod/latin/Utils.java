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

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.compat.InputMethodInfoCompatWrapper;
import com.android.inputmethod.compat.InputMethodManagerCompatWrapper;
import com.android.inputmethod.compat.InputMethodSubtypeCompatWrapper;
import com.android.inputmethod.compat.InputTypeCompatUtils;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.latin.define.JniLibName;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Utils {
    private static final String TAG = Utils.class.getSimpleName();
    private static final int MINIMUM_SAFETY_NET_CHAR_LENGTH = 4;
    private static boolean DBG = LatinImeLogger.sDBG;
    private static boolean DBG_EDIT_DISTANCE = false;

    private Utils() {
        // Intentional empty constructor for utility class.
    }

    /**
     * Cancel an {@link AsyncTask}.
     *
     * @param mayInterruptIfRunning <tt>true</tt> if the thread executing this
     *        task should be interrupted; otherwise, in-progress tasks are allowed
     *        to complete.
     */
    public static void cancelTask(AsyncTask<?, ?, ?> task, boolean mayInterruptIfRunning) {
        if (task != null && task.getStatus() != AsyncTask.Status.FINISHED) {
            task.cancel(mayInterruptIfRunning);
        }
    }

    public static class GCUtils {
        private static final String GC_TAG = GCUtils.class.getSimpleName();
        public static final int GC_TRY_COUNT = 2;
        // GC_TRY_LOOP_MAX is used for the hard limit of GC wait,
        // GC_TRY_LOOP_MAX should be greater than GC_TRY_COUNT.
        public static final int GC_TRY_LOOP_MAX = 5;
        private static final long GC_INTERVAL = DateUtils.SECOND_IN_MILLIS;
        private static GCUtils sInstance = new GCUtils();
        private int mGCTryCount = 0;

        public static GCUtils getInstance() {
            return sInstance;
        }

        public void reset() {
            mGCTryCount = 0;
        }

        public boolean tryGCOrWait(String metaData, Throwable t) {
            if (mGCTryCount == 0) {
                System.gc();
            }
            if (++mGCTryCount > GC_TRY_COUNT) {
                LatinImeLogger.logOnException(metaData, t);
                return false;
            } else {
                try {
                    Thread.sleep(GC_INTERVAL);
                    return true;
                } catch (InterruptedException e) {
                    Log.e(GC_TAG, "Sleep was interrupted.");
                    LatinImeLogger.logOnException(metaData, t);
                    return false;
                }
            }
        }
    }

    public static boolean hasMultipleEnabledIMEsOrSubtypes(
            final boolean shouldIncludeAuxiliarySubtypes) {
        final InputMethodManagerCompatWrapper imm = InputMethodManagerCompatWrapper.getInstance();
        if (imm == null) return false;
        final List<InputMethodInfoCompatWrapper> enabledImis = imm.getEnabledInputMethodList();

        // Number of the filtered IMEs
        int filteredImisCount = 0;

        for (InputMethodInfoCompatWrapper imi : enabledImis) {
            // We can return true immediately after we find two or more filtered IMEs.
            if (filteredImisCount > 1) return true;
            final List<InputMethodSubtypeCompatWrapper> subtypes =
                    imm.getEnabledInputMethodSubtypeList(imi, true);
            // IMEs that have no subtypes should be counted.
            if (subtypes.isEmpty()) {
                ++filteredImisCount;
                continue;
            }

            int auxCount = 0;
            for (InputMethodSubtypeCompatWrapper subtype : subtypes) {
                if (subtype.isAuxiliary()) {
                    ++auxCount;
                }
            }
            final int nonAuxCount = subtypes.size() - auxCount;

            // IMEs that have one or more non-auxiliary subtypes should be counted.
            // If shouldIncludeAuxiliarySubtypes is true, IMEs that have two or more auxiliary
            // subtypes should be counted as well.
            if (nonAuxCount > 0 || (shouldIncludeAuxiliarySubtypes && auxCount > 1)) {
                ++filteredImisCount;
                continue;
            }
        }

        if (filteredImisCount > 1) {
            return true;
        }
        final List<InputMethodSubtypeCompatWrapper> subtypes =
                imm.getEnabledInputMethodSubtypeList(null, true);
        int keyboardCount = 0;
        // imm.getEnabledInputMethodSubtypeList(null, true) will return the current IME's
        // both explicitly and implicitly enabled input method subtype.
        // (The current IME should be LatinIME.)
        for (InputMethodSubtypeCompatWrapper subtype : subtypes) {
            if (SubtypeSwitcher.KEYBOARD_MODE.equals(subtype.getMode())) {
                ++keyboardCount;
            }
        }
        return keyboardCount > 1;
    }

    public static String getInputMethodId(InputMethodManagerCompatWrapper imm, String packageName) {
        return getInputMethodInfo(imm, packageName).getId();
    }

    public static InputMethodInfoCompatWrapper getInputMethodInfo(
            InputMethodManagerCompatWrapper imm, String packageName) {
        for (final InputMethodInfoCompatWrapper imi : imm.getEnabledInputMethodList()) {
            if (imi.getPackageName().equals(packageName))
                return imi;
        }
        throw new RuntimeException("Can not find input method id for " + packageName);
    }

    // TODO: Resolve the inconsistencies between the native auto correction algorithms and
    // this safety net
    public static boolean shouldBlockAutoCorrectionBySafetyNet(SuggestedWords suggestions,
            Suggest suggest) {
        // Safety net for auto correction.
        // Actually if we hit this safety net, it's actually a bug.
        if (suggestions.size() <= 1 || suggestions.mTypedWordValid) return false;
        // If user selected aggressive auto correction mode, there is no need to use the safety
        // net.
        if (suggest.isAggressiveAutoCorrectionMode()) return false;
        final CharSequence typedWord = suggestions.getWord(0);
        // If the length of typed word is less than MINIMUM_SAFETY_NET_CHAR_LENGTH,
        // we should not use net because relatively edit distance can be big.
        if (typedWord.length() < MINIMUM_SAFETY_NET_CHAR_LENGTH) return false;
        final CharSequence suggestionWord = suggestions.getWord(1);
        final int typedWordLength = typedWord.length();
        final int maxEditDistanceOfNativeDictionary =
                (typedWordLength < 5 ? 2 : typedWordLength / 2) + 1;
        final int distance = BinaryDictionary.editDistance(
                typedWord.toString(), suggestionWord.toString());
        if (DBG) {
            Log.d(TAG, "Autocorrected edit distance = " + distance
                    + ", " + maxEditDistanceOfNativeDictionary);
        }
        if (distance > maxEditDistanceOfNativeDictionary) {
            if (DBG) {
                Log.e(TAG, "Safety net: before = " + typedWord + ", after = " + suggestionWord);
                Log.e(TAG, "(Error) The edit distance of this correction exceeds limit. "
                        + "Turning off auto-correction.");
            }
            return true;
        } else {
            return false;
        }
    }

    public static boolean canBeFollowedByPeriod(final int codePoint) {
        // TODO: Check again whether there really ain't a better way to check this.
        // TODO: This should probably be language-dependant...
        return Character.isLetterOrDigit(codePoint)
                || codePoint == Keyboard.CODE_SINGLE_QUOTE
                || codePoint == Keyboard.CODE_DOUBLE_QUOTE
                || codePoint == Keyboard.CODE_CLOSING_PARENTHESIS
                || codePoint == Keyboard.CODE_CLOSING_SQUARE_BRACKET
                || codePoint == Keyboard.CODE_CLOSING_CURLY_BRACKET
                || codePoint == Keyboard.CODE_CLOSING_ANGLE_BRACKET;
    }

    /* package */ static class RingCharBuffer {
        private static RingCharBuffer sRingCharBuffer = new RingCharBuffer();
        private static final char PLACEHOLDER_DELIMITER_CHAR = '\uFFFC';
        private static final int INVALID_COORDINATE = -2;
        /* package */ static final int BUFSIZE = 20;
        private InputMethodService mContext;
        private boolean mEnabled = false;
        private boolean mUsabilityStudy = false;
        private int mEnd = 0;
        /* package */ int mLength = 0;
        private char[] mCharBuf = new char[BUFSIZE];
        private int[] mXBuf = new int[BUFSIZE];
        private int[] mYBuf = new int[BUFSIZE];

        private RingCharBuffer() {
            // Intentional empty constructor for singleton.
        }
        public static RingCharBuffer getInstance() {
            return sRingCharBuffer;
        }
        public static RingCharBuffer init(InputMethodService context, boolean enabled,
                boolean usabilityStudy) {
            sRingCharBuffer.mContext = context;
            sRingCharBuffer.mEnabled = enabled || usabilityStudy;
            sRingCharBuffer.mUsabilityStudy = usabilityStudy;
            UsabilityStudyLogUtils.getInstance().init(context);
            return sRingCharBuffer;
        }
        private static int normalize(int in) {
            int ret = in % BUFSIZE;
            return ret < 0 ? ret + BUFSIZE : ret;
        }
        public void push(char c, int x, int y) {
            if (!mEnabled) return;
            if (mUsabilityStudy) {
                UsabilityStudyLogUtils.getInstance().writeChar(c, x, y);
            }
            mCharBuf[mEnd] = c;
            mXBuf[mEnd] = x;
            mYBuf[mEnd] = y;
            mEnd = normalize(mEnd + 1);
            if (mLength < BUFSIZE) {
                ++mLength;
            }
        }
        public char pop() {
            if (mLength < 1) {
                return PLACEHOLDER_DELIMITER_CHAR;
            } else {
                mEnd = normalize(mEnd - 1);
                --mLength;
                return mCharBuf[mEnd];
            }
        }
        public char getBackwardNthChar(int n) {
            if (mLength <= n || n < 0) {
                return PLACEHOLDER_DELIMITER_CHAR;
            } else {
                return mCharBuf[normalize(mEnd - n - 1)];
            }
        }
        public int getPreviousX(char c, int back) {
            int index = normalize(mEnd - 2 - back);
            if (mLength <= back
                    || Character.toLowerCase(c) != Character.toLowerCase(mCharBuf[index])) {
                return INVALID_COORDINATE;
            } else {
                return mXBuf[index];
            }
        }
        public int getPreviousY(char c, int back) {
            int index = normalize(mEnd - 2 - back);
            if (mLength <= back
                    || Character.toLowerCase(c) != Character.toLowerCase(mCharBuf[index])) {
                return INVALID_COORDINATE;
            } else {
                return mYBuf[index];
            }
        }
        public String getLastWord(int ignoreCharCount) {
            StringBuilder sb = new StringBuilder();
            int i = ignoreCharCount;
            for (; i < mLength; ++i) {
                char c = mCharBuf[normalize(mEnd - 1 - i)];
                if (!((LatinIME)mContext).isWordSeparator(c)) {
                    break;
                }
            }
            for (; i < mLength; ++i) {
                char c = mCharBuf[normalize(mEnd - 1 - i)];
                if (!((LatinIME)mContext).isWordSeparator(c)) {
                    sb.append(c);
                } else {
                    break;
                }
            }
            return sb.reverse().toString();
        }
        public void reset() {
            mLength = 0;
        }
    }

    // Get the current stack trace
    public static String getStackTrace() {
        StringBuilder sb = new StringBuilder();
        try {
            throw new RuntimeException();
        } catch (RuntimeException e) {
            StackTraceElement[] frames = e.getStackTrace();
            // Start at 1 because the first frame is here and we don't care about it
            for (int j = 1; j < frames.length; ++j) sb.append(frames[j].toString() + "\n");
        }
        return sb.toString();
    }

    public static class UsabilityStudyLogUtils {
        private static final String USABILITY_TAG = UsabilityStudyLogUtils.class.getSimpleName();
        private static final String FILENAME = "log.txt";
        private static final UsabilityStudyLogUtils sInstance =
                new UsabilityStudyLogUtils();
        private final Handler mLoggingHandler;
        private File mFile;
        private File mDirectory;
        private InputMethodService mIms;
        private PrintWriter mWriter;
        private final Date mDate;
        private final SimpleDateFormat mDateFormat;

        private UsabilityStudyLogUtils() {
            mDate = new Date();
            mDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss.SSSZ");

            HandlerThread handlerThread = new HandlerThread("UsabilityStudyLogUtils logging task",
                    Process.THREAD_PRIORITY_BACKGROUND);
            handlerThread.start();
            mLoggingHandler = new Handler(handlerThread.getLooper());
        }

        public static UsabilityStudyLogUtils getInstance() {
            return sInstance;
        }

        public void init(InputMethodService ims) {
            mIms = ims;
            mDirectory = ims.getFilesDir();
        }

        private void createLogFileIfNotExist() {
            if ((mFile == null || !mFile.exists())
                    && (mDirectory != null && mDirectory.exists())) {
                try {
                    mWriter = getPrintWriter(mDirectory, FILENAME, false);
                } catch (IOException e) {
                    Log.e(USABILITY_TAG, "Can't create log file.");
                }
            }
        }

        public static void writeBackSpace() {
            UsabilityStudyLogUtils.getInstance().write("<backspace>\t0\t0");
        }

        public void writeChar(char c, int x, int y) {
            String inputChar = String.valueOf(c);
            switch (c) {
                case '\n':
                    inputChar = "<enter>";
                    break;
                case '\t':
                    inputChar = "<tab>";
                    break;
                case ' ':
                    inputChar = "<space>";
                    break;
            }
            UsabilityStudyLogUtils.getInstance().write(inputChar + "\t" + x + "\t" + y);
            LatinImeLogger.onPrintAllUsabilityStudyLogs();
        }

        public void write(final String log) {
            mLoggingHandler.post(new Runnable() {
                @Override
                public void run() {
                    createLogFileIfNotExist();
                    final long currentTime = System.currentTimeMillis();
                    mDate.setTime(currentTime);

                    final String printString = String.format("%s\t%d\t%s\n",
                            mDateFormat.format(mDate), currentTime, log);
                    if (LatinImeLogger.sDBG) {
                        Log.d(USABILITY_TAG, "Write: " + log);
                    }
                    mWriter.print(printString);
                }
            });
        }

        private synchronized String getBufferedLogs() {
            mWriter.flush();
            StringBuilder sb = new StringBuilder();
            BufferedReader br = getBufferedReader();
            String line;
            try {
                while ((line = br.readLine()) != null) {
                    sb.append('\n');
                    sb.append(line);
                }
            } catch (IOException e) {
                Log.e(USABILITY_TAG, "Can't read log file.");
            } finally {
                if (LatinImeLogger.sDBG) {
                    Log.d(USABILITY_TAG, "Got all buffered logs\n" + sb.toString());
                }
                try {
                    br.close();
                } catch (IOException e) {
                    // ignore.
                }
            }
            return sb.toString();
        }

        public void emailResearcherLogsAll() {
            mLoggingHandler.post(new Runnable() {
                @Override
                public void run() {
                    final Date date = new Date();
                    date.setTime(System.currentTimeMillis());
                    final String currentDateTimeString =
                            new SimpleDateFormat("yyyyMMdd-HHmmssZ").format(date);
                    if (mFile == null) {
                        Log.w(USABILITY_TAG, "No internal log file found.");
                        return;
                    }
                    if (mIms.checkCallingOrSelfPermission(
                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                        != PackageManager.PERMISSION_GRANTED) {
                        Log.w(USABILITY_TAG, "Doesn't have the permission WRITE_EXTERNAL_STORAGE");
                        return;
                    }
                    mWriter.flush();
                    final String destPath = Environment.getExternalStorageDirectory()
                            + "/research-" + currentDateTimeString + ".log";
                    final File destFile = new File(destPath);
                    try {
                        final FileChannel src = (new FileInputStream(mFile)).getChannel();
                        final FileChannel dest = (new FileOutputStream(destFile)).getChannel();
                        src.transferTo(0, src.size(), dest);
                        src.close();
                        dest.close();
                    } catch (FileNotFoundException e1) {
                        Log.w(USABILITY_TAG, e1);
                        return;
                    } catch (IOException e2) {
                        Log.w(USABILITY_TAG, e2);
                        return;
                    }
                    if (destFile == null || !destFile.exists()) {
                        Log.w(USABILITY_TAG, "Dest file doesn't exist.");
                        return;
                    }
                    final Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (LatinImeLogger.sDBG) {
                        Log.d(USABILITY_TAG, "Destination file URI is " + destFile.toURI());
                    }
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + destPath));
                    intent.putExtra(Intent.EXTRA_SUBJECT,
                            "[Research Logs] " + currentDateTimeString);
                    mIms.startActivity(intent);
                }
            });
        }

        public void printAll() {
            mLoggingHandler.post(new Runnable() {
                @Override
                public void run() {
                    mIms.getCurrentInputConnection().commitText(getBufferedLogs(), 0);
                }
            });
        }

        public void clearAll() {
            mLoggingHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mFile != null && mFile.exists()) {
                        if (LatinImeLogger.sDBG) {
                            Log.d(USABILITY_TAG, "Delete log file.");
                        }
                        mFile.delete();
                        mWriter.close();
                    }
                }
            });
        }

        private BufferedReader getBufferedReader() {
            createLogFileIfNotExist();
            try {
                return new BufferedReader(new FileReader(mFile));
            } catch (FileNotFoundException e) {
                return null;
            }
        }

        private PrintWriter getPrintWriter(
                File dir, String filename, boolean renew) throws IOException {
            mFile = new File(dir, filename);
            if (mFile.exists()) {
                if (renew) {
                    mFile.delete();
                }
            }
            return new PrintWriter(new FileOutputStream(mFile), true /* autoFlush */);
        }
    }

    public static int getKeyboardMode(EditorInfo editorInfo) {
        if (editorInfo == null)
            return KeyboardId.MODE_TEXT;

        final int inputType = editorInfo.inputType;
        final int variation = inputType & InputType.TYPE_MASK_VARIATION;

        switch (inputType & InputType.TYPE_MASK_CLASS) {
        case InputType.TYPE_CLASS_NUMBER:
        case InputType.TYPE_CLASS_DATETIME:
            return KeyboardId.MODE_NUMBER;
        case InputType.TYPE_CLASS_PHONE:
            return KeyboardId.MODE_PHONE;
        case InputType.TYPE_CLASS_TEXT:
            if (InputTypeCompatUtils.isEmailVariation(variation)) {
                return KeyboardId.MODE_EMAIL;
            } else if (variation == InputType.TYPE_TEXT_VARIATION_URI) {
                return KeyboardId.MODE_URL;
            } else if (variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
                return KeyboardId.MODE_IM;
            } else if (variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
                return KeyboardId.MODE_TEXT;
            } else {
                return KeyboardId.MODE_TEXT;
            }
        default:
            return KeyboardId.MODE_TEXT;
        }
    }

    public static boolean containsInCsv(String key, String csv) {
        if (csv == null)
            return false;
        for (String option : csv.split(",")) {
            if (option.equals(key))
                return true;
        }
        return false;
    }

    public static boolean inPrivateImeOptions(String packageName, String key,
            EditorInfo editorInfo) {
        if (editorInfo == null)
            return false;
        return containsInCsv(packageName != null ? packageName + "." + key : key,
                editorInfo.privateImeOptions);
    }

    /**
     * Returns a main dictionary resource id
     * @return main dictionary resource id
     */
    public static int getMainDictionaryResourceId(Resources res) {
        final String MAIN_DIC_NAME = "main";
        String packageName = LatinIME.class.getPackage().getName();
        return res.getIdentifier(MAIN_DIC_NAME, "raw", packageName);
    }

    public static void loadNativeLibrary() {
        try {
            System.loadLibrary(JniLibName.JNI_LIB_NAME);
        } catch (UnsatisfiedLinkError ule) {
            Log.e(TAG, "Could not load native library " + JniLibName.JNI_LIB_NAME);
            if (LatinImeLogger.sDBG) {
                throw new RuntimeException(
                        "Could not load native library " + JniLibName.JNI_LIB_NAME);
            }
        }
    }

    /**
     * Returns true if a and b are equal ignoring the case of the character.
     * @param a first character to check
     * @param b second character to check
     * @return {@code true} if a and b are equal, {@code false} otherwise.
     */
    public static boolean equalsIgnoreCase(char a, char b) {
        // Some language, such as Turkish, need testing both cases.
        return a == b
                || Character.toLowerCase(a) == Character.toLowerCase(b)
                || Character.toUpperCase(a) == Character.toUpperCase(b);
    }

    /**
     * Returns true if a and b are equal ignoring the case of the characters, including if they are
     * both null.
     * @param a first CharSequence to check
     * @param b second CharSequence to check
     * @return {@code true} if a and b are equal, {@code false} otherwise.
     */
    public static boolean equalsIgnoreCase(CharSequence a, CharSequence b) {
        if (a == b)
            return true;  // including both a and b are null.
        if (a == null || b == null)
            return false;
        final int length = a.length();
        if (length != b.length())
            return false;
        for (int i = 0; i < length; i++) {
            if (!equalsIgnoreCase(a.charAt(i), b.charAt(i)))
                return false;
        }
        return true;
    }

    /**
     * Returns true if a and b are equal ignoring the case of the characters, including if a is null
     * and b is zero length.
     * @param a CharSequence to check
     * @param b character array to check
     * @param offset start offset of array b
     * @param length length of characters in array b
     * @return {@code true} if a and b are equal, {@code false} otherwise.
     * @throws IndexOutOfBoundsException
     *   if {@code offset < 0 || length < 0 || offset + length > data.length}.
     * @throws NullPointerException if {@code b == null}.
     */
    public static boolean equalsIgnoreCase(CharSequence a, char[] b, int offset, int length) {
        if (offset < 0 || length < 0 || length > b.length - offset)
            throw new IndexOutOfBoundsException("array.length=" + b.length + " offset=" + offset
                    + " length=" + length);
        if (a == null)
            return length == 0;  // including a is null and b is zero length.
        if (a.length() != length)
            return false;
        for (int i = 0; i < length; i++) {
            if (!equalsIgnoreCase(a.charAt(i), b[offset + i]))
                return false;
        }
        return true;
    }

    public static float getDipScale(Context context) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return scale;
    }

    /** Convert pixel to DIP */
    public static int dipToPixel(float scale, int dip) {
        return (int) (dip * scale + 0.5);
    }

    /**
     * Remove duplicates from an array of strings.
     *
     * This method will always keep the first occurence of all strings at their position
     * in the array, removing the subsequent ones.
     */
    public static void removeDupes(final ArrayList<CharSequence> suggestions) {
        if (suggestions.size() < 2) return;
        int i = 1;
        // Don't cache suggestions.size(), since we may be removing items
        while (i < suggestions.size()) {
            final CharSequence cur = suggestions.get(i);
            // Compare each suggestion with each previous suggestion
            for (int j = 0; j < i; j++) {
                CharSequence previous = suggestions.get(j);
                if (TextUtils.equals(cur, previous)) {
                    removeFromSuggestions(suggestions, i);
                    i--;
                    break;
                }
            }
            i++;
        }
    }

    private static void removeFromSuggestions(final ArrayList<CharSequence> suggestions,
            final int index) {
        final CharSequence garbage = suggestions.remove(index);
        if (garbage instanceof StringBuilder) {
            StringBuilderPool.recycle((StringBuilder)garbage);
        }
    }

    public static String getFullDisplayName(Locale locale, boolean returnsNameInThisLocale) {
        if (returnsNameInThisLocale) {
            return toTitleCase(SubtypeLocale.getFullDisplayName(locale), locale);
        } else {
            return toTitleCase(locale.getDisplayName(), locale);
        }
    }

    public static String getDisplayLanguage(Locale locale) {
        return toTitleCase(SubtypeLocale.getFullDisplayName(locale), locale);
    }

    public static String getMiddleDisplayLanguage(Locale locale) {
        return toTitleCase((LocaleUtils.constructLocaleFromString(
                locale.getLanguage()).getDisplayLanguage(locale)), locale);
    }

    public static String getShortDisplayLanguage(Locale locale) {
        return toTitleCase(locale.getLanguage(), locale);
    }

    public static String toTitleCase(String s, Locale locale) {
        if (s.length() <= 1) {
            // TODO: is this really correct? Shouldn't this be s.toUpperCase()?
            return s;
        }
        // TODO: fix the bugs below
        // - This does not work for Greek, because it returns upper case instead of title case.
        // - It does not work for Serbian, because it fails to account for the "lj" character,
        // which should be "Lj" in title case and "LJ" in upper case.
        // - It does not work for Dutch, because it fails to account for the "ij" digraph, which
        // are two different characters but both should be capitalized as "IJ" as if they were
        // a single letter.
        // - It also does not work with unicode surrogate code points.
        return s.toUpperCase(locale).charAt(0) + s.substring(1);
    }

    public static boolean willAutoCorrect(SuggestedWords suggestions) {
        return !suggestions.mTypedWordValid && suggestions.mHasAutoCorrectionCandidate
                && !suggestions.shouldBlockAutoCorrection();
    }

    public static class Stats {
        public static void onNonSeparator(final char code, final int x,
                final int y) {
            RingCharBuffer.getInstance().push(code, x, y);
            LatinImeLogger.logOnInputChar();
        }

        public static void onSeparator(final char code, final int x,
                final int y) {
            RingCharBuffer.getInstance().push(code, x, y);
            LatinImeLogger.logOnInputSeparator();
        }

        public static void onAutoCorrection(final String typedWord, final String correctedWord,
                final int separatorCode) {
            if (TextUtils.isEmpty(typedWord)) return;
            LatinImeLogger.logOnAutoCorrection(typedWord, correctedWord, separatorCode);
        }

        public static void onAutoCorrectionCancellation() {
            LatinImeLogger.logOnAutoCorrectionCancelled();
        }
    }

    public static int codePointCount(String text) {
        if (TextUtils.isEmpty(text)) return 0;
        return text.codePointCount(0, text.length());
    }
}
