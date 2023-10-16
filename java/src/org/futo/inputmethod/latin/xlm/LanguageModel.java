package org.futo.inputmethod.latin.xlm;

import android.content.Context;
import android.util.Log;

import org.futo.inputmethod.latin.Dictionary;
import org.futo.inputmethod.latin.NgramContext;
import org.futo.inputmethod.latin.R;
import org.futo.inputmethod.latin.SuggestedWords;
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
import java.util.function.IntPredicate;

// TODO: Avoid loading the LanguageModel if the setting is disabled
public class LanguageModel extends Dictionary {
    static long mNativeState = 0;

    private String getPathToModelResource(Context context, int modelResource, int tokenizerResource, boolean forceDelete) {
        File outputDir = context.getCacheDir();
        File outputFile = new File(outputDir, "ggml-model-" + String.valueOf(modelResource) + ".gguf");
        File outputFileTokenizer = new File(outputDir, "tokenizer-" + String.valueOf(tokenizerResource) + ".tokenizer");

        if(forceDelete && outputFile.exists()) {
            outputFile.delete();
            outputFileTokenizer.delete();
        }

        if((!outputFile.exists()) || forceDelete){
            // FIXME: We save this to a random temporary file so that we can have a path instead of an InputStream
            InputStream is = context.getResources().openRawResource(modelResource);
            InputStream is_t = context.getResources().openRawResource(tokenizerResource);

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


                OutputStream os_t = new FileOutputStream(outputFileTokenizer);

                read = 0;
                while ((read = is_t.read(bytes)) != -1) {
                    os_t.write(bytes, 0, read);
                }

                os_t.flush();
                os_t.close();
                is_t.close();

            } catch(IOException e) {
                e.printStackTrace();
                throw new RuntimeException("Failed to write model asset to file");
            }
        }

        return outputFile.getAbsolutePath() + ":" + outputFileTokenizer.getAbsolutePath();
    }

    Thread initThread = null;
    public LanguageModel(Context context, String dictType, Locale locale) {
        super(dictType, locale);

        initThread = new Thread() {
            @Override public void run() {
                if(mNativeState != 0) return;

                String modelPath = getPathToModelResource(context, R.raw.ml3_q8, R.raw.ml3_tokenizer, false);
                mNativeState = openNative(modelPath);

                if(mNativeState == 0){
                    modelPath = getPathToModelResource(context, R.raw.ml3_q8, R.raw.ml3_tokenizer, true);
                    mNativeState = openNative(modelPath);
                }

                if(mNativeState == 0){
                    throw new RuntimeException("Failed to load R.raw.ml3_q8, R.raw.ml3_tokenizer model");
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
        Log.d("LanguageModel", "getSuggestions called");

        if (mNativeState == 0) {
            Log.d("LanguageModel", "Exiting becuase mNativeState == 0");
            return null;
        }
        if (initThread != null && initThread.isAlive()){
            Log.d("LanguageModel", "Exiting because initThread");
            return null;
        }

        final InputPointers inputPointers = composedData.mInputPointers;
        final boolean isGesture = composedData.mIsBatchMode;
        final int inputSize;
        inputSize = inputPointers.getPointerSize();

        String context = ngramContext.extractPrevWordsContext().replace(NgramContext.BEGINNING_OF_SENTENCE_TAG, " ").trim();
        if(!ngramContext.fullContext.isEmpty()) {
            context = ngramContext.fullContext.trim();
        }

        String partialWord = composedData.mTypedWord;
        if(!partialWord.isEmpty() && context.endsWith(partialWord)) {
            context = context.substring(0, context.length() - partialWord.length()).trim();
        }

        if(!partialWord.isEmpty()) {
            partialWord = partialWord.trim();
        }

        if(partialWord.length() > 40) {
            partialWord = partialWord.substring(partialWord.length() - 40);
        }

        // Trim the context
        while(context.length() > 128) {
            if(context.contains("\n")) {
                context = context.substring(context.indexOf("\n") + 1).trim();
            }else if(context.contains(".") || context.contains("?") || context.contains("!")) {
                int v = Arrays.stream(
                        new int[]{
                                context.indexOf("."),
                                context.indexOf("?"),
                                context.indexOf("!")
                        }).filter(i -> i != -1).min().orElse(-1);

                if(v == -1) break; // should be unreachable

                context = context.substring(v + 1).trim();
            } else if(context.contains(",")) {
                context = context.substring(context.indexOf(",") + 1).trim();
            } else if(context.contains(" ")) {
                context = context.substring(context.indexOf(" ") + 1).trim();
            } else {
                break;
            }
        }

        if(context.length() > 400) {
            // This context probably contains some spam without adequate whitespace to trim, set it to blank
            context = "";
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

        int kind = SuggestedWords.SuggestedWordInfo.KIND_PREDICTION;

        boolean mustNotAutocorrect = false;
        for(int i=0; i<maxResults; i++) {
            if (outStrings[i] == null) continue;
            if(!partialWord.isEmpty() && partialWord.trim().equalsIgnoreCase(outStrings[i].trim())) {
                // If this prediction matches the partial word ignoring case, and this is the top
                // prediction, then we can break.
                if(i == 0) {
                    break;
                } else {
                    // Otherwise, we cannot autocorrect to the top prediction unless the model is
                    // super confident about this
                    if(outProbabilities[i] * 2.5f >= outProbabilities[0]) {
                        mustNotAutocorrect = true;
                    }
                }
            }
        }

        if(!partialWord.isEmpty() && !mustNotAutocorrect) {
            kind = SuggestedWords.SuggestedWordInfo.KIND_WHITELIST | SuggestedWords.SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION;
        }

        for(int i=0; i<maxResults; i++) {
            if(outStrings[i] == null) continue;

            String word = outStrings[i].trim();

            suggestions.add(new SuggestedWords.SuggestedWordInfo( word, context, (int)(outProbabilities[i] * 100.0f), kind, this, 0, 0 ));
        }

        if(kind == SuggestedWords.SuggestedWordInfo.KIND_PREDICTION) {
            // TODO: Forcing the thing to appear
            for (int i = suggestions.size(); i < 3; i++) {
                String word = " ";
                for (int j = 0; j < i; j++) word += " ";

                suggestions.add(new SuggestedWords.SuggestedWordInfo(word, context, 1, kind, this, 0, 0));
            }
        }

        Log.d("LanguageModel", "returning " + String.valueOf(suggestions.size()) + " suggestions");

        return suggestions;
    }


    private synchronized void closeInternalLocked() {
        try {
            if (initThread != null) initThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        /*if (mNativeState != 0) {
            closeNative(mNativeState);
            mNativeState = 0;
        }*/
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
        // TODO: Provide the word spelling to the model and see if the probability of correcting it to that is beyond a certain limit
        return false;
    }


    private static native long openNative(String sourceDir);
    private static native void closeNative(long state);
    private static native void getSuggestionsNative(
            // inputs
            long state,
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
