//
// Created by alex on 7/24/23.
//

#ifndef LATINIME_LANGUAGEMODEL_H
#define LATINIME_LANGUAGEMODEL_H

#include <vector>
#include <unordered_set>
#include "context.h"
#include "defines.h"
#include "gpt_neox.h"

class LanguageModelAdapter {
public:
    int numThreads = 4;

    virtual int getVocabSize() const = 0;
    virtual const char *getToken(int id) const = 0;
    virtual bool eval(int nPast, token_sequence input, std::vector<float> &outLogits) = 0;

    virtual std::vector<int> tokenize(const char *text) = 0;
    virtual std::string decode(const token_sequence &tokens) const = 0;

    virtual ~LanguageModelAdapter() = 0;
};

class LanguageModel {
public:
    LanguageModel(LanguageModelAdapter *adapter);

    // Tokenizes the given text to tokens
    AK_FORCE_INLINE std::vector<int> tokenize(const char *text) const {
        return adapter->tokenize(text);
    }
    AK_FORCE_INLINE std::vector<int> tokenize(const std::string &text) const {
        return tokenize(text.c_str());
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
private:
    token_sequence pendingContext;
    token_sequence pendingEvaluationSequence;
    int pendingNPast = 0;

    LanguageModelAdapter *adapter;

    transformer_context transformerContext;

    std::vector<float> outLogits;
    std::vector<float> tmpOutLogits;

    std::unordered_set<int> punctIds;
};


class GPTNeoXAdapter : public LanguageModelAdapter {
public:
    int getVocabSize() const;
    const char *getToken(int id) const;
    bool eval(int nPast, token_sequence input, std::vector<float> &outLogits);
    virtual std::vector<int> tokenize(const char *text);
    virtual std::string decode(const token_sequence &tokens) const;

    static LanguageModel *createLanguageModel(const char *path);
private:
    GPTNeoXAdapter();
    gpt_vocab vocab;
    gpt_neox_model model;

    size_t memPerToken = 0;
};

#endif //LATINIME_LANGUAGEMODEL_H
