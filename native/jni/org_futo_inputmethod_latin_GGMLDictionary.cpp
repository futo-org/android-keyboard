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
#include "dictionary/structure/v2/patricia_trie_policy.h"
#include "dictionary/structure/pt_common/dynamic_pt_reading_helper.h"
#include "ggml/LanguageModel.h"

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
int levenshtein(const char *a, const char *b, size_t len) {
    size_t a_len = len;
    size_t b_len = len;

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
    bool initialized = false;

    // TODO: We should store dictionary here too to look up words during multi-token sampling

    std::vector<int> tokenStrategies;

    std::unordered_set<int> invalidTokens;

    // TODO: words like "won't", "can't", are tokenized like won, 't, can, 't or won, ', t, can, ', t
    //       By taking the first token and assuming it's done we get nonsensical suggestions like won, can,
    std::unordered_set<int> wordTokens;


    std::unordered_set<int> continueSamplingTokens;
    std::unordered_set<int> continuationToken;

};

#define STRATEGY_CONTINUATION 4
void DictionaryRescorer_addDictionary(Dictionary &dict, const LanguageModel &model, DictionaryRescorer &rescorer) {
    if(rescorer.initialized) return;

    rescorer.tokenStrategies.clear();

    if(rescorer.tokenStrategies.size() < model.getVocabSize()) {
        rescorer.tokenStrategies.resize(model.getVocabSize());
    }

    for(int i=0; i<model.getVocabSize(); i++) {
        const char *word = model.getToken(i);

        char c = word[0];

        bool startOfWord = c == ' ';

        bool isInvalid = c == ',' || c == '.' || c == '?' || c == '!' || ((c >= '0') && (c <= '9')) || c == '(' || c == ')' || c == '"' || c == '[' || c == ']' || c == '+' || c == '#' || c == '<' || c == '>' || c == '|';

        // TODO: The dictionary never contains numbers
        const int strategy = isInvalid ? STRATEGY_INVALID : (startOfWord ? dict.getWordStrategy(word + 1) : STRATEGY_CONTINUATION);
        rescorer.tokenStrategies[i] = strategy;
        switch(strategy) {
            case STRATEGY_COMMIT_WORD:
                rescorer.wordTokens.insert(i);
                break;
            case STRATEGY_CONTINUE_SAMPLING:
                // TODO: We may need something like
                rescorer.continueSamplingTokens.insert(i);
                break;
            case STRATEGY_INVALID:
                rescorer.invalidTokens.insert(i);
                break;
            case STRATEGY_CONTINUATION:
                rescorer.continuationToken.insert(i);
                break;
            default:
                ASSERT(false);
        }
    }

    rescorer.initialized = true;
}

template<typename T>
bool sortProbabilityPairDescending(const std::pair<float, T>& a, const std::pair<float, T>& b) {
    return a.first > b.first;
}


template<typename T>
static inline void sortProbabilityPairVectorDescending(std::vector<std::pair<float, T>> &vec) {
    std::sort(vec.begin(), vec.end(), sortProbabilityPairDescending<T>);
}

template<typename T>
static inline void sortProbabilityPairVectorDescending(std::vector<std::pair<float, T>> &vec, int partial) {
    std::partial_sort(vec.begin(), vec.begin() + partial, vec.end(), sortProbabilityPairDescending<T>);
}



float rescore_token_levenshtein(float prob, const char *text, int length, const std::string &partialWord, bool applyLengthPenalty) {
    if(prob == 0.0f) return 0.0f;

    if(!partialWord.empty()) {
        unsigned int min_length = std::min(length, partialWord.length());
        float distance = (float)levenshtein(text, partialWord.c_str(), min_length);

        if(applyLengthPenalty && (length < partialWord.length())) {
            distance += (partialWord.length() - length) * 2.0f;
        }

        // this assumes the probabilities are all positive
        ASSERT(prob >= 0.0f;)

        prob = prob / (1.0f + distance);

        return prob;
    } else {
        return prob;
    }
}

float rescore_token_levenshtein(float prob, const std::string &text, const std::string &partialWord, bool applyLengthPenalty) {
    return rescore_token_levenshtein(prob, text.c_str(), text.length(), partialWord, applyLengthPenalty);
}


