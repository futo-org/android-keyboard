/**
 * Interface for a trie to be used by the swipe decoder
 */

#ifndef SWIPE_DECODER_ITRIE_H
#define SWIPE_DECODER_ITRIE_H

#include <stdint.h>
#define TrieId uint32_t

#ifdef __cplusplus
extern "C" {
#endif

struct ITrieVTable {
    int (*num_chars)(void*);

    // start a search
    TrieId (*root)(void*); 

    // technically this is an index into letters(), not codepoint
    int (*get_codepoint)(void*, TrieId);
    uint32_t (*get_child_count)(void*, TrieId);
    TrieId (*get_child)(void*, TrieId, uint32_t);
    bool (*is_word)(void*, TrieId);
    float (*get_frequency)(void*, TrieId);
    float (*get_log_frequency)(void*, TrieId);
    uint16_t (*get_depth)(void*, TrieId);
    char *(*get_word)(void*, TrieId, bool);

    // finishes the search, all TrieIds become invalid
    void (*end_search)(void*);
};

struct ITrie {
    void *userdata;
    const ITrieVTable *vtable;

    inline int    num_chars(void) const { return vtable->num_chars(userdata); }
    inline TrieId root(void) const { return vtable->root(userdata); }

    // technically this is an index into letters(), not codepoint
    inline int      get_codepoint(TrieId id)           const { return vtable->get_codepoint(userdata, id);     }
    inline uint32_t get_child_count(TrieId id)         const { return vtable->get_child_count(userdata, id);   }
    inline TrieId   get_child(TrieId id, uint32_t idx) const { return vtable->get_child(userdata, id, idx);    }
    inline bool     is_word(TrieId id)                 const { return vtable->is_word(userdata, id);           }
    inline float    get_frequency(TrieId id)           const { return vtable->get_frequency(userdata, id);     }
    inline float    get_log_frequency(TrieId id)       const { return vtable->get_log_frequency(userdata, id); }
    inline uint16_t get_depth(TrieId id)               const { return vtable->get_depth(userdata, id);         }
    inline char*    get_word(TrieId id, bool final)    const { return vtable->get_word(userdata, id, final);   }
    inline void     end_search(void)                   const { return vtable->end_search(userdata);            }
};

#ifdef __cplusplus
}
#endif

#endif