#define LOG_TAG "LatinIME: jni: LanguageModel"

#include "org_futo_inputmethod_latin_xlm_LanguageModel.h"

#include <cstring> // for memset()
#include <vector>

#include "jni.h"
#include "jni_common.h"
#include "ggml/LanguageModel.h"
#include "defines.h"

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

struct LanguageModelState {
    LanguageModel *model;

    struct {
        int XBU;
        int XBC;
        int XEC;

        int LETTERS_TO_IDS[26];
    } specialTokens;

    bool Initialize(const std::string &paths){
        model = LlamaAdapter::createLanguageModel(paths);
        if(!model) {
            AKLOGE("GGMLDict: Could not load model");
            return false;
        }

        specialTokens.XBU = 104; //model->tokenToId("_XBU_");
        specialTokens.XBC = 105; //model->tokenToId("_XBC_");
        specialTokens.XEC = 106; //model->tokenToId("_XEC_");
        specialTokens.LETTERS_TO_IDS[0] = 124; //model->tokenToId("_XU_LETTER_A_");

        ASSERT(specialTokens.XBU != 0);
        ASSERT(specialTokens.XBC != 0);
        ASSERT(specialTokens.XEC != 0);
        ASSERT(specialTokens.LETTERS_TO_IDS[0] != 0);

        for(int i = 1; i < 26; i++) {
            specialTokens.LETTERS_TO_IDS[i] = specialTokens.LETTERS_TO_IDS[0] + i;
        }

        return true;
    }

    std::pair<float, token_sequence> Sample(){
        float probability = 0.0f;
        token_sequence sampled_sequence;

        std::vector<std::pair<float, int>> index_value;

        while(sampled_sequence.size() < 8) {
            std::vector<float> logits = model->infer();
            logits[specialTokens.XBU] = -999.0f;

            index_value.clear();
            for (size_t i = 0; i < logits.size(); i++) {
                index_value.emplace_back(logits[i], i);
            }

            sortProbabilityPairVectorDescending(index_value, 1);

            int next_token = index_value[0].second;
            model->pushToContext(next_token);

            // Check if this is the end of correction
            if(next_token == specialTokens.XEC) {
                break;
            }

            probability += index_value[0].first;
            sampled_sequence.push_back(next_token);


            // Check if this is the end of a word
            std::string token = model->getToken(next_token);
            if(token.size() >= 3 && (token[token.size() - 1] == '\x81') && (token[token.size() - 2] == '\x96') && token[token.size() - 3] == '\xe2') {
                break;
            }
        }

        return {probability, std::move(sampled_sequence)};
    }

    std::string PredictNextWord(const std::string &context) {
        token_sequence next_context = model->tokenize(trim(context) + " ");
        model->updateContext(next_context);

        auto result = Sample();

        return model->decode(result.second);
    }

    std::string PredictCorrection(const std::string &context, std::string &word) {
        token_sequence next_context = model->tokenize(trim(context) + " ");
        next_context.push_back(specialTokens.XBU);

        for(char c : trim(word)) {
            if(c >= 'a' && c <= 'z') {
                next_context.push_back(specialTokens.LETTERS_TO_IDS[c - 'a']);
            }else if(c >= 'A' && c <= 'Z') {
                next_context.push_back(specialTokens.LETTERS_TO_IDS[c - 'A']);
            } else {
                AKLOGI("ignoring character in partial word [%c]", c);
            }
        }
        next_context.push_back(specialTokens.XBC);

        model->updateContext(next_context);

        auto result = Sample();

        return model->decode(result.second);
    }
};

namespace latinime {
    class ProximityInfo;

    static jlong xlm_LanguageModel_open(JNIEnv *env, jclass clazz, jstring modelDir) {
        AKLOGI("open LM");
        const jsize sourceDirUtf8Length = env->GetStringUTFLength(modelDir);
        if (sourceDirUtf8Length <= 0) {
            AKLOGE("DICT: Can't get sourceDir string");
            return 0;
        }
        char sourceDirChars[sourceDirUtf8Length + 1];
        env->GetStringUTFRegion(modelDir, 0, env->GetStringLength(modelDir), sourceDirChars);
        sourceDirChars[sourceDirUtf8Length] = '\0';

        LanguageModelState *state = new LanguageModelState();

        if(!state->Initialize(sourceDirChars)) {
            free(state);
            return 0;
        }

        return reinterpret_cast<jlong>(state);
    }

    static void xlm_LanguageModel_close(JNIEnv *env, jclass clazz, jlong statePtr) {
        LanguageModelState *state = reinterpret_cast<LanguageModelState *>(statePtr);
        if(state == nullptr) return;
        delete state;
    }

    static void xlm_LanguageModel_getSuggestions(JNIEnv *env, jclass clazz,
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
        LanguageModelState *state = reinterpret_cast<LanguageModelState *>(dict);

        const char* cstr = env->GetStringUTFChars(context, nullptr);
        std::string contextString(cstr);
        env->ReleaseStringUTFChars(context, cstr);

        std::string partialWordString;
        if(partialWord != nullptr){
            const char* pwstr = env->GetStringUTFChars(partialWord, nullptr);
            partialWordString = std::string(pwstr);
            env->ReleaseStringUTFChars(partialWord, pwstr);
        }

        AKLOGI("LanguageModel context [%s]", contextString.c_str());

        bool isAutoCorrect = false;
        std::string result;
        if(partialWordString.empty()) {
            result = state->PredictNextWord(contextString);

            AKLOGI("LanguageModel suggestion [%s]", result.c_str());
        } else {
            isAutoCorrect = true;
            result = state->PredictCorrection(contextString, partialWordString);

            AKLOGI("LanguageModel correction [%s] -> [%s]", partialWordString.c_str(), result.c_str());
        }

        // Output
        size_t size = env->GetArrayLength(outPredictions);

        jfloat *probsArray = env->GetFloatArrayElements(outProbabilities, nullptr);

        // Output predictions for next word
        for (int i = 0; i < 1; i++) {
            jstring jstr = env->NewStringUTF(result.c_str());
            env->SetObjectArrayElement(outPredictions, i, jstr);
            probsArray[i] = isAutoCorrect ? 200.0f : 100.0f;
            env->DeleteLocalRef(jstr);
        }

        env->ReleaseFloatArrayElements(outProbabilities, probsArray, 0);
    }

    static const JNINativeMethod sMethods[] = {
            {
                    const_cast<char *>("openNative"),
                    const_cast<char *>("(Ljava/lang/String;)J"),
                    reinterpret_cast<void *>(xlm_LanguageModel_open)
            },
            {
                    const_cast<char *>("closeNative"),
                    const_cast<char *>("(J)V"),
                    reinterpret_cast<void *>(xlm_LanguageModel_close)
            },
            {
                    const_cast<char *>("getSuggestionsNative"),
                    const_cast<char *>("(JJLjava/lang/String;Ljava/lang/String;[F[F[Ljava/lang/String;[F)V"),
                    reinterpret_cast<void *>(xlm_LanguageModel_getSuggestions)
            }
    };

    int register_LanguageModel(JNIEnv *env) {
        llama_backend_init(true /* numa??? */);

        const char *const kClassPathName = "org/futo/inputmethod/latin/xlm/LanguageModel";
        return registerNativeMethods(env, kClassPathName, sMethods, NELEMS(sMethods));
    }
} // namespace latinime
