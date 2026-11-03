package org.futo.inputmethod.latin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Verifies the pinyin swipe lexicon builds a real native trie and is queryable.
 * This exercises the exact native path (in-memory BinaryDictionary + getITrie)
 * that ChineseIME uses for pinyin swipe decoding.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class PinyinSwipeTrieTest {

    @Test
    public void buildsNativeTrie() {
        final PinyinSwipeTrie trie = new PinyinSwipeTrie();
        final long handle = trie.handle("abcdefghijklmnopqrstuvwxyz");
        assertNotEquals("pinyin trie must produce a non-zero native handle", 0L, handle);
    }

    @Test
    public void lexiconContainsSyllables() {
        final PinyinSwipeTrie trie = new PinyinSwipeTrie();
        assertTrue("zhong", trie.contains("zhong"));
        assertTrue("guo", trie.contains("guo"));
        assertTrue("ni", trie.contains("ni"));
        assertTrue("hao", trie.contains("hao"));
        assertTrue("lv", trie.contains("lv"));   // ü -> v
        assertTrue("a", trie.contains("a"));    // shortest syllable
        assertTrue("zhuang", trie.contains("zhuang")); // longest common syllable
        // Multi-syllable compounds are not single syllables, so must be absent:
        // pinyin swipe is syllable-by-syllable and librime accumulates them.
        assertFalse("compound is not a single syllable", trie.contains("nihao"));
        assertFalse("xyzzy", trie.contains("xyzzy"));
    }
}
