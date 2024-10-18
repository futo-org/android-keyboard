# FUTO Keyboard

The goal is to make a good keyboard that doesn't spy on users. This repository is a fork of [LatinIME (the Android Open-Source Keyboard)](https://android.googlesource.com/platform/packages/inputmethods/LatinIME).

The code is licensed under the [FUTO Source First License 1.1](LICENSE.md).

Check out the [FUTO Keyboard Wiki](https://gitlab.futo.org/alex/keyboard-wiki/-/wikis/FUTO-Keyboard)

## Layouts

If you want to contribute layouts, check out the [layouts repo](https://github.com/futo-org/futo-keyboard-layouts).

## Building

When cloning the repository, you must perform a recursive clone to fetch all dependencies:
```
git clone --recursive https://gitlab.futo.org/keyboard/latinime.git
```

You can also initialize this way if you forgot to specify the recursive clone:
```
git submodule update --init --recursive
```

You can then open the project in Android Studio and build it that way.