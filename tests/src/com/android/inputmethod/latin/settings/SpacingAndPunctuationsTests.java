/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin.settings;

import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.utils.RunInLocale;

import junit.framework.AssertionFailedError;

import java.util.Locale;

@SmallTest
public class SpacingAndPunctuationsTests extends AndroidTestCase {
    private static final int ARMENIAN_FULL_STOP = '\u0589';
    private static final int ARMENIAN_COMMA = '\u055D';

    private int mScreenMetrics;

    private boolean isPhone() {
        return Constants.isPhone(mScreenMetrics);
    }

    private boolean isTablet() {
        return Constants.isTablet(mScreenMetrics);
    }

    private SpacingAndPunctuations ENGLISH;
    private SpacingAndPunctuations FRENCH;
    private SpacingAndPunctuations GERMAN;
    private SpacingAndPunctuations ARMENIAN;
    private SpacingAndPunctuations THAI;
    private SpacingAndPunctuations KHMER;
    private SpacingAndPunctuations LAO;
    private SpacingAndPunctuations ARABIC;
    private SpacingAndPunctuations PERSIAN;
    private SpacingAndPunctuations HEBREW;

    private SpacingAndPunctuations UNITED_STATES;
    private SpacingAndPunctuations UNITED_KINGDOM;
    private SpacingAndPunctuations CANADA_FRENCH;
    private SpacingAndPunctuations SWISS_GERMAN;
    private SpacingAndPunctuations INDIA_ENGLISH;
    private SpacingAndPunctuations ARMENIA_ARMENIAN;
    private SpacingAndPunctuations CAMBODIA_KHMER;
    private SpacingAndPunctuations LAOS_LAO;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mScreenMetrics = Settings.readScreenMetrics(getContext().getResources());

        // Language only
        ENGLISH = getSpacingAndPunctuations(Locale.ENGLISH);
        FRENCH = getSpacingAndPunctuations(Locale.FRENCH);
        GERMAN = getSpacingAndPunctuations(Locale.GERMAN);
        THAI = getSpacingAndPunctuations(new Locale("th"));
        ARMENIAN = getSpacingAndPunctuations(new Locale("hy"));
        KHMER = getSpacingAndPunctuations(new Locale("km"));
        LAO = getSpacingAndPunctuations(new Locale("lo"));
        ARABIC = getSpacingAndPunctuations(new Locale("ar"));
        PERSIAN = getSpacingAndPunctuations(new Locale("fa"));
        HEBREW = getSpacingAndPunctuations(new Locale("iw"));

