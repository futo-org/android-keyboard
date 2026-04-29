#ifndef LATINIME_DICTIONARY_ITRIE_H
#define LATINIME_DICTIONARY_ITRIE_H

#include <string>
#include <unordered_map>
#include "itrie.h"
#include "suggest/core/dicnode/dic_node.h"

namespace latinime {
class DictionaryStructureWithBufferPolicy;

class DicITrieWrapper {
public:
    DictionaryStructureWithBufferPolicy *structure;
    std::string letters;
    std::vector<uint32_t> lettersAsCodePoints;
    std::unordered_map<int, int> letter_mapping;
    std::unordered_map<int, DicNode> nodes;

    // In nodes with siblings that are omissible (e.g. apostrophe), we need
    // to merge these to the parent node for easier iteration
    std::unordered_map<int, std::vector<TrieId>> exceptional_children;

    void initLetters(const std::string &letters);
    void populateITrie(ITrie *trie) const;
};
}

#endif //LATINIME_DICTIONARY_ITRIE_H
