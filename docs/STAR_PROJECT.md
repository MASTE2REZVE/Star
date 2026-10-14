# STAR PROJECT — MASTER CONTEXT

## 1. WHAT IS STAR

Star is an Android app. Star is an AI agent. Star runs on a Xiaomi Pad 7 tablet. Star has no PC.

The user builds Star on the tablet. The build tool is Termux.

Star controls the tablet. Star runs shell commands. Star taps the screen. Star talks to the user. Star builds tools from GitHub.

## 2. KEY LOCATIONS

| Item | Path |
|------|------|
| Project root | /data/data/com.termux/files/home/StarApp |
| APK output | /data/data/com.termux/files/home/StarApp/app/build/outputs/apk/debug/app-debug.apk |
| APK share copy | /storage/emulated/0/Star.apk |
| Settings store | SharedPreferences named "star" |
| DSH bridge files | /sdcard/Star/ |
| Candy files | /sdcard/Star/candy/ |
| Termux home | /data/data/com.termux/files/home |
| Termux binaries | /data/data/com.termux/files/usr/bin |
| Shell-usable binaries | /data/local/tmp/star/bin |
| Skill binaries (shared) | /sdcard/Star/candy/bin |
| DSH install | /data/data/com.termux/files/usr/lib/node_modules/@deepseek-ai/dsh |
| DSH profiles | /data/data/com.termux/files/home/.dsh/profiles |

## 3. BUILD AND INSTALL

### Build the app

Run this command in Termux:

    star-build

The script does two steps:
1. Runs gradle assembleDebug.
2. Copies the APK to /storage/emulated/0/Star.apk.

The script is at /data/data/com.termux/files/home/bin/star-build.

### Install the app

1. Open the Star app.
2. Open the drawer. Tap System Info.
3. Tap "Install /sdcard/Star.apk".
4. Star installs the APK with Shizuku. Star does not show the system installer.

### Manual install (if the button fails)

1. Open the Files app.
2. Go to Internal storage.
3. Tap Star.apk.
4. Allow "Install unknown apps" for Files.
5. Tap Install.

## 4. ARCHITECTURE

### 4.1 Three engines

Star has three engines. A router picks one engine for each message.

| Engine | When to use | Speed |
|--------|-------------|-------|
| Native | UI tasks. Open apps. Tap. Type. Screenshots. Short chat. | 2 to 5 seconds |
| DSH | Reasoning. Files. Analysis. Multi-step tasks. | 15 to 40 seconds |
| Candy | Build CLI tools from GitHub. | 1 to 10 minutes |

### 4.2 The router

File: app/src/main/java/com/star/app/EngineRouter.java

The router reads the user message. The router counts keywords.

UI keywords: open, launch, tap, click, scroll, swipe, press, search for, type, screenshot, youtube, facebook, whatsapp, instagram, chrome.

DSH keywords: analyze, explain, summarize, count, find all, search files, read file, write file, edit file, grep, list files, run script, code, python, javascript, debug, refactor, compare, calculate, plan, reason, step by step, workflow.

Rule: UI keywords go to Native. DSH keywords go to DSH. Long messages (over 120 chars) go to DSH. Other messages go to Native.

### 4.3 The DSH bridge

DSH is not an Android library. DSH is a Node.js program. DSH runs in Termux.

Star cannot call DSH directly. Star and DSH use different Linux users. Star is UID 2000 (shell). DSH is UID 10xxx (Termux user).

Solution: file-based IPC. Star writes a task file. Termux watches the file. Termux runs DSH. Termux writes an answer file. Star reads the answer file.

Files:
- /sdcard/Star/task.txt — Star writes the task here
- /sdcard/Star/answer.txt — DSH writes the answer here
- /sdcard/Star/reason.txt — DSH writes the reasoning here
- /sdcard/Star/.done — signals completion
- /sdcard/Star/.lock — prevents double-run
- /sdcard/Star/bridge.log — bridge activity log

The watcher script is /data/data/com.termux/files/home/star-bridge.sh.

### 4.4 The Candy bridge

Candy builds CLI tools from GitHub. The build tool needs Rust, Node.js, or Python. Star does not have these. Termux has these.

