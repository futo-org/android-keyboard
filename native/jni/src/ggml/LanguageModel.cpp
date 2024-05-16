//
// Created by alex on 7/24/23.
//

#include <sentencepiece/sentencepiece_processor.h>
#include "LanguageModel.h"
#include "ModelMeta.h"

LanguageModel::LanguageModel(LlamaAdapter *adapter): adapter(adapter) { }

LlamaAdapter::~LlamaAdapter() {
    llama_free_model(model);
    llama_free(context);
}

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

LanguageModel *LlamaAdapter::createLanguageModel(const std::string &modelPath) {
    auto adapter = new LlamaAdapter();
    adapter->metadata = loadModelMetadata(modelPath);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = LLAMA_CONTEXT_SIZE;
    ctx_params.n_threads = 1;
    ctx_params.n_threads_batch = 1;

    adapter->n_batch = ctx_params.n_batch;

    llama_model_params model_params = llama_model_default_params();

    adapter->model = llama_load_model_from_file(modelPath.c_str(), model_params);

    if(adapter->model == nullptr) {
        delete adapter;
        return nullptr;
    }

    adapter->context = llama_new_context_with_model(adapter->model, ctx_params);

    if(adapter->metadata.ext_tokenizer_type == ExternalTokenizerType::SentencePiece) {
        auto spm_load_result = adapter->spm.LoadFromSerializedProto(adapter->metadata.ext_tokenizer_data);
        if(!spm_load_result.ok()) {
            AKLOGE("SPM load failed: %s", spm_load_result.ToString().c_str());
            llama_free(adapter->context);
            llama_free_model(adapter->model);
            delete adapter;
            return nullptr;
        }
    } else {
        AKLOGE("TODO: Non SPM models");
        llama_free(adapter->context);
        llama_free_model(adapter->model);
        delete adapter;
        return nullptr;
    }

    adapter->batch = llama_batch_init(LLAMA_CONTEXT_SIZE, 0, 1);

    if(adapter->metadata.HasFeature(FEATURE_EMBED_MIXING)) {
        adapter->embeddings.resize(llama_n_embd(adapter->model) * llama_n_vocab(adapter->model));

        auto tensor = llama_get_model_tensor(adapter->model, "token_embd.weight");
        ASSERT(tensor);

        if (tensor->type != GGML_TYPE_F32) {
            ggml_internal_get_type_traits(tensor->type).to_float(tensor->data,
                                                                 adapter->embeddings.data(),
                                                                 adapter->embeddings.size());
        } else {
            ASSERT((tensor->ne[0] * tensor->ne[1]) == adapter->embeddings.size());
            memcpy(adapter->embeddings.data(), tensor->data,
                   adapter->embeddings.size() * sizeof(float));
        }
    }

    if(adapter->metadata.HasFeature(FEATURE_ENCODER)) {
        adapter->encoder_weight.resize(llama_n_embd(adapter->model) * 2);
        adapter->encoder_bias.resize(llama_n_embd(adapter->model));

        for(int i = 0; i < llama_n_embd(adapter->model); i++) {
            adapter->encoder_weight[i*2]     = adapter->embeddings.data()[FEATURE_ENCODER_W_X_ID * llama_n_embd(adapter->model) + i];
            adapter->encoder_weight[i*2 + 1] = adapter->embeddings.data()[FEATURE_ENCODER_W_Y_ID * llama_n_embd(adapter->model) + i];
            adapter->encoder_bias[i]         = adapter->embeddings.data()[FEATURE_ENCODER_B_ID   * llama_n_embd(adapter->model) + i];
        }
    }

    return new LanguageModel(adapter);
}

LlamaAdapter::LlamaAdapter() = default;
