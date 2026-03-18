# LocalLlamaBoard — Project Specification

## Purpose of This Document

This document is designed to be read by a coding LLM (e.g., Claude, ChatGPT, Copilot) to understand the goal, architecture, and implementation plan for the **LocalLlamaBoard** project. It should provide enough context for an LLM to generate code, review PRs, and assist with development across sessions without needing repeated explanation.

---

## Project Overview

**LocalLlamaBoard** is a fork of the [FUTO Keyboard](https://github.com/futo-org/android-keyboard) (itself a fork of Android AOSP LatinIME) with one major addition: **an integrated LLM prompt system** that lets the user write custom prompts, submit them along with their current text to a locally-hosted LLM, and have the LLM rewrite/transform that text — all without leaving the keyboard.

The concept is directly inspired by [3Sparks Keyboard](https://www.3sparks.net/) for iOS, which offers this functionality but has no Android version. LocalLlamaBoard brings this capability to Android, built on top of an already excellent privacy-focused keyboard.

### The Irony

FUTO Keyboard's entire philosophy is that a keyboard should never connect to the internet. LocalLlamaBoard forks it specifically to add an *optional* network feature — but one that connects only to a user-controlled local LLM, not to any cloud service. The spirit of privacy and user control is preserved even as the letter of "no network" is broken.

---

## User Experience Goal

The end-user workflow should be:

1. User types a message in any app (SMS, email, Slack, etc.) using the LocalLlamaBoard keyboard.
2. User taps an **LLM action button** on the keyboard toolbar (e.g., a small wand/sparkle icon).
3. A **prompt picker** appears — a scrollable list of user-defined prompts (e.g., "Fix grammar", "Make formal", "Make concise", "Translate to Spanish", "ELI5", etc.).
4. User selects a prompt.
5. The keyboard reads the current text from the input field, combines it with the selected prompt template, and sends it to the LLM backend via API.
6. The LLM response replaces (or is offered as a replacement for) the original text in the input field.
7. Optionally, user can undo/revert to the original text.

### Secondary Features (Future)

- Prompt editor/manager (create, edit, delete, reorder prompts) accessible from a settings activity.
- Ability to configure multiple LLM backends (not just one).
- Prompt sharing/import/export (JSON format, like 3Sparks' Prompt Directory).
- Streaming response display (show tokens as they arrive).
- A "freeform" mode where the user can type a one-off instruction without saving it as a prompt.

---

## Technical Architecture

### Base Codebase

- **Repository**: LocalLlamaBoard (forked from `futo-org/android-keyboard`)
- **Language**: Primarily Java and C++ (inherited from LatinIME/FUTO), with FUTO's newer UI code in **Kotlin** (under `java/src/org/futo/inputmethod/latin/uix/`). New LLM integration code should be written in **Kotlin**.
- **Build system**: Gradle (see `build.gradle` in repo root)
- **Min SDK**: Inherited from FUTO (Android 8.0 / API 26)
- **Clone command**: `git clone --recursive` (has submodule dependencies)

### Key Source Directories

```
java/src/org/futo/inputmethod/latin/
├── LatinIME.java              # Main InputMethodService — the keyboard's entry point
├── uix/                       # FUTO's modern UI extension layer (Kotlin, Jetpack Compose)
│   ├── ActionBar.kt           # The toolbar/action bar above the keyboard
│   ├── settings/              # Settings screens (Jetpack Compose)
│   │   └── pages/             # Individual settings pages
│   └── actions/               # Toolbar action implementations
└── ...
```

**The `uix/` directory is where most new code should go.** FUTO built a modern Kotlin/Compose UI layer on top of the legacy LatinIME Java code. The action bar (toolbar above keys) and the "more actions" menu are the natural integration points for the LLM feature.

### Network Permissions

FUTO Keyboard deliberately does **not** request `android.permission.INTERNET` in its manifest. LocalLlamaBoard must add this permission:

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
```

This is the single most philosophically significant change to the fork. Consider adding a user-facing note in settings explaining that network access is used *only* for connecting to the user's own LLM backend and that no data is sent to any external service.

---

## LLM Backend Configuration

### Target Backend: KoboldCpp

The primary (and initially only) backend is [KoboldCpp](https://github.com/LostRuins/koboldcpp), running on the user's home network.

**Connection details for the developer's environment:**

| Parameter | Value |
|-----------|-------|
| Hostname | `enterprise.arpa` |
| Port | `5001` |
| API base path | `/v1/` |
| Chat completions endpoint | `/v1/chat/completions` |
| Completions endpoint | `/v1/completions` |
| Model list endpoint | `/v1/models` |
| Authentication | None required (local network) |
| Network path | Android device → WireGuard VPN → home network → PiHole DNS resolves `enterprise.arpa` → KoboldCpp host |

**Full base URL**: `http://enterprise.arpa:5001`

### API Format: OpenAI-Compatible Chat Completions

KoboldCpp exposes a standard OpenAI-compatible API. Use the **Chat Completions** endpoint for all LLM interactions.

**Request format:**

```json
POST http://enterprise.arpa:5001/v1/chat/completions
Content-Type: application/json

{
  "messages": [
    {
      "role": "system",
      "content": "You are a text rewriting assistant. You receive user text and a rewriting instruction. Output ONLY the rewritten text with no preamble, explanation, or markdown formatting."
    },
    {
      "role": "user",
      "content": "Instruction: {{PROMPT_TEXT}}\n\nText to rewrite:\n{{USER_INPUT_TEXT}}"
    }
  ],
  "max_tokens": 1024,
  "temperature": 0.7,
  "stream": false
}
```

**Response format:**

```json
{
  "id": "chatcmpl-...",
  "object": "chat.completion",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "The rewritten text appears here."
      },
      "finish_reason": "stop"
    }
  ]
}
```

**Extract the rewritten text from**: `response.choices[0].message.content`

### Streaming Support (Future Enhancement)

For streaming, set `"stream": true` in the request. The response becomes a series of `text/event-stream` SSE events:

```
data: {"choices":[{"delta":{"content":"The"},"index":0}]}
data: {"choices":[{"delta":{"content":" rewritten"},"index":0}]}
...
data: [DONE]
```

Streaming is a nice-to-have for UX (shows text appearing token-by-token) but is not required for MVP.

---

## Implementation Plan

### Phase 1: Core Infrastructure (MVP)

#### 1.1 — Add Internet Permission

Add `<uses-permission android:name="android.permission.INTERNET" />` to `AndroidManifest.xml`.

#### 1.2 — Create LLM API Client

Create a new Kotlin file at `java/src/org/futo/inputmethod/latin/uix/llm/LlmApiClient.kt`:

```kotlin
// Pseudocode structure — implement with OkHttp or ktor-client
class LlmApiClient(
    private val baseUrl: String,  // e.g., "http://enterprise.arpa:5001"
    private val apiKey: String? = null  // optional, for future backends
) {
    data class ChatMessage(val role: String, val content: String)

    data class LlmResponse(
        val success: Boolean,
        val rewrittenText: String?,
        val error: String?
    )

    suspend fun rewriteText(
        systemPrompt: String,
        userPrompt: String,
        inputText: String,
        maxTokens: Int = 1024,
        temperature: Float = 0.7f
    ): LlmResponse {
        // Build the messages array
        // POST to $baseUrl/v1/chat/completions
        // Parse response, extract choices[0].message.content
        // Return LlmResponse
    }

    suspend fun testConnection(): Boolean {
        // GET $baseUrl/v1/models
        // Return true if 200 OK
    }
}
```

**HTTP client choice**: FUTO already includes OkHttp as a transitive dependency (via various Android libraries). Use OkHttp directly rather than adding a new dependency. Alternatively, use `java.net.HttpURLConnection` for zero additional dependencies.

**Threading**: All network calls must be off the main thread. Use Kotlin coroutines (`suspend fun` + `Dispatchers.IO`). FUTO's codebase already uses coroutines in places.

#### 1.3 — Create Prompt Data Model and Storage

Create `java/src/org/futo/inputmethod/latin/uix/llm/PromptManager.kt`:

```kotlin
data class LlmPrompt(
    val id: String = UUID.randomUUID().toString(),
    val name: String,           // Display name, e.g., "Fix Grammar"
    val icon: String? = null,   // Optional emoji or icon identifier
    val systemPrompt: String,   // System message for the LLM
    val userPromptTemplate: String,  // Template with {{TEXT}} placeholder
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val sortOrder: Int = 0
)

class PromptManager(context: Context) {
    // Store prompts in SharedPreferences as JSON
    // or use Room database if complexity warrants it

    fun getPrompts(): List<LlmPrompt>
    fun savePrompt(prompt: LlmPrompt)
    fun deletePrompt(id: String)
    fun reorderPrompts(ids: List<String>)

    fun getDefaultPrompts(): List<LlmPrompt>  // Ship sensible defaults
}
```

**Default prompts to ship with:**

| Name | System Prompt | User Prompt Template |
|------|---------------|---------------------|
| Fix Grammar | You are a text editor. Fix grammar, spelling, and punctuation. Output only the corrected text. | Fix the grammar and spelling in this text:\n\n{{TEXT}} |
| Make Formal | You are a professional writing assistant. Rewrite text in a formal, professional tone. Output only the rewritten text. | Rewrite this text in a formal, professional tone:\n\n{{TEXT}} |
| Make Casual | You are a friendly writing assistant. Rewrite text in a casual, conversational tone. Output only the rewritten text. | Rewrite this text in a casual, friendly tone:\n\n{{TEXT}} |
| Make Concise | You are an editor focused on brevity. Shorten the text while preserving meaning. Output only the shortened text. | Make this text more concise while preserving its meaning:\n\n{{TEXT}} |
| Expand | You are a writing assistant. Elaborate on the text with more detail and explanation. Output only the expanded text. | Expand this text with more detail:\n\n{{TEXT}} |
| Translate to Spanish | You are a translator. Translate the text to Spanish. Output only the translation. | Translate this text to Spanish:\n\n{{TEXT}} |
| ELI5 | You are an explainer. Rewrite the text so a 5-year-old could understand it. Output only the simplified text. | Rewrite this text so it's simple enough for a 5-year-old to understand:\n\n{{TEXT}} |

#### 1.4 — Create LLM Settings Page

Add a new settings page at `java/src/org/futo/inputmethod/latin/uix/settings/pages/LlmSettingsPage.kt`:

This should be a Jetpack Compose screen (consistent with FUTO's existing settings pages) that allows the user to configure:

- **Backend URL** (text field, default: `http://enterprise.arpa:5001`)
- **API Key** (optional password field, for backends that require it)
- **Connection test button** (calls `/v1/models` and shows success/failure)
- **Default system prompt** (multiline text field — the base system message prepended to all prompts)
- **Default temperature** (slider, 0.0–2.0)
- **Default max tokens** (number field)

Register this page in the existing settings navigation (look at how other settings pages are registered in the `uix/settings/` directory).

#### 1.5 — Create Prompt Manager UI

Add a settings sub-page for managing prompts:

- List of prompts with drag-to-reorder
- Tap to edit a prompt (name, system prompt override, user prompt template, temperature, max tokens)
- Long-press or swipe to delete
- FAB or "+" button to add new prompt
- "Reset to defaults" option

#### 1.6 — Integrate LLM Action into the Keyboard Toolbar

This is the core UX integration. FUTO's keyboard has an **action bar** (toolbar) above the key area. Look at `uix/ActionBar.kt` and the `uix/actions/` directory to understand how existing actions (voice input, clipboard, emoji, etc.) are implemented.

**Add a new action** that:

1. Appears as an icon in the toolbar (a wand, sparkle, or brain icon).
2. When tapped, reads the current text from the active input field using `InputConnection.getExtractedText()` or `InputConnection.getTextBeforeCursor()` + `getTextAfterCursor()`.
3. Shows a prompt picker (a bottom sheet with the list of prompts).
4. On prompt selection:
   a. Shows a loading indicator (e.g., a small progress bar in the action bar area).
   b. Fires the API request asynchronously.
   c. On success, replaces the input field text using `InputConnection.commitText()` or `InputConnection.deleteSurroundingText()` + `commitText()`.
   d. On failure, shows a brief error toast.
5. Stores the original text so the user can undo (tap undo in the toolbar or shake-to-undo if that's simpler).

**Critical implementation detail — reading and replacing text:**

```kotlin
// Reading the current input text
val ic = currentInputConnection ?: return
val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
val currentText = extracted?.text?.toString() ?: ""

// Replacing the entire input field text
// First, select all
ic.performContextMenuAction(android.R.id.selectAll)
// Then commit the new text (replaces selection)
ic.commitText(rewrittenText, 1)
```

This approach (select-all then commit) is the most reliable way to replace the full contents of an input field from an IME. For partial replacements (e.g., only selected text), use `getSelectedText()` to check for a selection first, and only replace that portion.

#### 1.7 — Handle the "No Selection vs Full Text" Logic

The LLM action should be smart about what text to send:

1. If the user has **selected text** in the input field → send only the selected text, replace only the selection.
2. If there is **no selection** → send the entire contents of the input field, replace everything.

```kotlin
val ic = currentInputConnection ?: return
val selectedText = ic.getSelectedText(0)?.toString()

if (!selectedText.isNullOrEmpty()) {
    // User has a selection — rewrite just that
    sendToLlm(selectedText) { rewritten ->
        ic.commitText(rewritten, 1)  // replaces current selection
    }
} else {
    // No selection — rewrite everything
    val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
    val fullText = extracted?.text?.toString() ?: return
    sendToLlm(fullText) { rewritten ->
        ic.performContextMenuAction(android.R.id.selectAll)
        ic.commitText(rewritten, 1)
    }
}
```

### Phase 2: Polish and Refinement

#### 2.1 — Undo Support

After replacing text, store the original in a variable. Add an "undo" action to the toolbar that appears after a rewrite, allowing one-tap revert. Clear the undo buffer when the user starts typing new characters.

#### 2.2 — Streaming Responses

Switch to `"stream": true` and parse SSE events. Display the incoming text token-by-token in a preview area above the keyboard (similar to how autocomplete suggestions appear), with a "confirm" button to commit the result.

#### 2.3 — Freeform Prompt Mode

In addition to saved prompts, allow the user to type a one-off instruction. This could be a text field that appears in the prompt picker area — user types "translate to french" and hits send without saving it as a permanent prompt.

#### 2.4 — Multiple Backend Support

Extend the settings to allow multiple configured backends (e.g., KoboldCpp at home, an OpenAI API key for on-the-go). The user can select the active backend, or the app can auto-detect which is reachable.

#### 2.5 — Prompt Import/Export

JSON-based import/export of prompt collections, for sharing with other LocalLlamaBoard users or importing from 3Sparks' prompt directory format.

---

## Dependencies to Add

| Dependency | Purpose | Notes |
|-----------|---------|-------|
| OkHttp | HTTP client | Likely already present transitively; if not, add `com.squareup.okhttp3:okhttp:4.x` |
| kotlinx-serialization-json OR Gson | JSON parsing | Gson is likely already present via Android; use whichever is already in the dependency tree |
| kotlinx-coroutines | Async operations | Likely already present; FUTO uses coroutines |

**Minimize new dependencies.** FUTO's build is complex with native C++ components. Prefer using what's already in the dependency tree.

---

## Build and Test

### Building

```bash
# Clone with submodules
git clone --recursive <your-locallamaboard-repo-url>
cd LocalLlamaBoard

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

### Testing the API Connection

Before integrating into the keyboard, verify the API works from the device:

```bash
# From a terminal on the Android device (or via adb shell),
# or test from any machine on the same network:
curl http://enterprise.arpa:5001/v1/models

# Should return something like:
# {"object":"list","data":[{"id":"koboldcpp/your-model-name",...}]}

# Test a completion:
curl -X POST http://enterprise.arpa:5001/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "system", "content": "You are a helpful assistant."},
      {"role": "user", "content": "Say hello in exactly 5 words."}
    ],
    "max_tokens": 50
  }'
```

### Testing on Device

1. Build and install the debug APK.
2. Go to Android Settings → System → Languages & Input → On-screen keyboard → Manage keyboards → Enable LocalLlamaBoard.
3. Open any app with a text field, switch to LocalLlamaBoard.
4. Ensure WireGuard VPN is active (so `enterprise.arpa` resolves).
5. Type some text, tap the LLM action button, select a prompt, verify the text is rewritten.

---

## File Naming Conventions

All new files for the LLM integration should go under a new package:

```
java/src/org/futo/inputmethod/latin/uix/llm/
├── LlmApiClient.kt          # HTTP client for OpenAI-compatible API
├── LlmAction.kt             # Keyboard toolbar action implementation
├── PromptManager.kt          # Prompt CRUD and storage
├── LlmPrompt.kt             # Data class for prompts (or keep in PromptManager)
└── ui/
    ├── PromptPickerSheet.kt  # Bottom sheet for selecting prompts
    ├── LlmSettingsPage.kt    # Backend configuration settings
    └── PromptEditorPage.kt   # Prompt management settings
```

---

## Important Caveats

### FUTO Source First License

The FUTO Keyboard is licensed under the **FUTO Source First License 1.1**, which allows modification and redistribution for **non-commercial purposes**. LocalLlamaBoard is a personal/hobby project and falls squarely within this. Do not publish it commercially or on the Play Store as a paid app.

### InputConnection Limitations

Android's `InputConnection` API has quirks:
- `getExtractedText()` may return `null` or incomplete text in some apps (especially those with custom text editors like terminals or code editors).
- `commitText()` behavior varies by app — some apps handle it differently.
- Always null-check everything from `InputConnection`.
- Some apps limit how much text you can read — `getTextBeforeCursor(Int.MAX_VALUE, 0)` might not return everything in very long documents.

### Network Availability

The LLM backend is only reachable when the WireGuard VPN is active. The UI should handle connection failures gracefully:
- Show a clear error ("Cannot reach LLM server — is your VPN connected?")
- Don't block the keyboard or make it unusable when the backend is down
- Consider a connection status indicator (small dot on the LLM action icon: green = reachable, red = unreachable)

### Response Time

LLM inference on a local machine varies. A 7B model might respond in 1–3 seconds; a larger model could take 10+ seconds. The UI must:
- Show a loading state
- Allow the user to cancel a pending request
- Not freeze the keyboard while waiting
- Time out after a configurable duration (default: 30 seconds)

---

## Summary for the Coding LLM

If you are an LLM helping develop this project:

1. **Start with the FUTO Keyboard source** — clone it recursively, understand the `uix/` layer.
2. **Add INTERNET permission** to the manifest.
3. **Build the API client** — simple OkHttp POST to `/v1/chat/completions`, parse JSON response.
4. **Build prompt storage** — SharedPreferences or Room, with sensible defaults.
5. **Add the toolbar action** — follow the pattern of existing actions in `uix/actions/`.
6. **Wire it together** — action reads text → shows prompt picker → calls API → replaces text.
7. **Add settings UI** — Compose screens for backend config and prompt management.
8. **Test with KoboldCpp** at `http://enterprise.arpa:5001/v1/chat/completions`.
9. **Handle errors gracefully** — network down, timeout, malformed response.
10. **Keep it simple** — MVP first, streaming and multi-backend later.
