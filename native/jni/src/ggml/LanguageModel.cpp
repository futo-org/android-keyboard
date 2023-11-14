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
    ASSERT(nPast + input.size() < LLAMA_CONTEXT_SIZE);

    if(llama_eval(context, input.data(), input.size(), nPast) != 0) {
        return false;
    }

    // TODO: Zero-copy
    outLogits.resize(llama_n_vocab(model));
    memcpy(outLogits.data(), llama_get_logits(context), llama_n_vocab(model) * sizeof(float));

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
    ctx_params.n_ctx = LLAMA_CONTEXT_SIZE;
    ctx_params.n_threads = 1;
    ctx_params.n_threads_batch = 1;

    llama_model_params model_params = llama_model_default_params();

    adapter->model = llama_load_model_from_file(modelPath.c_str(), model_params);

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

    adapter->batch = llama_batch_init(LLAMA_CONTEXT_SIZE, 0, 1);

    return new LanguageModel(adapter);
}

LlamaAdapter::LlamaAdapter() = default;
