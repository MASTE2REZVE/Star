# STAR — SESSION LOG (APPENDIX)

Companion to the long state file. Records how the DSH and Candy foundation was built.

## 1. WHAT THIS SESSION BUILT
Started: Native engine only.
Ended: Native + DSH + Candy engines. Working GitHub-to-tool pipeline.

## 2. DSH BRIDGE PROTOCOL
1. Star writes /sdcard/Star/task.txt
2. Bridge reads it, deletes it, removes .done and .lock
3. Bridge runs: dsh --profile headless "$MSG" > answer.txt 2> reason.txt
4. Bridge writes .done AFTER DSH exits
5. Star waits for .done, then reads answer.txt

Never wait for answer.txt. Wait for .done.

Mandatory bridge env:
- LD_PRELOAD=$PREFIX/lib/libtermux-exec.so
- PATH=$PREFIX/bin:$HOME/.cargo/bin:$HOME/bin

## 3. CANDY BRIDGE PROTOCOL
1. Star writes /sdcard/Star/candy/build-request.json
2. Bridge detects language from repo root
3. Bridge runs build, checks exit code
4. On success: copies binary to $PREFIX/bin and /sdcard/Star/candy/bin
5. Bridge writes build-result.json (status: installed/failed/library)
6. Bridge writes build.log

## 4. USER-NAMESPACE CONFLICT
Star = shell user (UID 2000). DSH/Candy = Termux user (UID 10xxx).
- Shell cannot read Termux home (0700)
- Shell cannot execute from /sdcard (noexec)
- Shell can read/write /sdcard
- Shell can read/write/execute /data/local/tmp

Binary path: Termux builds -> /sdcard/Star/candy/bin/ -> Star copies to /data/local/tmp/star/bin/ -> Star runs it.

## 5. HYPEROS BOOT BLOCK
Termux:Boot does not fire. Use termux-job-scheduler:
  termux-job-scheduler --script ~/star-ensure.sh --period-ms 900000 --persist true
Bridge returns within 15 min after reboot.

## 6. ROUTER KEYWORDS
Native: open, launch, tap, click, scroll, swipe, press, search for, type, screenshot, youtube, facebook, whatsapp, chrome, keyboard, back, home, recents
DSH: analyze, explain, summarize, count, find all, list files, read file, write file, grep, code, python, debug, refactor, plan, reason, workflow
Rule: Native wins if both match. Long messages (>120 chars) go DSH.

## 7. VERIFIED EXAMPLES
Native: "open YouTube and search for cats" -> works.
DSH: "list files in /sdcard/Star" -> works.
Candy: "https://github.com/BurntSushi/ripgrep" -> "ripgrep 15.2.0" runs.

## 8. NEW CHAT STARTUP
1. Run: star-state
2. Copy the full output
3. New chat first line: "You are the developer of Star. Read all below. Do not write code until I ask."
4. Paste the star-state output
5. Task line: "Task: continue Section 14 of the long state file."

## 9. APPEND PATTERN
cat >> ~/StarApp/docs/STAR_SESSION_LOG.md << 'ENDOFDOC'
## Day N — title
- what was built

## Day 11 — Freeform ChatGPT pipeline fixed end-to-end
- Root cause: two independent failures masked each other.
  1. `Shell.run("ffmpeg ... 2>/dev/null")` — Termux ffmpeg is u0_a407 0700, Shizuku-shell (UID 2000) cannot exec it. The stderr redirect hid "not found".
  2. `java.io.File(...).writeText` from the app UID — no MANAGE_EXTERNAL_STORAGE, no media_rw group. EPERM writing `/sdcard/Star/candy/run-request.json`.
- Fix: added `bridgeRun(cmd, args)` in FreeformApp.kt. Shell encodes request as base64, writes to run-request.json via `sh -c 'echo ... | base64 -d > ...'`, polls run-result.json, deletes on receipt. All /sdcard writes now done by Shell (UID 2000); ffmpeg/tesseract run as Termux via pkg-bridge.
- Stability loop now validates hash: 32 hex chars only, else treats as unstable. Old code counted "No such file or directory" as a stable frame.
- Verified: `ask chatgpt: what is 2+2?` -> "ChatGPT says: 4."
- Log markers: `window bounds = 1076,164,1975,1753`, `crop bridge={"status":"ok"...}`, `crop hash=b29c1407... real=true`, `ocr body={"status":"ok","stdout":"...177 chars..."}`.
- Known imperfection: crop rect includes sidebar + usage banner. OCR text is noisy but parseable. Next: tighter crop around last reply bubble.
- Known imperfection: StarAgentLoop re-ran the pipeline once (task 4426 issued twice). Investigate retry trigger.

## Day 3 — Build fix + Candy/Builder connection

### Gradle downgrade
- System Gradle 9.8.0 fails with cache corruption on Termux.
- Downloaded Gradle 8.10.2 to ~/.gradle/wrapper/dists/gradle-8.10.2/
- star-build script points at that binary.
- AGP 8.5.2 + Gradle 8.10.2 is the tested pair.

### aapt2 symlink fix
Termux aapt2 is not x86. Gradle extracts an x86 aapt2 from the AAR.
Symlink replaces it:

    find ~/.gradle/caches -type f -name "aapt2" | while read f; do
      rm -f "$f"; ln -sf /data/data/com.termux/files/usr/bin/aapt2 "$f"
    done

Must run after any cache clear. Add to star-build if it becomes flaky.

### star-build rewritten
Now uses `set -e`. Exits 1 if APK missing. No more fake "OK".

### Candy + Builder connection
- CandyActivity: adds offerBuilder() on build failure.
- BuilderActivity: accepts repo_url from Intent (prefill).
- Flow: Candy fails -> popup -> Try Builder -> BuilderActivity pre-filled.
