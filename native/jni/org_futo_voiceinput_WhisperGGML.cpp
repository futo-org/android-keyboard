//
// Created by hp on 11/22/23.
//

#include <string>
#include <bits/sysconf.h>
#include "org_futo_voiceinput_WhisperGGML.h"
#include "jni_common.h"
#include "defines.h"
#include "ggml/whisper.h"
#include "jni_utils.h"

struct WhisperModelState {
    int n_threads = 4;
    struct whisper_context *context = nullptr;
};

static jlong WhisperGGML_open(JNIEnv *env, jclass clazz, jstring model_dir) {
    std::string model_dir_str = jstring2string(env, model_dir);

    auto *state = new WhisperModelState();

    state->context = whisper_init_from_file(model_dir_str.c_str());

    if(!state->context){
        AKLOGE("Failed to initialize whisper_context from path %s", model_dir_str.c_str());
        delete state;
        return 0L;
    }

    return reinterpret_cast<jlong>(state);
}

static jlong WhisperGGML_openFromBuffer(JNIEnv *env, jclass clazz, jobject buffer) {
    void* buffer_address = env->GetDirectBufferAddress(buffer);
    jlong buffer_capacity = env->GetDirectBufferCapacity(buffer);

    auto *state = new WhisperModelState();

    state->context = whisper_init_from_buffer(buffer_address, buffer_capacity);

    if(!state->context){
        AKLOGE("Failed to initialize whisper_context from direct buffer");
        delete state;
        return 0L;
    }

    return reinterpret_cast<jlong>(state);
}

static void WhisperGGML_infer(JNIEnv *env, jobject instance, jlong handle, jfloatArray samples_array, jstring prompt) {
    auto *state = reinterpret_cast<WhisperModelState *>(handle);

    size_t num_samples = env->GetArrayLength(samples_array);
    jfloat *samples = env->GetFloatArrayElements(samples_array, nullptr);

    AKLOGI("Received %d samples", (int)num_samples);


    long num_procs = sysconf(_SC_NPROCESSORS_ONLN);
    if(num_procs < 2 || num_procs > 16) num_procs = 6; // Make sure the number is sane

    AKLOGI("num procs = %d", (int)num_procs);

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress = false;
    wparams.print_realtime = false;
    wparams.print_special = false;
    wparams.print_timestamps = false;
    wparams.max_tokens = 256;
    wparams.n_threads = (int)num_procs;

    //wparams.audio_ctx = (int)ceil((double)num_samples / (double)(160.0 * 2.0));
    wparams.temperature_inc = 0.0f;



    //std::string prompt_str = jstring2string(env, prompt);
    //wparams.initial_prompt = prompt_str.c_str();
    //AKLOGI("Initial prompt is [%s]", prompt_str.c_str());

    wparams.new_segment_callback = [](struct whisper_context * ctx, struct whisper_state * state, int n_new, void * user_data) {
        const int n_segments = whisper_full_n_segments(ctx);
        const int s0 = n_segments - n_new;

        if (s0 == 0) {
            AKLOGI("s0 == 0, \\n");
        }

        for (int i = s0; i < n_segments; i++) {
            auto seg = whisper_full_get_segment_text(ctx, i);
            AKLOGI("WhisperGGML new segment %s", seg);
        }
    };

    AKLOGI("Calling whisper_full");
    int res = whisper_full(state->context, wparams, samples, (int)num_samples);
    if(res != 0) {
        AKLOGE("WhisperGGML whisper_full failed with non-zero code %d", res);
    }
    AKLOGI("whisper_full finished :3");

    whisper_print_timings(state->context);

    /*
    ASSERT(mel_count % 80 == 0);
    whisper_set_mel(state->context, mel, (int)(mel_count / 80), 80);

    whisper_encode(state->context, 0, 4);

    whisper_token tokens[512] = { 0 };

    whisper_decode(state->context, tokens, 512, 0, 4);
    */
}

static void WhisperGGML_close(JNIEnv *env, jclass clazz, jlong handle) {
    auto *state = reinterpret_cast<WhisperModelState *>(handle);
    if(!state) return;

    delete state;
}


namespace voiceinput {
    static const JNINativeMethod sMethods[] = {
        {
            const_cast<char *>("openNative"),
            const_cast<char *>("(Ljava/lang/String;)J"),
            reinterpret_cast<void *>(WhisperGGML_open)
        },
        {
            const_cast<char *>("openFromBufferNative"),
            const_cast<char *>("(Ljava/nio/Buffer;)J"),
            reinterpret_cast<void *>(WhisperGGML_openFromBuffer)
        },
        {
            const_cast<char *>("inferNative"),
            const_cast<char *>("(J[FLjava/lang/String;)V"),
            reinterpret_cast<void *>(WhisperGGML_infer)
        },
        {
            const_cast<char *>("closeNative"),
            const_cast<char *>("(J)V"),
            reinterpret_cast<void *>(WhisperGGML_close)
        }
    };

    int register_WhisperGGML(JNIEnv *env) {
        const char *const kClassPathName = "org/futo/voiceinput/shared/ggml/WhisperGGML";
        return latinime::registerNativeMethods(env, kClassPathName, sMethods, NELEMS(sMethods));
    }
}