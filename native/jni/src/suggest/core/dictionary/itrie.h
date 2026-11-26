/**
 * Interface that allows the swipe decoder to traverse an abstract trie.
 * You will need to implement all methods. You can see the built-in Trie
 * implementation for an example.
 *
 * This currently depends on the letters you passed in the layout to
 * SwipeEngine, and your ITrie implementation needs to be aware of those
 * letters when returning in get_char_idx and num_chars.
 */

#ifndef SWIPE_DECODER_ITRIE_H
#define SWIPE_DECODER_ITRIE_H

#include <stdint.h>
#define TrieId uint32_t

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Note: The first parameter to all of these is your userdata.
 *
 * TrieIds are arbitrarily defined by you and should correspond to a specific
 * node in the trie.
 */
struct ITrieVTable {
    /**
     * The number of letters, e.g. 26 for English. This must correspond with
     * the letters in your layout.
     */
    int (*num_chars)(void*);

    /** Return the root node, called at beginning of traversal */
    TrieId (*root)(void*);

    /**
     * Get the character index for a specified node.
     * For example, if the letters are "abcdef", and this node represents
     * the letter "b", then its get_char_idx should return 1. Then b's "a"
     * child should return 0, and that child's "a" child should also return 0,
     * and true for is_word, forming the word "baa".
     *
     * For the root node or invalid nodes, you can return -1, though the
     * library should never call get_char_idx in such cases, most likely you
     * are accidentally returning wrong TrieIds from get_child.
     */
    int (*get_char_idx)(void*, TrieId);

    /** Get the number of children for the specified node */
    uint32_t (*get_child_count)(void*, TrieId);

    /** Get the id of a specified child of a specified node */
    TrieId (*get_child)(void*, TrieId, uint32_t);

    /**
     * If this node forms a valid word, return true.
     * For example, "ba" isn't a valid word yet, but its child "baa" is.
     * Even a word can have more children with words, like "baa's"
     */
    bool (*is_word)(void*, TrieId);

    /**
     * Get the log frequency for this node. It should be 1 to 255, similar to
     * the word frequencies in the AOSP dictionaries.
     */
    float (*get_log_frequency)(void*, TrieId);

    /**
     * Get the depth of this node
     */
    uint16_t (*get_depth)(void*, TrieId);   // normalized depth

    /**
     * Get the word as a null-terminated string. It'll be copied instantly, so
     * you can return a static buffer that gets overwritten each time.
     *
     * If this word contains non-letters like apostrophes that don't exist on
     * this layout, you can include them here
     */
    const char *(*get_word)(void*, TrieId);

    /**
     * Called at the end of traversal. You can clean up temporary state and
     * invalidate all TrieIds here, they won't be reused.
     */
    void (*end_search)(void*);
};

struct ITrie {
    void *userdata;
    const ITrieVTable *vtable;

    inline int    num_chars(void) const { return vtable->num_chars(userdata); }
    inline TrieId root(void) const { return vtable->root(userdata); }

    inline int         get_char_idx(TrieId id)            const { return vtable->get_char_idx(userdata, id);      }
    inline uint32_t    get_child_count(TrieId id)         const { return vtable->get_child_count(userdata, id);   }
    inline TrieId      get_child(TrieId id, uint32_t idx) const { return vtable->get_child(userdata, id, idx);    }
    inline bool        is_word(TrieId id)                 const { return vtable->is_word(userdata, id);           }
    inline float       get_log_frequency(TrieId id)       const { return vtable->get_log_frequency(userdata, id); }
    inline uint16_t    get_depth(TrieId id)               const { return vtable->get_depth(userdata, id);         }
    inline const char* get_word(TrieId id)                const { return vtable->get_word(userdata, id);          }
    inline void        end_search(void)                   const { return vtable->end_search(userdata);            }
};

#ifdef __cplusplus
}
#endif

#endif