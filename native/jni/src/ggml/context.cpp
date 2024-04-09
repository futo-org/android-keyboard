#include "context.h"


std::pair<token_sequence, token_sequence::size_type> transformer_context_fastforward(const transformer_context &ctx, const token_sequence &next_context, bool allow_empty) {
    int npast = 0;

    // Compare the two sequences and find the first index at which they differ.
    int max_length = std::min(ctx.active_context.size(), next_context.size());
    for(int i=0; i<max_length; i++) {
        if(ctx.active_context[i] != next_context[i]) {
            break;
        }
        npast = i + 1;
    }

    if(!allow_empty) {
        // Handle the case when we have a shorter input than active context, requiring the last
        // token to be recomputed to get up-to-date logits
        if ((npast == (int)next_context.size()) && (next_context.size() <= ctx.active_context.size())) {
            npast -= 1;
        }
    }

    // If next_context is empty and allow_empty==false, npast may be -1 at this point
    if(npast < 0) {
        npast = 0;
    }

    token_sequence new_context(next_context.size() - npast);
    new_context.assign(next_context.begin() + npast, next_context.end());

    return {new_context, npast};
}


void transformer_context_apply(transformer_context &ctx, const std::pair<token_sequence, int> &fastforward_info) {
    ctx.active_context.resize(fastforward_info.second);

    for(auto i : fastforward_info.first) {
        ctx.active_context.emplace_back(i);
    }
}