#include <sstream>
#include "ModelMeta.h"
#include "ggml.h"
#include "../defines.h"

#define GGUF_GET_KEY(ctx, dst, func, type, req, key) \
do { \
    const std::string skey(key); \
    const int kid = gguf_find_key(ctx, skey.c_str()); \
    if (kid >= 0) { \
        enum gguf_type ktype = gguf_get_kv_type(ctx, kid); \
        if (ktype != (type)) { \
            AKLOGE("key %s has wrong type: %s", skey.c_str(), gguf_type_name(ktype)); \
        } \
        (dst) = func(ctx, kid); \
    } else if (req) { \
        AKLOGE("key not found in model: %s", skey.c_str()); \
    } \
} while (0)

struct ModelMetadata loadModelMetadata(const std::string &modelPath) {
    std::string languages;
    std::string features;
    std::string ext_tokenizer_type;

    struct ModelMetadata result;

    struct gguf_init_params params = {
            /*.no_alloc = */ true,
            /*.ctx      = */ nullptr,
    };

    struct gguf_context *ctx_gguf = gguf_init_from_file(modelPath.c_str(), params);
    // TODO: ctx_gguf may be null, and it likely will be null if the user imports a bad model

    GGUF_GET_KEY(ctx_gguf, result.name, gguf_get_val_str, GGUF_TYPE_STRING, false, "general.name");
    GGUF_GET_KEY(ctx_gguf, result.author, gguf_get_val_str, GGUF_TYPE_STRING, false, "general.author");
    GGUF_GET_KEY(ctx_gguf, result.description, gguf_get_val_str, GGUF_TYPE_STRING, false, "general.description");
    GGUF_GET_KEY(ctx_gguf, result.license, gguf_get_val_str, GGUF_TYPE_STRING, false, "general.license");
    GGUF_GET_KEY(ctx_gguf, result.url, gguf_get_val_str, GGUF_TYPE_STRING, false, "general.url");

    // TODO: move general -> keyboardlm
    GGUF_GET_KEY(ctx_gguf, languages, gguf_get_val_str, GGUF_TYPE_STRING, false, "general.languages");
    GGUF_GET_KEY(ctx_gguf, result.finetuning_count, gguf_get_val_u32, GGUF_TYPE_UINT32, false, "general.finetuning_count");
    GGUF_GET_KEY(ctx_gguf, result.history, gguf_get_val_str, GGUF_TYPE_STRING, false, "general.history");
    GGUF_GET_KEY(ctx_gguf, features, gguf_get_val_str, GGUF_TYPE_STRING, false, "general.features");
    GGUF_GET_KEY(ctx_gguf, ext_tokenizer_type, gguf_get_val_str, GGUF_TYPE_STRING, false, "general.ext_tokenizer_type");

    // Get tokenizer data
    do {
        const int kid = gguf_find_key(ctx_gguf, "general.ext_tokenizer_data");
        if (kid >= 0) {
            \
        enum gguf_type ktype = gguf_get_kv_type(ctx_gguf, kid);
            if (ktype != GGUF_TYPE_STRING) {
                AKLOGE("key %s has wrong type: %s", "general.ext_tokenizer_data",
                       gguf_type_name(ktype));
            }

            const char *data = gguf_get_val_str(ctx_gguf, kid);
            size_t len = gguf_get_val_str_n(ctx_gguf, kid);
            result.ext_tokenizer_data = std::string(data, len);
        } else {
            AKLOGE("key not found in model: %s", "general.ext_tokenizer_data");
        }
    } while(0);

    gguf_free(ctx_gguf);


    std::istringstream languages_iss(languages);
    std::string temp;
    while (languages_iss >> temp) {
        result.languages.insert(temp);
    }

    std::istringstream features_iss(features);
    while (features_iss >> temp) {
        result.features.insert(temp);
    }

    if(ext_tokenizer_type.empty()) {
        result.ext_tokenizer_type = ExternalTokenizerType::None;
    } else if(ext_tokenizer_type == "sentencepiece") {
        result.ext_tokenizer_type = ExternalTokenizerType::SentencePiece;
    } else {
        result.ext_tokenizer_type = ExternalTokenizerType::Unknown;
    }

    return result;
}