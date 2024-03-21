#include <jni.h>
#include <string>
#include "org_futo_inputmethod_latin_xlm_ModelInfoLoader.h"
#include "defines.h"
#include "jni_common.h"
#include "ggml/finetune.h"
#include "sentencepiece/sentencepiece_processor.h"
#include "jni_utils.h"
#include "ggml/ModelMeta.h"

namespace latinime {

    jobject metadata_open(JNIEnv *env, jobject thiz, jstring pathString) {
        std::string path = jstring2string(env, pathString);
        auto metadata = loadModelMetadata(path);

        if(metadata.error) {
            AKLOGE("ModelInfoLoader: loading metadata for %s failed", path.c_str());
            return NULL;
        }

        jclass modelInfoClass = env->FindClass("org/futo/inputmethod/latin/xlm/ModelInfo");
        jmethodID constructor = env->GetMethodID(modelInfoClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/List;Ljava/util/List;Ljava/lang/String;ILjava/lang/String;)V");

        // Create example data
        jstring name = string2jstring(env, metadata.name.c_str());
        jstring description = string2jstring(env, metadata.description.c_str());
        jstring author = string2jstring(env, metadata.author.c_str());
        jstring license = string2jstring(env, metadata.license.c_str());

        const char *tokenizer_type_value;
        switch(metadata.ext_tokenizer_type) {
            case None:
                tokenizer_type_value = "None";
                break;
            case SentencePiece:
                tokenizer_type_value = "SentencePiece";
                break;
            case Unknown:
                tokenizer_type_value = "Unknown";
                break;
        }

        jstring tokenizer_type = string2jstring(env, tokenizer_type_value);
        jint finetune_count = metadata.finetuning_count;

        // Create example features and languages lists
        jclass listClass = env->FindClass("java/util/ArrayList");
        jmethodID listConstructor = env->GetMethodID(listClass, "<init>", "()V");
        jmethodID listAdd = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");

        jobject features = env->NewObject(listClass, listConstructor);
        jobject languages = env->NewObject(listClass, listConstructor);

        for (const auto& feature : metadata.features) {
            jstring jFeature = string2jstring(env, feature.c_str());
            env->CallBooleanMethod(features, listAdd, jFeature);
            env->DeleteLocalRef(jFeature);
        }

        for (const auto& language : metadata.languages) {
            jstring jLanguage = string2jstring(env, language.c_str());
            env->CallBooleanMethod(languages, listAdd, jLanguage);
            env->DeleteLocalRef(jLanguage);
        }

        // Create the ModelInfo object
        jobject modelInfo = env->NewObject(modelInfoClass, constructor, name, description, author, license, features, languages, tokenizer_type, finetune_count, pathString);

        // Clean up local references
        env->DeleteLocalRef(name);
        env->DeleteLocalRef(description);
        env->DeleteLocalRef(author);
        env->DeleteLocalRef(license);
        env->DeleteLocalRef(features);
        env->DeleteLocalRef(languages);
        env->DeleteLocalRef(tokenizer_type);

        return modelInfo;
    }

    static const JNINativeMethod sMethods[] = {
            {
                    const_cast<char *>("loadNative"),
                    const_cast<char *>("(Ljava/lang/String;)Lorg/futo/inputmethod/latin/xlm/ModelInfo;"),
                    reinterpret_cast<void *>(metadata_open)
            },
    };

    int register_ModelInfoLoader(JNIEnv *env) {
        const char *const kClassPathName = "org/futo/inputmethod/latin/xlm/ModelInfoLoader";
        return registerNativeMethods(env, kClassPathName, sMethods, NELEMS(sMethods));
    }

}