package org.futo.inputmethod.latin;

import static org.futo.inputmethod.latin.BinaryDictionary.DICTIONARY_MAX_WORD_LENGTH;

import android.content.Context;
import android.os.Build;
import android.util.SparseArray;

import androidx.annotation.RequiresApi;

import org.futo.inputmethod.latin.common.ComposedData;
import org.futo.inputmethod.latin.common.Constants;
import org.futo.inputmethod.latin.common.FileUtils;
import org.futo.inputmethod.latin.common.InputPointers;
import org.futo.inputmethod.latin.settings.SettingsValuesForSuggestion;
import org.futo.inputmethod.latin.utils.WordInputEventForPersonalization;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;



// Still kind of unsure. Maybe we should integrate more with BinaryDictionary
// sort of like: P(word) = P(word) * P_TransformerLM( tokenize(word)[0] )

// Step 1. Suggest next word based on the last three words in ngramContext
// Step 2. Suggest next word based on the full previous sentence
// Step 3. Suggest correction based on composeddata and proximityinfohandle
public class GGMLDictionary extends Dictionary {
    long mNativeState = 0;

    private String getPathToModelResource(Context context, int resource) {
        File outputDir = context.getCacheDir();
        File outputFile = new File(outputDir, "ggml-model-" + String.valueOf(resource) + ".bin");

        if(outputFile.exists()) {
            outputFile.delete();
        }
        {
            // FIXME: We save this to a random temporary file so that we can have a path instead of an InputStream
            InputStream is = context.getResources().openRawResource(resource);

            try {
                OutputStream os = new FileOutputStream(outputFile);

                int read = 0;
                byte[] bytes = new byte[1024];

                while ((read = is.read(bytes)) != -1) {
                    os.write(bytes, 0, read);
                }

                os.flush();
                os.close();
                is.close();
            } catch(IOException e) {
                e.printStackTrace();
                throw new RuntimeException("Failed to write model asset to file");
            }
        }

        return outputFile.getAbsolutePath();
    }

    public GGMLDictionary(Context context, String dictType, Locale locale) {
        super(dictType, locale);

        String modelPath = getPathToModelResource(context, R.raw.pythia_160m_q4_0);

        mNativeState = openNative(modelPath, 0, 0, false);
    }

    @Override
    public ArrayList<SuggestedWords.SuggestedWordInfo> getSuggestions(
            ComposedData composedData,
            NgramContext ngramContext,
            long proximityInfoHandle,
            SettingsValuesForSuggestion settingsValuesForSuggestion,
            int sessionId,
            float weightForLocale,
            float[] inOutWeightOfLangModelVsSpatialModel
    ) {
        if (mNativeState == 0) {
            return null;
        }

        final InputPointers inputPointers = composedData.mInputPointers;
        final boolean isGesture = composedData.mIsBatchMode;
        final int inputSize;
        inputSize = inputPointers.getPointerSize();

        String context = " " + ngramContext.extractPrevWordsContext().replace(NgramContext.BEGINNING_OF_SENTENCE_TAG, " ").trim();
        System.out.println("Context for ggml is " + context);
        String[] outStrings = new String[256];

        // TOOD: Pass multiple previous words information for n-gram.
        getSuggestionsNative(mNativeState, proximityInfoHandle, context, outStrings);

        final ArrayList<SuggestedWords.SuggestedWordInfo> suggestions = new ArrayList<>();
        for(int i=0; i<3; i++) {
            suggestions.add(new SuggestedWords.SuggestedWordInfo( outStrings[i], context, 10, 1, this, 0, 0 ));
        }
        return suggestions;
    }


    private synchronized void closeInternalLocked() {
        if (mNativeState != 0) {
            closeNative(mNativeState);
            mNativeState = 0;
        }
    }


    @Override
    protected void finalize() throws Throwable {
        try {
            closeInternalLocked();
        } finally {
            super.finalize();
        }
    }

    @Override
    public boolean isInDictionary(String word) {
        return false;
    }


    private static native long openNative(String sourceDir, long dictOffset, long dictSize,
                                          boolean isUpdatable);
    private static native void closeNative(long dict);
    private static native void getSuggestionsNative(long dict, long proximityInfo, String context, String[] strings);
}
