package com.android.inputmethod.latin.tests;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.util.List;

import android.content.Context;
import android.test.AndroidTestCase;
import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.latin.Suggest;
import com.android.inputmethod.latin.WordComposer;

public class SuggestTests extends AndroidTestCase {
    private static final String TAG = "SuggestTests";

    private Suggest mSuggest;

    int[][] adjacents = {
        {'a','s','w','q',-1},
        {'b','h','v','n','g','j',-1},
        {'c','v','f','x','g',},
        {'d','f','r','e','s','x',-1},
        {'e','w','r','s','d',-1},
        {'f','g','d','c','t','r',-1},
        {'g','h','f','y','t','v',-1},
        {'h','j','u','g','b','y',-1},
        {'i','o','u','k',-1},
        {'j','k','i','h','u','n',-1},
        {'k','l','o','j','i','m',-1},
        {'l','k','o','p',-1},
        {'m','k','n','l',-1},
        {'n','m','j','k','b',-1},
        {'o','p','i','l',-1},
        {'p','o',-1},
        {'q','w',-1},
        {'r','t','e','f',-1},
        {'s','d','e','w','a','z',-1},
        {'t','y','r',-1},
        {'u','y','i','h','j',-1},
        {'v','b','g','c','h',-1},
        {'w','e','q',-1},
        {'x','c','d','z','f',-1},
        {'y','u','t','h','g',-1},
        {'z','s','x','a','d',-1},
    };

    @Override
    protected void setUp() {
        final Context context = getTestContext();
        InputStream is = context.getResources().openRawResource(R.raw.test);
        Log.i(TAG, "Stream type is " + is);
        try {
            int avail = is.available();
            if (avail > 0) {
                ByteBuffer byteBuffer =
                    ByteBuffer.allocateDirect(avail).order(ByteOrder.nativeOrder());
                int got = Channels.newChannel(is).read(byteBuffer);
                if (got != avail) {
                    Log.e(TAG, "Read " + got + " bytes, expected " + avail);
                } else {
                    mSuggest = new Suggest(context, byteBuffer);
                    Log.i(TAG, "Created mSuggest " + avail + " bytes");
                }
            }
        } catch (IOException ioe) {
            Log.w(TAG, "No available size for binary dictionary");
        }
        mSuggest.setAutoTextEnabled(false);
        mSuggest.setCorrectionMode(Suggest.CORRECTION_FULL_BIGRAM);
    }

    /************************** Helper functions ************************/

