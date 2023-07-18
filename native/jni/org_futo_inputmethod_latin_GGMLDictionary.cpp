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

namespace latinime {

// TODO: Make use of proximityInfo
int levenshtein(std::string a, std::string b) {
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


struct GGMLDictionaryState {
    int n_threads = 3;

    transformer_context t_context;

    std::vector<float> logits;
    std::vector<gpt_vocab::id> bad_logits;
    std::unordered_set<gpt_vocab::id> punct_logits;

    std::map<ProximityInfo *, KeyboardVocab> proximity_info_to_kvoc;

    size_t mem_per_token = 0;

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

    if(state->proximity_info_to_kvoc.find(pInfo) == state->proximity_info_to_kvoc.end()) {
        KeyboardVocab vocab;

        state->proximity_info_to_kvoc.insert({
            pInfo,
            vocab
        });

        init_key_vocab(state->proximity_info_to_kvoc[pInfo], pInfo, state->vocab, state->model.hparams.n_vocab);
    }

    const KeyboardVocab &keyboardVocab = state->proximity_info_to_kvoc[pInfo];

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

    // Get a vector of index and value pairs
    std::vector<std::pair<float, int>> index_value;
    for (int i = 0; i < state->logits.size(); i++) {
        index_value.emplace_back(state->logits[i], i);
    }

    // Sort the index_value vector in descending order of value
    std::sort(index_value.begin(), index_value.end(),
              [](const std::pair<float, int>& a, const std::pair<float, int>& b) {
                  return a.first > b.first;  // Descending
              });

    // Adjust probabilities according to the partial word
    if(!partialWordString.empty()) {
        int xArrayElems = env->GetArrayLength(inComposeX);
        int yArrayElems = env->GetArrayLength(inComposeY);
        assert(xArrayElems == yArrayElems);

        jfloat *xArray = env->GetFloatArrayElements(inComposeX, nullptr);
        jfloat *yArray = env->GetFloatArrayElements(inComposeY, nullptr);


        std::vector<KeyCoord> typeCoords(xArrayElems);
        for(int i=0; i<xArrayElems; i++){
            if(xArray[i] == 0.0f && yArray[i] == 0.0f) continue;

            typeCoords.push_back({
                xArray[i],
                yArray[i],
                0.0f
            });
        }

        // Consider only the top 5000 predictions
        index_value.resize(5000);

        // Adjust probabilities according to levenshtein distance
        for(auto &v : index_value) {
            int token_id = v.second;

            if(false) {
                // Distance based (WIP)
                std::vector<KeyCoord> token = keyboardVocab.vocab_to_coords[token_id];

                int min_length = std::min(typeCoords.size(), typeCoords.size());

                std::vector<KeyCoord> typeCoordsWLen(typeCoords.begin(),
                                                     typeCoords.begin() + min_length);

                float distance = modifiedLevenshtein(token, typeCoordsWLen) /
                                 (float) pInfo->getMostCommonKeyWidthSquare();

                // Add a penalty for when the token is too short
                if (token.size() < typeCoords.size()) {
                    distance += (float) (typeCoords.size() - token.size()) * 5.0f;
                }

                // this assumes the probabilities are all positive
                v.first = v.first / (1.0f + distance);
            }
            else {
                // String based
                std::string token = state->vocab.id_to_token[token_id];

                int min_length = std::min(token.length(), partialWordString.length());

                float distance = (float)levenshtein(token.substr(0, min_length), partialWordString.substr(0, min_length));

                // Add a penalty for when the token is too short
                if(token.length() < partialWordString.length()) {
                    distance += (partialWordString.length() - token.length()) * 2.0f;
                }

                // this assumes the probabilities are all positive
                v.first = v.first / (1.0f + distance);
            }
        }

        // Sort the index_value vector in descending order of value again
        std::sort(index_value.begin(), index_value.end(),
                  [](const std::pair<float, int>& a, const std::pair<float, int>& b) {
                      return a.first > b.first;  // Descending
                  });


        env->ReleaseFloatArrayElements(inComposeX, xArray, 0);
        env->ReleaseFloatArrayElements(inComposeY, yArray, 0);
    }


    size_t size = env->GetArrayLength(outPredictions);

    // Get the array elements
    jfloat *probsArray = env->GetFloatArrayElements(outProbabilities, nullptr);

    // Output predictions for next word
    for (int i = 0; i < std::min(size, index_value.size()); i++) {
        int token_id = index_value[i].second;
        if (i < 8) {
            AKLOGI(" - prediction[%d]: %s", i, state->vocab.id_to_token[token_id].c_str());
        }
        jstring jstr = env->NewStringUTF(state->vocab.id_to_token[token_id].c_str());

        env->SetObjectArrayElement(outPredictions, i, jstr);

        probsArray[i] = index_value[i].first;

        env->DeleteLocalRef(jstr);
    }

    env->ReleaseFloatArrayElements(outProbabilities, probsArray, 0);
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
        const_cast<char *>("(JJLjava/lang/String;Ljava/lang/String;[F[F[Ljava/lang/String;[F)V"),
        reinterpret_cast<void *>(latinime_GGMLDictionary_getSuggestions)
    }
};

int register_GGMLDictionary(JNIEnv *env) {
    const char *const kClassPathName = "org/futo/inputmethod/latin/GGMLDictionary";
    return registerNativeMethods(env, kClassPathName, sMethods, NELEMS(sMethods));
}
} // namespace latinime
