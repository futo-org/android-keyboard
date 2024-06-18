# FUTO Keyboard

The goal is to make a good keyboard that doesn't spy on users. This repository is a fork of [LatinIME (the Android Open-Source Keyboard)](https://android.googlesource.com/platform/packages/inputmethods/LatinIME).

The code is licensed under the [FUTO Source First License 1.0](LICENSE.md).

Check out the [FUTO Keyboard Wiki](https://gitlab.futo.org/alex/keyboard-wiki/-/wikis/FUTO-Keyboard)

## Building

When cloning the repository, you must perform a recursive clone to fetch all dependencies:
```
git clone --recursive git@gitlab.futo.org:alex/latinime.git
```

You can also initialize this way if you forgot to specify the recursive clone:
```
git submodule update --init --recursive
```

You can then open the project in Android Studio and build it that way.