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
    struct ModelMetadata result;

    struct gguf_init_params params = {
            /*.no_alloc = */ true,
            /*.ctx      = */ nullptr,
    };

    struct gguf_context *ctx_gguf = gguf_init_from_file(modelPath.c_str(), params);
    if(ctx_gguf == NULL) {
        result.error = true;
        return result;
    }

    std::string languages;
    std::string features;
    std::string ext_tokenizer_type;

    GGUF_GET_KEY(ctx_gguf, result.name, gguf_get_val_str, GGUF_TYPE_STRING, false, "general.name");
    GGUF_GET_KEY(ctx_gguf, result.author, gguf_get_val_str, GGUF_TYPE_STRING, false, "general.author");
    GGUF_GET_KEY(ctx_gguf, result.description, gguf_get_val_str, GGUF_TYPE_STRING, false, "general.description");
    GGUF_GET_KEY(ctx_gguf, result.license, gguf_get_val_str, GGUF_TYPE_STRING, false, "general.license");
    GGUF_GET_KEY(ctx_gguf, result.url, gguf_get_val_str, GGUF_TYPE_STRING, false, "general.url");


    GGUF_GET_KEY(ctx_gguf, languages, gguf_get_val_str, GGUF_TYPE_STRING, false, META_KEY_LANGUAGES_STR);
    GGUF_GET_KEY(ctx_gguf, result.finetuning_count, gguf_get_val_u32, GGUF_TYPE_UINT32, false, META_KEY_FINETUNING_COUNT_U32);
    GGUF_GET_KEY(ctx_gguf, result.history, gguf_get_val_str, GGUF_TYPE_STRING, false, META_KEY_HISTORY_STR);
    GGUF_GET_KEY(ctx_gguf, features, gguf_get_val_str, GGUF_TYPE_STRING, false, META_KEY_FEATURES_STR);
    GGUF_GET_KEY(ctx_gguf, ext_tokenizer_type, gguf_get_val_str, GGUF_TYPE_STRING, false, META_KEY_TOKENIZER_TYPE_STR);

    // Get tokenizer data
    const int kid = gguf_find_key(ctx_gguf, META_KEY_TOKENIZER_DATA_ARR);
    if (kid >= 0) {
        enum gguf_type ktype = gguf_get_kv_type(ctx_gguf, kid);
        if (ktype != GGUF_TYPE_ARRAY) {
            AKLOGE("key %s has wrong type: %s", META_KEY_TOKENIZER_DATA_ARR,
                   gguf_type_name(ktype));
        }

        const char *data = (const char*)gguf_get_arr_data(ctx_gguf, kid);
        size_t len = gguf_get_arr_n(ctx_gguf, kid);

        // sentencepiece library wants string_view, so we'll just store it as string
        result.ext_tokenizer_data = std::string(data, len);
    } else {
        AKLOGE("key not found in model: %s", META_KEY_TOKENIZER_DATA_ARR);
    }

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
    } else if(ext_tokenizer_type == META_TOKENIZER_SENTENCEPIECE) {
        result.ext_tokenizer_type = ExternalTokenizerType::SentencePiece;
    } else {
        result.ext_tokenizer_type = ExternalTokenizerType::Unknown;
    }

    result.error = false;
    return result;
}

int writeModelMetadata(gguf_context *fctx, const ModelMetadata &metadata) {
    gguf_set_val_str(fctx, "general.name", metadata.name.c_str());
    gguf_set_val_str(fctx, "general.author", metadata.author.c_str());
    gguf_set_val_str(fctx, "general.description", metadata.description.c_str());
    gguf_set_val_str(fctx, "general.license", metadata.license.c_str());
    gguf_set_val_str(fctx, "general.url", metadata.url.c_str());

    size_t idx = 0;
    std::string languages_combined;
    std::string features_combined;

    idx = 0;
    for (const auto& elem : metadata.languages) {
        if(idx != 0) languages_combined.append(" ");
        languages_combined.append(elem);
        ++idx;
    }

    idx = 0;
    for (const auto& elem : metadata.features) {
        if(idx != 0) features_combined.append(" ");
        features_combined.append(elem);
        ++idx;
    }

    gguf_set_val_str(fctx, META_KEY_LANGUAGES_STR, languages_combined.c_str());
    gguf_set_val_u32(fctx, META_KEY_FINETUNING_COUNT_U32, metadata.finetuning_count);
    gguf_set_val_str(fctx, META_KEY_HISTORY_STR, metadata.history.c_str());
    gguf_set_val_str(fctx, META_KEY_FEATURES_STR, features_combined.c_str());

    const char *tokenizer_type;
    switch(metadata.ext_tokenizer_type) {
        case ExternalTokenizerType::None:
            tokenizer_type = "";
            break;
        case ExternalTokenizerType::SentencePiece:
            tokenizer_type = META_TOKENIZER_SENTENCEPIECE;
            break;
        case ExternalTokenizerType::Unknown:
            AKLOGE("ModelMeta: Unknown tokenizer type, refusing to export!");
            gguf_free(fctx);
            return 9;
    }

    gguf_set_val_str(fctx, META_KEY_TOKENIZER_TYPE_STR, tokenizer_type);
    gguf_set_arr_data(fctx, META_KEY_TOKENIZER_DATA_ARR, GGUF_TYPE_UINT8,
                      metadata.ext_tokenizer_data.c_str(),
                      metadata.ext_tokenizer_data.length());

    return 0;
}