// Standalone test for isExactMatch diacritic normalization.
// Compile: g++ -std=c++17 -o test_isexactmatch native/jni/tests/test_isexactmatch.cpp
// Run: ./test_isexactmatch

#include <string>
#include <cassert>
#include <cctype>
#include <cstdio>

// --- Copied from org_futo_inputmethod_latin_xlm_LanguageModel.cpp ---
// Keep in sync with the production code. This test verifies the
// normalization logic in isolation (no JNI / ggml dependencies).

static int append_diacritic_normalized(const unsigned char *str, size_t len, size_t i, std::string &out) {
    if(i + 1 >= len) return 0;
    unsigned char c1 = str[i];
    unsigned char c2 = str[i + 1];

    if(c1 == 0xC3) {
        switch(c2) {
            case 0x80: case 0x81: case 0x82: case 0x83: case 0x84: case 0x85:
            case 0xA0: case 0xA1: case 0xA2: case 0xA3: case 0xA4: case 0xA5:
                out += 'a'; return 2;
            case 0x86: case 0xA6:
                out += "ae"; return 2;
            case 0x87: case 0xA7:
                out += 'c'; return 2;
            case 0x88: case 0x89: case 0x8A: case 0x8B:
            case 0xA8: case 0xA9: case 0xAA: case 0xAB:
                out += 'e'; return 2;
            case 0x8C: case 0x8D: case 0x8E: case 0x8F:
            case 0xAC: case 0xAD: case 0xAE: case 0xAF:
                out += 'i'; return 2;
            case 0x91: case 0xB1:
                out += 'n'; return 2;
            case 0x92: case 0x93: case 0x94: case 0x95: case 0x96:
            case 0x98:
            case 0xB2: case 0xB3: case 0xB4: case 0xB5: case 0xB6:
            case 0xB8:
                out += 'o'; return 2;
            case 0x99: case 0x9A: case 0x9B: case 0x9C:
            case 0xB9: case 0xBA: case 0xBB: case 0xBC:
                out += 'u'; return 2;
            case 0x9D: case 0xBD: case 0x9F: case 0xBF:
                out += 'y'; return 2;
            default:
                return 0;
        }
    }

    if(c1 == 0xC5 && (c2 == 0x92 || c2 == 0x93)) {
        out += "oe"; return 2;
    }

    return 0;
}

bool isExactMatch(const std::string &a, const std::string &b){
    auto preprocess = [](const std::string &str) -> std::string {
        std::string result;
        for(size_t i = 0; i < str.size();) {
            unsigned char c = str[i];
            if(c == '\'' || c == '-' || c == ' ') {
                i++;
                continue;
            }
            int consumed = append_diacritic_normalized(
                    reinterpret_cast<const unsigned char *>(str.data()),
                    str.size(), i, result);
            if(consumed > 0) {
                i += consumed;
                continue;
            }
            result += (char)tolower(c);
            i++;
        }
        return result;
    };

    return preprocess(a) == preprocess(b);
}

// --- End copied code ---

static int tests_run = 0;
static int tests_passed = 0;

#define CHECK(expr) do { \
    tests_run++; \
    if(expr) { tests_passed++; } \
    else { fprintf(stderr, "FAIL: %s  (line %d)\n", #expr, __LINE__); } \
} while(0)

int main() {
    // --- French accents: lowercase ---
    CHECK(isExactMatch("garçon", "garcon"));
    CHECK(isExactMatch("café", "cafe"));
    CHECK(isExactMatch("être", "etre"));
    CHECK(isExactMatch("français", "francais"));
    CHECK(isExactMatch("après", "apres"));
    CHECK(isExactMatch("fête", "fete"));
    CHECK(isExactMatch("hôtel", "hotel"));
    CHECK(isExactMatch("où", "ou"));
    CHECK(isExactMatch("sûr", "sur"));
    CHECK(isExactMatch("maïs", "mais"));
    CHECK(isExactMatch("Noël", "Noel"));

    // --- French accents: mixed case ---
    CHECK(isExactMatch("Garçon", "GARCON"));
    CHECK(isExactMatch("CAFÉ", "CAFE"));
    CHECK(isExactMatch("Être", "ETRE"));
    CHECK(isExactMatch("Français", "FRANCAIS"));

    // --- oe/ae ligatures expand correctly ---
    CHECK(isExactMatch("cœur", "coeur"));
    CHECK(isExactMatch("Œuvre", "Oeuvre"));
    CHECK(isExactMatch("naïve", "naive"));

    // --- Apostrophes, dashes, spaces stripped ---
    CHECK(isExactMatch("l'homme", "lhomme"));
    CHECK(isExactMatch("d'accord", "daccord"));
    CHECK(isExactMatch("Hello-World", "helloworld"));
    CHECK(isExactMatch("peu importe", "peuimporte"));

    // --- Plain ASCII unchanged ---
    CHECK(isExactMatch("maison", "maison"));
    CHECK(isExactMatch("Maison", "MAISON"));

    // --- Different words must differ ---
    CHECK(!isExactMatch("maison", "maisonn"));
    CHECK(!isExactMatch("chat", "chien"));
    CHECK(!isExactMatch("bon", "bonne"));

    // --- Unmapped sequences preserve original behavior ---
    // ñ normalizes to 'n' (Latin-1 Supplement handling)
    CHECK(isExactMatch("ñandu", "nandu"));
    CHECK(!isExactMatch("ðtest", "dtest"));

    // --- Edge cases ---
    CHECK(isExactMatch("", ""));
    CHECK(!isExactMatch("a", ""));
    CHECK(!isExactMatch("", "a"));
    CHECK(isExactMatch("a", "a"));

    printf("%d/%d tests passed\n", tests_passed, tests_run);
    return (tests_passed == tests_run) ? 0 : 1;
}
