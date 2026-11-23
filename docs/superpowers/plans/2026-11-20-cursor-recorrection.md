# Cursor recorrection implementation plan

> **For agentic workers:** Use test-driven-development and verification-before-completion. Steps use checkbox syntax for tracking.

**Goal:** Prevent the keyboard from arming replacement of an existing word with cursor coordinates that disagree with the editor, without disabling ordinary typing or either suggestion engine.

**Approach:** Verify the editor selection before arming a composing region; use the editor's existing selection API on modern Android and the `ExtractedText` fallback on older Android. The alternative of disabling recorrection on resume also avoids the stale composing span, but takes suggestions away from healthy editors and does not cover a delayed callback after the view is already visible.

**Scope:** Keep changes in the Latin input-connection and composition path. Do not enable the alternative composition wrapper on legacy Android. Do not publish to upstream.

## Steps

- [x] Locate the shared recorrection path, compare upstream and the reported release, and isolate the fix branch.
- [x] Add a regression for an editor that cannot return `SurroundingText` but does return distinct extracted selection endpoints at a nonzero offset. The unchanged production helper returned `(-1, -1)` in the host test before the fix.
- [x] Make extraction preserve both endpoints and use the legacy fallback for Latin cursor checks. Validate the live caret before creating a composing span and before a first key can replace an already opened word; add Android tests for delayed and missing callbacks.
- [ ] Verify correct-word recorrection and ordinary typing, then build `assembleUnstableDebug` and verify the APK package and signature.
- [ ] Independently review the fix, commit code and verification evidence, and push the fix branch only to the confirmed user repository. Leave the official stable app installed when testing the unstable APK.

## Android verification

On a disposable Markor note, type two distinct words on separate lines. Press Home, restore Markor from Recents, tap inside the other word, and immediately type a letter. Repeat with Space in a fresh copy of the note and with the classic and LLM suggestion engines separately. The originally edited word must not change and the letter must insert at the tapped caret. Repeat with Back-to-hide instead of Home, and in a plain text field. Also check that ordinary tap-to-reopen suggestions still work when the editor reports a valid selection.

The focused helper passed against editor doubles for simulated API levels 30, 31, and 35. This is not a device run. The newly created LDPlayer instance booted its window but did not expose a working ADB connection; shutdown and exit of both player and VM were verified.

If an editor cannot report a live selection, the keyboard skips reopening the word instead of risking a replacement. Legacy Android editors may return more text than requested by `getExtractedText`; editing latency in a large note remains a device check.
