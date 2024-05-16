#define LOG_TAG "LatinIME: jni: LanguageModel"

#include "org_futo_inputmethod_latin_xlm_LanguageModel.h"

#include <cstring> // for memset()
#include <vector>

#include "jni.h"
#include "jni_common.h"
#include "ggml/LanguageModel.h"
#include "defines.h"
#include "suggest/core/layout/proximity_info.h"
#include "jni_utils.h"

#define EPS 0.0001

#if false
#define TIME_START(name)  const int64_t start_##name = ggml_time_us();
#define TIME_END(name)    const int64_t end_##name = ggml_time_us(); \
                          const int64_t time_taken_##name = (end_##name - start_##name) / 1000L; \
                          AKLOGI("%s:     Time taken by %s: %d ms\n", __func__, #name, (int)time_taken_##name);
#else
#define TIME_START(name)
#define TIME_END(name)
#endif

#define RETURNVAL_AUTOCORRECT "autocorrect"
#define RETURNVAL_UNCERTAIN "uncertain"
#define RETURNVAL_CLUELESS "clueless"

static std::string trim(const std::string &s) {
    auto start = s.begin();
    while (start != s.end() && std::isspace(*start)) {
        start++;
    }

    auto end = s.end();
    do {
        end--;
    } while (std::distance(start, end) > 0 && std::isspace(*end));

    return {start, end + 1};
}

template<typename T>
bool sortProbabilityPairDescending(const std::pair<float, T>& a, const std::pair<float, T>& b) {
    return a.first > b.first;
}

template<typename T>
static inline void sortProbabilityPairVectorDescending(std::vector<std::pair<float, T>> &vec) {
    std::sort(vec.begin(), vec.end(), sortProbabilityPairDescending<T>);
}

template<typename T>
static inline void sortProbabilityPairVectorDescending(std::vector<std::pair<float, T>> &vec, size_t partial) {
    if(partial > vec.size()) partial = vec.size();
    std::partial_sort(vec.begin(), vec.begin() + partial, vec.end(), sortProbabilityPairDescending<T>);
}

typedef struct potential_sequence_data {
    token_sequence tokens;
    llama_seq_id seq_id{};
} potential_sequence_data;

// P = P(tokens[0]) * P(tokens[1]) * [...]
typedef std::pair<float, potential_sequence_data> potential_sequence;


typedef struct banned_sequence {
    token_sequence sequence;
    int hash;
} banned_sequence;

int compute_sequence_hash(const token_sequence &seq) {
    int hash = 0;
    for(llama_token t : seq) {
        hash = (hash + t) % 999999999;
    }
    return hash;
}

int append_sequence_hash(int hash, llama_token t) {
    return (hash + t) % 999999999;
}


static void softmax(float * input, size_t input_len) {
    float m = -INFINITY;
    for (size_t i = 0; i < input_len; i++) {
        if (input[i] > m) {
            m = input[i];
        }
    }

    float sum = 0.0;
    for (size_t i = 0; i < input_len; i++) {
        sum += expf(input[i] - m);
    }

    float offset = m + logf(sum);
    for (size_t i = 0; i < input_len; i++) {
        input[i] = expf(input[i] - offset);
    }
}

#define NUM_TOKEN_MIX 4
struct TokenMix {
    float x;
    float y;
    struct {
        float weight;
        llama_token token;
    } mixes[NUM_TOKEN_MIX];
};


struct DecodeResult {
    int logits_head;
    int size;
};

enum WordCapitalizeMode {
    IgnoredCapitals, // partialWord = "t"  or partialWord = "test"
    FirstCapital,    // partialWord = "T"  or partialWord = "Test"
    AllCapitals      // partialWord = "TE" or partialWord = "TEST"
};


bool isFirstCharLowercase(const char* str) {
    if (str == nullptr || str[0] == '\0')
        return false;
    return islower(static_cast<unsigned char>(str[0])) != 0;
}


bool hasLowercase(const char* str) {
    if (str == nullptr)
        return false;

    for (; *str != '\0'; ++str) {
        if (islower(static_cast<unsigned char>(*str)))
            return true;
    }
    return false;
}

bool isExactMatch(const std::string &a, const std::string &b){
    auto preprocess = [](const std::string &str) -> std::string {
        std::string result;
        for(char c : str) {
            if(c != '\'' && c != '-' && c != ' ') {
                result += (char)tolower(c);
            }
        }
        return result;
    };

    return preprocess(a) == preprocess(b);
}


struct LanguageModelState {
    std::unique_ptr<LanguageModel> model;

    struct {
        int SPACE = 0;

        int XBU = 0;
        int XBC = 0;
        int XEC = 0;

        int XC0_SWIPE_MODE = 0;

        int DASH = 0;
        int STAR = 0;

        int LETTERS_TO_IDS[26] = { 0 };

        std::vector<int> banned_start_of_word_tokens;
        std::vector<int> banned_tokens_for_first_capital;
        std::vector<int> banned_tokens_for_all_capitals;
        std::vector<int> banned_tokens_word_separators; // probabilities add to space token
        std::vector<int> general_banned_tokens;
    } specialTokens;

    bool Initialize(const std::string &paths){
        model = std::unique_ptr<LanguageModel>(LlamaAdapter::createLanguageModel(paths));

        if(!model) {
            AKLOGE("GGMLDict: Could not load model");
            return false;
        }

        specialTokens.SPACE = model->tokenToId("▁"); // ▁
        specialTokens.DASH = model->tokenToId("-");
        specialTokens.STAR = model->tokenToId("*");

        if(model->adapter->hasFeature(FEATURE_AUTOCORRECT)) {
            specialTokens.XBU = model->tokenToId("<XBU>");
            specialTokens.XBC = model->tokenToId("<XBC>");
            specialTokens.XEC = model->tokenToId("<XEC>");

            specialTokens.LETTERS_TO_IDS[0] = model->tokenToId("<CHAR_A>");

            ASSERT(specialTokens.XBU != 0);
            ASSERT(specialTokens.XBC != 0);
            ASSERT(specialTokens.XEC != 0);
            ASSERT(specialTokens.LETTERS_TO_IDS[0] != 0);

            for(int i = 1; i < 26; i++) {
                specialTokens.LETTERS_TO_IDS[i] = specialTokens.LETTERS_TO_IDS[0] + i;
            }

            if(model->adapter->hasFeature(FEATURE_SWIPE_TYPING)) {
                specialTokens.XC0_SWIPE_MODE = model->tokenToId("<XC0>");
                ASSERT(specialTokens.XC0_SWIPE_MODE != 0);
            }
        } else {
            specialTokens.XBU = -1;
            specialTokens.XBC = -1;
            specialTokens.XEC = -1;
        }

        specialTokens.banned_tokens_word_separators = { };
        specialTokens.general_banned_tokens = { model->tokenToId("-▁") };

        //int permitted_period_token = model->tokenToId(".");

        const char *blacklist_symbols = ".!@#$%^&*()_=?/,\\][{};:\"><+`~|\r\n\t\x0b\x0c";
        for(int i = 0; i < model->getVocabSize(); i++) {
            //if(i == permitted_period_token) continue;

            const char *token = model->getToken(i);

            bool has_symbol = false;
            for(char c : std::string(token)){
                if(strchr(blacklist_symbols, c) != nullptr) {
                    has_symbol = true;
                    break;
                }
            }

            if(has_symbol) {
                specialTokens.banned_tokens_word_separators.emplace_back(i);
            }
        }

        size_t n_vocab = llama_n_vocab(model->model());
        for(int i=0; i < (int)n_vocab; i++) {
            const char *text = model->adapter->getToken(i);
            if(isFirstCharLowercase(text)) {
                specialTokens.banned_tokens_for_first_capital.push_back(i);
                specialTokens.banned_tokens_for_all_capitals.push_back(i);
            }else if(hasLowercase(text)){
                specialTokens.banned_tokens_for_all_capitals.push_back(i);
            }

            if(text[0] == '\'' || text[0] == '-') {
                specialTokens.banned_start_of_word_tokens.push_back(i);
            }
        }

        return true;
    }

    bool transform_logits(float *logits, size_t n_vocab, bool is_first_token, bool allow_correction_token, WordCapitalizeMode capitals, llama_token prev_token){
        for(size_t i = 0; i < n_vocab; i++) {
            if(isnan(logits[i])){
                return false;
            }
        }

        softmax(logits, n_vocab);

        for(int x : specialTokens.banned_tokens_word_separators) {
            if(allow_correction_token && x == specialTokens.XEC) continue;

            logits[specialTokens.SPACE] += std::max(0.0f, logits[x]);
            logits[x] = -999.0f;
        }

        if(is_first_token) {
            logits[specialTokens.SPACE] = -999.0f;

            for(int i : specialTokens.banned_start_of_word_tokens) {
                logits[i] = -999.0f;
            }
        }

        for(int i : specialTokens.general_banned_tokens) {
            logits[i] = -999.0f;
        }

        if(prev_token == specialTokens.DASH) {
            logits[specialTokens.DASH] = -999.0f;
        }

        if(capitals == WordCapitalizeMode::FirstCapital && is_first_token) {
            for(int i : specialTokens.banned_tokens_for_first_capital) {
                logits[i] = -999.0f;
            }
        }else if(capitals == WordCapitalizeMode::AllCapitals) {
            // Note: In case the word is something like "AMD's" we may not wish to ban lowercase completely
            for(int i : specialTokens.banned_tokens_for_all_capitals) {
                logits[i] = -999.0f;
            }
        }
        return true;
    }

    std::vector<TokenMix> past_mixes = { };
    int GetCachedMixAmount(const std::vector<TokenMix> &mixes) {
        TIME_START(GetcachedMixAmount)
        size_t i;
        for(i = 0; i < std::min(past_mixes.size(), mixes.size()); i++) {
            if(std::abs(past_mixes[i].x - mixes[i].x) >= EPS) break;
            if(std::abs(past_mixes[i].y - mixes[i].y) >= EPS) break;
        }

        TIME_END(GetcachedMixAmount)

        return (int)i;
    }

    DecodeResult DecodePromptAndMixes(const token_sequence &prompt, const std::vector<TokenMix> &mixes) {
        TIME_START(PromptDecode)
        llama_context *ctx = model->context();
        llama_batch batch = model->adapter->batch;
        LlamaAdapter *llamaAdapter = model->adapter.get();

        size_t n_embd = llama_n_embd(llama_get_model(ctx));
        size_t n_vocab = llama_n_vocab(llama_get_model(ctx));

        auto prompt_ff = transformer_context_fastforward(model->transformerContext, prompt, !mixes.empty());

        int n_batch = llamaAdapter->n_batch;

        int head = -1;
        if(!prompt_ff.first.empty()) {
            for (size_t b = 0; b < (prompt_ff.first.size() + n_batch - 1) / n_batch; b++) {
                batch.n_tokens = std::min((int)n_batch, (int)(prompt_ff.first.size() - b*n_batch));
                for (int i = 0; i < batch.n_tokens; i++) {
                    batch.token[i] = prompt_ff.first[n_batch*b + i];
                    batch.pos[i] = (llama_pos)(prompt_ff.second + n_batch*b + i);
                    batch.seq_id[i][0] = 0;
                    batch.n_seq_id[i] = 1;
                    batch.logits[i] = false;
                }

                batch.logits[batch.n_tokens - 1] = (int8_t)(mixes.empty());
                if(mixes.empty()) head = batch.n_tokens - 1;

                llama_kv_cache_seq_rm(ctx, 0, (llama_pos)prompt_ff.second, -1);

                if (llama_decode(ctx, batch) != 0) {
                    AKLOGE("llama_decode() failed");
                    return {};
                }
            }
        } else {
            //AKLOGI("No need to recompute prompt, proceeding to mixes");
        }

        transformer_context_apply(model->transformerContext, prompt_ff);
        TIME_END(PromptDecode)

        TIME_START(EmbedMixing)
        size_t size = prompt.size();

        std::vector<float> embeds;

        bool useEncoder = !llamaAdapter->encoder_weight.empty();
        //AKLOGI("DecodePromptAndMixes: useEncoder=%d", useEncoder);

        for(auto &mix : mixes) {

            int num_added = 0;

            std::vector<float> mix_f(n_embd, 0.0f);

            if(useEncoder) {
                num_added = 1;

                for(size_t i=0; i<n_embd; i++) {
                    mix_f[i] = llamaAdapter->encoder_bias[i]
                            + llamaAdapter->encoder_weight[i*2]*mix.x
                            + llamaAdapter->encoder_weight[i*2 + 1]*mix.y;
                }

                //AKLOGI("DEBUG: pos %.4f %.4f got this: [%.4f %.4f %.4f %.4f %.4f %.4f %.4f ...",
                //       mix.x, mix.y,
                //             mix_f[0], mix_f[1], mix_f[2], mix_f[3], mix_f[4], mix_f[5], mix_f[6]);
            } else {
                for (auto &t: mix.mixes) {
                    if (t.weight < EPS) continue;
                    if (t.token < 0 || t.token >= (int)n_vocab) continue;

                    float *src = llamaAdapter->embeddings.data() +
                                 (t.token * n_embd);
                    float weight = t.weight;

                    for (size_t i = 0; i < n_embd; i++) {
                        mix_f[i] += src[i] * weight;
                    }

                    num_added++;
                }
            }

            if(num_added == 0){
                AKLOGE("Somehow a token mix had 0 weight for everything");
                ASSERT(false);
            }

            embeds.insert(embeds.end(), mix_f.begin(), mix_f.end());
            size++;
        }
        TIME_END(EmbedMixing)

        TIME_START(CachedMixAmount)
        int n_tokens = int32_t(mixes.size());
        int n_past = GetCachedMixAmount(mixes);
        past_mixes = mixes;

        if(!prompt_ff.first.empty()) n_past = 0; // We have to recompute embeds completely if prompt changed
        llama_kv_cache_seq_rm(ctx, 0, (llama_pos)prompt.size() + n_past, -1);
        TIME_END(CachedMixAmount)

        if(!embeds.empty()) {
            TIME_START(DecodeEmbeds)
            // TODO: This is only processing one embd at a time, increasing n_tokens doesn't seem to work
            for(int h = n_past; h < n_tokens; h++ ) {
                llama_batch embd_batch = {
                        1,

                        nullptr,
                        embeds.data() + h*n_embd,
                        batch.pos,
                        batch.n_seq_id,
                        batch.seq_id,
                        batch.logits,

                        batch.all_pos_0,
                        batch.all_pos_1,
                        batch.all_seq_id
                };

                batch.pos[0] = (llama_pos)(prompt.size() + h);
                batch.seq_id[0][0] = 0;
                batch.n_seq_id[0] = 1;
                batch.logits[0] = false;

                if (llama_decode(ctx, embd_batch) != 0) {
                    AKLOGE("llama_decode() with embeds failed");
                    return {};
                }
            }
            TIME_END(DecodeEmbeds)

            TIME_START(DecodeXBC)

            // We always force an XBC token after
            size += 1;
            batch.n_tokens = 1;
            batch.token[0] = specialTokens.XBC;
            batch.seq_id[0][0] = 0;
            batch.n_seq_id[0] = 1;
            batch.logits[0] = true;
            batch.pos[0] = (llama_pos)(prompt.size() + n_tokens);
            head = 0;

            if (llama_decode(ctx, batch) != 0) {
                AKLOGE("llama_decode() for XBC failed");
                return {};
            }

            TIME_END(DecodeXBC)

            ASSERT(size == prompt.size() + n_tokens + 1);
            ASSERT(size == prompt.size() + (embeds.size() / n_embd) + 1);
        } else {
            ASSERT(size == prompt.size());
            //ASSERT(head == prompt_ff.first.size() - 1);
        }

        //AKLOGI("-- Decode");
        //AKLOGI("First we processed the prompt (%d):", prompt_ff.first.size());
        //for(auto t : prompt) {
        //    AKLOGI(" - [%s]", model->getToken(t));
        //}
        //AKLOGI("Then %d embeds (cached %d)", embeds.size(), n_past);
        //AKLOGI("The final size is %d and head is %d", size, head);

        TIME_START(FinishRm)

        llama_kv_cache_seq_rm(ctx, 0, (llama_pos)size, -1);

        TIME_END(FinishRm)
        return {
            head,
            (int)size
        };
    }

    bool MatchesBanned(const token_sequence &prior, int prior_hash, llama_token next, const std::vector<banned_sequence> &banned_sequences) const {
        int new_hash = append_sequence_hash(prior_hash, next);
        for(const auto &banned_sequence : banned_sequences) {
            if(banned_sequence.sequence.back() == specialTokens.STAR && (prior.size() >= banned_sequence.sequence.size() - 1)) {
                bool matches = true;
                for(size_t i = 0; i < banned_sequence.sequence.size() - 1; i++) {
                    if(prior[i] != banned_sequence.sequence[i]) {
                        matches = false;
                        break;
                    }
                }

                if(matches){
                    auto priorTxt = model->decode(prior);
                    auto nextTxt = model->decode({next});
                    auto bannedTxt = model->decode(banned_sequence.sequence);
                    //AKLOGI("Tokens [%s] + [%s] matches banned wildcard [%s]", priorTxt.c_str(), nextTxt.c_str(), bannedTxt.c_str());
                    return true;
                }
            }else if((banned_sequence.sequence.size() == prior.size() + 1) && (banned_sequence.hash == new_hash)) {
                if(banned_sequence.sequence.back() == next) {
                    bool matches = true;
                    for(size_t i = 0; i < prior.size(); i++) {
                        if(prior[i] != banned_sequence.sequence[i]) {
                            matches = false;
                            break;
                        }
                    }

                    if(matches) {
                        auto priorTxt = model->decode(prior);
                        auto nextTxt = model->decode({next});
                        auto bannedTxt = model->decode(banned_sequence.sequence);
                        //AKLOGI("Tokens [%s] + [%s] matches banned [%s]", priorTxt.c_str(), nextTxt.c_str(), bannedTxt.c_str());
                        return true;
                    }
                }
            }
        }

        return false;
    }

    std::vector<std::pair<float, token_sequence>> Sample(DecodeResult decodeResult, int n_results, WordCapitalizeMode capitals, const std::vector<banned_sequence> &banned_sequences) {
        llama_context *ctx = model->context();
        llama_batch batch = model->adapter->batch;

        size_t n_vocab = llama_n_vocab(llama_get_model(ctx));

        std::vector<potential_sequence> sequences;

        bool allow_correction_token = decodeResult.logits_head == 0;

        float *logits = llama_get_logits_ith(ctx, decodeResult.logits_head);
        //AKLOGI("Value of [the ] before transform: %f", logits[561]);

        bool is_bugged = logits[561] == 0.0f;

        if(!transform_logits(logits, n_vocab, true, allow_correction_token, capitals, 0)) {
            AKLOGE("logits have NaN!");
            return { };
        }

        // TODO: This should really not be here
        is_bugged = is_bugged && logits[561] < -990.0f && logits[561] > -1100.0f;
        if(is_bugged) {
            AKLOGE("Detected bug!!!! Trying to mitigate. Let's just reset cache and exit");
            llama_kv_cache_seq_rm(ctx, -1, -1, -1);
            model->transformerContext.active_context = { };
            return { };
        }

        //AKLOGI("Value of [the ] after transform: %f", logits[561]);

        std::vector<std::pair<float, int>> index_value;
        index_value.clear();
        for (size_t i = 0; i < n_vocab; i++) {
            index_value.emplace_back(logits[i], i);
        }


        sortProbabilityPairVectorDescending(index_value, n_results * 2);
        const token_sequence blank = {};
        for(int i = 0; i < n_results * 2; i++) {
            if(MatchesBanned(blank, 0, index_value[i].second, banned_sequences)) {
                index_value[i].first = 0.0f;
            }
        }
        sortProbabilityPairVectorDescending(index_value, n_results);

        sequences.reserve(n_results);
        for (int i = 0; i < n_results; i++) {
            sequences.emplace_back(
                    index_value[i].first,
                    potential_sequence_data {
                            {index_value[i].second},
                            i
                    }
            );
        }

        // TODO: This should really not be here
        is_bugged = true;
        for(const auto &seq : sequences) {
            if(seq.second.tokens.front() > 48 || seq.first != sequences[0].first) {
                is_bugged = false;
                break;
            }
        }
        if(is_bugged) {
            AKLOGE("Detected bug2!!!! Trying to mitigate. Let's just reset cache and exit");
            llama_kv_cache_seq_rm(ctx, -1, -1, -1);
            model->transformerContext.active_context = { };
            return { };
        }


        for (auto &sequence: sequences) {
            if (sequence.second.seq_id == 0) continue;

            llama_kv_cache_seq_cp(ctx, 0, sequence.second.seq_id, 0, decodeResult.size);
        }

        std::vector<potential_sequence> next_sequences;

        std::vector<std::pair<float, token_sequence>> outputs;

        for(int tok=0; tok<10; tok++) {
            next_sequences.clear();
            for (auto sequence: std::move(sequences)) {
                int next_token = sequence.second.tokens[sequence.second.tokens.size() - 1];

                // Check if this is the end of correction
                if (next_token == specialTokens.XEC) {
                    token_sequence resulting_tokens = std::move(sequence.second.tokens);
                    resulting_tokens.resize(resulting_tokens.size() - 1);
                    outputs.emplace_back(sequence.first, resulting_tokens);
                    continue;
                }

                // Check if this is the end of a word
                std::string token = model->getToken(next_token);
                if (token.size() >= 3 && (token[token.size() - 1] == '\x81') &&
                    (token[token.size() - 2] == '\x96') && token[token.size() - 3] == '\xe2') {
                    outputs.emplace_back(sequence.first, std::move(sequence.second.tokens));
                    continue;
                }

                next_sequences.emplace_back(sequence);
            }

            sequences = next_sequences;
            next_sequences.clear();

            size_t remaining_count = n_results - outputs.size();
            batch.n_tokens = 0;

            //for(int i=0; i<batch.n_tokens; i++) batch.logits[i] = false;
            for (auto &sequence: sequences) {
                batch.token[batch.n_tokens] = sequence.second.tokens[sequence.second.tokens.size() - 1];
                batch.pos[batch.n_tokens] = (llama_pos)(decodeResult.size + (sequence.second.tokens.size() - 1));
                batch.seq_id[batch.n_tokens][0] = sequence.second.seq_id;
                batch.n_seq_id[batch.n_tokens] = 1;
                batch.logits[batch.n_tokens] = true;

                batch.n_tokens += 1;
            }

            ASSERT(batch.n_tokens == (int)remaining_count); // usually 3

            if (batch.n_tokens == 0) {
                break;
            }

            llama_decode(ctx, batch);

            for (int seq = 0; seq < (int)remaining_count; seq++) {
                const potential_sequence &parent_seq = sequences[seq];
                auto hash = compute_sequence_hash(parent_seq.second.tokens);

                llama_token prev_token = 0;
                if(!parent_seq.second.tokens.empty()) prev_token = parent_seq.second.tokens.back();

                logits = llama_get_logits_ith(ctx, seq);
                if(!transform_logits(logits, n_vocab, false, allow_correction_token, capitals, prev_token)) {
                    AKLOGE("Logits have NaN!");
                    return { };
                }

                index_value.clear();
                for (size_t i = 0; i < n_vocab; i++) {
                    index_value.emplace_back(logits[i], i);
                }


                sortProbabilityPairVectorDescending(index_value, remaining_count * 2);
                for(size_t i = 0; i < remaining_count * 2; i++) {
                    if(MatchesBanned(parent_seq.second.tokens, hash, index_value[i].second, banned_sequences)) {
                        index_value[i].first = 0.0f;
                    }
                }
                sortProbabilityPairVectorDescending(index_value, remaining_count);

                for (size_t i = 0; i < remaining_count; i++) {
                    token_sequence new_sequence = parent_seq.second.tokens;
                    new_sequence.push_back(index_value[i].second);

                    if (index_value[i].first > 1.0f || index_value[i].first < 0.0f) {
                        AKLOGE("Expected index_value to be probability [%.2f]",
                               index_value[i].first);
                    }

                    if (sequences[i].first > 1.0f || sequences[i].first < 0.0f) {
                        AKLOGE("Expected sequences value to be probability [%.2f]",
                               sequences[i].first);
                    }

                    next_sequences.emplace_back(
                            index_value[i].first * sequences[i].first,
                            potential_sequence_data{
                                    new_sequence,
                                    parent_seq.second.seq_id
                            }
                    );
                }
            }

            sortProbabilityPairVectorDescending(next_sequences, remaining_count);
            next_sequences.resize(remaining_count);
            sequences.clear();

            // In some cases we may have picked a sequence from the same parent sequence
            // We must re-assign the seq_id
            int seq_id_use_count[n_results];
            for (int i = 0; i < n_results; i++) seq_id_use_count[i] = 0;

            for (auto &seq: next_sequences) seq_id_use_count[seq.second.seq_id] += 1;

            for (auto &seq: next_sequences) {
                if (seq_id_use_count[seq.second.seq_id] > 1) {
                    int old_seq_id = seq.second.seq_id;

                    int new_seq_id = -1;
                    for (int i = 0; i < n_results; i++) {
                        if (seq_id_use_count[i] == 0) {
                            new_seq_id = i;
                            break;
                        }
                    }

                    if (new_seq_id == -1) {
                        AKLOGE("Couldn't find an empty sequence id to use. This should never happen.");
                        return {};
                    }

                    seq_id_use_count[old_seq_id]--;
                    seq_id_use_count[new_seq_id]++;

                    llama_kv_cache_seq_cp(
                            ctx,
                            old_seq_id,
                            new_seq_id,
                            0, // could start from prompt.size()
                            (llama_pos)(decodeResult.size + (seq.second.tokens.size() - 1))
                    );

                    seq.second.seq_id = new_seq_id;
                }
            }

            sequences = next_sequences;
        }

        for (int i = 1; i < n_results; i++) {
            llama_kv_cache_seq_rm(ctx, i, 0, -1);
        }

        return outputs;
    }

    std::vector<std::pair<float, std::string>> PredictNextWord(const std::string &context, const std::vector<std::string> &banned_words) {
        std::vector<banned_sequence> banned_sequences;
        for(const std::string &bw : banned_words) {
            auto tokenized = model->tokenize(trim(bw) + " ");
            banned_sequences.push_back({ tokenized, compute_sequence_hash(tokenized) });

            auto tokenized2 = model->tokenize(trim(bw));
            banned_sequences.push_back({ tokenized2, compute_sequence_hash(tokenized2) });
        }

        token_sequence next_context = model->tokenize(trim(context) + " ");
        next_context.insert(next_context.begin(), 1); // BOS

        auto decoding_result = DecodePromptAndMixes(next_context, { });
        auto results = Sample(decoding_result, 3, WordCapitalizeMode::IgnoredCapitals, banned_sequences);

        std::vector<std::pair<float, std::string>> str_results;
        str_results.reserve(results.size());
        for(const auto& result : results) {
            str_results.emplace_back(result.first, model->decode(result.second));
        }

        return str_results;
    }

    std::vector<std::pair<float, std::string>> PredictCorrection(const std::string &context, const std::vector<TokenMix> &mixes, bool swipe_mode, WordCapitalizeMode capitals, const std::vector<std::string> &banned_words) {
        if(specialTokens.XBU == -1) return { };

        std::vector<banned_sequence> banned_sequences;
        for(const std::string &bw : banned_words) {
            auto tokenized = model->tokenize(trim(bw) + " ");
            banned_sequences.push_back({ tokenized, compute_sequence_hash(tokenized) });

            auto tokenized2 = model->tokenize(trim(bw));
            banned_sequences.push_back({ tokenized2, compute_sequence_hash(tokenized2) });
        }

        token_sequence next_context;
        if(!context.empty()) {
            next_context = model->tokenize(trim(context) + " ");
        }

        next_context.insert(next_context.begin(), 1); // BOS
        next_context.push_back(specialTokens.XBU);

        if(swipe_mode) {
            next_context.push_back(specialTokens.XC0_SWIPE_MODE);
        }

        auto decoding_result = DecodePromptAndMixes(next_context, mixes);
        auto results = Sample(decoding_result, 3, capitals, banned_sequences);

        std::vector<std::pair<float, std::string>> str_results;
        str_results.reserve(results.size());
        for(const auto& result : results) {
            str_results.emplace_back(result.first, model->decode(result.second));
        }

        return str_results;
    }
};

struct SuggestionItemToRescore {
    int index;

    int originalScore;
    float transformedScore;

    std::string word;
    token_sequence tokens;
};

namespace latinime {
    static jlong xlm_LanguageModel_open(JNIEnv *env, jclass clazz, jstring modelDir) {
        GGML_UNUSED(clazz);

        AKLOGI("open LM");
        const jsize sourceDirUtf8Length = env->GetStringUTFLength(modelDir);
        if (sourceDirUtf8Length <= 0) {
            AKLOGE("DICT: Can't get sourceDir string");
            return 0;
        }
        char sourceDirChars[sourceDirUtf8Length + 1];
        env->GetStringUTFRegion(modelDir, 0, env->GetStringLength(modelDir), sourceDirChars);
        sourceDirChars[sourceDirUtf8Length] = '\0';

        auto *state = new LanguageModelState();

        if(!state->Initialize(sourceDirChars)) {
            delete state;
            return 0;
        }

        return reinterpret_cast<jlong>(state);
    }

    static void xlm_LanguageModel_close(JNIEnv *env, jclass clazz, jlong statePtr) {
        GGML_UNUSED(env);
        GGML_UNUSED(clazz);

        AKLOGI("LanguageModel_close called!");
        auto *state = reinterpret_cast<LanguageModelState *>(statePtr);
        if(state == nullptr) return;
        delete state;
    }

    // (JLjava/lang/String;[Ljava/lang/String;[I[I)V
    // TODO: This will also need caching to not make things extremely slow by recomputing every time
    static void xlm_LanguageModel_rescoreSuggestions(JNIEnv *env, jclass clazz,
        jlong dict,
        jstring context,
        jobjectArray inWords,
        jintArray inScores,

        jintArray outScores
    ) {
        GGML_UNUSED(clazz);
        auto *state = reinterpret_cast<LanguageModelState *>(dict);

        std::string contextString = jstring2string(env, context);

        jsize inputSize = env->GetArrayLength(inScores);
        int scores[inputSize];
        env->GetIntArrayRegion(inScores, 0, inputSize, scores);

        float maxScore = -INFINITY;
        float minScore = INFINITY;
        for(int score : scores) {
            auto scoref = (float)score;

            if(scoref > maxScore) maxScore = scoref;
            if(scoref < minScore) minScore = scoref;
        }

        minScore -= (maxScore - minScore) * 0.33f;

        std::vector<SuggestionItemToRescore> words;
        jsize numWords = env->GetArrayLength(inWords);

        for(jsize i=0; i<numWords; i++) {
            auto jstr = (jstring)env->GetObjectArrayElement(inWords, i);
            SuggestionItemToRescore item = {
                (int) i,
                scores[i],
                ((float)scores[i] - minScore) / (maxScore - minScore),
                jstring2string(env, jstr),
                {}
            };

            item.tokens = state->model->tokenize(trim(item.word) + " ");
            words.push_back(item);
        }


        // TODO: Transform here
        llama_context *ctx = state->model->context();
        size_t n_vocab = llama_n_vocab(llama_get_model(ctx));

        token_sequence next_context = state->model->tokenize(trim(contextString) + " ");
        next_context.insert(next_context.begin(), 1); // BOS

        auto decoding_result = state->DecodePromptAndMixes(next_context, { });
        float *logits = llama_get_logits_ith(ctx, decoding_result.logits_head);

        softmax(logits, n_vocab);

        AKLOGI("Iter");
        for(auto &entry : words) {
            float pseudoScore = logits[entry.tokens[0]] / (float)entry.tokens.size();
            AKLOGI("Word [%s], %d tokens, prob[0] = %.8f", entry.word.c_str(), entry.tokens.size(), pseudoScore);
            entry.transformedScore *= pseudoScore * 1000.0f;
        }
        // TODO: Transform here

        // Output scores
        jint *outArray = env->GetIntArrayElements(outScores, nullptr);

        for(const auto &entry : words) {
            outArray[entry.index] = (jint)(entry.transformedScore * (maxScore - minScore) + minScore);
        }

        env->ReleaseIntArrayElements(outScores, outArray, 0);
    }

    static void xlm_LanguageModel_getSuggestions(JNIEnv *env, jclass clazz,
         // inputs
         jlong dict,
         jlong proximityInfo,
         jstring context,
         jstring partialWord,
         jint inputMode,
         jintArray inComposeX,
         jintArray inComposeY,
         jfloat autocorrectThreshold,
         jobjectArray bannedWordsArray,

         // outputs
         jobjectArray outPredictions,
         jfloatArray outProbabilities
    ) {
        GGML_UNUSED(clazz);

        auto *state = reinterpret_cast<LanguageModelState *>(dict);
        auto *pInfo = reinterpret_cast<ProximityInfo *>(proximityInfo);

        size_t inputSize = env->GetArrayLength(inComposeX);

        std::string contextString;
        if(context != nullptr) {
            contextString = jstring2string(env, context);
        }

        std::string partialWordString;
        if(partialWord != nullptr){
            partialWordString = jstring2string(env, partialWord);
        }

        if(partialWordString.size() < inputSize) inputSize = partialWordString.size();

        WordCapitalizeMode capitals = WordCapitalizeMode::IgnoredCapitals;

        if(!partialWordString.empty() && !isFirstCharLowercase(partialWordString.c_str())) {
            if(partialWordString.size() > 1 && !hasLowercase(partialWordString.c_str())) {
                capitals = WordCapitalizeMode::AllCapitals;
            } else {
                capitals = WordCapitalizeMode::FirstCapital;
            }
        }

        std::vector<std::string> bannedWords;
        size_t numBannedWords = env->GetArrayLength(bannedWordsArray);
        for(size_t i=0; i<numBannedWords; i++) {
            bannedWords.push_back(jstring2string(
                env,
                (jstring)env->GetObjectArrayElement(bannedWordsArray, (jsize) i)
            ));
        }

        TIME_START(GettingMixes)
        int xCoordinates[inputSize];
        int yCoordinates[inputSize];
        env->GetIntArrayRegion(inComposeX, 0, (jsize)inputSize, xCoordinates);
        env->GetIntArrayRegion(inComposeY, 0, (jsize)inputSize, yCoordinates);

        std::vector<TokenMix> mixes;
        for(size_t i=0; i<inputSize; i++) {
            char wc = partialWordString[i];
            if (!(wc >= 'a' && wc <= 'z') && !(wc >= 'A' && wc <= 'Z')) {
                //AKLOGI("%d | Char %c skipped due to not within range", i, wc);
                continue;
            }
            if (xCoordinates[i] == -1 || yCoordinates[i] == -1) {
                //AKLOGI("%d | Char %c skipped due to -1", i, wc);
                continue;
            }

            std::vector<float> proportions = pInfo->decomposeTapPosition(xCoordinates[i], yCoordinates[i]);
            for(float &f : proportions) {
                if(f < 0.05f) f = 0.0f;
            }

            std::vector<std::pair<float, int>> index_value;
            index_value.clear();
            for (size_t k = 0; k < proportions.size(); k++) {
                index_value.emplace_back(proportions[k], k);
            }

            sortProbabilityPairVectorDescending(index_value, NUM_TOKEN_MIX);

            bool needs_resorting = false;
            int num_symbols = 0;
            for(int s=0; s<4; s++) {
                num_symbols = 0;
                for (int j = 0; j < NUM_TOKEN_MIX; j++) {
                    char c = (char) (pInfo->getKeyCodePoint(index_value[j].second));

                    if (c >= 'a' && c <= 'z') {
                    } else if (c >= 'A' && c <= 'Z') {
                    } else if(index_value[j].first > 0.0f) {
                        index_value[j].first = 0.0f;
                        needs_resorting = true;
                        num_symbols++;
                    }
                }
                if(num_symbols == NUM_TOKEN_MIX) break;
                if(!needs_resorting) break;
                sortProbabilityPairVectorDescending(index_value, NUM_TOKEN_MIX);
            }
            if(num_symbols == NUM_TOKEN_MIX) {
                //AKLOGI("%d | Char %c skipped due to num_symbols == NUM_TOKEN_MIX", i, wc);
                continue;
            } // Skip the symbol character

            float total_sum = 0.0f;
            for(int j=0; j<NUM_TOKEN_MIX; j++) {
                total_sum += index_value[j].first;
            }

            if(total_sum == 0.0f) {
                continue;
            }

            for(int j=0; j<NUM_TOKEN_MIX; j++) {
                index_value[j].first /= total_sum;
            }

            TokenMix results {};
            results.x = ((float)xCoordinates[i]) / ((float)pInfo->getKeyboardWidth());
            results.y = ((float)yCoordinates[i]) / ((float)pInfo->getKeyboardHeight());

            //AKLOGI("%d | Char %c, pos %.6f %.6f, nearest is %c at %.2f, then %c at %.2f, finally %c at %.2f", i, partialWordString[i],
            //       results.x, results.y,
            //       (char)(pInfo->getKeyCodePoint(index_value[0].second)), (float)(index_value[0].first),
            //       (char)(pInfo->getKeyCodePoint(index_value[1].second)), (float)(index_value[1].first),
            //       (char)(pInfo->getKeyCodePoint(index_value[2].second)), (float)(index_value[2].first)
            //   );


            for(int j=0; j<NUM_TOKEN_MIX; j++) {
                char c = (char) (pInfo->getKeyCodePoint(index_value[j].second));
                float w = index_value[j].first;

                results.mixes[j].weight = w;
                if(c >= 'a' && c <= 'z') {
                    results.mixes[j].token = (state->specialTokens.LETTERS_TO_IDS[c - 'a']);
                }else if(c >= 'A' && c <= 'Z') {
                    results.mixes[j].token = (state->specialTokens.LETTERS_TO_IDS[c - 'A']);
                } else {
                    //AKLOGI("ignoring character in partial word [%c]", c);
                    results.mixes[j].weight = 0.0f;
                }
            }

            mixes.push_back(results);
        }

        TIME_END(GettingMixes)

        //AKLOGI("LanguageModel context [%s]", contextString.c_str());

        std::vector<std::pair<float, std::string>> results;
        if(partialWordString.empty()) {
            results = state->PredictNextWord(contextString, bannedWords);

            //for(const auto &result : results) {
            //    AKLOGI("LanguageModel suggestion %.2f [%s]", result.first, result.second.c_str());
            //}
        } else {
            bool swipeMode = inputMode == 1;
            results = state->PredictCorrection(contextString, mixes, swipeMode, capitals, bannedWords);

            //for(const auto &result : results) {
            //    AKLOGI("LanguageModel correction %.2f [%s] -> [%s]", result.first, partialWordString.c_str(), result.second.c_str());
            //}

            // Exact match rule
            bool hasExactMatch = false;
            for(const auto &result : results) {
                if(isExactMatch(result.second, partialWordString)) {
                    hasExactMatch = true;
                }
            }
            if(hasExactMatch){
                for(auto &result : results) {
                    if(!isExactMatch(result.second, partialWordString)) {
                        result.first -= 1.0f;
                    }
                }
            }
        }

        // Probability check
        sortProbabilityPairVectorDescending(results);

        const char *result_probability_mode;
        if(results.size() < 2) {
            // Not sure what to do here
            result_probability_mode = RETURNVAL_UNCERTAIN;
        }else if(results[0].first > autocorrectThreshold * results[1].first) {
            result_probability_mode = RETURNVAL_AUTOCORRECT;
        }else if(results[0].first > (autocorrectThreshold * 0.1f) * results[1].first) {
            result_probability_mode = RETURNVAL_UNCERTAIN;
        } else {
            result_probability_mode = RETURNVAL_CLUELESS;
            // TODO: If we end up here, we could try sampling differently / etc
        }

        // No way it's correct if it's way shorter! (unless we're swipe typing)
        if(!results.empty() && !partialWordString.empty() && (results[0].second.size() * 2 < partialWordString.size()) && inputMode != 1) {
            result_probability_mode = RETURNVAL_CLUELESS;
        }

        // Output
        size_t size = env->GetArrayLength(outPredictions);

        jstring result_str = string2jstring(env, result_probability_mode);
        env->SetObjectArrayElement(outPredictions, (jsize)(size - 1), result_str);
        env->DeleteLocalRef(result_str);

        jfloat *probsArray = env->GetFloatArrayElements(outProbabilities, nullptr);

        // Output predictions for next word
        for (int i = 0; i < (int)results.size(); i++) {
            jstring jstr = string2jstring(env, results[i].second.c_str());
            env->SetObjectArrayElement(outPredictions, i, jstr);
            probsArray[i] = results[i].first;
            env->DeleteLocalRef(jstr);
        }

        env->ReleaseFloatArrayElements(outProbabilities, probsArray, 0);
    }

    static const JNINativeMethod sMethods[] = {
            {
                    const_cast<char *>("openNative"),
                    const_cast<char *>("(Ljava/lang/String;)J"),
                    reinterpret_cast<void *>(xlm_LanguageModel_open)
            },
            {
                    const_cast<char *>("closeNative"),
                    const_cast<char *>("(J)V"),
                    reinterpret_cast<void *>(xlm_LanguageModel_close)
            },
            {
                    const_cast<char *>("getSuggestionsNative"),
                    const_cast<char *>("(JJLjava/lang/String;Ljava/lang/String;I[I[IF[Ljava/lang/String;[Ljava/lang/String;[F)V"),
                    reinterpret_cast<void *>(xlm_LanguageModel_getSuggestions)
            },
            {
                    const_cast<char *>("rescoreSuggestionsNative"),
                    const_cast<char *>("(JLjava/lang/String;[Ljava/lang/String;[I[I)V"),
                    reinterpret_cast<void *>(xlm_LanguageModel_rescoreSuggestions)
            }
    };


    static void llama_log_callback(ggml_log_level level, const char * text, void * user_data) {
        GGML_UNUSED(user_data);

        switch(level) {
            case GGML_LOG_LEVEL_ERROR:
                AKLOGE("llama err:  %s", text);
                break;
            case GGML_LOG_LEVEL_WARN:
                AKLOGI("llama warn: %s", text);
                break;
            case GGML_LOG_LEVEL_INFO:
                AKLOGI("llama info: %s", text);
                break;
        }
    }

    int register_LanguageModel(JNIEnv *env) {
        llama_backend_init(true /* numa??? */);
        llama_log_set(llama_log_callback, nullptr);

        const char *const kClassPathName = "org/futo/inputmethod/latin/xlm/LanguageModel";
        return registerNativeMethods(env, kClassPathName, sMethods, NELEMS(sMethods));
    }
} // namespace latinime
