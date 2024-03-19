#ifndef LATINIME_FINETUNE_H
#define LATINIME_FINETUNE_H

#include "ggml.h"
#include "ggml-alloc.h"
#include "llama.h"
#include "common.h"
#include "train.h"

struct train_params {
    struct train_params_common common;

    std::vector<std::vector<llama_token>> training_data;

    const char * fn_model_base;
    const char * fn_lora_out;

    bool only_write_lora;

    float f_norm_rms_eps;
    float rope_freq_base;
    float rope_freq_scale;

    bool custom_f_norm_rms_eps;
    bool custom_rope_freq_base;
    bool custom_rope_freq_scale;

    int32_t lora_r;
    int32_t lora_alpha;
    bool custom_lora_alpha;

    uint32_t n_rank_attention_norm;
    uint32_t n_rank_wq;
    uint32_t n_rank_wk;
    uint32_t n_rank_wv;
    uint32_t n_rank_wo;
    uint32_t n_rank_ffn_norm;
    uint32_t n_rank_w1;
    uint32_t n_rank_w2;
    uint32_t n_rank_w3;
    uint32_t n_rank_tok_embeddings;
    uint32_t n_rank_norm;
    uint32_t n_rank_output;

    bool custom_n_rank_attention_norm;
    bool custom_n_rank_wq;
    bool custom_n_rank_wk;
    bool custom_n_rank_wv;
    bool custom_n_rank_wo;
    bool custom_n_rank_ffn_norm;
    bool custom_n_rank_w1;
    bool custom_n_rank_w2;
    bool custom_n_rank_w3;
    bool custom_n_rank_tok_embeddings;
    bool custom_n_rank_norm;
    bool custom_n_rank_output;
};

static struct train_params get_default_train_params() {
    struct train_params params;
    params.common = get_default_train_params_common();
    params.fn_model_base     = "";
    params.fn_lora_out       = "ggml-lora-ITERATION-f32.gguf";

    params.only_write_lora = false;

    params.f_norm_rms_eps  = 1e-5f;
    params.rope_freq_base  = 10000.0f;
    params.rope_freq_scale = 1.0f;

    params.custom_f_norm_rms_eps  = false;
    params.custom_rope_freq_base  = false;
    params.custom_rope_freq_scale = false;

    params.lora_r      = 4;
    params.lora_alpha  = 4;
    params.custom_lora_alpha = false;

    params.n_rank_attention_norm = 1;
    params.n_rank_wq             = 4;
    params.n_rank_wk             = 4;
    params.n_rank_wv             = 4;
    params.n_rank_wo             = 4;
    params.n_rank_ffn_norm       = 1;
    params.n_rank_w1             = 4;
    params.n_rank_w2             = 4;
    params.n_rank_w3             = 4;
    params.n_rank_tok_embeddings = 4;
    params.n_rank_norm           = 1;
    params.n_rank_output         = 4;

    params.custom_n_rank_attention_norm = false;
    params.custom_n_rank_wq             = false;
    params.custom_n_rank_wk             = false;
    params.custom_n_rank_wv             = false;
    params.custom_n_rank_wo             = false;
    params.custom_n_rank_ffn_norm       = false;
    params.custom_n_rank_w1             = false;
    params.custom_n_rank_w2             = false;
    params.custom_n_rank_w3             = false;
    params.custom_n_rank_tok_embeddings = false;
    params.custom_n_rank_norm           = false;
    params.custom_n_rank_output         = false;

    return params;
}

int finetune_train(struct train_params params);

#endif //LATINIME_FINETUNE_H
