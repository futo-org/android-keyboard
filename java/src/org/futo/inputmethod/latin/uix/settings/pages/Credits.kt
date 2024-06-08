package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.updates.openURI

@Preview(showBackground = true)
@Composable
fun CreditsScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    ScrollableList {
        ScreenTitle("Credits", showBack = true, navController)

        ParagraphText("This project is made possible by the below projects!")

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

        Spacer(modifier = Modifier.height(16.dp))
        ParagraphText("Note: The authors listed above are not affiliated with us and do not endorse or promote us")
    }
}