# Whole-word suggestions during caret edits

The user confirmed Backspace responsiveness on the previous APK, but `for|k → fo|k → fon|k` drops `fok` from suggestions and starts an unrelated one-letter composition for `n`. The active milestone is to keep the complete word supplied to both suggestion engines throughout that sequence.

- [x] Trace the failure: the middle-of-word Backspace abandons editor composition, and the existing delete logic intentionally avoids restarting suggestions while letters remain on the right. The next letter takes the ordinary new-word path; the retained learned-word string is not suggestion input.
- [x] Preserve a bounded local snapshot for edits inside a reopened word. Use caret-relative editor operations, not replacement of the old word or full-document selection reads per character. Treat the snapshot as prediction input, not authority to replace text. Refresh or discard it on selection changes; require new verification on explicit suggestion replacement. Show a full native composing span only after the editor reports the edited caret on a compatible connection, without repainting text.
- [x] Verify the exact deletion/insertion sequence, text and caret, both sides of the word, and the component behavior after a later user tap before its callback. Verify that the literal edit path does not request full-document selection. Keep the previous APK as a rollback.
- [ ] Verify delayed suggestion clicks, repeated-key latency and visual spellcheck styling on an Android 12+ device or the user's phone. Do not infer those results from the Android 9 native path.

Owner: this session owns integration and verification. Bounded parallel helper development and read-only diagnosis do not own emulator slots. Check the course after the first red/green batch or within 30 active minutes. Do not route editing through automatic word replacement to obtain visual styling. No test state goes to the physical phone without permission.

## Runtime evidence

On LDPlayer Android 9 (API 28), `CursorWordJourneyTests` launches the existing “Try typing here” editor and sends actual FUTO software key taps. The previous delivered APK produced the correct text and caret after Backspace, then failed at the expected step: no whole-word suggestion for `fok`. The first whole-word candidate preserved `fo|k`, sent a native composing range of `0..3` without moving the caret, and displayed alternatives for `fok`; the strip's ordinary layout still hid the exact typed word. The cursor-edit suggestion style retains the exact word in the strip without changing the layout for normal typing.

On the candidate, the same test saw `fok` and `fonk` in the suggestion strip and the exact text and caret after both taps. Selection callbacks reported native composing bounds `0..3` at caret `2` and `0..4` at caret `3`. The build and all 18 focused tests passed. The Android 9 run covers the native composing connection; it does not establish underline rendering in the Android 12+ alternative input connection, transformer scoring when a local model is unavailable, or key latency on the user's phone.

The restored preferences rely on the defaults for alternative input and its native-region toggle. On Android 12+, that means the compatibility wrapper normally avoids native composing spans. The suggestion strip does not need an editor span. Do not turn native regions on globally just to paint an underline: the wrapper exists because some editors handle composition incorrectly.

Run the focused UI test on an isolated guest with the FUTO debug IME selected and suggestions enabled: `adb -s <serial> shell am instrument -w -e cursorWordJourney true -e class org.futo.inputmethod.latin.inputlogic.CursorWordJourneyTests org.futo.inputmethod.latin.tests/androidx.test.runner.AndroidJUnitRunner`. The opt-in prevents ordinary Android test runs from driving a device whose IME was not prepared for the scenario.

The wider Android test run is not green: 278 failures in 949 tests. All 278 test names also failed on the previously delivered APK, using the current runner. Two extra baseline-only failures were caused by linking current tests against the older `void setComposingRegion` signature; they are not behavioral differences. The full run is not evidence that every existing failure is harmless.

The first whole-word build was removed from the deliverables after the selection-safety review. The rollback is the earlier user-tested `build/deliverables/FUTO-Keyboard-recorrection-94c41a1-debug.apk`, not the superseded whole-word build.

## Final safety review

Do not distribute the first whole-word APK: the review reproduced an `InputConnection` that returns no selection text even while a range is selected. A Backspace at `for|[k]` deleted `r` beside that selection, incorrectly leaving `fok`. Use a bounded surrounding-text query when available, and let the editor dispatch Backspace when it cannot distinguish an unavailable selection from a collapsed one. The next acceptance batch must keep the original UI journey green, delete the selected `k` rather than `r`, keep the word through Shift, and leave no composing state when the alternative wrapper's native region is refused.

The final build passed all 20 focused tests, including `fork → fok → fonk`, Shift followed by `I` giving `fonIk`, Backspace with unavailable selection text, and an editor refusing a native region. In the wider run, 278 of 951 tests failed; all 278 also failed on the baseline. The original guest keyboard was restored, and both player and VM process exits were verified.

Deliverable: `build/deliverables/FUTO-Keyboard-whole-word-caret-debug.apk` (SHA-256 `f7d952f3398b69305d659590a070fd73516290e43bd5f32802518a3654535bc5`). Its debug certificate matches the rollback build. Its embedded app version was generated before committing the source; the filename and checksum identify the tested artifact.

Next course check: prioritize a newer Android editor or the user's original phone/editor. The decisive open checks are the underline with the compatibility wrapper, repeated-key latency, and delayed selection followed by a suggestion tap.
