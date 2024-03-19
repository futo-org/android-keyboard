#pragma once

#include <vector>

typedef std::vector<int> token_sequence;

struct transformer_context {
    token_sequence active_context;
};

std::pair<token_sequence, token_sequence::size_type> transformer_context_fastforward(const transformer_context &ctx, const token_sequence &next_context, bool allow_empty = false);
void transformer_context_apply(transformer_context &ctx, const std::pair<token_sequence, int> &fastforward_info);