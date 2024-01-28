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

enum ExternalTokenizerType {
    None,
    SentencePiece,
    Unknown
};

struct ModelMetadata {
public:
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

#endif