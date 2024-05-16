//
// Created by alex on 7/24/23.
//

#ifndef LATINIME_LANGUAGEMODEL_H
#define LATINIME_LANGUAGEMODEL_H

#include <vector>
#include <unordered_set>
#include <sentencepiece/sentencepiece_processor.h>
#include "context.h"
#include "llama.h"
#include "../defines.h"
#include "ModelMeta.h"

#define FEATURE_INVERTED_SPACE "inverted_space"
#define FEATURE_AUTOCORRECT "xbu_char_autocorrect_v1"
#define FEATURE_SWIPE_TYPING "xc0_swipe_typing_v1"
#define FEATURE_EMBED_MIXING "char_embed_mixing_v1"

#define FEATURE_ENCODER "experiment_linear_208_209_210"
#define FEATURE_ENCODER_W_X_ID 208
#define FEATURE_ENCODER_W_Y_ID 209
#define FEATURE_ENCODER_B_ID 210

class LanguageModel;

#define LLAMA_CONTEXT_SIZE 2048
class LlamaAdapter {
public:
    int getVocabSize() const;
    const char *getToken(int id) const;
    bool eval(int nPast, token_sequence input, std::vector<float> &outLogits);
    std::vector<int> tokenize(const char *text);
    int tokenToId(const char *text);
    std::string decode(const token_sequence &tokens) const;

    static LanguageModel *createLanguageModel(const std::string &paths);
    llama_context *context{};
    llama_model *model{};
    llama_batch batch{};

    std::vector<float> embeddings;

    std::vector<float> encoder_weight = {};
    std::vector<float> encoder_bias = {};

    int n_batch{};

    ModelMetadata metadata;

    inline bool hasFeature(const std::string &feature) const {
        return metadata.HasFeature(feature);
    }

    ~LlamaAdapter();

private:
    LlamaAdapter();

    sentencepiece::SentencePieceProcessor spm;
};


class LanguageModel {
public:
    explicit LanguageModel(LlamaAdapter *adapter);

    // Tokenizes the given text to tokens
    AK_FORCE_INLINE std::vector<int> tokenize(const char *text) const {
        return adapter->tokenize(text);
    }
    AK_FORCE_INLINE std::vector<int> tokenize(const std::string &text) const {
        return tokenize(text.c_str());
    }
    AK_FORCE_INLINE int tokenToId(const char *text) const {
        return adapter->tokenToId(text);
    }

    AK_FORCE_INLINE std::string decode(const token_sequence &tokens) const {
        return adapter->decode(tokens);
    }

    // Fast forward the context
    AK_FORCE_INLINE void updateContext(const std::vector<int> &newContext) {
        auto result = transformer_context_fastforward(transformerContext, newContext);
        pendingEvaluationSequence = result.first;
        pendingNPast = result.second;

        pendingContext = newContext;
    }
    AK_FORCE_INLINE void updateContext(const char *text) {
        return updateContext(tokenize(text));
    }

    AK_FORCE_INLINE void pushToContext(int token) {
        pendingContext.push_back(token);
        updateContext(pendingContext);
    }

    // TODO: This method returns a copy of 128kB of data
    AK_FORCE_INLINE std::vector<float> infer() {
        if(pendingEvaluationSequence.empty()){
            AKLOGI("LanguageModel: evaluation skipped due to empty pending evaluation sequence\n");
            return outLogits;
        }

        if(!adapter->eval(pendingNPast, pendingEvaluationSequence, outLogits)) {
            ASSERT(false);
        }

        transformer_context_apply(transformerContext, {pendingEvaluationSequence, pendingNPast});

        pendingEvaluationSequence.clear();

        return outLogits;
    }

    // Infers the given tokens on top of the active context without updating cache.
    // TODO: This method returns a copy of 128kB of data
    AK_FORCE_INLINE std::vector<float> temporarilyInfer(const std::vector<int> &tokens) {
        ASSERT(pendingEvaluationSequence.empty());
        ASSERT(!tokens.empty());

        if(!adapter->eval(transformerContext.active_context.size(), tokens, tmpOutLogits)) {
            ASSERT(false);
        }

        return tmpOutLogits;
    }

    AK_FORCE_INLINE int getVocabSize() const {
        return adapter->getVocabSize();
    }

    AK_FORCE_INLINE const char *getToken(int token) const {
        return adapter->getToken(token);
    }

    AK_FORCE_INLINE bool isPendingEvaluation() const {
        return pendingEvaluationSequence.size() > 0;
    }

    AK_FORCE_INLINE llama_context *context() const {
        return adapter->context;
    }

    AK_FORCE_INLINE llama_model *model() const {
        return adapter->model;
    }

    std::unique_ptr<LlamaAdapter> adapter;
    transformer_context transformerContext;
private:
    token_sequence pendingContext;
    token_sequence pendingEvaluationSequence;
    int pendingNPast = 0;

    std::vector<float> outLogits;
    std::vector<float> tmpOutLogits;

    std::unordered_set<int> punctIds;
};

#endif //LATINIME_LANGUAGEMODEL_H
