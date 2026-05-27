#include "dictionary_itrie.h"
#include "dictionary/property/ngram_context.h"
#include "suggest/core/dicnode/dic_node_vector.h"
#include "dictionary/interface/dictionary_structure_with_buffer_policy.h"
#include "ggml/unicode.h"

namespace latinime {

int get_letter_index(int code, DicITrieWrapper *wrapper) {
    auto found = wrapper->letter_mapping.find(code);
    if(found == wrapper->letter_mapping.end()) {
        int idx = -1;
        for (int i = 0; i < wrapper->lettersAsCodePoints.size(); i++) {
            if (code == wrapper->lettersAsCodePoints[i]) {
                idx = i;
                break;
            }
        }

        // If this is -1, try base letter
        if(idx == -1){
            int baseCode = CharUtils::toBaseLowerCase(code);
            for (int i = 0; i < wrapper->lettersAsCodePoints.size(); i++) {
                if (baseCode == wrapper->lettersAsCodePoints[i]) {
                    idx = i;
                    break;
                }
            }
        }

        wrapper->letter_mapping.emplace(code, idx);
        return idx;
    } else {
        return found->second;
    }
}

static int ITrie_NumChars(void* self) {
    return ((DicITrieWrapper *)self)->letters.length();
}
static const char *ITrie_Letters(void* self) {
    return ((DicITrieWrapper *)self)->letters.c_str();
}
static TrieId ITrie_Root(void* self) {
    auto dic = ((DicITrieWrapper *)self);

    DicNode rootNode;

    NgramContext emptyNgramContext;
    WordIdArray<MAX_PREV_WORD_COUNT_FOR_N_GRAM> prevWordIdArray;
    const WordIdArrayView prevWordIds = emptyNgramContext.getPrevWordIds(dic->structure, &prevWordIdArray, false);

    DicNodeUtils::initAsRoot(dic->structure, prevWordIds, &rootNode);

    dic->nodes.emplace(0, rootNode);

    return 0;
}
static int ITrie_GetCodepoint(void* self, TrieId id) {
    auto dic = ((DicITrieWrapper *)self);
    auto node = dic->nodes.find(id);
    if(node == dic->nodes.end()) {
        return -1;
    }

    int codepoint = node->second.getNodeCodePoint();
    int mapping = get_letter_index(codepoint, dic);
    return mapping;
}

static bool isOmissible(DicITrieWrapper *dic, DicNode *node) {
    return get_letter_index(node->getNodeCodePoint(), dic) == -1;
}

static TrieId CHILD_ID = 1;
static void addChildrenRecursivelySkippingOmissible(
        DicITrieWrapper *dic,
        DicNode *node,
        std::vector<TrieId> &fakeChildren
) {
    DicNodeVector childDicNodes;
    DicNodeUtils::getAllChildDicNodes(node, dic->structure, &childDicNodes);

    int size = childDicNodes.getSizeAndLock();
    for(int i=0; i<size; i++) {
        if(isOmissible(dic, childDicNodes[i])) {
            addChildrenRecursivelySkippingOmissible(dic, childDicNodes[i], fakeChildren);
        } else {
            dic->nodes.emplace(CHILD_ID, *childDicNodes[i]);
            fakeChildren.emplace_back(CHILD_ID++);
        }
    }
}

static uint32_t ITrie_GetChildCount(void* self, TrieId id) {
    auto dic = ((DicITrieWrapper *)self);

    auto precomputedChildren = dic->exceptional_children.find(id);
    if(precomputedChildren != dic->exceptional_children.end()) {
        return precomputedChildren->second.size();
    }

    auto node = dic->nodes.find(id);
    if(node == dic->nodes.end()) {
        return 0;
    }

    std::vector<TrieId> children;
    addChildrenRecursivelySkippingOmissible(dic, &node->second, children);
    dic->exceptional_children.emplace(id, children);
    return children.size();
}
static TrieId ITrie_GetChild(void* self, TrieId id, uint32_t idx) {
    auto dic = ((DicITrieWrapper *)self);

    auto precomputedChildren = dic->exceptional_children.find(id);
    if(precomputedChildren != dic->exceptional_children.end()) {
        if(idx > precomputedChildren->second.size()) return 0;
        else return precomputedChildren->second[idx];
    }

    return 0;
}
static bool ITrie_IsWord(void* self, TrieId id) {
    auto dic = ((DicITrieWrapper *)self);
    auto node = dic->nodes.find(id);
    if(node == dic->nodes.end()) return false;
    if(!node->second.isTerminalDicNode()) return false;

    BinaryDictionaryShortcutIterator shortcutIt = dic->structure->getShortcutIterator(node->second.getWordId());
    if(shortcutIt.hasNextShortcutTarget()) return true;

    const WordAttributes wordAttributes = dic->structure->getWordAttributesInContext(
            node->second.getPrevWordIds(),
            node->second.getWordId(), nullptr);

    if(wordAttributes.isNotAWord()) return false;
    if(wordAttributes.isBlacklisted()) return false;

    return true;
}

static float ITrie_GetFrequency(void* self, TrieId id) {
    auto dic = ((DicITrieWrapper *)self);
    auto node = dic->nodes.find(id);
    if(node == dic->nodes.end()) {
        return 0.0f;
    }
    int prob = dic->structure->getProbabilityOfWord(WordIdArrayView(), node->second.getWordId());

    return prob;
}

static float ITrie_GetLogFrequency(void* self, TrieId id) {
    return ITrie_GetFrequency(self, id);
}

static uint16_t ITrie_GetDepth(void* self, TrieId id) {
    auto dic = ((DicITrieWrapper *)self);
    auto node = dic->nodes.find(id);
    if(node == dic->nodes.end()) {
        return 0;
    }

    int depth = 0;
    const int* buf = node->second.getOutputWordBuf();
    for(int i=0; i<node->second.getNodeCodePointCount(); i++) {
        if(get_letter_index(buf[i], dic) != -1) depth++;
    }

    return depth;
}
static const char *ITrie_GetWord(void* self, TrieId id) {
    auto dic = ((DicITrieWrapper *)self);
    auto node = dic->nodes.find(id);
    if(node == dic->nodes.end()) {
        return nullptr;
    }

    static char charBuf[MAX_WORD_LENGTH];
    int charBufHead = 0;
    const int* buf = node->second.getOutputWordBuf();

    if(id == 0) {
        charBuf[0] = 0;
        return charBuf;
    }

    if(true) {
        BinaryDictionaryShortcutIterator shortcutIt = dic->structure->getShortcutIterator(node->second.getWordId());
        if(shortcutIt.hasNextShortcutTarget()) {
            int shortcutTarget[MAX_WORD_LENGTH];
            int shortcutTargetLength;
            bool isWhitelist;
            shortcutIt.nextShortcutTarget(MAX_WORD_LENGTH, shortcutTarget, &shortcutTargetLength, &isWhitelist);
            intArrayToCharArray(shortcutTarget, shortcutTargetLength, charBuf, MAX_WORD_LENGTH);
            return charBuf;
        }

        intArrayToCharArray(buf, node->second.getNodeCodePointCount(), charBuf, MAX_WORD_LENGTH);
        return charBuf;
    } else {
        for (int i = 0; i < node->second.getNodeCodePointCount(); i++) {
            int letterIdx = get_letter_index(buf[i], dic);
            if (letterIdx != -1) {
                int code = dic->lettersAsCodePoints[letterIdx];
                charBufHead += intArrayToCharArray(&code, 1, &charBuf[charBufHead],
                                                   MAX_WORD_LENGTH - charBufHead);
            }
        }

        return charBuf;
    }
}

void ITrie_EndSearch(void *self) {
    auto dic = ((DicITrieWrapper *)self);
    dic->nodes.clear();
    dic->exceptional_children.clear();
    CHILD_ID = 1;
}

static const ITrieVTable dicTraverseVtable = {
        &ITrie_NumChars, &ITrie_Root,
        &ITrie_GetCodepoint, &ITrie_GetChildCount, &ITrie_GetChild,
        &ITrie_IsWord, &ITrie_GetFrequency,
        &ITrie_GetDepth, &ITrie_GetWord, &ITrie_EndSearch
};

void DicITrieWrapper::initLetters(const std::string &letters) {
    if(this->letters == letters) return;

    this->letters = letters;
    this->lettersAsCodePoints = codepoints_from_utf8(letters);

    this->letter_mapping.clear();
}

void DicITrieWrapper::populateITrie(ITrie *trie) const {
    trie->userdata = (void *)this;
    trie->vtable = &dicTraverseVtable;
}
}