The Candy bridge uses the same file-based IPC pattern. The bridge is at /data/data/com.termux/files/home/candy-bridge.sh.

Candy files:
- /sdcard/Star/candy/build-request.json — Star writes the repo URL
- /sdcard/Star/candy/build-result.json — bridge writes the status
- /sdcard/Star/candy/build.log — bridge writes the log
- /sdcard/Star/candy/bin/ — bridge copies binaries here
- /sdcard/Star/candy/registry.json — Star stores installed skills

The registry format:

    [
      {
        "name": "ripgrep",
        "repo": "https://github.com/BurntSushi/ripgrep",
        "language": "rust",
        "installed": "2026-10-09",
        "status": "installed"
      }
    ]

### 4.5 Auto-start and recovery

The tablet kills Termux processes. The tablet is HyperOS. HyperOS is aggressive.

Three layers of recovery:

1. Termux:Boot — starts the bridge 60 seconds after boot. Path: ~/.termux/boot/start-bridge.sh. This needs the Autostart permission in HyperOS.

2. Job scheduler — runs star-ensure.sh every 15 minutes. star-ensure.sh checks the bridge. If the bridge is dead, star-ensure.sh restarts it.

3. Manual fallback — the user types "start-bridge" in Termux. The alias and script are in ~/bin/start-bridge.

## 5. FILE MAP

### Java source (app/src/main/java/com/star/app/)

| File | Purpose |
|------|---------|
| ChatActivity.java | Main chat UI. Drawer. Bubbles. Voice button. Attach button. Model picker. |
| MainActivity.java | System Info screen. Fix Permissions button. Test DSH button. |
| SettingsActivity.java | API keys. Engine toggle. Memory. Instructions. |
| VoiceActivity.java | Full-screen voice mode. Big orb. Auto-listen loop. |
| TerminalActivity.java | Shell terminal. Copy button. Share button. |
| ImageStudioActivity.java | Image generation. Pollinations API. |
| StudioModelsActivity.java | Model picker with filters (Free, Fast, Vision, Tools). |
| CandyActivity.java | Skills screen. Add repo. Build. Run. Remove. |
| DshSetupActivity.java | Setup wizard for new devices. |
| FloatingService.java | Floating bubble overlay. Drag-to-voice. |
| StarAccessibilityService.java | UI automation. Tap. Type. Scroll. Find. |
| StarAgentLoop.java | Agent loop with step limit. Native and DSH routing. |
| AIClient.java | HTTP client for OpenRouter, DeepSeek, Groq. |
| DshClient.java | File-based client for DSH bridge. |
| EngineRouter.java | Chooses engine for each message. |
| Shell.java | Runs shell commands via Shizuku. |
| ShizukuHelper.java | Shizuku binding and permission. |
| SelfHeal.java | Repairs accessibility service. |
| BridgeManager.java | Watches DSH bridge health. |
| Prefs.java | SharedPreferences wrapper. |
| Memory.java | Long-term memory file. |
| Markdown.java | Renders markdown in chat. |
| Attachments.java | File and image attachments. |
| Updater.java | GitHub self-update. |
| Candy.java, CandyManager.java | Candy skill model and logic. |

### Termux scripts (in ~/)

| Script | Purpose |
|--------|---------|
| star-bridge.sh | Watches /sdcard/Star/task.txt. Runs DSH. Writes answer. |
| candy-bridge.sh | Watches /sdcard/Star/candy/build-request.json. Builds repo. Copies binary. |
| star-ensure.sh | Job-scheduler watchdog. Restarts bridge if dead. |
| star-warm.sh | Warms DSH page cache. |
| star-run.sh | One-shot DSH runner (legacy). |
| start-bridge | Manual bridge starter. |
| star-build | Gradle build + APK copy. |

## 6. WHAT WORKS

- Chat with any OpenRouter model.
- Voice input (Android SpeechRecognizer).
- Voice Mode (TTS auto-listen loop).
- Floating bubble with drag-to-voice.
- Shell commands via Shizuku (UID 2000).
- Accessibility automation (tap, type, scroll, launch).
- Screenshots.
- Image Studio with Pollinations (free, no key).
- Model picker with live prices and filters.
- Self-update from GitHub.
- Auto-repair of accessibility.
- DSH integration for reasoning tasks.
- Smart router between Native and DSH.
- Candy: build CLI tools from GitHub (ripgrep works).
- Auto-start via job scheduler.
- Long-term memory and custom instructions.

