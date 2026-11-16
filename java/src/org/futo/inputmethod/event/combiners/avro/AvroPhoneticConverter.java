package org.futo.inputmethod.event.combiners.avro;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts Latin text typed with the Avro Phonetic scheme into Bengali script.
 *
 * Avro Phonetic is a transliteration scheme: Latin letters are matched against a table of
 * patterns (longest match first) and the replacement may depend on its context, such as whether the
 * preceding character is a consonant (which selects the dependent vowel sign instead of the
 * independent vowel) or whether the sequence is at the start of a word.
 *
 * Conjuncts are written explicitly (e.g. "kSh" gives ক্ষ), there is no automatic hasanta insertion,
 * ",," inserts an explicit hasanta, and a backtick (`) separates sequences that would otherwise merge.
 *
 * This class has no Android dependencies so it can be unit tested on the plain JVM.
 */
public final class AvroPhoneticConverter {
    private AvroPhoneticConverter() {}

    private static final String HASANTA = "্";
    private static final String ZWJ = "\u200D";
    private static final String ZWNJ = "\u200C";
    private static final int MAX_PATTERN_LENGTH = 5;

    /** Letters for which upper and lower case mean different things. All others are lowercased. */
    private static final String CASE_SENSITIVE = "oiudgjnrstyz";
    private static final String VOWELS = "aeiou";
    private static final String CONSONANTS = "bcdfghjklmnpqrstvwxyz";

    private static final Map<String, String> TABLE = new HashMap<>();

    /** Dependent vowel sign (index 0) and independent vowel (index 1) for each vowel pattern. */
    private static final Map<String, String[]> VOWEL_FORMS = new HashMap<>();

    private static void table(final String... pairs) {
        for (int i = 0; i < pairs.length; i += 2) TABLE.put(pairs[i], pairs[i + 1]);
    }

    private static void vowels(final String... triples) {
        for (int i = 0; i < triples.length; i += 3) {
            final String[] forms = {triples[i + 1], triples[i + 2]};
            VOWEL_FORMS.put(triples[i], forms);
            TABLE.put(triples[i], triples[i + 1]);
        }
    }

    static {
        // Vowels: pattern, dependent sign, independent letter
        vowels(
                "a", "া", "আ",
                "i", "ি", "ই", "I", "ী", "ঈ", "ee", "ী", "ঈ",
                "u", "ু", "উ", "oo", "ু", "উ", "U", "ূ", "ঊ",
                "e", "ে", "এ",
                "OI", "ৈ", "ঐ", "OU", "ৌ", "ঔ",
                "O", "ো", "ও",
                "rri", "ৃ", "ঋ");

        // Single consonants and signs
        table(
                "b", "ব", "c", "চ", "d", "দ", "D", "ড", "f", "ফ", "g", "গ", "G", "গ",
                "h", "হ", "j", "জ", "J", "জ", "k", "ক", "l", "ল", "m", "ম", "n", "ন",
                "N", "ণ", "p", "প", "q", "ক", "R", "ড়", "s", "স", "S", "শ", "t", "ত",
                "T", "ট", "v", "ভ", "z", "য", "Y", "য়",
                "Z", "্য", "^", "ঁ", ":", "ঃ", "$", "৳", ".", "।", ",", ",", "`", "",
                ",,", HASANTA + ZWNJ, "t``", "ৎ", "...", "...", "..", "।।", ".`", ".", ":`", ":", "^`", "^", "o`", "",
                "O`", "ো", "OI`", "ৈ", "OU`", "ৌ", "rri`", "ৃ",
                "aZ", "অ্যা", "oZ", "অ্য",
                // Context dependent, see resolve()
                "o", "", "r", "র", "rr", "রর", "ry", "র" + ZWJ + "্য", "rZ", "র" + ZWJ + "্য",
                "w", "ও", "x", "ক্স", "y", "্য");

        // Aspirates and nasals
        table(
                "bh", "ভ", "ch", "ছ", "Dh", "ঢ", "dh", "ধ", "gh", "ঘ", "Gh", "ঘ", "jh", "ঝ",
                "kh", "খ", "ph", "ফ", "Rh", "ঢ়", "sh", "শ", "Sh", "ষ", "th", "থ", "Th", "ঠ",
                "ng", "ং", "Ng", "ঙ", "NG", "ঞ");

        // Conjuncts
        table(
                "bj", "ব্জ", "bd", "ব্দ", "bb", "ব্ব", "bl", "ব্ল", "bhl", "ভ্ল",
                "cc", "চ্চ", "cch", "চ্ছ", "cchw", "চ্ছ্ব", "cNG", "চ্ঞ",
                "dg", "দ্গ", "dgh", "দ্ঘ", "dd", "দ্দ", "ddh", "দ্ধ", "dbh", "দ্ভ",
                "dv", "দ্ভ", "dm", "দ্ম", "dw", "দ্ব", "dhn", "ধ্ন", "dhm", "ধ্ম",
                "DD", "ড্ড",
                "gdh", "গ্ধ", "gn", "গ্ন", "gm", "গ্ম", "gl", "গ্ল",
                "gN", "গ্ণ", "gw", "গ্ব",
                "gg", "জ্ঞ", "gG", "জ্ঞ", "Gg", "জ্ঞ", "GG", "জ্ঞ",
                "fl", "ফ্ল", "kx", "ক্ষ", "mf", "ম্ফ", "sf", "স্ফ", "vl", "ভ্ল", "Gl", "গ্ল", "Gm", "গ্ম",
                "Gn", "গ্ন", "GN", "গ্ণ", "Nn", "ণ্ন", "Rg", "ড়্গ", "St", "শ্ত",
                "hN", "হ্ণ", "hn", "হ্ন", "hm", "হ্ম", "hl", "হ্ল",
                "jj", "জ্জ", "jjh", "জ্ঝ", "jw", "জ্ব", "jNG", "জ্ঞ",
                "kk", "ক্ক", "kT", "ক্ট", "kt", "ক্ত", "kl", "ক্ল", "ks", "ক্স", 
                "kw", "ক্ব", "kSh", "ক্ষ", "kkh", "ক্ষ", "kShN", "ক্ষ্ণ",
                "kShm", "ক্ষ্ম",
                "lk", "ল্ক", "lg", "ল্গ", "lT", "ল্ট", "lD", "ল্ড", "lp", "ল্প", "lm", "ল্ম", "ll", "ল্ল", "lb", "ল্ব", "lv", "ল্ভ",
                "mn", "ম্ন", "mp", "ম্প", "mph", "ম্ফ", "mb", "ম্ব", "mbh", "ম্ভ", "mv", "ম্ভ",
                "mm", "ম্ম", "ml", "ম্ল",
                "nj", "ঞ্জ", "nc", "ঞ্চ", "nch", "ঞ্ছ", "nn", "ন্ন", "nT", "ন্ট", "nTh", "ন্ঠ",
                "nD", "ন্ড", "nt", "ন্ত", "nth", "ন্থ", "nd", "ন্দ", "ndh", "ন্ধ",
                "ns", "ন্স", "nm", "ন্ম", "nw", "ন্ব", "nk", "ঙ্ক", "nkh", "ঙ্খ",
                "NGc", "ঞ্চ", "NGch", "ঞ্ছ", "NGj", "ঞ্জ", "NGjh", "ঞ্ঝ",
                "Ngk", "ঙ্ক", "Ngkh", "ঙ্খ", "Ngg", "ঙ্গ", "Nggh", "ঙ্ঘ", "Ngm", "ঙ্ম",
                "NT", "ণ্ট", "NTh", "ণ্ঠ", "ND", "ণ্ড", "NDh", "ণ্ঢ", "NN", "ণ্ণ", "Nm", "ণ্ম",
                "pT", "প্ট", "pt", "প্ত", "pn", "প্ন", "pp", "প্প", "pl", "প্ল", "ps", "প্স",
                "sT", "স্ট", "st", "স্ত", "sth", "স্থ", "sk", "স্ক", "skh", "স্খ",
                "sp", "স্প", "sph", "স্ফ", "sn", "স্ন", "sm", "স্ম", "sl", "স্ল", "sw", "স্ব",
                "Sc", "শ্চ", "Sch", "শ্ছ", "Sn", "শ্ন", "Sm", "শ্ম", "Sl", "শ্ল",
                "ShT", "ষ্ট", "ShTh", "ষ্ঠ", "Shk", "ষ্ক", "Shp", "ষ্প", "Shph", "ষ্ফ",
                "ShN", "ষ্ণ", "Shm", "ষ্ম",
                "tt", "ত্ত", "tth", "ত্থ", "tn", "ত্ন", "tm", "ত্ম", "tw", "ত্ব",
                "TT", "ট্ট", "Tm", "ট্ম");


        // Explicit exceptions and extra conjuncts of the scheme
        table(
                "bdh", "ব্ধ", "ghn", "ঘ্ন", "Ghn", "ঘ্ন", "Gdh", "গ্ধ",
                "kxm", "ক্ষ্ম", "kxN", "ক্ষ্ণ", "kkhN", "ক্ষ্ণ", "kkhm", "ক্ষ্ম",
                "lbh", "ল্ভ", "ldh", "ল্ধ", "mth", "ম্থ", "phl", "ফ্ল", "njh", "ঞ্ঝ",
                "shc", "শ্চ", "shl", "শ্ল", "shm", "শ্ম", "shn", "শ্ন", "sht", "শ্ত",
                "skl", "স্ক্ল", "spl", "স্প্ল", "Shf", "ষ্ফ", "Ngx", "ঙ্ষ", "Ngkx", "ঙ্ক্ষ",
                "NgkSh", "ঙ্ক্ষ", "Ngkkh", "ঙ্ক্ষ", "ngh", "ঙ্ঘ",
                "ksh", "কশ", "nsh", "নশ", "psh", "পশ", "sch", "সছ", "mpl", "মপ্ল",
                "shch", "শ্ছ", "lkh", "লখ", "lgh", "লঘ", "lph", "লফ",
                "ngr", "ংর", "Ngr", "ঙর", "NGr", "ঞর", "rry", "রর" + ZWJ + "্য", "rrZ", "রর" + ZWJ + "্য");

        // "ng" followed by a vowel is the ঙ্গ conjunct plus that vowel (e.g. "ange"), see resolve()
        for (final String v : new String[]{"a", "e", "i", "o", "u", "I", "O", "U", "OI", "OU"})
            TABLE.put("ng" + v, "ঙ্গ");

        // Digits
        for (char c = '0'; c <= '9'; c++) TABLE.put(String.valueOf(c), String.valueOf((char) ('০' + (c - '0'))));
    }

    private static boolean isVowel(final char c) { return c != 0 && VOWELS.indexOf(Character.toLowerCase(c)) >= 0; }
    private static boolean isConsonant(final char c) { return c != 0 && CONSONANTS.indexOf(Character.toLowerCase(c)) >= 0; }
    private static boolean isDigit(final char c) { return c >= '0' && c <= '9'; }
    private static boolean isPunctuation(final char c) { return !isVowel(c) && !isConsonant(c) && !isDigit(c); }

    /** Lowercases letters whose case carries no meaning in Avro. */
    private static String normalize(final CharSequence input) {
        final StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            final char c = input.charAt(i);
            sb.append(CASE_SENSITIVE.indexOf(Character.toLowerCase(c)) >= 0 ? c : Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static String resolve(final String find, final String defaultReplacement,
                                  final char prev, final char next) {
        final boolean afterConsonant = isConsonant(prev);
        final boolean backtickFollows = next == '`';

        if (find.length() > 2 && find.startsWith("ng") && find.charAt(2) != 'h' && find.charAt(2) != 'r') {
            final String vowel = find.substring(2);
            return defaultReplacement + resolve(vowel, TABLE.get(vowel), 'g', next);
        }

        final String[] vowelForms = VOWEL_FORMS.get(find);
        if (vowelForms != null) {
            switch (find) {
                case "a":
                    if (isPunctuation(prev) && !backtickFollows) return vowelForms[1];
                    if (!afterConsonant && prev != 'a' && !backtickFollows) return "য়া";
                    if (prev == 'a' && !backtickFollows) return vowelForms[1];
                    return vowelForms[0];
                case "O": case "oi": case "OI": case "ou": case "OU": case "rri":
                    return afterConsonant ? vowelForms[0] : vowelForms[1];
                default:
                    return (!afterConsonant && !backtickFollows) ? vowelForms[1] : vowelForms[0];
            }
        }

        switch (find) {
            case "o":
                if (isVowel(prev)) return prev == 'o' ? "অ" : "ও";
                if (isPunctuation(prev)) return "অ";
                return "";
            case "w":
                if (isPunctuation(prev) && isVowel(next)) return "ওয়";
                if (afterConsonant) return "্ব";
                return "ও";
            case "y":
                if (isPunctuation(prev)) return "ইয়";
                if (!afterConsonant) return "য়";
                return "্য";
            case "x":
                return isPunctuation(prev) ? "এক্স" : "ক্স";
            case "r":
                if (afterConsonant && prev != 'r' && prev != 'y' && prev != 'w' && prev != 'x' && prev != 'Z')
                    return "্র";
                return "র";
            case "rr":
                if (!afterConsonant && !isVowel(next) && next != 'r' && !isPunctuation(next)) return "র্";
                if (afterConsonant && prev != 'r') return "্রর";
                return "রর";
            case "ry": case "rZ":
                if (afterConsonant && prev != 'r' && prev != 'y' && prev != 'w' && prev != 'x') return "্র্য";
                return "র" + ZWJ + "্য";
            case ".":
                return isDigit(next) ? "." : "।";
            case ":":
                return isDigit(next) ? ":" : "ঃ";
            default:
                return defaultReplacement;
        }
    }

    public static String convert(final CharSequence input) {
        final String norm = normalize(input);
        final StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < norm.length()) {
            boolean matched = false;
            for (int len = Math.min(MAX_PATTERN_LENGTH, norm.length() - i); len >= 1; len--) {
                final String find = norm.substring(i, i + len);
                final String replacement = TABLE.get(find);
                if (replacement == null) continue;

                final char prev = i > 0 ? norm.charAt(i - 1) : 0;
                final char next = i + len < norm.length() ? norm.charAt(i + len) : 0;
                out.append(resolve(find, replacement, prev, next));
                i += len;
                matched = true;
                break;
            }
            if (!matched) {
                out.append(norm.charAt(i));
                i++;
            }
        }
        return out.toString();
    }
}
