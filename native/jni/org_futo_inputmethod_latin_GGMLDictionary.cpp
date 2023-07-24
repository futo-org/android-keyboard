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
#include <unordered_set>
#include <codecvt>

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
#include "suggest/core/layout/proximity_info.h"

#include "ggml/gpt_neox.h"
#include "ggml/context.h"
#include "ggml/common.h"

#include <android/log.h>

/*

typedef int KeyIndex;

struct KeyCoord {
    float x;
    float y;
    float radius;
};

struct KeyboardVocab {
    std::vector<
        std::vector<KeyIndex>
    > vocab_to_keys;

    std::vector<
        std::vector<KeyCoord>
    > vocab_to_coords;
};

void init_key_vocab(KeyboardVocab &kvoc, ProximityInfo *info, gpt_vocab vocab, int n_vocab) {
    kvoc.vocab_to_keys.clear();
    kvoc.vocab_to_coords.clear();

    kvoc.vocab_to_keys.reserve(n_vocab);
    kvoc.vocab_to_coords.reserve(n_vocab);

    std::wstring_convert<std::codecvt_utf8<wchar_t>> conv;
    for(int i=0; i<n_vocab; i++) {
        const std::string &vocab_str = vocab.id_to_token[i];

        std::wstring vocab_wstr = conv.from_bytes(vocab_str);

        std::vector<KeyIndex> curr_token_idx(vocab_wstr.length());
        std::vector<KeyCoord> curr_token_coords(vocab_wstr.length());
        for(auto codepoint : vocab_wstr) {
            if(codepoint == L' ') continue;
            KeyIndex keyIdx = info->getKeyIndexOf(codepoint);
            if(keyIdx != NOT_AN_INDEX) {
                curr_token_idx.push_back(keyIdx);

                curr_token_coords.push_back({
                    info->getSweetSpotCenterXAt(keyIdx),
                    info->getSweetSpotCenterYAt(keyIdx),
                    info->getSweetSpotRadiiAt(keyIdx)
                });
            } else {
                curr_token_idx.push_back(NOT_AN_INDEX);

                curr_token_coords.push_back({
                    -99999999.0f,
                    -99999999.0f,
                    0.0f
                });
            }
        }

        kvoc.vocab_to_keys.emplace_back(curr_token_idx);
        kvoc.vocab_to_coords.emplace_back(curr_token_coords);
    }
}

float kc_dist(const KeyCoord &a, const KeyCoord &b) {
    return std::max(0.0f, (float)std::sqrt(std::pow(a.x - b.x, 2) + std::pow(a.y - b.y, 2)) - a.radius - b.radius);
}

float modifiedLevenshtein(const std::vector<KeyCoord>& a, const std::vector<KeyCoord>& b) {
    float del_ins_cost = 10.0f;

    int a_len = a.size();
    int b_len = b.size();

    // Initialize matrix of zeros
    std::vector<std::vector<float>> d(a_len + 1, std::vector<float>(b_len + 1, 0));

    // Initialize edges to incrementing integers
    for (int i = 1; i <= a_len; i++) d[i][0] = i;
    for (int j = 1; j <= b_len; j++) d[0][j] = j;

    // Calculate distance
    for (int i = 1; i <= a_len; i++) {
        for (int j = 1; j <= b_len; j++) {
            float cost = kc_dist(a[i - 1], b[j - 1]);

            float delete_v = d[i - 1][j] + del_ins_cost;
            float insert_v = d[i][j - 1] + del_ins_cost;
            float substitute_v = d[i - 1][j - 1] + cost;

            d[i][j] = std::min(std::min(delete_v, insert_v), substitute_v);

            // Transposition (swap adjacent characters)
            if (i > 1 && j > 1 && kc_dist(a[i - 1], b[j - 2]) <= 0.0f && kc_dist(a[i - 2], b[j - 1]) <= 0.0f)
                d[i][j] = std::min(d[i][j], d[i - 2][j - 2] + cost);
        }
    }

    return d[a_len][b_len];
}

 */


