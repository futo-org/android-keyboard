package org.futo.inputmethod.latin;

import android.content.Context;

import org.futo.inputmethod.latin.common.ComposedData;
import org.futo.inputmethod.latin.common.InputPointers;
import org.futo.inputmethod.latin.settings.SettingsValuesForSuggestion;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;



// Still kind of unsure. Maybe we should integrate more with BinaryDictionary
// sort of like: P(word) = P(word) * P_TransformerLM( tokenize(word)[0] )

public class GGMLDictionary extends Dictionary {
    long mNativeState = 0;

    private String getPathToModelResource(Context context, int resource, boolean forceDelete) {
        File outputDir = context.getCacheDir();
        File outputFile = new File(outputDir, "ggml-model-" + String.valueOf(resource) + ".bin");

        if(forceDelete && outputFile.exists()) {
            outputFile.delete();
        }
        if((!outputFile.exists()) || forceDelete){
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

        String modelPath = getPathToModelResource(context, R.raw.pythia_160m_q4_0, false);
        mNativeState = openNative(modelPath, 0, 0, false);

        if(mNativeState == 0){
            modelPath = getPathToModelResource(context, R.raw.pythia_160m_q4_0, true);
            mNativeState = openNative(modelPath, 0, 0, false);
        }

        if(mNativeState == 0){
            throw new RuntimeException("Failed to load pythia_160m model");
        }
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
        if(!ngramContext.fullContext.isEmpty()) {
            context = " " + ngramContext.fullContext.trim();
        }

        String partialWord = composedData.mTypedWord;

        if(!partialWord.isEmpty() && context.endsWith(partialWord)) {
            context = " " + context.substring(0, context.length() - partialWord.length()).trim();
        }

        if(!partialWord.isEmpty()) {
            partialWord = " " + partialWord.trim();
        }

        System.out.println("Context for ggml is " + context);
        System.out.println("partialWord is " + partialWord);


        int maxResults = 128;
        int[] outProbabilities = new int[maxResults];
        String[] outStrings = new String[maxResults];

        // TOOD: Pass multiple previous words information for n-gram.
        getSuggestionsNative(mNativeState, proximityInfoHandle, context, partialWord, outStrings, outProbabilities);

        final ArrayList<SuggestedWords.SuggestedWordInfo> suggestions = new ArrayList<>();
        for(int i=0; i<maxResults; i++) {
            if(outStrings[i] == null) continue;

            suggestions.add(new SuggestedWords.SuggestedWordInfo( outStrings[i].trim(), context, outProbabilities[i], 1, this, 0, 0 ));
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
    private static native void getSuggestionsNative(long dict, long proximityInfoHandle, String context, String partialWord, String[] outStrings, int[] outProbs);
}
