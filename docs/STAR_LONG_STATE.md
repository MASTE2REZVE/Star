# STAR — COMPLETE PROJECT STATE

**Version:** 3.0
**Last updated:** 2026-10-10
**Owner:** MASTE2REZVE on GitHub

Read this fully before asking the user anything. Everything below is verified fact.

---

## 1. WHAT STAR IS

Android AI agent app on **Xiaomi Pad 7** (Android 14, HyperOS, 8 GB RAM, 256 GB).
Built entirely on the tablet. No PC. Termux is the build tool.
No ads. No tracking. No cloud login. User's own API keys.

---

## 2. HARDWARE

| Item | Detail |
|------|--------|
| Device | Xiaomi Pad 7 |
| OS | Android 14, HyperOS |
| Screen | 3200 x 2136 landscape |
| RAM | 8 GB (~2 GB free) |
| CPU | Snapdragon arm64-v8a |
| Shell | Shizuku (UID 2000) |
| Termux | UID ~10xxx |

Device limits:
- HyperOS kills background Termux. Job scheduler mitigates.
- Rust builds die at signal 9 above ~1.5 GB peak.
- No vibrator.
- Clipboard reads blocked in background (Android 12+).
- HyperOS blocks accessibility on secondary displays.
- HyperOS blocks app_process from loading classes out of the app private APK.
- HyperOS blocks getWindowsOnAllDisplays from returning non-display-0 windows.

---

## 3. KEY PATHS

### App
| What | Path |
|------|------|
| Root | `~/StarApp` |
| Java | `~/StarApp/app/src/main/java/com/star/app/` |
| Manifest | `~/StarApp/app/src/main/AndroidManifest.xml` |
| APK | `~/StarApp/app/build/outputs/apk/debug/app-debug.apk` |
| APK copy | `/storage/emulated/0/Star.apk` |
| Build | `~/bin/star-build` |

### Termux scripts
| What | Path |
|------|------|
| DSH bridge | `~/star-bridge.sh` |
| Candy bridge | `~/candy-bridge.sh` |
| Pkg bridge | `~/pkg-bridge.sh` |
| Audio bridge | `~/audio-bridge.sh` |
| Watchdog | `~/star-ensure.sh` |
| SDK source | `~/headless-android-sdk/` |

### Shared storage
| What | Path |
|------|------|
| Root | `/sdcard/Star/` |
| Candy | `/sdcard/Star/candy/` |
| Candy binaries | `/sdcard/Star/candy/bin/` |
| Run request | `/sdcard/Star/candy/run-request.json` |
| Run result | `/sdcard/Star/candy/run-result.json` |
| Built | `/sdcard/Star/built/` |
| Sessions | `/sdcard/Star/sessions/` |
| Audio | `/sdcard/Star/audio_out.wav` |
| Video | `/sdcard/Star/candy/interpolated.mp4` |
| Models cache | `/sdcard/Star/models.json` |
| Live shots | `/sdcard/Star/live/` |
| Freeform shot | `/sdcard/star_full.png` |
| Freeform crop | `/sdcard/star_shot_crop.png` |

### Termux-only
| What | Path |
|------|------|
| Sherpa voices | `~/sherpa-voices/` |
| Kokoro | `~/sherpa-voices/kokoro-en-v0_19/` |
| Lessac | `~/sherpa-voices/vits-piper-en_US-lessac-medium/` |
| RIFE models | `/data/data/com.termux/files/usr/opt/rife-ncnn-vulkan-nihui/rife-v4.6` |
| Extracted dex | `/data/local/tmp/star-dex/` |

---

## 4. BRIDGES

File-based IPC in `/sdcard/Star/`. App writes `X-request.json`, bridge writes `X-result.json`.

- Candy (`~/candy-bridge.sh`) — 19 languages, auto toolchain install, RIFE interpolate, video generate
- Pkg (`~/pkg-bridge.sh`) — pkg install, run command as Termux
- Audio (`~/audio-bridge.sh`) — Kokoro TTS via sherpa-onnx
- DSH (`~/star-bridge.sh`) — legacy
- Watchdog (`~/star-ensure.sh`) — 15-min restart check

---

## 5. WHAT WORKS TODAY

- Chat with any OpenRouter model
- Multi-model: Parallel, Mix (MoA), Chain
- Sessions (multi-chat, saved to disk)
- Live Mode: background watcher with TTS + follow-up voice
- Floating bubble: radial menu (Voice, Live, Screen, Hide), drag rotation, eye blink
- Image Studio: Pollinations (free)
- Video Studio: Ken Burns + RIFE
- Audio Studio: Kokoro TTS (offline, studio quality)
- Candy: builds 19 language families from GitHub
- RIFE: 12fps -> 24fps
- Builder: analyze repo -> workflow -> GitHub Actions -> download artifact
- Model picker: real benchmarks, real prices, per-provider drill, provider pinning
- GitHub Actions usage: real billing numbers

