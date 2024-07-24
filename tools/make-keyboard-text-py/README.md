# make-keyboard-text-py

Keyboard texts define language-specific key specifications, morekeys, etc.
For example, they allow a language to have a custom comma symbol, or custom parentheses, without needing
special cases in the keyboard layout definitions.

## Definitions

These are defined in json files located in the locales directory. There are a few predefined keys that act as prefixes.
For example, `morekeys.a` automatically becomes `!text/morekeys_a`. Items in the `other` key are prefix-less.

In morekeys, values can be arrays that are automatically joined by commas.

In general, morekeys for a specific letter such as "a" for a specific language should contain letters required to type in that language.
Other general morekeys can be placed in the "misc_a", and the user has the option to disable these extra letters.

## Updating

After updating a json file it's necessary to run the `src/generate.py` script to update the corresponding java file.
This script must be run in the `make-keyboard-text-py` directory by running `python3 src/generate.py`.
It is dependent on relative paths and it's necessary to be in the `make-keyboard-text-py` directory.