    private WordComposer createWordComposer(CharSequence s) {
        WordComposer word = new WordComposer();
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            int[] codes;
            // If it's not a lowercase letter, don't find adjacent letters
            if (c < 'a' || c > 'z') {
                codes = new int[] { c };
            } else {
                codes = adjacents[c - 'a'];
            }
            word.add(c, codes);
        }
        return word;
    }

    private void showList(String title, List<CharSequence> suggestions) {
        Log.i(TAG, title);
        for (int i = 0; i < suggestions.size(); i++) {
            Log.i(title, suggestions.get(i) + ", ");
        }
    }

    private boolean isDefaultSuggestion(List<CharSequence> suggestions, CharSequence word) {
        // Check if either the word is what you typed or the first alternative
        return suggestions.size() > 0 &&
                (/*TextUtils.equals(suggestions.get(0), word) || */
                  (suggestions.size() > 1 && TextUtils.equals(suggestions.get(1), word)));
    }

    private boolean isDefaultSuggestion(CharSequence typed, CharSequence expected) {
        WordComposer word = createWordComposer(typed);
        List<CharSequence> suggestions = mSuggest.getSuggestions(null, word, false, null);
        return isDefaultSuggestion(suggestions, expected);
    }

    private void getBigramSuggestions(CharSequence previous, CharSequence typed) {
        if(!TextUtils.isEmpty(previous) && (typed.length() > 1)) {
            WordComposer firstChar = createWordComposer(typed.charAt(0) + "");
            mSuggest.getSuggestions(null, firstChar, false, previous);
        }
    }

    private boolean isDefaultNextSuggestion(CharSequence previous, CharSequence typed,
            CharSequence expected) {
        WordComposer word = createWordComposer(typed);
        getBigramSuggestions(previous, typed);
        List<CharSequence> suggestions = mSuggest.getSuggestions(null, word, false, previous);
        return isDefaultSuggestion(suggestions, expected);
    }

    private boolean isDefaultCorrection(CharSequence typed, CharSequence expected) {
        WordComposer word = createWordComposer(typed);
        List<CharSequence> suggestions = mSuggest.getSuggestions(null, word, false, null);
        return isDefaultSuggestion(suggestions, expected) && mSuggest.hasMinimalCorrection();
    }

    private boolean isDefaultNextCorrection(CharSequence previous, CharSequence typed,
            CharSequence expected) {
        WordComposer word = createWordComposer(typed);
        getBigramSuggestions(previous, typed);
        List<CharSequence> suggestions = mSuggest.getSuggestions(null, word, false, previous);
        for(int i=0;i<suggestions.size();i++) {
            Log.i(TAG,i+" "+suggestions.get(i));
        }
        return isDefaultSuggestion(suggestions, expected) && mSuggest.hasMinimalCorrection();
    }

    private boolean isASuggestion(CharSequence typed, CharSequence expected) {
        WordComposer word = createWordComposer(typed);
        List<CharSequence> suggestions = mSuggest.getSuggestions(null, word, false, null);
        for (int i = 1; i < suggestions.size(); i++) {
            if (TextUtils.equals(suggestions.get(i), expected)) return true;
        }
        return false;
    }

    private boolean isASuggestion(CharSequence previous, CharSequence typed,
            CharSequence expected) {
        WordComposer word = createWordComposer(typed);
        getBigramSuggestions(previous, typed);
        List<CharSequence> suggestions = mSuggest.getSuggestions(null, word, false, previous);
        for (int i = 1; i < suggestions.size(); i++) {
            if (TextUtils.equals(suggestions.get(i), expected)) return true;
        }
        return false;
    }

    private boolean isValid(CharSequence typed) {
        return mSuggest.isValidWord(typed);
    }

    /************************** Tests ************************/

    /**
     * Tests for simple completions of one character.
     */
    public void testCompletion1char() {
        assertTrue(isDefaultSuggestion("peopl", "people"));
        assertTrue(isDefaultSuggestion("abou", "about"));
        assertTrue(isDefaultSuggestion("thei", "their"));
    }

    /**
     * Tests for simple completions of two characters.
     */
    public void testCompletion2char() {
        assertTrue(isDefaultSuggestion("peop", "people"));
        assertTrue(isDefaultSuggestion("calli", "calling"));
        assertTrue(isDefaultSuggestion("busine", "business"));
    }

    /**
     * Tests for proximity errors.
     */
    public void testProximityPositive() {
        assertTrue(isDefaultSuggestion("peiple", "people"));
        assertTrue(isDefaultSuggestion("peoole", "people"));
        assertTrue(isDefaultSuggestion("pwpple", "people"));
    }

    /**
     * Tests for proximity errors - negative, when the error key is not near.
     */
    public void testProximityNegative() {
        assertFalse(isDefaultSuggestion("arout", "about"));
        assertFalse(isDefaultSuggestion("ire", "are"));
    }

    /**
     * Tests for checking if apostrophes are added automatically.
     */
    public void testApostropheInsertion() {
        assertTrue(isDefaultSuggestion("im", "I'm"));
        assertTrue(isDefaultSuggestion("dont", "don't"));
    }

    /**
     * Test to make sure apostrophed word is not suggested for an apostrophed word.
     */
    public void testApostrophe() {
        assertFalse(isDefaultSuggestion("don't", "don't"));
    }

    /**
     * Tests for suggestion of capitalized version of a word.
     */
    public void testCapitalization() {
        assertTrue(isDefaultSuggestion("i'm", "I'm"));
        assertTrue(isDefaultSuggestion("sunday", "Sunday"));
        assertTrue(isDefaultSuggestion("sundat", "Sunday"));
    }

    /**
     * Tests to see if more than one completion is provided for certain prefixes.
     */
    public void testMultipleCompletions() {
        assertTrue(isASuggestion("com", "come"));
        assertTrue(isASuggestion("com", "company"));
        assertTrue(isASuggestion("th", "the"));
        assertTrue(isASuggestion("th", "that"));
        assertTrue(isASuggestion("th", "this"));
        assertTrue(isASuggestion("th", "they"));
    }

    /**
     * Does the suggestion engine recognize zero frequency words as valid words.
     */
    public void testZeroFrequencyAccepted() {
        assertTrue(isValid("yikes"));
        assertFalse(isValid("yike"));
    }

    /**
     * Tests to make sure that zero frequency words are not suggested as completions.
     */
    public void testZeroFrequencySuggestionsNegative() {
        assertFalse(isASuggestion("yike", "yikes"));
        assertFalse(isASuggestion("what", "whatcha"));
    }

    /**
     * Tests to ensure that words with large edit distances are not suggested, in some cases
     * and not considered corrections, in some cases.
     */
    public void testTooLargeEditDistance() {
        assertFalse(isASuggestion("sniyr", "about"));
        assertFalse(isDefaultCorrection("rjw", "the"));
    }

    /**
     * Make sure isValid is case-sensitive.
     */
    public void testValidityCaseSensitivity() {
        assertTrue(isValid("Sunday"));
        assertFalse(isValid("sunday"));
    }

    /**
     * Are accented forms of words suggested as corrections?
     */
    public void testAccents() {
        // ni<LATIN SMALL LETTER N WITH TILDE>o
        assertTrue(isDefaultCorrection("nino", "ni\u00F1o"));
        // ni<LATIN SMALL LETTER N WITH TILDE>o
        assertTrue(isDefaultCorrection("nimo", "ni\u00F1o"));
        // Mar<LATIN SMALL LETTER I WITH ACUTE>a
        assertTrue(isDefaultCorrection("maria", "Mar\u00EDa"));
    }

    /**
     * Make sure bigrams are showing when first character is typed
     *  and don't show any when there aren't any
     */
    public void testBigramsAtFirstChar() {
        assertTrue(isDefaultNextCorrection("about", "p", "part"));
        assertTrue(isDefaultNextCorrection("I'm", "a", "about"));
        assertTrue(isDefaultNextCorrection("about", "b", "business"));
        assertTrue(isASuggestion("about", "b", "being"));
        assertFalse(isDefaultNextSuggestion("about", "p", "business"));
    }

    /**
     * Make sure bigrams score affects the original score
     */
    public void testBigramsScoreEffect() {
       assertTrue(isDefaultCorrection("pa", "page"));
       assertTrue(isDefaultNextCorrection("about", "pa", "part"));
       assertTrue(isDefaultCorrection("sa", "said"));
       assertTrue(isDefaultNextCorrection("from", "sa", "same"));
    }
}