// TODO: https://www.npmjs.com/package/fastest-levenshtein?activeTab=code
int levenshtein(const std::string &a, const std::string &b) {
    int a_len = a.length();
    int b_len = b.length();

    // Initialize matrix of zeros
    std::vector<std::vector<int>> d(a_len + 1, std::vector<int>(b_len + 1, 0));

    // Initialize edges to incrementing integers
    for (int i = 1; i <= a_len; i++) d[i][0] = i;
    for (int j = 1; j <= b_len; j++) d[0][j] = j;

    // Calculate distance
    for (int i = 1; i <= a_len; i++) {
        for (int j = 1; j <= b_len; j++) {
            int cost = (a[i - 1] == b[j - 1]) ? 0 : 1;

            int delete_v = d[i - 1][j] + 1;
            int insert_v = d[i][j - 1] + 1;
            int substitute_v = d[i - 1][j - 1] + cost;

            d[i][j] = std::min(std::min(delete_v, insert_v), substitute_v);

            // Transposition (swap adjacent characters)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1])
                d[i][j] = std::min(d[i][j], d[i - 2][j - 2] + cost);
        }
    }

    return d[a_len][b_len];
}

static std::string trim(const std::string &s) {
    auto start = s.begin();
    while (start != s.end() && std::isspace(*start)) {
        start++;
    }

    auto end = s.end();
    do {
        end--;
    } while (std::distance(start, end) > 0 && std::isspace(*end));

    return {start, end + 1};
}

namespace latinime {

struct DictionaryRescorer {
    std::vector<std::vector<std::string>> id_to_word;
};

void DictionaryRescorer_addDictionary(Dictionary &dict, gpt_vocab &vocab, DictionaryRescorer &rescorer) {
    if(rescorer.id_to_word.size() < vocab.id_to_token.size()) {
        rescorer.id_to_word.resize(vocab.id_to_token.size());
    }
    int token = 0;

    int wordCodePoints[MAX_WORD_LENGTH];
    int wordCodePointCount = 0;

    char word_c[MAX_WORD_LENGTH * 4];

    AKLOGI("Adding words..");
    int n = 0;
    do {
        n++;
        token = dict.getNextWordAndNextToken(token, wordCodePoints, &wordCodePointCount);

        bool isBeginningOfSentence = false;
        if (wordCodePointCount > 0 && wordCodePoints[0] == CODE_POINT_BEGINNING_OF_SENTENCE) {
            isBeginningOfSentence = true;
        }

        intArrayToCharArray(
                isBeginningOfSentence ? wordCodePoints + 1 : wordCodePoints,
                isBeginningOfSentence ? wordCodePointCount - 1 : wordCodePointCount,
                word_c,
                MAX_WORD_LENGTH * 4
        );

        std::string word(word_c);

        word = std::string(" ") + trim(word);


        std::vector<gpt_vocab::id> tokens = gpt_tokenize(vocab, word);
        gpt_vocab::id key = tokens[0];

        rescorer.id_to_word[key].push_back(word);
    } while(token != 0);

    AKLOGI("Added %d words\n", n);
}

template<typename T>
bool sortProbabilityPairDescending(const std::pair<float, T>& a, const std::pair<float, T>& b) {
    return a.first > b.first;
}


template<typename T>
static inline void sortProbabilityPairVectorDescending(std::vector<std::pair<float, T>> vec) {
    std::sort(vec.begin(), vec.end(), sortProbabilityPairDescending<T>);
}

std::vector<std::pair<float, std::string>> DictionaryRescorer_process(
        const DictionaryRescorer &rescorer,
        const std::vector<float> &logits,
        const std::string &partialWord,
        gpt_vocab &vocab,
        int n
) {
    std::vector<std::pair<float, std::string>> top_n_results(n);

    // Get a vector of index and value pairs
    std::vector<std::pair<float, int>> index_value;
    for (int i = 0; i < logits.size(); i++) {
        index_value.emplace_back(logits[i], i);
    }

    // Sort the index_value vector in descending order of value
    sortProbabilityPairVectorDescending(index_value);

    if(!partialWord.empty()) {
        // TODO: Figure out a better way
        index_value.resize(1000);
        // Adjust probabilities according to levenshtein distance
        for(auto &v : index_value) {
            int token_id = v.second;

            // String based
            std::string token = vocab.id_to_token[token_id];

            unsigned int min_length = std::min(token.length(), partialWord.length());

            float distance = (float)levenshtein(token.substr(0, min_length), partialWord.substr(0, min_length));

            // this assumes the probabilities are all positive
            v.first = v.first / (1.0f + distance);
        }

        // Sort the index_value vector in descending order of value again
        sortProbabilityPairVectorDescending(index_value);
    }

    index_value.resize(100);

    for(auto & v : index_value){
        gpt_vocab::id token_id = v.second;

        for(const std::string& str : rescorer.id_to_word[token_id]) {
            top_n_results.emplace_back(v.first, str);
        }
    }


    if(!partialWord.empty()) {
        // Adjust probabilities according to levenshtein distance
        for(auto &v : top_n_results) {
            unsigned int min_length = std::min(v.second.length(), partialWord.length());

            float distance = (float)levenshtein(v.second.substr(0, min_length), partialWord.substr(0, min_length));

            // this assumes the probabilities are all positive
            v.first = v.first / (1.0f + distance);
        }

        // Sort the top_n_vector vector in descending order of probability
        sortProbabilityPairVectorDescending(top_n_results);
    }

    return top_n_results;
}



struct GGMLDictionaryState {
    int n_threads = 3;

