//
// Created by alex on 11/7/23.
//

#include <string>
#include "org_futo_inputmethod_latin_xlm_AdapterTrainer.h"
#include "defines.h"
#include "jni_common.h"
#include "ggml/finetune.h"
#include "sentencepiece/sentencepiece_processor.h"


std::string jstring2string(JNIEnv *env, jstring jStr) {
    const jsize stringUtf8Length = env->GetStringUTFLength(jStr);
    if (stringUtf8Length <= 0) {
        AKLOGE("Can't get jStr");
        return "";
    }
    char stringChars[stringUtf8Length + 1];
    env->GetStringUTFRegion(jStr, 0, env->GetStringLength(jStr), stringChars);
    stringChars[stringUtf8Length] = '\0';

    return {stringChars};
}


namespace latinime {
    struct AdapterTrainerState {
        std::string baseModelPath;
        std::string tokenizerPath;
        std::string outputPath;

        sentencepiece::SentencePieceProcessor spm;
        struct train_params params;

        bool Initialize() {
            params = get_default_train_params();
            params.common.fn_train_data = "";
            params.common.fn_checkpoint_in = "";
            params.common.fn_checkpoint_out = "";
            params.fn_model_base = baseModelPath.c_str();
            params.fn_lora_out = outputPath.c_str();

            params.common.fill_with_next_samples = true;
            params.common.n_threads = 6;
            params.common.n_gradient_accumulation = 2;
            params.common.n_batch = 2;
            params.common.n_ctx = 32;
            params.common.sample_random_offsets = true;

            params.common.warmup = 10;
            params.common.n_epochs = 1;
            params.common.adam_alpha = 1e-3;
            params.common.adam_n_iter = 64;

            // Increasing/decreasing this doesn't appear to significantly affect training time
            params.lora_r = 16;
            params.lora_alpha = 16;

            // TODO: Check model path valid / try to pre-load resources?

            if(!spm.Load(tokenizerPath).ok()){
                AKLOGE("Failed to load tokenizer at path %s!", tokenizerPath.c_str());
                return false;
            }

            return true;
        }

        void AddTrainingExample(const std::string &example) {
            std::vector<llama_token> result = spm.EncodeAsIds(example);
            params.training_data.push_back(result);
        }

        int Train() const {
            return finetune_train(params);
        }
    };

    static jlong xlm_AdapterTrainer_open(JNIEnv *env, jclass clazz, jstring baseModelPathStr, jstring tokenizerPathStr, jstring outputPathStr) {
        auto *state = new AdapterTrainerState();
        state->baseModelPath = jstring2string(env, baseModelPathStr);
        state->tokenizerPath = jstring2string(env, tokenizerPathStr);
        state->outputPath    = jstring2string(env, outputPathStr);

        if(!state->Initialize()) {
            delete state;
            return 0;
        }

        return reinterpret_cast<jlong>(state);
    }

    static void xlm_AdapterTrainer_close(JNIEnv *env, jclass clazz, jlong statePtr) {
        auto *state = reinterpret_cast<AdapterTrainerState *>(statePtr);
        if(state == nullptr) return;
        delete state;
    }

    static void xlm_AdapterTrainer_addExample(JNIEnv *env, jclass clazz, jlong statePtr, jstring exampleStr) {
        auto *state = reinterpret_cast<AdapterTrainerState *>(statePtr);
        state->AddTrainingExample(jstring2string(env, exampleStr));
    }

    // TODO: Callback for progress
    static void xlm_AdapterTrainer_train(JNIEnv *env, jclass clazz, jlong statePtr) {
        auto *state = reinterpret_cast<AdapterTrainerState *>(statePtr);
        int result = state->Train();
        if(result != 0) {
            AKLOGE("train returned with non-zero code %d", result);
        }
    }

    static const JNINativeMethod sMethods[] = {
            {
                    const_cast<char *>("openNative"),
                    const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)J"),
                    reinterpret_cast<void *>(xlm_AdapterTrainer_open)
            },
            {
                    const_cast<char *>("closeNative"),
                    const_cast<char *>("(J)V"),
                    reinterpret_cast<void *>(xlm_AdapterTrainer_close)
            },
            {
                    const_cast<char *>("addExample"),
                    const_cast<char *>("(JLjava/lang/String;)V"),
                    reinterpret_cast<void *>(xlm_AdapterTrainer_addExample)
            },
            {
                    const_cast<char *>("train"),
                    const_cast<char *>("(J)V"),
                    reinterpret_cast<void *>(xlm_AdapterTrainer_train)
            },

    };

    int register_AdapterTrainer(JNIEnv *env) {
        const char *const kClassPathName = "org/futo/inputmethod/latin/xlm/AdapterTrainer";
        return registerNativeMethods(env, kClassPathName, sMethods, NELEMS(sMethods));
    }
}