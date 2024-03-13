package org.futo.inputmethod.latin.xlm;

import android.content.Context;
import android.util.Log;

import org.futo.inputmethod.keyboard.KeyDetector;
import org.futo.inputmethod.latin.NgramContext;
import org.futo.inputmethod.latin.SuggestedWords;
import org.futo.inputmethod.latin.common.ComposedData;
import org.futo.inputmethod.latin.common.InputPointers;
import org.futo.inputmethod.latin.settings.SettingsValuesForSuggestion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class LanguageModel {
    static long mNativeState = 0;

    Context context = null;
    Thread initThread = null;
    Locale locale = null;

    ModelInfoLoader modelInfoLoader = null;

    public LanguageModel(Context context, ModelInfoLoader modelInfoLoader, Locale locale) {
        this.context = context;
        this.locale = locale;
        this.modelInfoLoader = modelInfoLoader;
    }

    public Locale getLocale() {
        return Locale.ENGLISH;
    }

    private void loadModel() {
        if (initThread != null && initThread.isAlive()){
            Log.d("LanguageModel", "Cannot load model again, as initThread is still active");
            return;
        }

        initThread = new Thread() {
            @Override public void run() {
                if(mNativeState != 0) return;

                String modelPath = modelInfoLoader.getPath().getAbsolutePath();
                mNativeState = openNative(modelPath);

                // TODO: Not sure how to handle finetuned model being corrupt. Maybe have finetunedA.gguf and finetunedB.gguf and swap between them

                if(mNativeState == 0){
                    throw new RuntimeException("Failed to load models " + modelPath);
                }
            }
        };

        initThread.start();
    }

    public ArrayList<SuggestedWords.SuggestedWordInfo> getSuggestions(
            ComposedData composedData,
            NgramContext ngramContext,
            KeyDetector keyDetector,
            SettingsValuesForSuggestion settingsValuesForSuggestion,
            long proximityInfoHandle,
            int sessionId,
            float autocorrectThreshold,
            float[] inOutWeightOfLangModelVsSpatialModel,
            List<String> personalDictionary,
            String[] bannedWords
    ) {
        Log.d("LanguageModel", "getSuggestions called");

        if (mNativeState == 0) {
            loadModel();
            Log.d("LanguageModel", "Exiting because mNativeState == 0");
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
            context = ngramContext.fullContext;
            context = context.substring(context.lastIndexOf("\n") + 1).trim();
        }

        String partialWord = composedData.mTypedWord;
        if(!partialWord.isEmpty() && context.endsWith(partialWord)) {
            context = context.substring(0, context.length() - partialWord.length()).trim();
        }

        int[] xCoords;
        int[] yCoords;

        int inputMode = 0;
        if(isGesture) {
            inputMode = 1;
            List<Integer> xCoordsList = new ArrayList<>();
            List<Integer> yCoordsList = new ArrayList<>();
            // Partial word is gonna be derived from batch data
            partialWord = BatchInputConverter.INSTANCE.convertToString(
                composedData.mInputPointers.getXCoordinates(),
                composedData.mInputPointers.getYCoordinates(),
                inputSize,
                keyDetector,
                xCoordsList, yCoordsList
            );

            xCoords = new int[xCoordsList.size()];
            yCoords = new int[yCoordsList.size()];

            for(int i=0; i<xCoordsList.size(); i++) xCoords[i] = xCoordsList.get(i);
            for(int i=0; i<yCoordsList.size(); i++) yCoords[i] = yCoordsList.get(i);
        } else {
            xCoords = new int[composedData.mInputPointers.getPointerSize()];
            yCoords = new int[composedData.mInputPointers.getPointerSize()];

            int[] xCoordsI = composedData.mInputPointers.getXCoordinates();
            int[] yCoordsI = composedData.mInputPointers.getYCoordinates();

            for(int i=0; i<composedData.mInputPointers.getPointerSize(); i++) xCoords[i] = (int)xCoordsI[i];
            for(int i=0; i<composedData.mInputPointers.getPointerSize(); i++) yCoords[i] = (int)yCoordsI[i];
        }

        if(!partialWord.isEmpty()) {
            partialWord = partialWord.trim();
        }

        if(partialWord.length() > 40) {
            partialWord = partialWord.substring(partialWord.length() - 40);
        }

        // Trim the context
        while(context.length() > 128) {
            if(context.contains(".") || context.contains("?") || context.contains("!")) {
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

        if(!personalDictionary.isEmpty()) {
            StringBuilder glossary = new StringBuilder();
            for (String s : personalDictionary) {
                glossary.append(s.trim()).append(", ");
            }

            if(glossary.length() > 2) {
                context = "(Glossary: " + glossary.substring(0, glossary.length() - 2) + ")\n\n" + context;
            }
        }

        int maxResults = 128;
        float[] outProbabilities = new float[maxResults];
        String[] outStrings = new String[maxResults];

        getSuggestionsNative(mNativeState, proximityInfoHandle, context, partialWord, inputMode, xCoords, yCoords, autocorrectThreshold, bannedWords, outStrings, outProbabilities);

        final ArrayList<SuggestedWords.SuggestedWordInfo> suggestions = new ArrayList<>();

        int kind = SuggestedWords.SuggestedWordInfo.KIND_PREDICTION;

        String resultMode = outStrings[maxResults - 1];

        boolean canAutocorrect = resultMode.equals("autocorrect");
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
                        canAutocorrect = false;
                    }
                }
            }
        }

        if(!partialWord.isEmpty() && canAutocorrect) {
            kind = SuggestedWords.SuggestedWordInfo.KIND_WHITELIST | SuggestedWords.SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION;
        }

        // It's a bit ugly to communicate "clueless" with negative score, but then again
        // it sort of makes sense
        float probMult = 500000.0f;
        float probOffset = 100000.0f;
        if(resultMode.equals("clueless")) {
            probMult = 10.0f;
            probOffset = -100000.0f;
        }


        for(int i=0; i<maxResults - 1; i++) {
            if(outStrings[i] == null) continue;

            int currKind = kind;
            String word = outStrings[i].trim();
            if(word.equals(partialWord)) {
                currKind |= SuggestedWords.SuggestedWordInfo.KIND_FLAG_EXACT_MATCH;
            }

            suggestions.add(new SuggestedWords.SuggestedWordInfo( word, context, (int)(outProbabilities[i] * probMult + probOffset), currKind, null, 0, 0 ));
        }

        /*
        if(kind == SuggestedWords.SuggestedWordInfo.KIND_PREDICTION) {
            // TODO: Forcing the thing to appear
            for (int i = suggestions.size(); i < 3; i++) {
                String word = " ";
                for (int j = 0; j < i; j++) word += " ";

                suggestions.add(new SuggestedWords.SuggestedWordInfo(word, context, 1, kind, this, 0, 0));
            }
        }
        */

        for(SuggestedWords.SuggestedWordInfo suggestion : suggestions) {
            suggestion.mOriginatesFromTransformerLM = true;
        }

        Log.d("LanguageModel", "returning " + String.valueOf(suggestions.size()) + " suggestions");

        return suggestions;
    }


    public synchronized void closeInternalLocked() {
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

    private static native long openNative(String sourceDir);
    private static native void closeNative(long state);
    private static native void getSuggestionsNative(
            // inputs
            long state,
            long proximityInfoHandle,
            String context,
            String partialWord,
            int inputMode,
            int[] inComposeX,
            int[] inComposeY,
            float thresholdSetting,
            String[] bannedWords,

            // outputs
            String[] outStrings,
            float[] outProbs
    );
}
