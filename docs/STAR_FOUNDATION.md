# STAR PROJECT — FOUNDATION LOG

**Purpose:** This file records how Star was first built. It is a companion to STAR_PROJECT.md and to the "star-state" snapshot. Read all three together.

**Style:** ASD-STE100. Short sentences. Simple words.

---

## 1. WHAT THIS CHAT COVERED

This chat built Star from zero to a working agent app.
Star did not exist at the start.
Star is a full Android app at the end.
The user has no PC. All work happened in Termux on the Xiaomi Pad 7.
The next chat continues from a more advanced state. That state is in "star-state".
This file is the history that led to that state.

---

## 2. TIMELINE OF BUILD

### Day 1 — Toolchain

- Installed Termux from F-Droid.
- Installed OpenJDK 21 in Termux.
- Installed the Android SDK and Gradle.
- Added the aapt2 override for Termux.

Command used in ~/.gradle/gradle.properties:

    android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2

### Day 1 — First app

- Built a Hello World app with a shell script.
- Installed it on the tablet.
- Renamed it to Star.
- Added a custom icon (hexagon with a sparkle).

### Day 1 — AI brain

- Added AIClient.java. It talks to OpenRouter.
- Added a chat screen with a settings screen for the API key.
- Tested with DeepSeek and Nemotron models.
- Added a "Fetch Models" button. It reads the model list from the provider.
- Added a preset model picker with FREE and PAID tags.

### Day 1 — Shell and Shizuku

- Added Shizuku support. Shizuku gives ADB-level shell.
- Wrote ShizukuHelper.java. It uses reflection to run commands.
- Shizuku 13.x makes newProcess() private. Reflection is required.
- The shell user is UID 2000. The user is "shell".
- Added a Terminal screen. It runs commands as shell.

### Day 1 — Accessibility service

- Added StarAccessibilityService.java.
- The service reads the screen. It finds buttons. It taps. It types.
- The user must enable the service in Settings > Accessibility.
- Added ui_dump, ui_find, ui_tap, ui_type, ui_press, ui_scroll, ui_seq.
- ui_seq runs multiple actions in one call. It is the fastest method.

### Day 1 — Agent loop

- Added StarAgentLoop.java.
- The loop sends the message to the AI.
- The AI returns either a reply or a tool call.
- The loop runs the tool. The loop sends the result back.
- The loop stops at MAX_TOOL_DEPTH steps.

### Day 1 — Self-update

- Added Updater.java.
- The app downloads a new APK from a GitHub release.
- The manifest holds the version number.
- GitHub serves the file. The app checks the version. The app installs the new APK.
- The user must allow "Install unknown apps".

### Day 1 — Voice

- Added voice input with Android SpeechRecognizer.
- Added Voice Mode. It is a full-screen activity.
- The orb listens. It thinks. It speaks back with TextToSpeech.
- The orb auto-listens again after speaking.

### Day 1 — Floating bubble

- Added FloatingService.java. It shows a small overlay bubble.
- The bubble taps to open a chat panel.
- The bubble drags to move.
- The bubble drags to a red X to remove.
- The bubble drags UP to start a voice command in place.
- Drag down first = reposition mode. Drag up after that does not start voice.

### Day 2 — DSH integration

- DSH was already installed in Termux.
- DSH runs as a Node.js program. Star cannot call DSH directly.
- Solution: file-based IPC in /sdcard/Star/.
- Star writes task.txt. The bridge reads it. The bridge runs DSH. The bridge writes answer.txt.
- The bridge script is ~/star-bridge.sh.
- Star polls for the .done file. When .done exists, the answer is ready.

### Day 2 — Engine router

- Added EngineRouter.java.
- The router picks Native for UI tasks. It picks DSH for reasoning tasks.
- The keywords are in the source. See STAR_PROJECT.md section 4.2.
- The user does not pick the engine. The router picks.

### Day 2 — Candy

- Added Candy. Candy builds CLI tools from GitHub.
- The bridge clones the repo. It detects the language. It runs the build. It copies the binary.
- The first tool built was ripgrep. It works.
- Binaries install to $PREFIX/bin and to /sdcard/Star/candy/bin.
- Star copies from /sdcard to /data/local/tmp/star/bin to run the binary.

### Day 2 — Auto recovery

- Added star-ensure.sh. It runs every 15 minutes.
- The job scheduler runs star-ensure.sh. The scheduler survives reboot.
- star-ensure.sh restarts the bridge if the bridge is dead.
- Termux:Boot is also configured. It does not always fire on HyperOS.

---

## 3. KEY TECHNICAL DECISIONS

