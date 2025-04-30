package org.futo.inputmethod.latin.uix.settings.pages.credits


data class ThirdPartyItem(
    val name: String,
    val description: String,
    val projectUrl: String,
    val copyright: String,
    val license: License
)

typealias TP = ThirdPartyItem

@Suppress("HardCodedStringLiteral")
val ThirdPartyList: List<ThirdPartyItem> = listOf(
    TP(
        "AOSP LatinIME",
        "Keyboard based on AOSP LatinIME",
        "https://android.googlesource.com/platform/packages/inputmethods/LatinIME/",
        "Copyright (c) 2011 The Android Open Source Project",
        License.Apache2
    ),
    TP(
        "OpenAI Whisper",
        "Voice Input powered by OpenAI Whisper",
        "https://github.com/openai/whisper",
        "Copyright (c) 2022 OpenAI",
        License.Apache2
    ),
    TP(
        "ggml projects",
        "whisper.cpp, llama.cpp, ggml",
        "https://ggml.ai",
        "Copyright (c) 2023 Georgi Gerganov",
        License.MIT
    ),
    TP(
        "Feather Icons",
        "Feather Icons",
        "https://feathericons.com",
        "Copyright (c) 2013-2017 Cole Bemis",
        License.MIT,
    ),
    TP(
        "WebRTC VAD",
        "WebRTC VAD",
        "https://webrtc.org",
        "Copyright (c) 2011 The WebRTC project authors",
        License.BSD3
    ),
    TP(
        "android-vad",
        "android-vad",
        "https://github.com/gkonovalov/android-vad",
        "Copyright (c) 2023 Georgiy Konovalov",
        License.MIT
    ),
    TP(
        "LineageOS LatinIME",
        "Some keyboard layouts used from LineageOS",
        "https://github.com/LineageOS/android_packages_inputmethods_LatinIME",
        "Copyright (c) 2015 The CyanogenMod Project",
        License.Apache2
    ),
    TP(
        "Reorderable",
        "Reorderable",
        "https://github.com/Calvin-LL/Reorderable",
        "Copyright (c) 2023 Calvin Liang",
        License.Apache2
    ),
    TP(
        "Noto Emoji",
        "Noto Emoji",
        "https://fonts.google.com/noto/specimen/Noto+Emoji",
        "Copyright (c) 2013 Google LLC",
        License.OFL1_1
    ),
    TP(
        "Noto Mono",
        "Noto Mono",
        "https://fonts.google.com/noto",
        "Copyright (c) 2022 The Noto Project Authors",
        License.OFL1_1
    ),
    TP(
        "Anton Font",
        "Anton Font",
        "https://fonts.google.com/specimen/Anton",
        "Copyright (c) 2020 The Anton Project Authors",
        License.OFL1_1
    ),
)