## 7. WHAT IS BROKEN OR UNFINISHED

| Item | Status | Notes |
|------|--------|-------|
| DSH bridge occasionally dies | Mitigated | star-ensure.sh restarts within 15 min |
| Termux:Boot does not always fire | Mitigated | Job scheduler is the fallback |
| Candy "✓" showed on failed builds | Fixed | Bridge now checks exit code |
| pdfcraft does not build | Known limit | pdfcraft is a GUI app. GUI apps need a mobile UI rewrite. |
| Candy runs only from /data/local/tmp | Working | Star copies from /sdcard/Star/candy/bin |
| Voice recognition accuracy | Fair | Depends on Google app |
| Some weak models return "null" | Handled | Reply filter drops "null" |

## 8. NEXT STEPS (Priority Order)

### Step 1: Termux package skills
Add a Candy mode. Paste a pkg name. Candy installs it. Candy registers it as a skill.
Example pkgs: poppler-utils, qpdf, ffmpeg, imagemagick, yt-dlp.

### Step 2: pdfcraft CLI wrapper
Write a Rust CLI wrapper. Call pdfcraft's library crates. Skip the GUI. Register the wrapper as a Candy skill.

### Step 3: Video Studio
Add a video generation screen. Use OpenRouter video models. Use Pollinations for free fallback.

### Step 4: Audio Studio
Add text-to-speech and music generation. Use OpenRouter audio models. Use Android TTS for offline.

### Step 5: ComfyUI-style node canvas
Replace the Image Studio UI. Use a draggable node graph. Send the graph as a JSON workflow.

### Step 6: Multi-session chat
Add conversation history. Add a sidebar to switch sessions.

### Step 7: Live Mode
Add background screen watching. Stream screenshots to a vision model. Speak answers back.

### Step 8: Polish
Add a splash screen. Add animations. Improve onboarding.

## 9. RULES FOR THE AI ASSISTANT

1. Do not write long explanations. Give code.
2. Use heredocs for file writes. Do not use nano.
3. Use Python for multi-line string replacement.
4. After each build, tell the user to install via System Info.
5. Test after every change. Ask for the exact error text.
6. Keep the code in one batch. Do not split into many messages.
7. Use absolute paths. Do not assume environment variables.
8. Do not write to /tmp. Use $HOME or /sdcard.
9. Remember that shell user (Star) and Termux user are different.
10. Cross-user file access uses /sdcard or /data/local/tmp.

## 10. USER PREFERENCES

- The user speaks English as a second language. Use simple words.
- The user dislikes flattery. Be direct.
- The user wants small commits. The user wants to see each step work.
- The user has a Xiaomi Pad 7. 8 GB RAM. 256 GB storage. Android 14. HyperOS.
- The user has Shizuku running. The user has Termux with DSH.
- The user wants an all-in-one AI app.
- The user wants no ads. No tracking. No cloud dependency.
- The user wants the app to work after moving to a new device.

## 11. HOW TO GET INFORMATION FROM THE TERMINAL

If a command fails, ask the user to run these in Termux:

    pgrep -f star-bridge.sh
    pgrep -f candy-bridge.sh
    tail -20 /sdcard/Star/bridge.log
    tail -20 /sdcard/Star/candy/build.log
    cat /sdcard/Star/answer.txt
    ls -la /sdcard/Star/

If the user must run Android commands, use Star's Terminal app. Star's Terminal runs as shell user. Star's Terminal can run "pm", "am", "logcat", and "settings".

## 12. STYLE

- Colors: black background (#0F0F0F). Blue accent (#7CB3FF). Gold (#FFB800). Green (#7EE787). Red (#F85149). Orange (#F0883E).
- No emoji in buttons. Use text labels.
- No capsules. Use flat text buttons.
- No Material Design. Use plain TextViews.
- Font: monospace for terminal. Sans-serif for chat.
END
