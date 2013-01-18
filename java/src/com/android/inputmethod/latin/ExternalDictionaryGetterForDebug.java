/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Environment;

import com.android.inputmethod.latin.makedict.BinaryDictIOUtils;
import com.android.inputmethod.latin.makedict.FormatSpec.FileHeader;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

/**
 * A class to read a local file as a dictionary for debugging purposes.
 */
public class ExternalDictionaryGetterForDebug {
    private static final String SOURCE_FOLDER = Environment.getExternalStorageDirectory().getPath()
            + "/Download";
    private static final String DICTIONARY_LOCALE_ATTRIBUTE = "locale";

    private static FileHeader getDictionaryFileHeaderOrNull(final File file) {
        try {
            final FileHeader header = BinaryDictIOUtils.getDictionaryFileHeader(file);
            return header;
        } catch (UnsupportedFormatException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private static String[] findDictionariesInTheDownloadedFolder() {
        final File[] files = new File(SOURCE_FOLDER).listFiles();
        final ArrayList<String> eligibleList = CollectionUtils.newArrayList();
        for (File f : files) {
            final FileHeader header = getDictionaryFileHeaderOrNull(f);
            if (null == header) continue;
            eligibleList.add(f.getName());
        }
        return eligibleList.toArray(new String[0]);
    }

    public static void chooseAndInstallDictionary(final Context context) {
        final String[] fileNames = findDictionariesInTheDownloadedFolder();
        if (0 == fileNames.length) {
            showNoFileDialog(context);
        } else if (1 == fileNames.length) {
            askInstallFile(context, fileNames[0]);
        } else {
            showChooseFileDialog(context, fileNames);
        }
    }

    private static void showNoFileDialog(final Context context) {
        new AlertDialog.Builder(context)
                .setMessage(R.string.read_external_dictionary_no_files_message)
                .setPositiveButton(android.R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                    }
                }).create().show();
    }

    private static void showChooseFileDialog(final Context context, final String[] fileNames) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.read_external_dictionary_multiple_files_title)
                .setItems(fileNames, new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        askInstallFile(context, fileNames[which]);
                    }
                })
                .create().show();
    }

    private static void askInstallFile(final Context context, final String fileName) {
        final File file = new File(SOURCE_FOLDER, fileName.toString());
        final FileHeader header = getDictionaryFileHeaderOrNull(file);
        final StringBuilder message = new StringBuilder();
        final String locale =
                header.mDictionaryOptions.mAttributes.get(DICTIONARY_LOCALE_ATTRIBUTE);
        for (String key : header.mDictionaryOptions.mAttributes.keySet()) {
            message.append(key + " = " + header.mDictionaryOptions.mAttributes.get(key));
            message.append("\n");
        }
        final String languageName = LocaleUtils.constructLocaleFromString(locale)
                .getDisplayName(Locale.getDefault());
        final String title = String.format(
                context.getString(R.string.read_external_dictionary_confirm_install_message),
                languageName);
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                    }
                }).setPositiveButton(android.R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        installFile(file, header);
                        dialog.dismiss();
                    }
                }).create().show();
    }

    private static void installFile(final File file, final FileHeader header) {
        // TODO: actually install the dictionary
    }
}
