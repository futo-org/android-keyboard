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
        "mozc",
        "Japanese input powered by mozc",
        "https://github.com/google/mozc",
        "Copyright (c) 2010-2018, Google Inc",
        License.BSD3
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
    TP(
        "mozc dictionaries",
        "Japanese dictionaries include data from IPAdic",
        "https://github.com/google/mozc",
        "Copyright (c) 2000, 2001, 2002, 2003 Nara Institute of Science and Technology.  All Rights Reserved.",
        License.NAIST_2003
    ),
    TP(
        "UT Dictionary",
        "Japanese dictionaries enriched with UT Dictionary project which includes sources listed below",
        "https://github.com/utuhiro78/merge-ut-dictionaries",
        "Copyright (c) utuhiro78",
        License.Apache2
    ),
    TP(
        "Wikipedia",
        "Japanese dictionaries - Wikipedia",
        "https://ja.wikipedia.org/wiki/Wikipedia:%E3%82%A6%E3%82%A3%E3%82%AD%E3%83%9A%E3%83%87%E3%82%A3%E3%82%A2%E3%82%92%E4%BA%8C%E6%AC%A1%E5%88%A9%E7%94%A8%E3%81%99%E3%82%8B",
        "Copyright (c) Wikipedia",
        License.CC_BY_SA_4_0
    ),
    TP(
        "alt-cannadic",
        "Japanese dictionaries - alt-cannadic",
        "http://web.archive.org/https://ja.osdn.net/projects/alt-cannadic/wiki/FrontPage",
        "Copyright (c) alt-cannadic authors",
        License.GPL_V2
    ),
    TP(
        "edict2",
        "Japanese dictionaries - edict2",
        "https://www.edrdg.org/wiki/index.php/JMdict-EDICT_Dictionary_Project",
        "Copyright (c) Electronic Dictionary Research and Development Group",
        License.CC_BY_SA_4_0
    ),
    TP(
        "SudachiDict",
        "Japanese dictionaries - SudachiDict",
        "https://github.com/WorksApplications/SudachiDict",
        "Copyright (c) SudachiDict authors",
        License.Apache2
    ),
    TP(
        "SKK-JISYO",
        "Japanese dictionaries - SKK-JISYO",
        "https://github.com/skk-dev/dict/blob/master/SKK-JISYO.L",
        """Copyright (c) 1988-1995, 1997, 1999-2014
Masahiko Sato <masahiko@kuis.kyoto-u.ac.jp>
Hironobu Takahashi <takahasi@tiny.or.jp>,
Masahiro Doteguchi, Miki Inooka,
Yukiyoshi Kameyama <kameyama@kuis.kyoto-u.ac.jp>,
Akihiko Sasaki, Dai Ando, Junichi Okukawa,
Katsushi Sato and Nobuhiro Yamagishi
NAKAJIMA Mikio <minakaji@osaka.email.ne.jp>
MITA Yuusuke <clefs@mail.goo.ne.jp>
SKK Development Team <skk@ring.gr.jp>""",
        License.GPL_V2
    ),
    TP(
        "Sour Gummy font",
        "Sour Gummy font - used in Christmas 2025 theme, modified so that the j doesn't look like an i",
        "https://github.com/eifetx/Sour-Gummy-Fonts",
        "Copyright 2018 The Sour Gummy Project Authors",
        License.OFL1_1
    ),
    TP(
        "Christmas 2025 theme",
        "Seasonal theme for Christmas 2025 (Gingerbread)",
        "https://zilluzion.art/",
        "Copyright (c) 2025 zilluzion",
        License.Proprietary
    )
)