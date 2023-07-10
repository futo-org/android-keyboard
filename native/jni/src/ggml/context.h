#pragma once

#include <vector>

#include "common.h"

struct transformer_context {
    token_sequence active_context;
};

std::pair<token_sequence, int> transformer_context_fastforward(const transformer_context &ctx, const token_sequence &next_context);
void transformer_context_apply(transformer_context &ctx, const std::pair<token_sequence, int> &fastforward_info);