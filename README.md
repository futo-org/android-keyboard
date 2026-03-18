# LocalLlamaBoard

LocalLlamaBoard is a fork of [FUTO Keyboard](https://github.com/futo-org/android-keyboard) with one major addition: an integrated LLM prompt system that lets you rewrite and transform text using a locally-hosted LLM, all without leaving the keyboard.

Inspired by [3Sparks Keyboard](https://www.3sparks.net/) for iOS, LocalLlamaBoard brings the same AI text rewriting capability to Android, built on top of FUTO's excellent privacy-focused keyboard.

## How It Works

1. Type a message in any app (SMS, email, Slack, etc.) using LocalLlamaBoard.
2. Tap the **LLM Rewrite** button (sparkle icon) in the keyboard toolbar.
3. A prompt picker appears with options like "Fix Grammar", "Make Formal", "Make Concise", etc.
4. Select a prompt — the keyboard reads your text, sends it to your local LLM, and replaces it with the rewritten version.
5. Tap **Undo Rewrite** if you want to revert to the original text.

If you have text selected, only the selection is sent and replaced. Otherwise, the entire input field contents are used.

## Prerequisites

### Local LLM Server

LocalLlamaBoard connects to a locally-hosted LLM that exposes an **OpenAI-compatible API**. The primary supported backend is [KoboldCpp](https://github.com/LostRuins/koboldcpp), but any server that implements the `/v1/chat/completions` endpoint will work (e.g., llama.cpp server, text-generation-webui with OpenAI extension, Ollama, LocalAI).

Your LLM server must be:

- **Running** on your local network (or accessible via VPN)
- **Reachable** from your Android device over HTTP
- **Serving** an OpenAI-compatible chat completions API at `/v1/chat/completions`

Example setup with KoboldCpp:

```bash
# Start KoboldCpp with your model
python koboldcpp.py --model your-model.gguf --port 5001
```

The server will then be available at `http://<your-machine-ip>:5001`.

### Network Access

Your Android device must be able to reach the LLM server. Common setups:

- **Same Wi-Fi network** — Device and server on the same local network. Use the server machine's local IP (e.g., `http://192.168.1.100:5001`).
- **WireGuard/VPN** — If the server is on a different network, use a VPN like WireGuard to connect. You can use a hostname if DNS resolves it (e.g., `http://enterprise.arpa:5001`).
- **Tailscale** — Another option for accessing your home server from anywhere.

No data is ever sent to any cloud service. All traffic goes directly to your own server.

## Installation

### Building from Source

1. Clone the repository with submodules:

```bash
git clone --recursive https://github.com/Saintshroomie/LocalLlamaBoard.git
cd LocalLlamaBoard
```

If you forgot `--recursive`:
```bash
git submodule update --init --recursive
```

2. Build the debug APK:

```bash
./gradlew assembleDebug
```

Or build a release APK:
```bash
./gradlew assembleStableRelease
```

3. Install on your device:

```bash
./gradlew installDebug
```

Or transfer the APK from `build/outputs/apk/` to your device and install it manually.

### Enabling the Keyboard

1. Go to **Settings > System > Languages & Input > On-screen keyboard > Manage keyboards**.
2. Enable **LocalLlamaBoard**.
3. Open any app with a text field.
4. Switch to LocalLlamaBoard using the keyboard switcher (usually in the navigation bar or notification shade).

## Configuration

### LLM Backend Settings

Open the LocalLlamaBoard settings app (or tap the settings icon in the keyboard toolbar), then navigate to **LLM Backend**:

- **Backend URL** — The full URL of your LLM server (e.g., `http://192.168.1.100:5001`). Do not include `/v1/chat/completions` — just the base URL.
- **API Key** — Optional. Only needed if your backend requires authentication (e.g., OpenAI API, or a server with auth enabled).
- **Test Connection** — Tap to verify the keyboard can reach your server. This calls `/v1/models` and reports success or failure.
- **Default System Prompt** — The base system message sent with all prompts. Controls how the LLM behaves as a text rewriter.

### Managing Prompts

From the LLM Backend settings, tap **Manage Prompts** to:

- View all configured prompts
- Tap a prompt to edit its name, system prompt, user template, temperature, and max tokens
- Add new prompts with the **Add Prompt** button
- Delete prompts with the trash icon
- Reset to the 7 built-in defaults with **Reset to Defaults**

### Default Prompts

LocalLlamaBoard ships with these prompts out of the box:

| Prompt | Description |
|--------|-------------|
| Fix Grammar | Corrects grammar, spelling, and punctuation |
| Make Formal | Rewrites in a professional tone |
| Make Casual | Rewrites in a conversational tone |
| Make Concise | Shortens text while preserving meaning |
| Expand | Adds more detail and explanation |
| Translate to Spanish | Translates the text to Spanish |
| ELI5 | Simplifies text for easy understanding |

Each prompt uses `{{TEXT}}` as a placeholder in its user prompt template — this is replaced with the actual text from your input field.

### Adding the Action to the Toolbar

The LLM Rewrite action appears in the keyboard's **More Actions** menu by default. To pin it to the toolbar for quick access:

1. Open LocalLlamaBoard settings.
2. Go to **Actions > Edit Actions**.
3. Drag **LLM Rewrite** from "More Actions" into "Favorite Actions" or "Pinned Action(s)".

## Privacy

LocalLlamaBoard adds `INTERNET` permission to the FUTO Keyboard — the only network feature in an otherwise fully offline keyboard. This permission is used **exclusively** to connect to your own LLM server. No data is sent to any external service, analytics platform, or cloud API (unless you explicitly configure a cloud endpoint as your backend).

## License

This project inherits the [FUTO Source First License 1.1](LICENSE.md) from FUTO Keyboard. It is free for personal, non-commercial use. See the license file for details.

## Credits

- [FUTO Keyboard](https://github.com/futo-org/android-keyboard) — The excellent base keyboard
- [KoboldCpp](https://github.com/LostRuins/koboldcpp) — Primary supported LLM backend
- [3Sparks Keyboard](https://www.3sparks.net/) — Inspiration for the LLM rewrite concept