std::pair<float, token_sequence> process_token_sequence(    
    const DictionaryRescorer &rescorer,
    LanguageModel &model,
    const std::string &partialWord,
    const token_sequence &seq,
    float lastprob,
    float minprob,
    int recursionDepth
) {
    if(recursionDepth > 3) {
        // Cut our losses and exit
        return { 0.0f, {} };
    }

    std::vector<float> nextLogits = model.temporarilyInfer(seq);
    std::vector<std::pair<float, int>> nextIndexValue;

    for (int j = 0; j < nextLogits.size(); j++) {
        int thisStrategy = rescorer.tokenStrategies[j];

        nextIndexValue.emplace_back(nextLogits[j], j);
    }
    
    sortProbabilityPairVectorDescending(nextIndexValue, 3);
    for (int j = 0; j < 3; j++) {
        float probability = nextIndexValue[j].first;
        int tokenId = nextIndexValue[j].second;

        const char * chars = model.getToken(tokenId);

        // handle punctuation and stuf as well
        // we really need an abstract function to return token type
        if(chars[0] == ' ') {
            // The model believes the previous word has ended, so let's just cut it here and return verbatim
            // TODO: should lastprob be modified with the probability value? what if we reach this only in the
            //       3rd iteration?
            return { lastprob, seq };
        }

        token_sequence new_token_sequence = seq + { nextToken };

        std::string resultingWord = model.decode(new_token_sequence);

        // Rescore according to partial word, if exists
        probability = rescore_token_levenshtein(probability, resultingWord, partialWord, true);

        // We do this AFTER rescoring as lastprob is also after rescoring
        // TODO: Is a simple average sufficient here? What about during recursion?
        float resultingProbability = (probability + lastprob) / 2.0f;

        if(resultingProbability < minprob) continue;

        // TODO: Check with the dictionary here. Need to remember pt position for optimization
        // (pass pt from the function)
        int strategy = TODO(check strategy for this now)

        if(strategy == STRATEGY_COMMIT_WORD) {
            // We've finally written a word, so we can return this
            return { resultingProbability, new_token_sequence }
        }else if(strategy == STRATEGY_CONTINUE_SAMPLING) {
            return process_token_sequence(rescorer, model, partialWord, new_token_sequence, resultingProbability, minprob, recursionDepth+1);
        }else{
            // The dictionary says this is an invalid word. We can do two things here
            // 1. Trust the dictionary - Discard it, because we will never arrive at a word in the dictionary
            // 2. Trust the model - Continue sampling until space, we trust that the model is giving something useful
            //    (a special word like JQuery that may not be in the dictionary)
            // A problem for #2 is that we ignore invalid words anyway when it's the first token
        }
    }

    return { 0.0f, {} };
}

std::vector<std::pair<float, std::string>> DictionaryRescorer_process(
        const DictionaryRescorer &rescorer,
        const std::vector<float> &logitsOrig,
        const std::unordered_set<gpt_vocab::id> &punctIds,
        const std::string &partialWord,
        LanguageModel &model,
        int n
) {
    std::vector<std::pair<float, std::string>> top_n_results(n);

    std::vector<float> logits(logitsOrig);

    for(int i : rescorer.invalidTokens) {
        logits[i] = 0.0f;
    }

    for(int i : rescorer.continuationToken) {
        logits[i] = 0.0f; // TODO: Allow continuation only if it's the most probable token, and if partialWord is empty
    }

    // Restore punctuation
    // TODO: A better way
    for(int i : punctIds){
        logits[i] = logitsOrig[i];
    }

    // Get a vector of index and value pairs
    std::vector<std::pair<float, int>> index_value;
    for (int i = 0; i < logits.size(); i++) {
        index_value.emplace_back(logits[i], i);
    }

    // Sort the index_value vector in descending order of value
    sortProbabilityPairVectorDescending(index_value, 6000);

    if(!partialWord.empty()) {
        // TODO: Figure out a better way. It's slow to compute full levenshtein for every value, and inaccurate to resize to a small size like 500.
        // We could cache past distances to prune those that are too distant
        // We could compare first characters of words and pick those as more likely
        index_value.resize(std::min((size_t)6000, rescorer.wordTokens.size()));
        // Adjust probabilities according to levenshtein distance
        for(auto &v : index_value) {
            int token_id = v.second;
            int thisStrategy = rescorer.tokenStrategies[token_id];

            const char *token = model.getToken(token_id);
            size_t token_length = strlen(token);

            // Apply length penalty for when the token is too short, except when its continue_sampling (why?)
            v.first = rescore_token_levenshtein(v.first, token, token_length, partialWord, (thisStrategy != STRATEGY_CONTINUE_SAMPLING || (token_length < 3)) );
        }

        // Sort the index_value vector in descending order of value again
        sortProbabilityPairVectorDescending(index_value, n);
    }

    std::vector<std::pair<float, token_sequence>> top_three_results_so_far(3);

    // Select the top three results we can commit instantly
    for(int i=0; i<n; i++) {
        float probability = index_value[i].first;
        int tokenId = index_value[i].second;

        int strategy = rescorer.tokenStrategies[tokenId];

        if(strategy == STRATEGY_COMMIT_WORD) {
            top_three_results_so_far.emplace_back(probability, { tokenId });
            if(top_three_results_so_far.size() >= 3) break;
        }
    }

    // Iterate over those that require continuing sampling (top three only (TODO?))
    for(int i=0; i<std::min(3, n); i++) {
        float probability = index_value[i].first;
        int tokenId = index_value[i].second;

        int strategy = rescorer.tokenStrategies[tokenId];

        if(strategy != STRATEGY_CONTINUE_SAMPLING) continue;
        if(!top_three_results_so_far.empty() && probability < top_three_results_so_far.back().first) continue;

        auto result = process_token_sequence(rescorer, model, partialWord, { tokenId }, probability, top_three_results_so_far.back().first, 0);
        if(result.first != 0.0f) {
            top_three_results_so_far.push_back(result);
            sortProbabilityPairVectorDescending(top_three_results_so_far);
        }
    }

    return top_n_results;
}