### Headless SDK - partial
- Virtual display creation: works (BinderCodes code 24 hardcoded)
- App launch on virtual display: works
- Screencap on virtual display: works
- Reading/typing on virtual display: BLOCKED by HyperOS

### Freeform window (ChatGPT) - partial
- Launch freeform: works
- Type text via accessibility: works
- Click Send button: works
- ChatGPT replies: works
- Polling for stability: was broken (Star animation), just patched, not tested
- Freeform ChatGPT pipeline: WORKS end-to-end. ask chatgpt: X -> typed -> sent -> crop -> OCR -> reply
- OCR via tesseract: works, routed through pkg-bridge via Shell base64 handoff
- Java -> bridge OCR call: fixed. Shell writes run-request.json, Termux bridge runs ffmpeg/tesseract


---

## 6. FREEFORM CHATGPT PIPELINE - WORKING

Verified 2026-10-10. `ask chatgpt: what is 2+2?` returns "ChatGPT says: 4."

### Architecture (final)
1. Shell (UID 2000, Shizuku) runs `am start --windowingMode 5`, `input text`, `input keyevent 66`.
2. Shell runs `screencap -p /sdcard/star_full.png`.
3. Shell base64-encodes a `{"cmd":"ffmpeg","args":[...]}` request and writes it to `/sdcard/Star/candy/run-request.json`.
4. pkg-bridge (Termux, UID 10xxx) runs ffmpeg crop -> `/sdcard/star_shot_crop.png`.
5. Shell reads `run-result.json`, deletes it.
6. Shell `md5sum` on crop. Loop until same 32-hex hash 3 times in a row.
7. Shell writes `{"cmd":"tesseract","args":[crop,"stdout"]}` to run-request.json.
8. pkg-bridge runs tesseract. Shell reads run-result.json.
9. Star parses stdout, replies.

### Why direct calls failed
- Termux ffmpeg/tesseract are u0_a407 mode 0700. Shizuku-shell (UID 2000) cannot exec them.
- App UID has no MANAGE_EXTERNAL_STORAGE, no media_rw. Cannot write /sdcard/Star/candy/ directly.

### Helper
`FreeformApp.bridgeRun(cmd, args)` - Shell writes base64-decoded request, polls result, deletes on receipt.

### Known imperfections (backlog step 2)
- Crop rect includes sidebar + ChatGPT usage banner. OCR text noisy but parseable.
- StarAgentLoop retried the whole pipeline once on the working run (task id re-issued).

## 7. BLOCKED / LIMITS (do not retry)

- Virtual display + accessibility. HyperOS does not expose secondary display windows. Reading/typing on hidden displays is impossible. Do not retry.
- app_process launching Java classes from APK. HyperOS blocks exec from /data/app/com.star.app/. Extracted dex to /data/local/tmp/star-dex/ - still silent failure. Do not retry.
- Google apps (Gemini, Bard) on virtual display. They refuse secondary displays. Do not retry.
- Clipboard read from background. Android 12+ blocks.
- Tauri desktop (ArtCraft GUI). Cannot run on Android.

---

## 8. NEXT (backlog, priority order)

1. ~~Test freeform ChatGPT pipeline~~ DONE 2026-10-10
2. Freeform polish: tighten crop rect to last reply bubble (currently includes sidebar + usage banner), then window position top-right, hide soft keyboard via settings put secure show_ime_with_hard_keyboard 0, scroll inside ChatGPT, copy button fallback
2b. Investigate StarAgentLoop retry that re-ran the pipeline once on the working run
3. ChatGPT conversation memory: remember last thread, type into same thread, "remove this session" command
4. ArtCraft Rust api_clients -> CLI. ~3 hours, 90% success.
5. Studio polish. Image / Video / Audio UIs.
6. Multi-session chat sidebar (Doc Step 6).
7. Splash + animations + onboarding.

---

## 9. JAVA FILES

Path: ~/StarApp/app/src/main/java/com/star/app/

### Activities
ChatActivity, MainActivity, SettingsActivity, VoiceActivity, TerminalActivity,
ImageStudioActivity, StudioModelsActivity, CandyActivity, DshSetupActivity,
VideoStudioActivity, AudioStudioActivity, StudioActivity, BuilderActivity,
LiveModeActivity, ModelsActivity

### Services
FloatingService, LiveService, StarAccessibilityService