| Decision | Reason |
|----------|--------|
| Termux for build | The user has no PC. |
| Shizuku for shell | No root. ADB-level commands need Shizuku. |
| File IPC for DSH | Cross-user access is only safe in /sdcard and /data/local/tmp. |
| File IPC for Candy | Same reason. |
| Job scheduler for watchdog | Termux:Boot is unreliable on HyperOS. |
| /data/local/tmp/star/bin for runnable binaries | /sdcard has noexec. Termux home is 0700. |
| Java, not Kotlin | Simpler for scripting and for the shell user. |
| Plain TextViews, not Material | Lighter. Faster. No hidden styling. |
| Heredoc for file writes | nano is slow and does not paste well. |
| Python for multi-line replacement | sed fails on multi-line strings. |

---

## 4. PROBLEMS FOUND AND FIXED

| Problem | Fix |
|---------|-----|
| Gradle cannot find aapt2 | Set android.aapt2FromMavenOverride. |
| Shizuku 13 newProcess is private | Use reflection with setAccessible(true). |
| Process "hasn't exited" | Use plain proc.waitFor(), not the timed version. |
| AI replies with "null" as text | Filter "null" and empty replies. |
| AI dumps reasoning as the answer | System prompt blocks it. stripThinking removes it. |
| Model returns malformed tool calls | Parser is defensive. |
| FloatingService UI calls from background thread | Wrap every call in ui(() -> ...). |
| Accessibility stops after app close | SelfHeal writes the setting via Shizuku. |
| Auto-repair of accessibility fails on same value | Remove from list, wait 400ms, re-add. |
| DSH bridge reads empty answer.txt | Bridge writes .done after DSH exits. Star waits for .done. |
| Bridge dies after reboot | Job scheduler runs star-ensure.sh every 15 min. |
| Candy shows tick on failed build | Bridge checks exit code. Star reads status. |
| CandyActivity crashes on tap | The manifest did not register the activity. Added now. |
| Candy run uses $PREFIX (empty for shell) | Use absolute path /data/data/com.termux/files/usr/bin. |
| Shell user cannot read Termux home | Use /sdcard or /data/local/tmp. |
| Termux home is 0700 | Same. |
| Shell user cannot execute from /sdcard | Copy to /data/local/tmp first. |
| Python patches create duplicate code | Verify before and after. Use unique anchors. |
| /tmp is missing in Termux | Use $HOME or /sdcard. |
| HyperOS blocks boot receivers | Job scheduler is the fallback. |
| Termux:RunCommandService needs a signature permission | Cannot use. Use file IPC. |

---

## 5. SCRIPTS CREATED IN THIS CHAT

All in /data/data/com.termux/files/home/.

| Script | Purpose |
|--------|---------|
| star-build | Runs gradle assembleDebug. Copies the APK to /sdcard. |
| star-bridge.sh | Watches /sdcard/Star/task.txt. Runs DSH. Writes answer. |
| candy-bridge.sh | Watches /sdcard/Star/candy/build-request.json. Builds. Copies. |
| star-ensure.sh | Runs from job scheduler every 15 min. Restarts bridges. |
| star-warm.sh | Warms the DSH page cache. |
| star-run.sh | One-shot DSH runner (legacy). |
| start-bridge | Manual bridge starter. |
| star-status | Prints the current state. |

---

## 6. JAVA FILES CREATED IN THIS CHAT

Path: ~/StarApp/app/src/main/java/com/star/app/

| File | Purpose |
|------|---------|
| ChatActivity | Main chat. Drawer. Bubbles. Voice. Attach. |
| MainActivity | System Info. Fix Permissions. Test DSH. Install APK. |
| SettingsActivity | API key. Engine toggle. Memory. Instructions. |
| VoiceActivity | Full-screen voice mode. Big orb. Auto-listen. |
| TerminalActivity | Shell terminal. Copy. Share. |
| ImageStudioActivity | Image generation with Pollinations. |
| StudioModelsActivity | Model picker with filters. |
| CandyActivity | Skills screen. Add. Build. Run. Remove. |
| DshSetupActivity | Setup wizard for new devices. |
| FloatingService | Floating bubble overlay. |
| StarAccessibilityService | UI automation. |
| StarAgentLoop | Agent loop with step limit. |
| AIClient | HTTP to OpenRouter, DeepSeek, Groq. |
| DshClient | File-based client for DSH. |
| EngineRouter | Native vs DSH. |
| Shell | Runs shell commands via Shizuku. |
| ShizukuHelper | Shizuku binding. |
| SelfHeal | Repairs accessibility. |
| BridgeManager | Watches DSH health. |
| Prefs | SharedPreferences wrapper. |
| Memory | Long-term memory file. |
| Markdown | Markdown renderer. |
| Attachments | File and image attachments. |
| Updater | GitHub self-update. |
| Candy | Skill model. |
| CandyManager | Skill registry and build logic. |

---

## 7. USER PREFERENCES (LEARNED)

