//
// Created by alex on 7/24/23.
//

#include "LanguageModel.h"

LanguageModelAdapter::~LanguageModelAdapter() {};

LanguageModel::LanguageModel(LanguageModelAdapter *adapter): adapter(adapter) {

}

int GPTNeoXAdapter::getVocabSize() const {
    return model.hparams.n_vocab;
}

const char *GPTNeoXAdapter::getToken(int id) const {
    return vocab.id_to_token.at(id).c_str();
}

bool GPTNeoXAdapter::eval(int nPast, token_sequence input, std::vector<float> &outLogits) {
    // TODO
    ASSERT(nPast + input.size() < model.hparams.n_ctx);

    return gpt_neox_eval(model, numThreads, nPast, input, outLogits, memPerToken);
}

std::vector<int> GPTNeoXAdapter::tokenize(const char *text) {
    return gpt_tokenize(vocab, text);
}

std::string GPTNeoXAdapter::decode(const token_sequence &tokens) const {
    // For now we just merge the tokens together, this may need to be different for other languages and unicode
    size_t length = 0;
    for(int token : tokens) length += strlen(getToken(token));

    std::string result(length);
    for(int token : tokens) result.append(getToken(token));

    return result;
}

LanguageModel *GPTNeoXAdapter::createLanguageModel(const char *path) {
    auto adapter = new GPTNeoXAdapter();

    bool result = gpt_neox_model_load(path, adapter->model, adapter->vocab);
    if(!result) {
        delete adapter;
        return nullptr;
    }

    return new LanguageModel(adapter);
}

GPTNeoXAdapter::GPTNeoXAdapter() = default;
