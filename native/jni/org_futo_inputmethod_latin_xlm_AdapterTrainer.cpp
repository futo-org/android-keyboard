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
        std::string loraCachePath;
        std::string outputModelPath;
        float outputScale;

        sentencepiece::SentencePieceProcessor spm;
        struct train_params params;

        static void OnLossCallback(void *userdata, float loss) {
            auto *state = reinterpret_cast<AdapterTrainerState *>(userdata);
            state->OnLoss(loss);
        }

        static void OnProgressCallback(void *userdata, float progress) {
            auto *state = reinterpret_cast<AdapterTrainerState *>(userdata);
            state->OnProgress(progress);
        }

        JNIEnv *env;
        jobject callbackObject;
        jmethodID lossMethodId;
        jmethodID progressMethodId;
        void OnLoss(float loss) const {
            env->CallVoidMethod(callbackObject, lossMethodId, loss);
        }

        void OnProgress(float progress) const {
            env->CallVoidMethod(callbackObject, progressMethodId, progress);
        }

        bool Initialize() {
            params = get_default_train_params();
            params.common.fn_train_data = "";
            params.common.fn_checkpoint_in = "";
            params.common.fn_checkpoint_out = "";
            params.fn_model_base = baseModelPath.c_str();
            params.fn_lora_out = loraCachePath.c_str();

            params.common.fill_with_next_samples = true;
            params.common.n_threads = 6;
            params.common.n_gradient_accumulation = 2;
            params.common.n_batch = 2;
            params.common.n_ctx = 64;
            params.common.sample_random_offsets = true;

            params.common.warmup = 10;
            params.common.n_epochs = 1;
            params.common.adam_alpha = 1e-3;
            params.common.adam_n_iter = 128;

            // Increasing/decreasing this doesn't appear to significantly affect training time
            params.lora_r = 16;
            params.lora_alpha = 16;

            params.common.callbacks.userdata = this;
            params.common.callbacks.loss     = AdapterTrainerState::OnLossCallback;
            params.common.callbacks.progress = AdapterTrainerState::OnProgressCallback;

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

    static jlong xlm_AdapterTrainer_open(JNIEnv *env, jclass clazz, jstring baseModelPathStr, jstring tokenizerPathStr, jstring loraCacheStr, jstring outputModelPathStr, float outputScale) {
        auto *state = new AdapterTrainerState();
        state->baseModelPath   = jstring2string(env, baseModelPathStr);
        state->tokenizerPath   = jstring2string(env, tokenizerPathStr);
        state->loraCachePath   = jstring2string(env, loraCacheStr);
        state->outputModelPath = jstring2string(env, outputModelPathStr);
        state->outputScale = outputScale;

        state->env = env;

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
    static void xlm_AdapterTrainer_train(JNIEnv *env, jobject instance, jlong statePtr) {
        jclass clazz = env->GetObjectClass(instance);
        assert(clazz);

        jmethodID progressMethodId = env->GetMethodID(clazz, "emitProgress", "(F)V");
        jmethodID lossMethodId = env->GetMethodID(clazz, "emitLoss", "(F)V");
        assert(progressMethodId);
        assert(lossMethodId);

        auto *state = reinterpret_cast<AdapterTrainerState *>(statePtr);
        state->env = env;
        state->lossMethodId = lossMethodId;
        state->progressMethodId = progressMethodId;
        state->callbackObject = instance;

        int result = state->Train();
        if(result != 0) {
            AKLOGE("train returned with non-zero code %d", result);
            return;
        }

        // Apply LoRA
        llama_model_params model_params = llama_model_default_params();
        model_params.use_mmap = false;

        llama_model *model = llama_load_model_from_file(state->baseModelPath.c_str(), model_params);

        if(model == nullptr) {
            AKLOGE("failed to load model for exporting LoRA");
            return;
        }

        int err = llama_model_apply_lora_from_file(
                model,
               state->loraCachePath.c_str(),
               state->outputScale,
               nullptr,
               4
        );
        if(err != 0) {
            AKLOGE("Failed to apply lora: %d", err);
            return;
        }

        int status = save_llama_model_file(
            state->outputModelPath.c_str(),
            state->baseModelPath.c_str(),
            model
        );
        if(status != 0) {
            AKLOGE("Failed to save model! %d", status);
            return;
        }
    }

    static const JNINativeMethod sMethods[] = {
            {
                    const_cast<char *>("openNative"),
                    const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;F)J"),
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