- The user has no PC. All work is on the tablet.
- The user wants the app to be all-in-one.
- The user wants no ads. No tracking. No cloud login.
- The user wants self-setup on a new device.
- The user dislikes flattery. Be direct.
- The user dislikes long explanations. Give code.
- The user dislikes emoji in buttons.
- The user dislikes Material Design capsules.
- The user wants the build to run in one command: star-build.
- The user wants the install to run in one tap: System Info > Install.
- The user wants the app to recover after reboot with no action.

---

## 8. RULES THE USER TAUGHT

1. Do not use nano. Use heredoc.
2. Do not use /tmp. Use $HOME or /sdcard.
3. Do not split code across many messages. Send one batch.
4. Test after every change. Ask for the error text.
5. Do not add features the user did not ask for.
6. Do not narrate. Do not explain the plan. Give the code.
7. Do not use the emoji in a button.
8. Do not use capsules.
9. Do not fake progress. Show the real status.
10. When a build fails, read the log before guessing.

---

## 9. HOW TO CONTINUE

### To start a new chat

Step 1. Run in Termux:

    star-state

Step 2. Copy the output.

Step 3. Open a new chat. Paste this line first:

    You are the developer of an Android app named Star. Read all three documents below. Then wait for my instructions. Do not write code until I ask.

Step 4. Paste the three documents:
- STAR_PROJECT.md (architecture)
- STAR_FOUNDATION.md (this file — history)
- star-state output (current state)

Step 5. Write the next task.

### How to keep this file current

Append new entries to this file. Do not delete old entries.
Add a new "Day N" section under Section 2 for each session.
Add new bugs to Section 4.
Add new scripts to Section 5.
Add new Java files to Section 6.

---

## 10. COMMANDS THE NEW CHAT WILL NEED

Check the bridge:

    pgrep -f star-bridge.sh

Check the Candy bridge:

    pgrep -f candy-bridge.sh

Check the log:

    tail -20 /sdcard/Star/bridge.log
    tail -20 /sdcard/Star/candy/build.log

Check the state:

    star-state

Restart bridges:

    pkill -f star-bridge.sh; nohup ~/star-bridge.sh > /dev/null 2>&1 &
    pkill -f candy-bridge.sh; nohup ~/candy-bridge.sh > /dev/null 2>&1 &

Build the app:

    star-build

Install the app:

    Open Star > System Info > Install /sdcard/Star.apk

Read Android logs:

    Open Star > Terminal
    logcat -d | grep -i "star" | tail -40

---

## 11. CHECKLIST FOR A NEW DEVICE

If the user installs Star on a new tablet, the app must set itself up.

1. Install Termux from F-Droid.
2. Open Termux. Run: pkg update
3. Install Node.js, Git, Python, and Rust.

    pkg install -y nodejs-lts git python rust binutils

4. Install DSH.

    npm install -g @deepseek-ai/dsh

5. Copy the bridge scripts from the old device, or paste them from this document.
6. Add the boot script to ~/.termux/boot/.
7. Add the job scheduler entry:

    termux-job-scheduler --script ~/star-ensure.sh --period-ms 900000 --persist true

8. Start the bridges.
9. Open Star. Open the drawer. Tap DSH Setup. Follow the steps.

---

## 12. WHAT THE USER WANTS NEXT

The user wants an all-in-one AI app. It does everything. It has no equal. It runs on any Android tablet.

The user wants:
- Image editing (Adobe-like)
- Video editing (Adobe-like)
- Audio editing (Adobe-like)
- 3D mesh creation
- Code image mode (stickman, SVG, diagrams)
- Skill stealing from any GitHub repo
- Auto-update of skills
- Multi-skill pipelines
- ChatGPT automation (via freeform window)
- Live Mode (background screen watcher)
- Voice with a natural voice

The current state (star-state) shows much of this is already in progress.

---

## 13. HONEST LIMITS

- GUI Rust apps (ArtCraft) do not run on Android. They need a mobile UI rewrite. Or a CLI wrapper around the library crates.
- HyperOS blocks accessibility on secondary virtual displays. Do not retry.
- HyperOS blocks app_process from loading classes from the app APK. Do not retry.
- Google apps (Gemini, Bard) refuse the virtual display. Do not retry.
- Clipboard reads from background are blocked on Android 12+. Not fixable.
- Some commands need Shizuku. If Shizuku is off, the app cannot run them.

---

## 14. HOW TO APPEND TO THIS FILE

Use this pattern:

    cat >> ~/StarApp/docs/STAR_FOUNDATION.md << 'ENDOFDOC'

    ## Day N — [session title]

    - What was built.
    - What was fixed.
    - What was learned.

    ENDOFDOC

The file grows over time. The next chat reads it. Nothing is lost.

---

END OF FILE
