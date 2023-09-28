//
// Created by alex on 7/24/23.
//

#include <sentencepiece/sentencepiece_processor.h>
#include "LanguageModel.h"

LanguageModelAdapter::~LanguageModelAdapter() {};

LanguageModel::LanguageModel(LanguageModelAdapter *adapter): adapter(adapter) { }


int LlamaAdapter::getVocabSize() const {
    // assert(modelVocabSize >= sentencepieceVocabSize)
    
    return spm.GetPieceSize();
}

const char *LlamaAdapter::getToken(int id) const {
    return spm.IdToPiece(id).c_str();
}

bool LlamaAdapter::eval(int nPast, token_sequence input, std::vector<float> &outLogits) {
    // TODO
    ASSERT(nPast + input.size() < llama_model_n_ctx(model));

    if(llama_eval(context, input.data(), input.size(), nPast, numThreads) != 0) {
        return false;
    }

    // TODO: Zero-copy
    outLogits.resize(llama_n_vocab(context));
    memcpy(outLogits.data(), llama_get_logits(context), llama_n_vocab(context) * sizeof(float));

    return true;
}

std::vector<int> LlamaAdapter::tokenize(const char *text) {
    return spm.EncodeAsIds(text);
}

int LlamaAdapter::tokenToId(const char *text) {
    return spm.PieceToId(text);
}

std::string LlamaAdapter::decode(const token_sequence &tokens) const {
    return spm.DecodeIds(tokens);
}

LanguageModel *LlamaAdapter::createLanguageModel(const std::string &paths) {
    std::string modelPath = paths.substr(0,paths.find(':'));
    std::string tokenizerPath = paths.substr(paths.find(':') + 1);

    auto adapter = new LlamaAdapter();

    llama_context_params ctx_params = llama_context_default_params();

    adapter->model = llama_load_model_from_file(modelPath.c_str(), ctx_params);

    if(adapter->model == nullptr) {
        delete adapter;
        return nullptr;
    }

    adapter->context = llama_new_context_with_model(adapter->model, ctx_params);

    //adapter->spm = sentencepiece::SentencePieceProcessor();
    auto spm_load_result = adapter->spm.Load(tokenizerPath);
    if(!spm_load_result.ok()) {
        llama_free(adapter->context);
        llama_free_model(adapter->model);
        delete adapter;
        return nullptr;
    }

    return new LanguageModel(adapter);
}

LlamaAdapter::LlamaAdapter() = default;
