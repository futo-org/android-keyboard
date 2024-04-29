package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList

@Preview(showBackground = true)
@Composable
fun CreditsScreen(navController: NavHostController = rememberNavController()) {
    ScrollableList {
        ScreenTitle("Credits", showBack = true, navController)

        ParagraphText("This keyboard is based on the LatinIME keyboard from the Android Open Source Project. Thank you to the original AOSP LatinIME Keyboard developers. The LatinIME keyboard is Apache-2.0 licensed and Copyright (C) 2011 The Android Open Source Project")
        ParagraphText("Thank you to llama.cpp, whisper.cpp, ggml devs for making a compact machine learning inference library. These projects are MIT-licensed. Copyright (c) 2023 Georgi Gerganov")
        ParagraphText("Thank you to OpenAI Whisper for the speech recognition model which is used for voice input. Whisper is MIT-licensed. Copyright (c) 2022 OpenAI")
        ParagraphText("Thank you to Feather Icons for providing many of the icons seen in this app. Feather Icons is MIT-licensed. Copyright (c) 2013-2017 Cole Bemis")
    }
}