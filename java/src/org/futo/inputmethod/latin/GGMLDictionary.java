package org.futo.inputmethod.latin;

import android.content.Context;
import android.util.Log;

import org.futo.inputmethod.latin.common.ComposedData;
import org.futo.inputmethod.latin.common.InputPointers;
import org.futo.inputmethod.latin.settings.SettingsValuesForSuggestion;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
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

    Thread initThread = null;
    public GGMLDictionary(Context context, String dictType, Locale locale) {
        super(dictType, locale);

        initThread = new Thread() {
            @Override public void run() {
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
        };

        initThread.start();
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
        if (mNativeState == 0) return null;
        if (initThread != null && initThread.isAlive()) return null;

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

        // TODO: We may want to pass times too, and adjust autocorrect confidence
        // based on time (taking a long time to type a char = trust the typed character
        // more, speed typing = trust it less)
        int[] xCoordsI = composedData.mInputPointers.getXCoordinates();
        int[] yCoordsI = composedData.mInputPointers.getYCoordinates();

        float[] xCoords = new float[composedData.mInputPointers.getPointerSize()];
        float[] yCoords = new float[composedData.mInputPointers.getPointerSize()];

        for(int i=0; i<composedData.mInputPointers.getPointerSize(); i++) xCoords[i] = (float)xCoordsI[i];
        for(int i=0; i<composedData.mInputPointers.getPointerSize(); i++) yCoords[i] = (float)yCoordsI[i];

        int maxResults = 128;
        float[] outProbabilities = new float[maxResults];
        String[] outStrings = new String[maxResults];

        // TOOD: Pass multiple previous words information for n-gram.
        getSuggestionsNative(mNativeState, proximityInfoHandle, context, partialWord, xCoords, yCoords, outStrings, outProbabilities);

        final ArrayList<SuggestedWords.SuggestedWordInfo> suggestions = new ArrayList<>();
        for(int i=0; i<maxResults; i++) {
            if(outStrings[i] == null) continue;

            boolean isPunctuation = outStrings[i].equals("?") || outStrings[i].equals("!") || outStrings[i].equals(",") || outStrings[i].equals(".");

            String word = isPunctuation ? outStrings[i] : (outStrings[i].startsWith(" ") ? outStrings[i].trim() : ("+" + outStrings[i].trim()));

            int kind = isPunctuation ? SuggestedWords.SuggestedWordInfo.KIND_PUNCTUATION : SuggestedWords.SuggestedWordInfo.KIND_CORRECTION;

            suggestions.add(new SuggestedWords.SuggestedWordInfo( word, context, (int)(outProbabilities[i] * 16384.00f), kind, this, 0, 0 ));
        }
        return suggestions;
    }


    private synchronized void closeInternalLocked() {
        try {
            if (initThread != null) initThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

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
    private static native void getSuggestionsNative(
            // inputs
            long dict,
            long proximityInfoHandle,
            String context,
            String partialWord,
            float[] inComposeX,
            float[] inComposeY,

            // outputs
            String[] outStrings,
            float[] outProbs
    );
}