    transformer_context t_context;

    std::vector<float> logits;
    std::vector<gpt_vocab::id> bad_logits;
    std::unordered_set<gpt_vocab::id> punct_logits;

    //std::map<ProximityInfo *, KeyboardVocab> proximity_info_to_kvoc;
    DictionaryRescorer rescorer;

    size_t mem_per_token = 0;

    gpt_neox_model model;
    gpt_vocab vocab;
};

static jlong latinime_GGMLDictionary_open(JNIEnv *env, jclass clazz, jstring sourceDir,
        jlong dict) {
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

    bool result = gpt_neox_model_load(fname, state->model, state->vocab);

    if(!result) {
        AKLOGE("GGMLDict: Could not load model");
        free(state);
        return 0;
    }

    for(int i=0; i<state->model.hparams.n_vocab; i++){
        std::string token = state->vocab.id_to_token[i];

        bool is_bad = token.empty();
        bool has_punct = false;
        int num_chars = 0;
        if(!is_bad) {
            for (char c: token) {
                // Allow single-character punctuation
                bool is_punct = c == ',' || c == '.' || c == '?' || c == '!';
                bool is_letter = ((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z'));
                bool is_number = (c >= '0') && (c <= '9');
                bool is_special = c == '(' || c == ')' || c == '"' || c == '[' || c == ']' || c == '+' || c == '#';

                if(is_punct || is_special) has_punct = true;

                if((is_punct && token.length() == 1) || is_letter || is_number) {
                    num_chars++;
                }else if (is_punct || is_special) {
                    // TODO: We should allow special symbols for programming, etc
                    is_bad = true;
                    break;
                }
            }
        }

        is_bad = is_bad || num_chars == 0;

        if(is_bad) {
            state->bad_logits.emplace_back(i);
        }
        if(has_punct) {
            state->punct_logits.insert(i);
        }
    }



    PROF_TIMER_END(66);
    return reinterpret_cast<jlong>(state);
}

static void latinime_GGMLDictionary_close(JNIEnv *env, jclass clazz, jlong dict) {
    GGMLDictionaryState *state = reinterpret_cast<GGMLDictionaryState *>(dict);
    if(state == nullptr) return;
    delete state;
}


static void latinime_GGMLDictionary_addDict(JNIEnv *env, jclass clazz, jlong statePtr, jlong dict) {
    AKLOGI("Adding dictionary %ld\n", dict);
    GGMLDictionaryState *state = reinterpret_cast<GGMLDictionaryState *>(statePtr);
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);

    AKLOGI("Here is the dictionary we ading:");
    dictionary->logDictionaryInfo(env);

    DictionaryRescorer_addDictionary(*dictionary, state->vocab, state->rescorer);
}

static void latinime_GGMLDictionary_getSuggestions(JNIEnv *env, jclass clazz,
       // inputs
       jlong dict,
       jlong proximityInfo,
       jstring context,
       jstring partialWord,
       jfloatArray inComposeX,
       jfloatArray inComposeY,

       // outputs
       jobjectArray outPredictions,
       jfloatArray outProbabilities
) {
    GGMLDictionaryState *state = reinterpret_cast<GGMLDictionaryState *>(dict);
    ProximityInfo *pInfo = reinterpret_cast<ProximityInfo *>(proximityInfo);

    /*if(state->proximity_info_to_kvoc.find(pInfo) == state->proximity_info_to_kvoc.end()) {
        KeyboardVocab vocab;

        state->proximity_info_to_kvoc.insert({
            pInfo,
            vocab
        });

        init_key_vocab(state->proximity_info_to_kvoc[pInfo], pInfo, state->vocab, state->model.hparams.n_vocab);
    }

    const KeyboardVocab &keyboardVocab = state->proximity_info_to_kvoc[pInfo];
     */

    const char* cstr = env->GetStringUTFChars(context, nullptr);
    std::string contextString(cstr);
    env->ReleaseStringUTFChars(context, cstr);

    std::string partialWordString;
    if(partialWord != nullptr){
        const char* pwstr = env->GetStringUTFChars(partialWord, nullptr);
        partialWordString = std::string(pwstr);
        env->ReleaseStringUTFChars(partialWord, pwstr);
    }

    token_sequence next_context = gpt_tokenize(state->vocab, contextString);

    bool allow_punctuation_next = state->punct_logits.count(next_context[next_context.size() - 1]) == 0;

    //truncate to front of the prompt if its too long
    int32_t nctx = state->model.hparams.n_ctx;

    if (next_context.size() + 2 > nctx) {
        int offset = next_context.size() - nctx + 2;
        next_context = std::vector<int>(next_context.begin() + offset, next_context.end());
    }


    auto fastforward_info = transformer_context_fastforward(state->t_context, next_context);

    token_sequence &embd_inp = fastforward_info.first;
    int n_past = fastforward_info.second;

    if(!embd_inp.empty()) {
        AKLOGI("npast = %d, size(embd) = %d\n", n_past, (int) embd_inp.size());
        gpt_neox_eval(state->model, state->n_threads, n_past, embd_inp, state->logits,
                      state->mem_per_token);

        transformer_context_apply(state->t_context, fastforward_info);
    }

    int topid = std::min_element(state->logits.begin(),state->logits.end())-state->logits.begin();
    float zeroValue = (state->logits[topid] < 0 ? state->logits[topid] : 0);

    for(int bad_id : state->bad_logits) {
        state->logits[bad_id] = zeroValue;
    }

    // Don't allow punctuation after we just wrote punctuation
    if(!allow_punctuation_next) {
        for(int bad_id : state->punct_logits) {
            state->logits[bad_id] = zeroValue;
        }
    }

    auto results = DictionaryRescorer_process(state->rescorer, state->logits, partialWordString, state->vocab, 10);


    size_t size = env->GetArrayLength(outPredictions);

    // Get the array elements
    jfloat *probsArray = env->GetFloatArrayElements(outProbabilities, nullptr);

    // Output predictions for next word
    for (int i = 0; i < std::min(size, results.size()); i++) {
        std::string &word = results[i].second;
        if (i < 8) {
            AKLOGI(" - prediction[%d]: %s", i, word.c_str());
        }
        jstring jstr = env->NewStringUTF(word.c_str());

        env->SetObjectArrayElement(outPredictions, i, jstr);

        probsArray[i] = results[i].first;

        env->DeleteLocalRef(jstr);
    }

    env->ReleaseFloatArrayElements(outProbabilities, probsArray, 0);
}

static const JNINativeMethod sMethods[] = {
    {
        const_cast<char *>("openNative"),
        const_cast<char *>("(Ljava/lang/String;J)J"),
        reinterpret_cast<void *>(latinime_GGMLDictionary_open)
    },
    {
        const_cast<char *>("addDict"),
        const_cast<char *>("(JJ)V"),
        reinterpret_cast<void *>(latinime_GGMLDictionary_addDict)
    },
    {
        const_cast<char *>("closeNative"),
        const_cast<char *>("(J)V"),
        reinterpret_cast<void *>(latinime_GGMLDictionary_close)
    },
    {
        const_cast<char *>("getSuggestionsNative"),
        const_cast<char *>("(JJLjava/lang/String;Ljava/lang/String;[F[F[Ljava/lang/String;[F)V"),
        reinterpret_cast<void *>(latinime_GGMLDictionary_getSuggestions)
    }
};

int register_GGMLDictionary(JNIEnv *env) {
    const char *const kClassPathName = "org/futo/inputmethod/latin/GGMLDictionary";
    return registerNativeMethods(env, kClassPathName, sMethods, NELEMS(sMethods));
}
} // namespace latinime
