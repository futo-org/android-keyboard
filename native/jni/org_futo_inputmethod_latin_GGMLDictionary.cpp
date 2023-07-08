/*
 * Copyright (C) 2009 The Android Open Source Project
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

#define LOG_TAG "LatinIME: jni: GGMLDictionary"

#include "org_futo_inputmethod_latin_GGMLDictionary.h"

#include <cstring> // for memset()
#include <vector>

#include "defines.h"
#include "dictionary/property/unigram_property.h"
#include "dictionary/property/ngram_context.h"
#include "dictionary/property/word_property.h"
#include "dictionary/structure/dictionary_structure_with_buffer_policy_factory.h"
#include "jni.h"
#include "jni_common.h"
#include "suggest/core/dictionary/dictionary.h"
#include "suggest/core/result/suggestion_results.h"
#include "suggest/core/suggest_options.h"
#include "utils/char_utils.h"
#include "utils/int_array_view.h"
#include "utils/jni_data_utils.h"
#include "utils/log_utils.h"
#include "utils/profiler.h"
#include "utils/time_keeper.h"

#include "ggml/otherarch.h"

#include <android/log.h>

namespace latinime {

class ProximityInfo;

struct GGMLDictionaryState {
    int n_threads = 3;

    std::vector<float> logits;
    size_t mem_per_token = 0;
    bool use_scratch = true;

    gpt_neox_model model;
    gpt_vocab vocab;
};

static jlong latinime_GGMLDictionary_open(JNIEnv *env, jclass clazz, jstring sourceDir,
        jlong dictOffset, jlong dictSize, jboolean isUpdatable) {
    PROF_INIT;
    PROF_TIMER_START(66);
    const jsize sourceDirUtf8Length = env->GetStringUTFLength(sourceDir);
    if (sourceDirUtf8Length <= 0) {
        AKLOGE("DICT: Can't get sourceDir string");
        return 0;
    }
    char sourceDirChars[sourceDirUtf8Length + 1];
    env->GetStringUTFRegion(sourceDir, 0, env->GetStringLength(sourceDir), sourceDirChars);
    sourceDirChars[sourceDirUtf8Length] = '\0';

    GGMLDictionaryState *state = new GGMLDictionaryState();

    std::string fname(sourceDirChars);
    FileFormat format = check_file_format(fname);
    assert(format == 405);

    state->model.hparams.n_ctx = 2048;

    ModelLoadResult result = gpt_neox_model_load(fname, state->model, state->vocab, format, 0);

    if(result != ModelLoadResult::SUCCESS) {
        AKLOGE("GGMLDict: Could not load model");
        free(state);
        return 0;
    }


    gpt_neox_eval(state->model, state->n_threads, 0, { 0, 1, 2, 3 }, state->logits, state->mem_per_token, state->use_scratch);

    AKLOGI("GGMLDict: mem per token %zu", state->mem_per_token);

    PROF_TIMER_END(66);
    return reinterpret_cast<jlong>(state);
}

static void latinime_GGMLDictionary_close(JNIEnv *env, jclass clazz, jlong dict) {
    GGMLDictionaryState *state = reinterpret_cast<GGMLDictionaryState *>(dict);
    if(state == nullptr) return;
    delete state;
}

static void latinime_GGMLDictionary_getSuggestions(JNIEnv *env, jclass clazz, jlong dict,
        jlong proximityInfo, jstring context, jobjectArray outPredictions) {
    GGMLDictionaryState *state = reinterpret_cast<GGMLDictionaryState *>(dict);
    // Assign 0 to outSuggestionCount here in case of returning earlier in this method.

    ProximityInfo *pInfo = reinterpret_cast<ProximityInfo *>(proximityInfo);

    const char* cstr = env->GetStringUTFChars(context, nullptr);
    std::string contextString(cstr);
    env->ReleaseStringUTFChars(context, cstr);

    auto tokens = gpt_tokenize(state->vocab, contextString);

    gpt_neox_eval(state->model, state->n_threads, 0, tokens, state->logits, state->mem_per_token, state->use_scratch);

    int eosID = 0;
    int topid = std::min_element(state->logits.begin(),state->logits.end())-state->logits.begin();
    state->logits[eosID] = (state->logits[topid] < 0 ? state->logits[topid] : 0);

    // Get a vector of index and value pairs
    std::vector<std::pair<float, int>> index_value;
    for (int i = 0; i < state->logits.size(); i++) {
        index_value.push_back(std::make_pair(state->logits[i], i));
    }

    // Sort the index_value vector in descending order of value
    std::sort(index_value.begin(), index_value.end(),
              [](const std::pair<float, int>& a, const std::pair<float, int>& b) {
                  return a.first > b.first;  // Descending
              });

    for(int i=0; i<4; i++){
        int token_id = index_value[i].second;
        jstring jstr = env->NewStringUTF(state->vocab.id_to_token[token_id].c_str());

        env->SetObjectArrayElement(outPredictions, i, jstr);

        env->DeleteLocalRef(jstr);
    }

    AKLOGI("Asked for suggestions :)");
    /*
    // Input values
    int xCoordinates[inputSize];
    int yCoordinates[inputSize];
    int times[inputSize];
    int pointerIds[inputSize];
    const jsize inputCodePointsLength = env->GetArrayLength(inputCodePointsArray);
    int inputCodePoints[inputCodePointsLength];
    env->GetIntArrayRegion(xCoordinatesArray, 0, inputSize, xCoordinates);
    env->GetIntArrayRegion(yCoordinatesArray, 0, inputSize, yCoordinates);
    env->GetIntArrayRegion(timesArray, 0, inputSize, times);
    env->GetIntArrayRegion(pointerIdsArray, 0, inputSize, pointerIds);
    env->GetIntArrayRegion(inputCodePointsArray, 0, inputCodePointsLength, inputCodePoints);

    const jsize numberOfOptions = env->GetArrayLength(suggestOptions);
    int options[numberOfOptions];
    env->GetIntArrayRegion(suggestOptions, 0, numberOfOptions, options);
    SuggestOptions givenSuggestOptions(options, numberOfOptions);

    // Output values
    const jsize outputCodePointsLength = env->GetArrayLength(outCodePointsArray);
    if (outputCodePointsLength != (MAX_WORD_LENGTH * MAX_RESULTS)) {
        AKLOGE("Invalid outputCodePointsLength: %d", outputCodePointsLength);
        ASSERT(false);
        return;
    }
    const jsize scoresLength = env->GetArrayLength(outScoresArray);
    if (scoresLength != MAX_RESULTS) {
        AKLOGE("Invalid scoresLength: %d", scoresLength);
        ASSERT(false);
        return;
    }
    const jsize outputAutoCommitFirstWordConfidenceLength =
            env->GetArrayLength(outAutoCommitFirstWordConfidenceArray);
    ASSERT(outputAutoCommitFirstWordConfidenceLength == 1);
    if (outputAutoCommitFirstWordConfidenceLength != 1) {
        // We only use the first result, as obviously we will only ever autocommit the first one
        AKLOGE("Invalid outputAutoCommitFirstWordConfidenceLength: %d",
                outputAutoCommitFirstWordConfidenceLength);
        ASSERT(false);
        return;
    }
    float weightOfLangModelVsSpatialModel;
    env->GetFloatArrayRegion(inOutWeightOfLangModelVsSpatialModel, 0, 1,
            &weightOfLangModelVsSpatialModel);
    SuggestionResults suggestionResults(MAX_RESULTS);
    const NgramContext ngramContext = JniDataUtils::constructNgramContext(env,
            prevWordCodePointArrays, isBeginningOfSentenceArray, prevWordCount);
    if (givenSuggestOptions.isGesture() || inputSize > 0) {
        // TODO: Use SuggestionResults to return suggestions.
        dictionary->getSuggestions(pInfo, traverseSession, xCoordinates, yCoordinates,
                times, pointerIds, inputCodePoints, inputSize, &ngramContext,
                &givenSuggestOptions, weightOfLangModelVsSpatialModel, &suggestionResults);
    } else {
        dictionary->getPredictions(&ngramContext, &suggestionResults);
    }
    if (DEBUG_DICT) {
        suggestionResults.dumpSuggestions();
    }
    suggestionResults.outputSuggestions(env, outSuggestionCount, outCodePointsArray,
            outScoresArray, outSpaceIndicesArray, outTypesArray,
            outAutoCommitFirstWordConfidenceArray, inOutWeightOfLangModelVsSpatialModel);
    */
}

static const JNINativeMethod sMethods[] = {
    {
        const_cast<char *>("openNative"),
        const_cast<char *>("(Ljava/lang/String;JJZ)J"),
        reinterpret_cast<void *>(latinime_GGMLDictionary_open)
    },
    {
        const_cast<char *>("closeNative"),
        const_cast<char *>("(J)V"),
        reinterpret_cast<void *>(latinime_GGMLDictionary_close)
    },
    {
        const_cast<char *>("getSuggestionsNative"),
        const_cast<char *>("(JJLjava/lang/String;[Ljava/lang/String;)V"),
        reinterpret_cast<void *>(latinime_GGMLDictionary_getSuggestions)
    }
};

int register_GGMLDictionary(JNIEnv *env) {
    const char *const kClassPathName = "org/futo/inputmethod/latin/GGMLDictionary";
    return registerNativeMethods(env, kClassPathName, sMethods, NELEMS(sMethods));
}
} // namespace latinime
