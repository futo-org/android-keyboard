package org.futo.inputmethod.event.combiners.avro;

import junit.framework.TestCase;

public class AvroPhoneticConverterTests extends TestCase {
    private static void check(final String expected, final String typed) {
        assertEquals("Typing \"" + typed + "\"", expected, AvroPhoneticConverter.convert(typed));
    }

    public void testSentence() {
        check("আমি বাংলা\u09DF গান গাই", "ami banglay gan gai");
        check("কেমন আছ?", "kemon acho?");
    }

    public void testWords() {
        check("প্রেম", "prem");
        check("শিক্ষা", "shikkha");
        check("খাও\u09DFা", "khaoa");
        check("ভাত", "bhat");
        check("তুমি", "tumi");
        check("কথা\u09DF", "kothay");
        check("অনেক", "onek");
        check("বন্ধু", "bondhu");
    }

    public void testCaseSensitivity() {
        check("ডিম", "Dim");
        check("দিম", "dim");
        // Letters whose case does not matter
        check(AvroPhoneticConverter.convert("ami"), "AMi");
    }

    public void testConjuncts() {
        check("ক্ষ", "kSh");
        check("ঙ্গে", "nge");
    }

    public void testVowelFormDependsOnContext() {
        // Independent vowel at the start, dependent sign after a consonant
        check("ইকি", "iki");
        check("আং", "ang");
        check("আঙ্গে", "ange");
    }

    public void testSymbolsAndNumbers() {
        check("বাংলা।", "bangla.");
        check("১.৫", "1.5");
        check("৳১০০", "$100");
        check("ৎ", "t``");
        check("ক্‌", "k,,");
    }
}