        // Language and Country
        UNITED_STATES = getSpacingAndPunctuations(Locale.US);
        UNITED_KINGDOM = getSpacingAndPunctuations(Locale.UK);
        CANADA_FRENCH = getSpacingAndPunctuations(Locale.CANADA_FRENCH);
        SWISS_GERMAN = getSpacingAndPunctuations(new Locale("de", "CH"));
        INDIA_ENGLISH = getSpacingAndPunctuations(new Locale("en", "IN"));
        ARMENIA_ARMENIAN = getSpacingAndPunctuations(new Locale("hy", "AM"));
        CAMBODIA_KHMER = getSpacingAndPunctuations(new Locale("km", "KH"));
        LAOS_LAO = getSpacingAndPunctuations(new Locale("lo", "LA"));
    }

    private SpacingAndPunctuations getSpacingAndPunctuations(final Locale locale) {
        final RunInLocale<SpacingAndPunctuations> job = new RunInLocale<SpacingAndPunctuations>() {
            @Override
            protected SpacingAndPunctuations job(Resources res) {
                return new SpacingAndPunctuations(res);
            }
        };
        return job.runInLocale(getContext().getResources(), locale);
    }

    private static void testingStandardWordSeparator(final SpacingAndPunctuations sp) {
        assertTrue("Tab",         sp.isWordSeparator('\t'));
        assertTrue("Newline",     sp.isWordSeparator('\n'));
        assertTrue("Space",       sp.isWordSeparator(' '));
        assertTrue("Exclamation", sp.isWordSeparator('!'));
        assertTrue("Quotation",   sp.isWordSeparator('"'));
        assertFalse("Number",     sp.isWordSeparator('#'));
        assertFalse("Dollar",     sp.isWordSeparator('$'));
        assertFalse("Percent",    sp.isWordSeparator('%'));
        assertTrue("Ampersand",   sp.isWordSeparator('&'));
        assertFalse("Apostrophe", sp.isWordSeparator('\''));
        assertTrue("L Paren",     sp.isWordSeparator('('));
        assertTrue("R Paren",     sp.isWordSeparator(')'));
        assertTrue("Asterisk",    sp.isWordSeparator('*'));
        assertTrue("Plus",        sp.isWordSeparator('+'));
        assertTrue("Comma",       sp.isWordSeparator(','));
        assertFalse("Minus",      sp.isWordSeparator('-'));
        assertTrue("Period",      sp.isWordSeparator('.'));
        assertTrue("Slash",       sp.isWordSeparator('/'));
        assertTrue("Colon",       sp.isWordSeparator(':'));
        assertTrue("Semicolon",   sp.isWordSeparator(';'));
        assertTrue("L Angle",     sp.isWordSeparator('<'));
        assertTrue("Equal",       sp.isWordSeparator('='));
        assertTrue("R Angle",     sp.isWordSeparator('>'));
        assertTrue("Question",    sp.isWordSeparator('?'));
        assertFalse("Atmark",     sp.isWordSeparator('@'));
        assertTrue("L S Bracket", sp.isWordSeparator('['));
        assertFalse("B Slash",    sp.isWordSeparator('\\'));
        assertTrue("R S Bracket", sp.isWordSeparator(']'));
        assertFalse("Circumflex", sp.isWordSeparator('^'));
        assertTrue("Underscore",  sp.isWordSeparator('_'));
        assertFalse("Grave",      sp.isWordSeparator('`'));
        assertTrue("L C Brace",   sp.isWordSeparator('{'));
        assertTrue("V Line",      sp.isWordSeparator('|'));
        assertTrue("R C Brace",   sp.isWordSeparator('}'));
        assertFalse("Tilde",      sp.isWordSeparator('~'));
    }

    public void testWordSeparator() {
        testingStandardWordSeparator(ENGLISH);
        testingStandardWordSeparator(FRENCH);
        testingStandardWordSeparator(CANADA_FRENCH);
        testingStandardWordSeparator(ARMENIA_ARMENIAN);
        assertTrue(ARMENIA_ARMENIAN.isWordSeparator(ARMENIAN_FULL_STOP));
        assertTrue(ARMENIA_ARMENIAN.isWordSeparator(ARMENIAN_COMMA));
        // TODO: We should fix these.
        testingStandardWordSeparator(ARMENIAN);
        assertFalse(ARMENIAN.isWordSeparator(ARMENIAN_FULL_STOP));
        assertFalse(ARMENIAN.isWordSeparator(ARMENIAN_COMMA));
    }

    private static void testingStandardWordConnector(final SpacingAndPunctuations sp) {
        assertFalse("Tab",         sp.isWordConnector('\t'));
        assertFalse("Newline",     sp.isWordConnector('\n'));
        assertFalse("Space",       sp.isWordConnector(' '));
        assertFalse("Exclamation", sp.isWordConnector('!'));
        assertFalse("Quotation",   sp.isWordConnector('"'));
        assertFalse("Number",      sp.isWordConnector('#'));
        assertFalse("Dollar",      sp.isWordConnector('$'));
        assertFalse("Percent",     sp.isWordConnector('%'));
        assertFalse("Ampersand",   sp.isWordConnector('&'));
        assertTrue("Apostrophe",   sp.isWordConnector('\''));
        assertFalse("L Paren",     sp.isWordConnector('('));
        assertFalse("R Paren",     sp.isWordConnector(')'));
        assertFalse("Asterisk",    sp.isWordConnector('*'));
        assertFalse("Plus",        sp.isWordConnector('+'));
        assertFalse("Comma",       sp.isWordConnector(','));
        assertTrue("Minus",        sp.isWordConnector('-'));
        assertFalse("Period",      sp.isWordConnector('.'));
        assertFalse("Slash",       sp.isWordConnector('/'));
        assertFalse("Colon",       sp.isWordConnector(':'));
        assertFalse("Semicolon",   sp.isWordConnector(';'));
        assertFalse("L Angle",     sp.isWordConnector('<'));
        assertFalse("Equal",       sp.isWordConnector('='));
        assertFalse("R Angle",     sp.isWordConnector('>'));
        assertFalse("Question",    sp.isWordConnector('?'));
        assertFalse("Atmark",      sp.isWordConnector('@'));
        assertFalse("L S Bracket", sp.isWordConnector('['));
        assertFalse("B Slash",     sp.isWordConnector('\\'));
        assertFalse("R S Bracket", sp.isWordConnector(']'));
        assertFalse("Circumflex",  sp.isWordConnector('^'));
        assertFalse("Underscore",  sp.isWordConnector('_'));
        assertFalse("Grave",       sp.isWordConnector('`'));
        assertFalse("L C Brace",   sp.isWordConnector('{'));
        assertFalse("V Line",      sp.isWordConnector('|'));
        assertFalse("R C Brace",   sp.isWordConnector('}'));
        assertFalse("Tilde",       sp.isWordConnector('~'));

    }

    public void testWordConnector() {
        testingStandardWordConnector(ENGLISH);
        testingStandardWordConnector(FRENCH);
        testingStandardWordConnector(CANADA_FRENCH);
        testingStandardWordConnector(ARMENIA_ARMENIAN);
    }

    private static void testingCommonPrecededBySpace(final SpacingAndPunctuations sp) {
        assertFalse("Tab",         sp.isUsuallyPrecededBySpace('\t'));
        assertFalse("Newline",     sp.isUsuallyPrecededBySpace('\n'));
        assertFalse("Space",       sp.isUsuallyPrecededBySpace(' '));
        //assertFalse("Exclamation", sp.isUsuallyPrecededBySpace('!'));
        assertFalse("Quotation",   sp.isUsuallyPrecededBySpace('"'));
        assertFalse("Number",      sp.isUsuallyPrecededBySpace('#'));
        assertFalse("Dollar",      sp.isUsuallyPrecededBySpace('$'));
        assertFalse("Percent",     sp.isUsuallyPrecededBySpace('%'));
        assertTrue("Ampersand",    sp.isUsuallyPrecededBySpace('&'));
        assertFalse("Apostrophe",  sp.isUsuallyPrecededBySpace('\''));
        assertTrue("L Paren",      sp.isUsuallyPrecededBySpace('('));
        assertFalse("R Paren",     sp.isUsuallyPrecededBySpace(')'));
        assertFalse("Asterisk",    sp.isUsuallyPrecededBySpace('*'));
        assertFalse("Plus",        sp.isUsuallyPrecededBySpace('+'));
        assertFalse("Comma",       sp.isUsuallyPrecededBySpace(','));
        assertFalse("Minus",       sp.isUsuallyPrecededBySpace('-'));
        assertFalse("Period",      sp.isUsuallyPrecededBySpace('.'));
        assertFalse("Slash",       sp.isUsuallyPrecededBySpace('/'));
        //assertFalse("Colon",       sp.isUsuallyPrecededBySpace(':'));
        //assertFalse("Semicolon",   sp.isUsuallyPrecededBySpace(';'));
        assertFalse("L Angle",     sp.isUsuallyPrecededBySpace('<'));
        assertFalse("Equal",       sp.isUsuallyPrecededBySpace('='));
        assertFalse("R Angle",     sp.isUsuallyPrecededBySpace('>'));
        //assertFalse("Question",    sp.isUsuallyPrecededBySpace('?'));
        assertFalse("Atmark",      sp.isUsuallyPrecededBySpace('@'));
        assertTrue("L S Bracket",  sp.isUsuallyPrecededBySpace('['));
        assertFalse("B Slash",     sp.isUsuallyPrecededBySpace('\\'));
        assertFalse("R S Bracket", sp.isUsuallyPrecededBySpace(']'));
        assertFalse("Circumflex",  sp.isUsuallyPrecededBySpace('^'));
        assertFalse("Underscore",  sp.isUsuallyPrecededBySpace('_'));
        assertFalse("Grave",       sp.isUsuallyPrecededBySpace('`'));
        assertTrue("L C Brace",    sp.isUsuallyPrecededBySpace('{'));
        assertFalse("V Line",      sp.isUsuallyPrecededBySpace('|'));
        assertFalse("R C Brace",   sp.isUsuallyPrecededBySpace('}'));
        assertFalse("Tilde",       sp.isUsuallyPrecededBySpace('~'));
    }

    private static void testingStandardPrecededBySpace(final SpacingAndPunctuations sp) {
        testingCommonPrecededBySpace(sp);
        assertFalse("Exclamation", sp.isUsuallyPrecededBySpace('!'));
        assertFalse("Colon",       sp.isUsuallyPrecededBySpace(':'));
        assertFalse("Semicolon",   sp.isUsuallyPrecededBySpace(';'));
        assertFalse("Question",    sp.isUsuallyPrecededBySpace('?'));
    }

    public void testIsUsuallyPrecededBySpace() {
        testingStandardPrecededBySpace(ENGLISH);
        testingCommonPrecededBySpace(FRENCH);
        assertTrue("Exclamation", FRENCH.isUsuallyPrecededBySpace('!'));
        assertTrue("Colon",       FRENCH.isUsuallyPrecededBySpace(':'));
        assertTrue("Semicolon",   FRENCH.isUsuallyPrecededBySpace(';'));
        assertTrue("Question",    FRENCH.isUsuallyPrecededBySpace('?'));
        testingCommonPrecededBySpace(CANADA_FRENCH);
        assertFalse("Exclamation", CANADA_FRENCH.isUsuallyPrecededBySpace('!'));
        assertTrue("Colon",        CANADA_FRENCH.isUsuallyPrecededBySpace(':'));
        assertFalse("Semicolon",   CANADA_FRENCH.isUsuallyPrecededBySpace(';'));
        assertFalse("Question",    CANADA_FRENCH.isUsuallyPrecededBySpace('?'));
        testingStandardPrecededBySpace(ARMENIA_ARMENIAN);
    }

    private static void testingStandardFollowedBySpace(final SpacingAndPunctuations sp) {
        assertFalse("Tab",         sp.isUsuallyFollowedBySpace('\t'));
        assertFalse("Newline",     sp.isUsuallyFollowedBySpace('\n'));
        assertFalse("Space",       sp.isUsuallyFollowedBySpace(' '));
        assertTrue("Exclamation",  sp.isUsuallyFollowedBySpace('!'));
        assertFalse("Quotation",   sp.isUsuallyFollowedBySpace('"'));
        assertFalse("Number",      sp.isUsuallyFollowedBySpace('#'));
        assertFalse("Dollar",      sp.isUsuallyFollowedBySpace('$'));
        assertFalse("Percent",     sp.isUsuallyFollowedBySpace('%'));
        assertTrue("Ampersand",    sp.isUsuallyFollowedBySpace('&'));
        assertFalse("Apostrophe",  sp.isUsuallyFollowedBySpace('\''));
        assertFalse("L Paren",     sp.isUsuallyFollowedBySpace('('));
        assertTrue("R Paren",      sp.isUsuallyFollowedBySpace(')'));
        assertFalse("Asterisk",    sp.isUsuallyFollowedBySpace('*'));
        assertFalse("Plus",        sp.isUsuallyFollowedBySpace('+'));
        assertTrue("Comma",        sp.isUsuallyFollowedBySpace(','));
        assertFalse("Minus",       sp.isUsuallyFollowedBySpace('-'));
        assertTrue("Period",       sp.isUsuallyFollowedBySpace('.'));
        assertFalse("Slash",       sp.isUsuallyFollowedBySpace('/'));
        assertTrue("Colon",        sp.isUsuallyFollowedBySpace(':'));
        assertTrue("Semicolon",    sp.isUsuallyFollowedBySpace(';'));
        assertFalse("L Angle",     sp.isUsuallyFollowedBySpace('<'));
        assertFalse("Equal",       sp.isUsuallyFollowedBySpace('='));
        assertFalse("R Angle",     sp.isUsuallyFollowedBySpace('>'));
        assertTrue("Question",     sp.isUsuallyFollowedBySpace('?'));
        assertFalse("Atmark",      sp.isUsuallyFollowedBySpace('@'));
        assertFalse("L S Bracket", sp.isUsuallyFollowedBySpace('['));
        assertFalse("B Slash",     sp.isUsuallyFollowedBySpace('\\'));
        assertTrue("R S Bracket",  sp.isUsuallyFollowedBySpace(']'));
        assertFalse("Circumflex",  sp.isUsuallyFollowedBySpace('^'));
        assertFalse("Underscore",  sp.isUsuallyFollowedBySpace('_'));
        assertFalse("Grave",       sp.isUsuallyFollowedBySpace('`'));
        assertFalse("L C Brace",   sp.isUsuallyFollowedBySpace('{'));
        assertFalse("V Line",      sp.isUsuallyFollowedBySpace('|'));
        assertTrue("R C Brace",    sp.isUsuallyFollowedBySpace('}'));
        assertFalse("Tilde",       sp.isUsuallyFollowedBySpace('~'));
    }

    public void testIsUsuallyFollowedBySpace() {
        testingStandardFollowedBySpace(ENGLISH);
        testingStandardFollowedBySpace(FRENCH);
        testingStandardFollowedBySpace(CANADA_FRENCH);
        testingStandardFollowedBySpace(ARMENIA_ARMENIAN);
        assertTrue(ARMENIA_ARMENIAN.isUsuallyFollowedBySpace(ARMENIAN_FULL_STOP));
        assertTrue(ARMENIA_ARMENIAN.isUsuallyFollowedBySpace(ARMENIAN_COMMA));
    }

    private static void testingStandardSentenceSeparator(final SpacingAndPunctuations sp) {
        assertFalse("Tab",         sp.isUsuallyFollowedBySpace('\t'));
        assertFalse("Newline",     sp.isUsuallyFollowedBySpace('\n'));
        assertFalse("Space",       sp.isUsuallyFollowedBySpace(' '));
        assertFalse("Exclamation", sp.isUsuallyFollowedBySpace('!'));
        assertFalse("Quotation",   sp.isUsuallyFollowedBySpace('"'));
        assertFalse("Number",      sp.isUsuallyFollowedBySpace('#'));
        assertFalse("Dollar",      sp.isUsuallyFollowedBySpace('$'));
        assertFalse("Percent",     sp.isUsuallyFollowedBySpace('%'));
        assertFalse("Ampersand",   sp.isUsuallyFollowedBySpace('&'));
        assertFalse("Apostrophe",  sp.isUsuallyFollowedBySpace('\''));
        assertFalse("L Paren",     sp.isUsuallyFollowedBySpace('('));
        assertFalse("R Paren",     sp.isUsuallyFollowedBySpace(')'));
        assertFalse("Asterisk",    sp.isUsuallyFollowedBySpace('*'));
        assertFalse("Plus",        sp.isUsuallyFollowedBySpace('+'));
        assertFalse("Comma",       sp.isUsuallyFollowedBySpace(','));
        assertFalse("Minus",       sp.isUsuallyFollowedBySpace('-'));
        assertTrue("Period",       sp.isUsuallyFollowedBySpace('.'));
        assertFalse("Slash",       sp.isUsuallyFollowedBySpace('/'));
        assertFalse("Colon",       sp.isUsuallyFollowedBySpace(':'));
        assertFalse("Semicolon",   sp.isUsuallyFollowedBySpace(';'));
        assertFalse("L Angle",     sp.isUsuallyFollowedBySpace('<'));
        assertFalse("Equal",       sp.isUsuallyFollowedBySpace('='));
        assertFalse("R Angle",     sp.isUsuallyFollowedBySpace('>'));
        assertFalse("Question",    sp.isUsuallyFollowedBySpace('?'));
        assertFalse("Atmark",      sp.isUsuallyFollowedBySpace('@'));
        assertFalse("L S Bracket", sp.isUsuallyFollowedBySpace('['));
        assertFalse("B Slash",     sp.isUsuallyFollowedBySpace('\\'));
        assertFalse("R S Bracket", sp.isUsuallyFollowedBySpace(']'));
        assertFalse("Circumflex",  sp.isUsuallyFollowedBySpace('^'));
        assertFalse("Underscore",  sp.isUsuallyFollowedBySpace('_'));
        assertFalse("Grave",       sp.isUsuallyFollowedBySpace('`'));
        assertFalse("L C Brace",   sp.isUsuallyFollowedBySpace('{'));
        assertFalse("V Line",      sp.isUsuallyFollowedBySpace('|'));
        assertFalse("R C Brace",   sp.isUsuallyFollowedBySpace('}'));
        assertFalse("Tilde",       sp.isUsuallyFollowedBySpace('~'));
    }

    public void isSentenceSeparator() {
        testingStandardSentenceSeparator(ENGLISH);
        try {
            testingStandardSentenceSeparator(ARMENIA_ARMENIAN);
            fail("Armenian Sentence Separator");
        } catch (final AssertionFailedError e) {
            assertEquals("Period", e.getMessage());
        }
        assertTrue(ARMENIA_ARMENIAN.isSentenceSeparator(ARMENIAN_FULL_STOP));
        assertFalse(ARMENIA_ARMENIAN.isSentenceSeparator(ARMENIAN_COMMA));
    }

    public void testLanguageHasSpace() {
        assertTrue(ENGLISH.mCurrentLanguageHasSpaces);
        assertTrue(FRENCH.mCurrentLanguageHasSpaces);
        assertTrue(GERMAN.mCurrentLanguageHasSpaces);
        assertFalse(THAI.mCurrentLanguageHasSpaces);
        assertFalse(CAMBODIA_KHMER.mCurrentLanguageHasSpaces);
        assertFalse(LAOS_LAO.mCurrentLanguageHasSpaces);
        // TODO: We should fix these.
        assertTrue(KHMER.mCurrentLanguageHasSpaces);
        assertTrue(LAO.mCurrentLanguageHasSpaces);
    }

    public void testUsesAmericanTypography() {
        assertTrue(ENGLISH.mUsesAmericanTypography);
        assertTrue(UNITED_STATES.mUsesAmericanTypography);
        assertTrue(UNITED_KINGDOM.mUsesAmericanTypography);
        assertTrue(INDIA_ENGLISH.mUsesAmericanTypography);
        assertFalse(FRENCH.mUsesAmericanTypography);
        assertFalse(GERMAN.mUsesAmericanTypography);
        assertFalse(SWISS_GERMAN.mUsesAmericanTypography);
    }

    public void testUsesGermanRules() {
        assertFalse(ENGLISH.mUsesGermanRules);
        assertFalse(FRENCH.mUsesGermanRules);
        assertTrue(GERMAN.mUsesGermanRules);
        assertTrue(SWISS_GERMAN.mUsesGermanRules);
    }

    // Punctuations for phone.
    private static final String[] PUNCTUATION_LABELS_PHONE = {
        "!", "?", ",", ":", ";", "\"", "(", ")", "'", "-", "/", "@", "_"
    };
    private static final String[] PUNCTUATION_WORDS_PHONE_LTR = PUNCTUATION_LABELS_PHONE;
    private static final String[] PUNCTUATION_WORDS_PHONE_HEBREW = {
        "!", "?", ",", ":", ";", "\"", ")", "(", "'", "-", "/", "@", "_"
    };
    // U+061F: "؟" ARABIC QUESTION MARK
    // U+060C: "،" ARABIC COMMA
    // U+061B: "؛" ARABIC SEMICOLON
    private static final String[] PUNCTUATION_LABELS_PHONE_ARABIC_PERSIAN = {
        "!", "\u061F", "\u060C", ":", "\u061B", "\"", "(", ")", "'", "-", "/", "@", "_"
    };
    private static final String[] PUNCTUATION_WORDS_PHONE_ARABIC_PERSIAN = {
        "!", "\u061F", "\u060C", ":", "\u061B", "\"", ")", "(", "'", "-", "/", "@", "_"
    };

    // Punctuations for tablet.
    private static final String[] PUNCTUATION_LABELS_TABLET = {
        ":", ";", "\"", "(", ")", "'", "-", "/", "@", "_"
    };
    private static final String[] PUNCTUATION_WORDS_TABLET_LTR = PUNCTUATION_LABELS_TABLET;
    private static final String[] PUNCTUATION_WORDS_TABLET_HEBREW = {
        ":", ";", "\"", ")", "(", "'", "-", "/", "@", "_"
    };
    private static final String[] PUNCTUATION_LABELS_TABLET_ARABIC_PERSIAN = {
        "!", "\u061F", ":", "\u061B", "\"", "'", "(", ")",  "-", "/", "@", "_"
    };
    private static final String[] PUNCTUATION_WORDS_TABLET_ARABIC_PERSIAN = {
        "!", "\u061F", ":", "\u061B", "\"", "'", ")", "(",  "-", "/", "@", "_"
    };

    private static void testingStandardPunctuationSuggestions(final SpacingAndPunctuations sp,
            final String[] punctuationLabels, final String[] punctuationWords) {
        final SuggestedWords suggestedWords = sp.mSuggestPuncList;
        assertFalse("typedWordValid", suggestedWords.mTypedWordValid);
        assertFalse("willAutoCorrect", suggestedWords.mWillAutoCorrect);
        assertTrue("isPunctuationSuggestions", suggestedWords.isPunctuationSuggestions());
        assertFalse("isObsoleteSuggestions", suggestedWords.mIsObsoleteSuggestions);
        assertFalse("isPrediction", suggestedWords.isPrediction());
        assertEquals("size", punctuationLabels.length, suggestedWords.size());
        for (int index = 0; index < suggestedWords.size(); index++) {
            assertEquals("punctuation label at " + index,
                    punctuationLabels[index], suggestedWords.getLabel(index));
            assertEquals("punctuation word at " + index,
                    punctuationWords[index], suggestedWords.getWord(index));
        }
    }

    public void testPhonePunctuationSuggestions() {
        if (!isPhone()) {
            return;
        }
        testingStandardPunctuationSuggestions(ENGLISH,
                PUNCTUATION_LABELS_PHONE, PUNCTUATION_WORDS_PHONE_LTR);
        testingStandardPunctuationSuggestions(FRENCH,
                PUNCTUATION_LABELS_PHONE, PUNCTUATION_WORDS_PHONE_LTR);
        testingStandardPunctuationSuggestions(GERMAN,
                PUNCTUATION_LABELS_PHONE, PUNCTUATION_WORDS_PHONE_LTR);
        testingStandardPunctuationSuggestions(ARABIC,
                PUNCTUATION_LABELS_PHONE_ARABIC_PERSIAN, PUNCTUATION_WORDS_PHONE_ARABIC_PERSIAN);
        testingStandardPunctuationSuggestions(PERSIAN,
                PUNCTUATION_LABELS_PHONE_ARABIC_PERSIAN, PUNCTUATION_WORDS_PHONE_ARABIC_PERSIAN);
        testingStandardPunctuationSuggestions(HEBREW,
                PUNCTUATION_LABELS_PHONE, PUNCTUATION_WORDS_PHONE_HEBREW);
    }

    public void testTabletPunctuationSuggestions() {
        if (!isTablet()) {
            return;
        }
        testingStandardPunctuationSuggestions(ENGLISH,
                PUNCTUATION_LABELS_TABLET, PUNCTUATION_WORDS_TABLET_LTR);
        testingStandardPunctuationSuggestions(FRENCH,
                PUNCTUATION_LABELS_TABLET, PUNCTUATION_WORDS_TABLET_LTR);
        testingStandardPunctuationSuggestions(GERMAN,
                PUNCTUATION_LABELS_TABLET, PUNCTUATION_WORDS_TABLET_LTR);
        testingStandardPunctuationSuggestions(ARABIC,
                PUNCTUATION_LABELS_TABLET_ARABIC_PERSIAN, PUNCTUATION_WORDS_TABLET_ARABIC_PERSIAN);
        testingStandardPunctuationSuggestions(PERSIAN,
                PUNCTUATION_LABELS_TABLET_ARABIC_PERSIAN, PUNCTUATION_WORDS_TABLET_ARABIC_PERSIAN);
        testingStandardPunctuationSuggestions(HEBREW,
                PUNCTUATION_LABELS_TABLET, PUNCTUATION_WORDS_TABLET_HEBREW);
    }
}
