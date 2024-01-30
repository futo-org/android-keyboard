//
// Created by alex on 1/23/24.
//

#ifndef LATINIME_MODELMETA_H
#define LATINIME_MODELMETA_H

#include <string>
#include <vector>
#include <cstdint>
#include <algorithm>
#include <set>
#include "ggml.h"

#define META_KEY_LANGUAGES_STR          "keyboardlm.languages"
#define META_KEY_FINETUNING_COUNT_U32   "keyboardlm.finetuning_count"
#define META_KEY_HISTORY_STR            "keyboardlm.history"
#define META_KEY_FEATURES_STR           "keyboardlm.features"
#define META_KEY_TOKENIZER_TYPE_STR     "keyboardlm.ext_tokenizer_type"
#define META_KEY_TOKENIZER_DATA_ARR     "keyboardlm.ext_tokenizer_data"

#define META_TOKENIZER_SENTENCEPIECE "sentencepiece"

enum ExternalTokenizerType {
    None,
    SentencePiece,
    Unknown
};

struct ModelMetadata {
public:
    bool error;

    std::string name;
    std::string description;
    std::string author;
    std::string url;
    std::string license;

    std::set<std::string> languages;
    std::set<std::string> features;

    uint32_t finetuning_count = 0;
    std::string history = "";

    ExternalTokenizerType ext_tokenizer_type = None;
    std::string ext_tokenizer_data = "";

    inline bool HasFeature(const std::string &feature) const {
        return features.find(feature) != features.end();
    }
};


struct ModelMetadata loadModelMetadata(const std::string &modelPath);
int writeModelMetadata(gguf_context *fctx, const ModelMetadata &metadata);

#endif