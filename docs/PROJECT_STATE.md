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
- OCR via tesseract: works manually
- Java -> bridge OCR call: was malformed, just rewritten, not tested


---

## 6. IN PROGRESS - the freeform ChatGPT pipeline

**Current untested state:** Just patched FreeformApp to:
1. Get ChatGPT window bounds via StarAccessibilityService.boundsOfPackage(pkg)
2. Crop full screencap to those bounds with ffmpeg
3. Hash the crop (not full screen) - should stabilize
4. When stable 3 polls in a row, OCR the crop via ocrFile()
5. Fall back to full-screen OCR if crop OCR is short

**Must verify next session:**
- Build succeeds
- Install APK via System Info
- "ask chatgpt: what is 2+2?" returns an answer
- Check logcat: grep "StarFreeform" /sdcard/slog.txt | tail -40
  - Look for "window bounds = l,t,r,b"
  - Look for "crop hash=... stable=1" then 2 then 3
  - Look for "crop OCR length=N" where N > 30

**If it fails:**
- Crop hash never stabilizes -> tighter crop
- OCR length 0 -> check manual pipeline: write run-request.json with tesseract and star_full.png, sleep 4, cat run-result.json

---

## 7. BLOCKED / LIMITS (do not retry)

- Virtual display + accessibility. HyperOS does not expose secondary display windows. Reading/typing on hidden displays is impossible. Do not retry.
- app_process launching Java classes from APK. HyperOS blocks exec from /data/app/com.star.app/. Extracted dex to /data/local/tmp/star-dex/ - still silent failure. Do not retry.
- Google apps (Gemini, Bard) on virtual display. They refuse secondary displays. Do not retry.
- Clipboard read from background. Android 12+ blocks.
- Tauri desktop (ArtCraft GUI). Cannot run on Android.

---

## 8. NEXT (backlog, priority order)

1. Test freeform ChatGPT pipeline (Section 6)
2. Freeform polish: window position top-right, hide soft keyboard via settings put secure show_ime_with_hard_keyboard 0, scroll inside ChatGPT, copy button fallback
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

Working on: Freeform ChatGPT pipeline (Section 6).

Right before context ran out: Patched FreeformApp.kt to:
1. Get ChatGPT window bounds via StarAccessibilityService.boundsOfPackage(pkg)
2. Crop screencap to those bounds
3. Hash the crop instead of full screen
4. OCR the crop via tesseract

Next step: Build -> install -> run "ask chatgpt: what is 2+2?" -> check:
- grep "StarFreeform" /sdcard/slog.txt | tail -40
- Look for "window bounds = l,t,r,b"
- Look for "crop hash=... stable=1 2 3"
- Look for "crop OCR length=N" with N > 30

Key facts:
- tesseract 5.5.3 installed in Termux, works on manual test
- pkg-bridge running, manual OCR works
- ChatGPT reply DOES appear (2 + 2 = 4, good sir.)
- Star's own animation was breaking the full-screen hash stabilization
- All this code is in FreeformApp.kt

Blocked on: nothing. All tools installed.
