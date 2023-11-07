// Various helper functions and utilities

#pragma once

#include "llama.h"

#define LOG_NO_FILE_LINE_FUNCTION

#include <string>
#include <vector>
#include <random>
#include <thread>
#include <unordered_map>
#include <tuple>

#ifdef _WIN32
#define DIRECTORY_SEPARATOR '\\'
#else
#define DIRECTORY_SEPARATOR '/'
#endif // _WIN32


template<typename ... Args>
std::string string_format( const std::string& format, Args ... args )
{
    int size_s = std::snprintf( nullptr, 0, format.c_str(), args ... ) + 1; // Extra space for '\0'
    if( size_s <= 0 ){ throw std::runtime_error( "Error during formatting." ); }
    auto size = static_cast<size_t>( size_s );
    std::unique_ptr<char[]> buf( new char[ size ] );
    std::snprintf( buf.get(), size, format.c_str(), args ... );
    return std::string( buf.get(), buf.get() + size - 1 ); // We don't want the '\0' inside
}

#define die(msg)          do { throw std::runtime_error(msg); } while (0)
#define die_fmt(fmt, ...) do { throw std::runtime_error(string_format(fmt, __VA_ARGS__)); } while (0)

#define print_build_info() do {                                                             \
    fprintf(stderr, "%s: build = %d (%s)\n", __func__, BUILD_NUMBER, BUILD_COMMIT);         \
    fprintf(stderr, "%s: built with %s for %s\n", __func__, BUILD_COMPILER, BUILD_TARGET);  \
} while(0)

//
// CLI argument parsing
//
int32_t get_num_physical_cores();

std::string gpt_random_prompt(std::mt19937 & rng);

void process_escapes(std::string& input);

//
// Model utils
//

// Batch utils

void llama_batch_clear(struct llama_batch & batch);

void llama_batch_add(
        struct llama_batch & batch,
        llama_token   id,
        llama_pos   pos,
        const std::vector<llama_seq_id> & seq_ids,
        bool   logits);

//
// Vocab utils
//

// tokenizes a string into a vector of tokens
// should work similar to Python's `tokenizer.encode`
std::vector<llama_token> llama_tokenize(
        const struct llama_context * ctx,
        const std::string & text,
        bool   add_bos,
        bool   special = false);

std::vector<llama_token> llama_tokenize(
        const struct llama_model * model,
        const std::string & text,
        bool   add_bos,
        bool   special = false);

// tokenizes a token into a piece
// should work similar to Python's `tokenizer.id_to_piece`
std::string llama_token_to_piece(
        const struct llama_context * ctx,
        llama_token   token);

// TODO: these should be moved in llama.h C-style API under single `llama_detokenize` function
//       that takes into account the tokenizer type and decides how to handle the leading space
//
// detokenizes a vector of tokens into a string
// should work similar to Python's `tokenizer.decode`
// removes the leading space from the first non-BOS token
std::string llama_detokenize_spm(
        llama_context * ctx,
        const std::vector<llama_token> & tokens);

// detokenizes a vector of tokens into a string
// should work similar to Python's `tokenizer.decode`
std::string llama_detokenize_bpe(
        llama_context * ctx,
        const std::vector<llama_token> & tokens);

//
// YAML utils
//

bool create_directory_with_parents(const std::string & path);
void dump_vector_float_yaml(FILE * stream, const char * prop_name, const std::vector<float> & data);
void dump_vector_int_yaml(FILE * stream, const char * prop_name, const std::vector<int> & data);
void dump_string_yaml_multiline(FILE * stream, const char * prop_name, const char * data);
std::string get_sortable_timestamp();