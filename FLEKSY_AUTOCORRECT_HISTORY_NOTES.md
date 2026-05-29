# Fleksy Autocorrect Revert History Notes

Current swipe-up autocorrect revert is intentionally span/text based, not word-boundary based. This is required for corrections where one typed token becomes multiple committed words, e.g. `alot` -> `a lot`.

Potential future improvement: keep a small history of recent revertable autocorrect commits instead of only relying on the latest `LastComposedWord`.

Each history entry should store:

- originally typed text, e.g. `alot`
- committed text, e.g. `a lot`
- separator string, usually a space
- active/consumed state
- optional cursor/end offset or timestamp if useful

Important constraint: saving more words only helps if revert matching is still validated against actual editor text. Do not revert an old entry unless the text around the cursor still matches the stored committed span. Word-boundary matching is not enough for split-word autocorrections.

Likely implementation shape:

- Keep existing `mLastComposedWord` behavior for backspace undo to minimize risk.
- Add a separate capped deque/list of recent autocorrect commits, probably 3-5 entries.
- Push entries when `commitChosenWord(...)` creates a revertable autocorrect.
- On swipe-up, scan newest to oldest and revert the first entry whose committed text plus optional separator matches before the cursor.
- Mark entries consumed/inactive after reverting so stale history cannot repeatedly mutate text.

Avoid broadening normal backspace undo until swipe-up history is proven safe; backspace currently assumes a single most recent commit.
