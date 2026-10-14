package com.star.app

import android.content.Context
import kotlin.concurrent.thread

/**
 * Ask another Android app on the physical screen, in a small freeform window.
 * Accessibility works on display 0, so this pipeline succeeds where the hidden
 * virtual display could not (HyperOS blocks accessibility on secondary displays).
 *
 * Flow:
 *  1. force-stop the target so no existing task hijacks the launch
 *  2. launch in freeform windowing mode
 *  3. resize the task to a small window in the top-right corner
 *  4. focus the input field, type the question via accessibility SET_TEXT
 *  5. press Enter
 *  6. poll the tree until the reply stops changing
 *  7. force-stop the app to close it
 *  8. return the full screen text
 *
 * The user sees a small window for 8-15 seconds. They do not leave the current app.
 */
object FreeformApp {

    interface Cb {
        fun onOk(text: String)
        fun onErr(msg: String)
    }

    @JvmStatic
    fun ask(ctx: Context, pkg: String, question: String, cb: Cb) {
        thread(name = "FreeformApp") {
            try {
                askBlocking(pkg, question, cb)
            } catch (t: Throwable) {
                cb.onErr(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    private fun askBlocking(pkg: String, question: String, cb: Cb) {
        // Suppress the on-screen keyboard while we work.
        // 0 = hide the soft keyboard when a hardware keyboard is connected.
        // We flip the global setting temporarily, then restore it.
        var prevIme = "1"
        try {
            val g = Shell.run("settings get secure show_ime_with_hard_keyboard")
            prevIme = g.output?.trim() ?: "1"
            Shell.run("settings put secure show_ime_with_hard_keyboard 0")
        } catch (_: Throwable) {}

        try {
            askBlockingInner(pkg, question, cb)
        } finally {
            try { Shell.run("settings put secure show_ime_with_hard_keyboard $prevIme") } catch (_: Throwable) {}
        }
    }

    private fun askBlockingInner(pkg: String, question: String, cb: Cb) {
        // 1. clean slate
        try { Shell.run("am force-stop $pkg") } catch (_: Throwable) {}
        Thread.sleep(500)

        // 2. find main activity
        val component = resolveMainComponent(pkg)
            ?: run { cb.onErr("could not resolve launcher activity for $pkg"); return }

        // 3. launch in freeform
        try {
            Shell.run("am start --windowingMode 5 -n $component")
        } catch (t: Throwable) {
            cb.onErr("launch failed: ${t.message}")
            return
        }
        Thread.sleep(3500)

        // 4. resize the task to a small window in the top-right corner.
        //    Screen is 3200x2136 on the Pad 7. We use a 900x1400 window at
        //    x=2250, y=40, which is the far right edge.
        try {
            val taskId = findTaskId(pkg)
            android.util.Log.i("StarFreeform", "task id = " + taskId)
            if (taskId > 0) {
                val r = Shell.run("am task resize $taskId 2250 40 3150 1440")
                android.util.Log.i("StarFreeform", "resize: " + (r.output ?: ""))
                Thread.sleep(1200)
            }
        } catch (t: Throwable) {
            android.util.Log.i("StarFreeform", "resize failed: " + t.message)
        }

        // 5. find and type into the input field
        var lastTypeResult = ""
        var typed = false
        for (attempt in 0 until 10) {
            val r = StarAccessibilityService.typeText(question)
            lastTypeResult = r
            android.util.Log.i("StarFreeform", "type attempt $attempt: $r")
            if (r.startsWith("Typed")) { typed = true; break }
            // try clicking the input field first, then typing
            try { StarAccessibilityService.findAndTap("Message") } catch (_: Throwable) {}
            Thread.sleep(1200)
        }
        if (!typed) {
            // last resort: try tapping near the bottom of the window
            try { StarAccessibilityService.tap(2600, 1350) } catch (_: Throwable) {}
            Thread.sleep(400)
            val r2 = StarAccessibilityService.typeText(question)
            lastTypeResult = r2
            if (r2.startsWith("Typed")) typed = true
        }
        if (!typed) {
            try { Shell.run("am force-stop $pkg") } catch (_: Throwable) {}
            cb.onErr("could not type into $pkg: $lastTypeResult")
            return
        }

        // 6. send. ChatGPT uses a multiline field, so ENTER inserts a newline.
        //    We must find and tap the send button instead.
        Thread.sleep(900)
        var sent = false
        val sendLabels = listOf("Send", "Send message", "Send prompt", "send", "Submit")
        for (label in sendLabels) {
            try {
                val r = StarAccessibilityService.findAndTap(label)
                android.util.Log.i("StarFreeform", "send by label '" + label + "': " + r)
                if (r.startsWith("Clicked") || r.startsWith("Tapped")) { sent = true; break }
            } catch (_: Throwable) {}
        }
        if (!sent) {
            // try content descriptions on the paper-plane icon
            try {
                val r = StarAccessibilityService.tapSendButton()
                android.util.Log.i("StarFreeform", "send by tapSendButton: " + r)
                if (r.startsWith("Clicked") || r.startsWith("Tapped")) sent = true
            } catch (_: Throwable) {}
        }
        if (!sent) {
            // last resort: shell keyevent 66 as before
            try { Shell.run("input keyevent 66") } catch (_: Throwable) {}
            android.util.Log.i("StarFreeform", "fell back to keyevent 66")
        }

        // 7. Wait for the reply, reading pixels. ChatGPT's UI is a canvas, so the
        //    accessibility tree inside its window is empty. We crop the window,
        //    hash the crop (no animation lives there), and when it stays the same
        //    for a few polls we OCR it.
        Thread.sleep(3000)
        val bounds = StarAccessibilityService.boundsOfPackage(pkg)
        android.util.Log.i("StarFreeform", "window bounds = $bounds")
        val parts = bounds.split(",")
        val crop: String
        if (parts.size == 4) {
            val l = parts[0].toIntOrNull() ?: 2250
            val t = parts[1].toIntOrNull() ?: 40
            val r = parts[2].toIntOrNull() ?: 3150
            val b = parts[3].toIntOrNull() ?: 1440
            val w = (r - l).coerceAtLeast(200)
            val h = (b - t).coerceAtLeast(200)
            crop = "crop=" + w + ":" + h + ":" + l + ":" + t
        } else {
            crop = "crop=900:1400:2250:40"
        }
        android.util.Log.i("StarFreeform", "crop filter = $crop")

        var lastHash = ""
        var stable = 0
        var shotPath = "/sdcard/star_shot_crop.png"
        val deadline = System.currentTimeMillis() + 120_000L
        while (System.currentTimeMillis() < deadline) {
            Shell.run("screencap -p /sdcard/star_full.png")
            val cropBody = bridgeRun("ffmpeg", listOf("-y", "-i", "/sdcard/star_full.png", "-vf", crop, shotPath))
            android.util.Log.i("StarFreeform", "crop bridge=" + cropBody.take(160))
            val md5r = Shell.run("md5sum " + shotPath)
            val h = md5r.output?.trim() ?: ""
            val isRealHash = h.length >= 32 && h.substring(0, 32).all { c -> c in "0123456789abcdef" }
            android.util.Log.i("StarFreeform", "crop hash=" + h.take(40) + " stable=" + stable + " real=" + isRealHash)
            if (isRealHash && h == lastHash) {
                stable++
                if (stable >= 3) break
            } else {
                stable = 0
                lastHash = h
            }
            Thread.sleep(1800)
        }

        var finalText = ocrFile(shotPath)
        android.util.Log.i("StarFreeform", "crop OCR length=" + finalText.length)
        if (finalText.length < 20) {
            finalText = ocrFile("/sdcard/star_full.png")
            android.util.Log.i("StarFreeform", "full OCR length=" + finalText.length)
        }

        // 8. close the app
        try { Shell.run("am force-stop $pkg") } catch (_: Throwable) {}

        if (finalText.isBlank() || finalText.startsWith("ERROR")
            || finalText.startsWith("No active window")) {
            cb.onErr("empty reply from $pkg")
        } else {
            cb.onOk(finalText)
        }
    }

    /** Send a request to the pkg-bridge via Shell (UID 2000). Bridge runs as Termux. */
    private fun bridgeRun(cmd: String, args: List<String>): String {
        return try {
            Shell.run("rm -f /sdcard/Star/candy/run-result.json")
            val req = org.json.JSONObject().apply {
                put("cmd", cmd)
                put("args", org.json.JSONArray(args))
            }.toString()
            val b64 = android.util.Base64.encodeToString(
                req.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            Shell.run("sh -c 'echo " + b64 + " | base64 -d > /sdcard/Star/candy/run-request.json'")
            android.util.Log.i("StarFreeform", "bridge req cmd=" + cmd + " args=" + args)
            val deadline = System.currentTimeMillis() + 60_000L
            while (System.currentTimeMillis() < deadline) {
                val r = Shell.run("cat /sdcard/Star/candy/run-result.json 2>/dev/null")
                val out = r.output?.trim() ?: ""
                if (out.startsWith("{")) {
                    Shell.run("rm -f /sdcard/Star/candy/run-result.json")
                    return out
                }
                Thread.sleep(500)
            }
            android.util.Log.i("StarFreeform", "bridge timeout cmd=" + cmd)
            """{"status":"failed","error":"timeout"}"""
        } catch (t: Throwable) {
            android.util.Log.i("StarFreeform", "bridge ex: " + t.message)
            "{\"status\":\"failed\",\"error\":\"" + (t.message ?: "") + "\"}"
        }
    }

    private fun resolveMainComponent(pkg: String): String? {
        return try {
            val r = Shell.run("cmd package resolve-activity --brief $pkg")
            val last = r.output?.lineSequence()?.map { it.trim() }?.lastOrNull { it.isNotEmpty() }
            if (last != null && last.contains("/")) last else null
        } catch (_: Throwable) { null }
    }

    private fun findTaskId(pkg: String): Int {
        return try {
            val r = Shell.run("dumpsys activity activities 2>/dev/null | grep -m1 \"Task{.*$pkg\"")
            val out = r.output ?: return -1
            // Task{...  #NUM type=standard ...}
            val i = out.indexOf('#')
            if (i < 0) return -1
            val j = out.indexOf(' ', i)
            val s = if (j < 0) out.substring(i + 1) else out.substring(i + 1, j)
            s.trim().toIntOrNull() ?: -1
        } catch (_: Throwable) { -1 }
    }

    /** Run tesseract on a PNG via the Termux pkg-bridge. Returns plain text. */
    private fun ocrFile(path: String): String {
        return try {
            val body = bridgeRun("tesseract", listOf(path, "stdout"))
            android.util.Log.i("StarFreeform", "ocr body: " + body.take(300))
            parseStdout(body)
        } catch (t: Throwable) {
            android.util.Log.i("StarFreeform", "ocr ex: " + t.message)
            ""
        }
    }

    private fun parseStdout(body: String): String {
        val key = "\"stdout\""
        val ki = body.indexOf(key)
        if (ki < 0) return ""
        val ci = body.indexOf(':', ki + key.length)
        if (ci < 0) return ""
        var i = ci + 1
        while (i < body.length && body[i].isWhitespace()) i++
        if (i >= body.length || body[i] != '"') return ""
        i++
        val sb = StringBuilder()
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) {
                when (body[i + 1]) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    '/' -> sb.append('/')
                    else -> sb.append(body[i + 1])
                }
                i += 2
            } else if (c == '"') {
                return sb.toString().trim()
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString().trim()
    }
}