### Core
AIClient.java - HTTP to OpenRouter / OpenAI / DeepSeek / Groq
Worker.java - one-shot worker model call
StarAgentLoop.java - React loop, tools, routing
EngineRouter.java - Native vs DSH
SkillRunner.java - runs Candy skills from chat
Memory.java - long-term memory
Prefs.java - all settings
Markdown.java - markdown renderer
Attachments.java - attachments
Updater.java - self-update
SelfHeal.java - accessibility repair
BridgeManager.java - DSH health
Shell.java - shell via Shizuku
ShizukuHelper.java - Shizuku bind
Candy.java / CandyManager.java - skill model + registry
Session.java / SessionStore.java - chat sessions
ModelCatalog.java - models, benchmarks, prices, providers
ModelPickerDialog.java - picker with sort/filter/provider drill
BuilderExpert.java - cross-compile prompt
GitHubActions.java - GitHub REST
ProviderIcons.java - provider logos
StudioGlyphView.java - studio radial icons
RadialIconView.java - bubble radial icons
BubbleFaceView.java - eye / listening face
ScreenCapture.java - screencap + JPEG + base64
ClipboardReader.java - read clipboard (foreground only)
HeadlessApp.kt - Kotlin, SDK bridge, kept for reference
FreeformApp.kt - Kotlin, current ChatGPT pipeline

---

## 10. THE SDK (integrated as :sdk module)

Location: ~/StarApp/sdk/
Original: ~/headless-android-sdk/
Namespace: com.headless.android

What we changed:
- sdk/build.gradle: Java 21 -> 17, jvmTarget 21 -> 17, aidl true removed
- Removed AgentUserService.kt, removed AIDL file
- PrivilegeBackend.kt / ShizukuBackend.kt: removed IAgentUserService methods
- BinderCodes.kt: displayCreate() hardcoded to 24
- VirtualDisplayManager.kt: null surface; direct call to code 24
- AppLauncher.kt: force-stop target before launch; verify 6 -> 30 attempts
- UiHierarchyExtractor.kt: extra logging
- UiAgentMain.kt: untouched, does not start on HyperOS

Warning: SDK consumes one virtual display per call and does not auto-close.
HeadlessApp.ask closes stale sessions on entry.

---

## 11. UI CONVENTIONS

Colors:
- BG #0F0F0F, Card #1E1E1E, Border #2A2A2A
- Text #ECECEC, Muted #8E8E8E
- Blue #7CB3FF, Gold #FFB800, Green #7EE787, Red #F85149, Orange #F0883E, Purple #A78BFA

Rules:
- No emoji in buttons
- No Material Design
- Monospace for terminal / numbers / IDs
- Plain TextViews with GradientDrawable backgrounds
- setTextIsSelectable(true) on copyable text

Top bar: 5 circle pills (Agent, Skills, Multi, Chat Model, Worker Model).
Long-press any pill opens an info card.

---

## 12. RULES FOR THE AI ASSISTANT

1. Do not write long explanations. Give code.
2. Heredocs for file writes. No nano.
3. Python for multi-line replacement, safe outer delimiters.
4. After each build, tell user to install via System Info.
5. Test after every change. Ask for exact error text.
6. Keep code in one batch.
7. Absolute paths. No env assumptions.
8. No /tmp. Use $HOME or /sdcard.
9. Shell user (Star) and Termux user are different.
10. Cross-user: /sdcard or /data/local/tmp.
11. Never fake a number. Real data or "unknown".
12. If user says stop drifting, refer to this file.

---

## 13. HOW TO USE THIS FILE

Extract in Termux: star-state
New chat: paste the full output as the first message.

---

## 14. CURRENT FOCUS

Working on: Freeform ChatGPT polish (Section 8 step 2).

Just completed: Section 6. Freeform ChatGPT pipeline works end-to-end. Verified 2026-10-10 with `ask chatgpt: what is 2+2?` -> "ChatGPT says: 4."

Next:
1. Tighten crop rect. Current crop is full window bounds (e.g. 899x1589 at 1076,164). Reply text is a small fraction of that. Options:
   a. Use accessibility to find the last message bubble's bounds directly.
   b. Heuristic: crop the lower 60% of the window, then look for the densest text region.
   c. Post-OCR filter: strip known chrome strings ("Get Plus", "Files, images, and data analysis", sidebar artifacts).
2. Investigate StarAgentLoop retry. On the working run, task id 4426 was issued twice. Check if the first successful result was rejected (parsing) or if a redundant retry fires unconditionally.
3. Then proceed to Section 8 items 3-7 (conversation memory, ArtCraft CLI, studio polish, multi-session sidebar, splash).

Key facts:
- Shell.java routes through Shizuku when available, falls back to local ProcessBuilder. On this device, Shizuku is active - all Shell.run() calls run as UID 2000.
- pkg-bridge must be running. Watchdog star-ensure.sh restarts it.
- base64 handoff avoids MANAGE_EXTERNAL_STORAGE entirely. Do not add that permission; the current design is correct.
