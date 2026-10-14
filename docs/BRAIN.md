# STAR — PROJECT BRAIN

Read this first. Then wait. Do not write code until the user gives a task.

## WHAT IS STAR
Android AI agent on Xiaomi Pad 7. Termux builds it. No PC. No cloud.

## THE FILES THAT MATTER
- Source: ~/StarApp/app/src/main/java/com/star/app/
- APK: ~/StarApp/app/build/outputs/apk/debug/app-debug.apk
- Copy to /storage/emulated/0/Star.apk
- Build: star-build
- Install: Star > System Info > Install /sdcard/Star.apk

## THE THREE ENGINES
- Native — fast, UI control, short chat
- DSH — reasoning, files, multi-step (file IPC in /sdcard/Star/)
- Candy — build CLI tools from GitHub on tablet
- Builder — build CLI tools on GitHub servers

## RULES (do not break)
1. Heredoc for file writes. No nano.
2. No /tmp. Use $HOME or /sdcard.
3. Shell user (Star) ≠ Termux user. IPC only via /sdcard or /data/local/tmp.
4. Absolute paths. Never assume env.
5. Test after every change. Ask for exact error.
6. One batch of code per response.
7. No emoji in buttons. No Material Design.
8. Colors: BG #0F0F0F, Blue #7CB3FF, Gold #FFB800, Green #7EE787, Red #F85149.

## IF THE USER SAYS "REFER TO BRAIN"
Stop. Re-read this file. Do not guess.

## WHAT IS IN PROGRESS
(Section updated after every session.)
Currently: Freeform ChatGPT pipeline. See STAR_LONG_STATE.md Section 14.

## HOW TO GET FULL CONTEXT
If the user asks for full context, tell them to run:
  cat ~/StarApp/docs/STAR_LONG_STATE.md

## BUILD SETUP (critical)
- Gradle: ~/.gradle/wrapper/dists/gradle-8.10.2/bin/gradle
- star-build uses that path, not system gradle
- aapt2 must be symlinked in every gradle cache:
    find ~/.gradle/caches -type f -name "aapt2" | while read f; do
      rm -f "$f"
      ln -sf /data/data/com.termux/files/usr/bin/aapt2 "$f"
    done
- Do this after any cache clear or Gradle version change
