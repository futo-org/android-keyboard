# Note: Run this tool in the make-keyboard-text-py directory

import json
import glob

def load_data():
    all_data = []
    for fname in glob.glob("locales/*.json"):
        with open(fname, "r") as f:
            all_data.append(json.loads(f.read()))

    return all_data

def transform_to_texts(locale_data):
    texts = { }

    texts["locale"] = locale_data["locale"]

    prefixes = ['morekeys_', 'keyspec_', 'label_', 'keylabel_', 'keyhintlabel_', 'additional_morekeys_']

    for prefix in prefixes:
        for k, v in locale_data[prefix.rstrip("_")].items():
            if isinstance(v, list):
                texts[prefix + k] = ",".join(v)
            else:
                texts[prefix + k] = v

    for k, v in locale_data["other"].items():
        if isinstance(v, list):
            texts[k] = ",".join(v)
        else:
            texts[k] = v

    return texts

def transform_texts_dict_to_array(texts_dict, names):
    texts = []

    last_idx = 0
    for name in names:
        if name in texts_dict:
            texts.append(texts_dict[name])
            last_idx = len(texts)
        else:
            texts.append(None)

    return texts[:last_idx]

def dump_names(names):
    out = ""
    for name in names:
        out += "        " + json.dumps(name) + ",\n"

    return out[:-2]

def dump_texts(texts_by_locale):
    out = ""
    for locale, texts in texts_by_locale.items():
        out += "    private static final String[] TEXTS_" + locale + " = {\n"
        for item in texts:
            if item == "":
                out += "        EMPTY,\n"
            else:
                out += "        " + json.dumps(item) + ",\n"
        out = out[:-2] + "\n    };\n\n"

    return out

def dump_locales_and_texts(texts_by_locale):
    out = ""
    for locale in texts_by_locale.keys():
        out += "        " + json.dumps(locale) + ", " + "TEXTS_" + locale + ",\n"

    return out[:-2]

def generate_templated_code(names, texts_by_locale):
    out = ""
    with open("src/KeyboardTextsTable.tmpl", "r") as f:
        for line in f:
            if "@NAMES@" in line:
                out += dump_names(names) + "\n"
            elif "@TEXTS@" in line:
                out += dump_texts(texts_by_locale) + "\n"
            elif "@LOCALES_AND_TEXTS@" in line:
                out += dump_locales_and_texts(texts_by_locale) + "\n"
            else:
                out += line
    return out

if __name__ == "__main__":
    data = load_data()

    data = list(map(transform_to_texts, data))

    all_names = set([k for texts in data for k in texts.keys()])
    names_and_uses = [
        (name, sum([
                       1 if name in texts else 0
                       for texts in data
                   ]))

        for name in all_names
    ]

    names_and_uses.sort(key=lambda x: -x[1])

    names = [x[0] for x in names_and_uses]

    texts_by_locale = {}
    for texts in data:
        texts_by_locale[texts["locale"]] = transform_texts_dict_to_array(texts, names)


    code = generate_templated_code(names, texts_by_locale)

    with open("../../java/src/org/futo/inputmethod/keyboard/internal/KeyboardTextsTable.java", "w") as f:
        f.write(code)
