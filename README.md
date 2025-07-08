# FUTO Keyboard

The goal is to make a good modern keyboard that stays offline and doesn't spy on you. This keyboard is a fork of [LatinIME, The Android Open-Source Keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME), with significant changes made to it.

Check out the [FUTO Keyboard website](https://keyboard.futo.org/) for downloads and more information.

## Features

- Offline-first keyboard with privacy-focused design.
- Settings search bar integrated into the settings screen with a helpful "Settings search or try typing here." placeholder. The field grows to fit long queries.
- Most extra features, such as Quick Switch, can be enabled or disabled from the settings.
- AMOLED friendly dark themes with purple, red, blue, and green accents for battery savings.
- Built-in theme generator lets you pick custom accent and base colors.
- Theme generator handles invalid color codes without crashing.
- Settings icons use accent variations so each menu item has a unique color.
- AI Reply menu for configuring Groq-powered quick replies using chat completion models fetched from Groq.
  The model picker now lists Llama chat models only.
- Groq Reply API settings store a separate API key and model for chat completions.
- AI Reply prompt can be customized from the keyboard or settings and the clipboard text is sent to Groq for context.
- AI Reply now always uses the most recent clipboard entry when generating a reply, even when launched directly from the actions row.
- Voice recognition output is normalized so repeated words are removed.
- Voice input respects the keyboard's caps lock state.
- Long voice recordings are transcribed in 30 second chunks so earlier audio isn't overwritten.
- Groq Voice API settings under Voice Input show only Whisper models.
- The voice input spinner turns orange when using Groq and green for local recognition.
- New settings search button at the bottom of the settings screen highlights itself with a smooth repeating border animation for easier discovery.
- AI Reply menu for configuring Groq-powered quick replies.
- AI reply generation now streams responses using coroutines for smoother updates.
- Quick Switch can be toggled in settings and uses an accessibility service to jump to your previously used app.

The code is licensed under the [FUTO Source First License 1.1](LICENSE.md).

## Issue tracking and PRs

Please check the GitHub repository to report issues: [https://github.com/futo-org/android-keyboard/](https://github.com/futo-org/android-keyboard/)

The source code is hosted on our [internal GitLab](https://gitlab.futo.org/keyboard/latinime) and mirrored to [GitHub](https://github.com/futo-org/android-keyboard/). As registration is closed on our internal GitLab, we use GitHub instead for issues and pull requests.

Due to custom license, pull requests to this repository require signing a [CLA](https://cla.futo.org/) which you can do after opening a PR. Contributions to the [layouts repo](https://github.com/futo-org/futo-keyboard-layouts) don't require CLA as they're Apache-2.0

## Layouts

If you want to contribute layouts, check out the [layouts repo](https://github.com/futo-org/futo-keyboard-layouts).

## Building

When cloning the repository, you must perform a recursive clone to fetch all dependencies:
```
git clone --recursive https://gitlab.futo.org/keyboard/latinime.git
```

If you forgot to specify recursive clone, use this to fetch submodules:
```
git submodule update --init --recursive
```

You can then open the project in Android Studio and build it that way, or use gradle commands:
```
./gradlew assembleUnstableDebug
./gradlew assembleStableRelease
```

Install the Android SDK if it isn't already present. On Ubuntu you can run:
```
sudo apt-get install android-sdk
```
After installing, accept the licenses with:
```
yes | sdkmanager --licenses
```
You will also need the Android NDK for native builds:
```
sdkmanager "ndk;25.1.8937393"
```

Make sure Gradle can locate your Android SDK. Either export `ANDROID_HOME`, for example:
```
export ANDROID_HOME=/usr/lib/android-sdk
```
or create a `local.properties` file with:

```
sdk.dir=/path/to/android-sdk
```

To avoid Play Protect warnings when installing the APK, sign the release build with your own keystore:
```
./gradlew assemblePlaystoreRelease \
    -Pandroid.injected.signing.store.file=/path/to/keystore.jks \
    -Pandroid.injected.signing.store.password=****** \
    -Pandroid.injected.signing.key.alias=keyAlias \
    -Pandroid.injected.signing.key.password=******
```
You can also provide these values in CI by setting the `KEYSTORE_*` secrets and
running `setUpPropertiesCI.sh`.

When running GitHub Actions workflows, use the latest `v4` releases of the standard actions such as `actions/upload-artifact@v4` to avoid deprecation errors.