struct GGMLDictionaryState {
    LanguageModel *model;

    std::vector<gpt_vocab::id> bad_ids;
    std::unordered_set<gpt_vocab::id> punct_ids;

    //std::map<ProximityInfo *, KeyboardVocab> proximity_info_to_kvoc;
    DictionaryRescorer rescorer;
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

    state->model = GPTNeoXAdapter::createLanguageModel(sourceDirChars);
    if(!state->model) {
        AKLOGE("GGMLDict: Could not load model");
        free(state);
        return 0;
    }

    for(int i=0; i<state->model->getVocabSize(); i++){
        std::string token = state->model->getToken(i);

        bool is_bad = token.empty();
        bool has_punct = false;
        int num_chars = 0;
        if(!is_bad) {
            for (char c: token) {
                // Allow single-character punctuation
                bool is_punct = c == ',' || c == '.' || c == '?' || c == '!';
                bool is_letter = ((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z'));
                bool is_number = (c >= '0') && (c <= '9');
                bool is_special = c == '(' || c == ')' || c == '"' || c == '[' || c == ']' || c == '+' || c == '#' || c == '<' || c == '>';

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
            state->bad_ids.emplace_back(i);
        }
        if(has_punct) {
            state->punct_ids.insert(i);
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
    GGMLDictionaryState *state = reinterpret_cast<GGMLDictionaryState *>(statePtr);
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);

    AKLOGI("Here is the dictionary we are adding:");
    dictionary->logDictionaryInfo(env);

    time_t t1 = time(NULL);
    DictionaryRescorer_addDictionary(*dictionary, *state->model, state->rescorer);
    time_t t2 = time(NULL);
    AKLOGI("Took %.2f to add dictionary", difftime(t2, t1));
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

    token_sequence next_context = state->model->tokenize(contextString);
    bool allow_punctuation_next = state->punct_ids.count(next_context[next_context.size() - 1]) == 0;

    state->model->updateContext(next_context);
    std::vector<float> logits = state->model->infer();

    float zeroValue = 0.0f;
    for(int bad_id : state->bad_ids) {
        logits[bad_id] = zeroValue;
    }

    // Don't allow punctuation after we just wrote punctuation
    if(!allow_punctuation_next) {
        for(int bad_id : state->punct_ids) {
            logits[bad_id] = zeroValue;
        }
    }

    auto results = DictionaryRescorer_process(state->rescorer, logits, state->punct_ids, partialWordString, *state->model, 50);

    size_t size = env->GetArrayLength(outPredictions);

    // Get the array elements
    jfloat *probsArray = env->GetFloatArrayElements(outProbabilities, nullptr);

    AKLOGI("Predictions:");
    // Output predictions for next word
    for (int i = 0; i < std::min(size, results.size()); i++) {
        std::string &word = results[i].second;
        if (i < 8) {
            AKLOGI(" - prediction[%d]: [%s]", i, word.c_str());
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
