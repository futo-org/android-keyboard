package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SpacedColumn
import org.futo.inputmethod.updates.openURI

data class ContributorInfo(
    val name: String,
    val features: List<String>
)

private fun contributor(name: String, vararg features: String) = ContributorInfo(name, features.toList())

val contributors = listOf<ContributorInfo>(
    contributor("Ravyar Tahir (@ravarage)", "Central Kurdish keyboard layout"),
    contributor("Emre Eren (@remreren)", "Turkish keyboard layout"),
    contributor("@ornstrange", "Icelandic keyboard layout"),
    contributor("@roguesensei", "Colemak DH / DH ANSI layouts"),
    contributor("@BoFFire", "Kabyle layout"),
    contributor("@Midnight1938", "Optimized Devanagari layout"),
    contributor("@NicolasLagaillardie", "BEPO Inclusive layouts"),
    contributor("@abdelhaqueidali", "Amazigh layout"),
    contributor("@lomtjjz", "Belarusian Latin layout"),
    contributor("@Imold", "pcqwerty fix"),
    contributor("@tenextractor", "Korean layout, Maltese, Hungarian, Romanian, Albanian, Hansa, Burmese/Myanmar, Bashkir, Kurdish, and more"),
)

@Preview(showBackground = true)
@Composable
fun CreditsScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    ScrollableList {
        ScreenTitle("Credits", showBack = true, navController)

        SpacedColumn(24.dp, Modifier.padding(16.dp)) {
            ParagraphText("This project is made possible by the below projects!")

            SpacedColumn(10.dp) {
                ParagraphText("This keyboard is based on the LatinIME keyboard from the Android Open Source Project. Thank you to the original AOSP LatinIME Keyboard developers. The LatinIME keyboard is Apache-2.0 licensed and Copyright (C) 2011 The Android Open Source Project",
                    modifier = Modifier.clickable {
                        context.openURI("https://android.googlesource.com/platform/packages/inputmethods/LatinIME/")
                    })

                ParagraphText("Thank you to llama.cpp, whisper.cpp, ggml devs for making a compact machine learning inference library. These projects are MIT-licensed. Copyright (c) 2023 Georgi Gerganov",
                    modifier = Modifier.clickable {
                        context.openURI("https://ggml.ai")
                    })

                ParagraphText("Thank you to OpenAI Whisper for the speech recognition model which is used for local voice input. Whisper is MIT-licensed. Copyright (c) 2022 OpenAI",
                    modifier = Modifier.clickable {
                        context.openURI("https://github.com/openai/whisper")
                    })

                ParagraphText("Thank you to Feather Icons for providing many of the icons seen in this app. Feather Icons is MIT-licensed. Copyright (c) 2013-2017 Cole Bemis",
                    modifier = Modifier.clickable {
                        context.openURI("https://feathericons.com")
                    })

                ParagraphText("Thank you to the WebRTC project for the voice activity detection used to automatically stop voice input. WebRTC is BSD-3-Clause licensed. Copyright (c) 2011, The WebRTC project authors",
                    modifier = Modifier.clickable {
                        context.openURI("https://webrtc.org")
                    })

                ParagraphText("Thank you to the android-vad project for providing Android bindings to the WebRTC voice activity detection. android-vad is MIT-licensed. Copyright (c) 2023 Georgiy Konovalov",
                    modifier = Modifier.clickable {
                        context.openURI("https://github.com/gkonovalov/android-vad")
                    })

                ParagraphText("Some keyboard layouts were taken from the CyanogenMod/LineageOS fork of the LatinIME keyboard. Their fork is Apache-2.0 licensed. Copyright (C) 2015 The CyanogenMod Project",
                    modifier = Modifier.clickable {
                        context.openURI("https://github.com/LineageOS/android_packages_inputmethods_LatinIME")
                    })

                ParagraphText("Emoji icons are taken from the Noto Emoji font, licensed under OFL.",
                    modifier = Modifier.clickable {
                        context.openURI("https://fonts.google.com/noto/specimen/Noto+Emoji")
                    })

                ParagraphText("Actions rearrangement menu uses the Reorderable library made by Calvin Liang, licensed under Apache-2.0. Reorderable is Copyright (c) 2023 Calvin Liang",
                    modifier = Modifier.clickable {
                        context.openURI("https://github.com/Calvin-LL/Reorderable")
                    })
            }

            ScreenTitle("Contributors")
            SpacedColumn(10.dp) {
                contributors.reversed().forEach {
                    ParagraphText("Thank you to ${it.name} for contributing ${it.features.joinToString()}")
                }
            }

            ScreenTitle("Notice")
            ParagraphText("The authors listed above are not affiliated with us and may not necessarily endorse or promote us")
        }
    }
}