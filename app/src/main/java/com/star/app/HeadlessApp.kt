package com.star.app

import android.content.Context
import com.headless.android.HeadlessAutomation
import com.headless.android.HeadlessRuntime
import com.headless.android.HeadlessSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Star's bridge to the headless-android-sdk.
 *
 * Runs other Android apps on a hidden virtual display, types into them via the
 * accessibility agent (no keyboard), and reads their screen text back — while the
 * user keeps using the tablet normally.
 */
object HeadlessApp {

    private var runtime: HeadlessRuntime? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    interface Cb {
        fun onOk(text: String)
        fun onErr(msg: String)
    }

    interface ReadyCb {
        fun onReady(ready: Boolean, msg: String)
    }

    /** Init once per process. Cheap; safe to call repeatedly. */
    @JvmStatic
    fun start(ctx: Context) {
        if (runtime == null) {
            runtime = try {
                HeadlessAutomation.start(ctx.applicationContext)
            } catch (t: Throwable) {
                null
            }
        }
    }

    /** Release the runtime and close any open sessions. */
    @JvmStatic
    fun stop() {
        val r = runtime
        runtime = null
        if (r == null) return
        scope.launch {
            try {
                r.openSessions().forEach { s ->
                    try { s.close() } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
            try { r.close() } catch (_: Throwable) {}
        }
    }

    /** Check whether Shizuku is authorized. Requests permission if needed. */
    @JvmStatic
    fun isReady(cb: ReadyCb) {
        val r = runtime
        if (r == null) {
            cb.onReady(false, "HeadlessApp.start() not called")
            return
        }
        scope.launch {
            try {
                if (!r.isAuthorized()) {
                    val ok = r.requestAuthorization()
                    if (!ok) {
                        cb.onReady(false, "Shizuku permission not granted")
                        return@launch
                    }
                }
                cb.onReady(true, "ready")
            } catch (t: Throwable) {
                cb.onReady(false, t.message ?: "unknown")
            }
        }
    }

    /**
     * Ask another app a question on the hidden display.
     *
     * Full pipeline:
     *  1. Create a hidden virtual display session.
     *  2. Launch [packageId] on it.
     *  3. Wait for the app's UI to appear.
     *  4. Find the first editable field, set the text to [question], verify.
     *  5. Press Enter.
     *  6. Poll the UI text until it stabilizes (reply has finished streaming).
     *  7. Close the session.
     *  8. Return the full screen text to the caller.
     *
     * The caller is responsible for extracting the answer from the returned text —
     * it includes the question echoed back plus the reply.
     */
    @JvmStatic
    fun ask(packageId: String, question: String, cb: Cb) {
        val r = runtime
        if (r == null) {
            cb.onErr("HeadlessApp.start() not called")
            return
        }
        if (!r.isAuthorized()) {
            cb.onErr("Shizuku permission not granted")
            return
        }

        scope.launch {
            var session: HeadlessSession? = null
            try {
                // close any sessions the SDK still holds from previous runs
                try {
                    r.openSessions().forEach { os ->
                        android.util.Log.i("StarHeadless", "closing stale session " + os.id)
                        try { os.close() } catch (_: Throwable) {}
                    }
                } catch (_: Throwable) {}

                // 1. session
                android.util.Log.i("StarHeadless", "creating session...")
                session = try { r.createSession() } catch (t: Throwable) {
                    cb.onErr("createSession failed: " + (t.message ?: t.javaClass.simpleName))
                    return@launch
                }
                android.util.Log.i("StarHeadless", "session id=" + session.id
                    + " displayId=" + session.displayId)

                // 2. launch
                android.util.Log.i("StarHeadless", "launching " + packageId)
                try {
                    session.launch(packageId)
                    android.util.Log.i("StarHeadless", "launch call returned OK")
                } catch (t: Throwable) {
                    val sw = java.io.StringWriter()
                    t.printStackTrace(java.io.PrintWriter(sw))
                    android.util.Log.e("StarHeadless", "launch threw: " + sw.toString())
                    cb.onErr("launch failed: " + (t.message ?: t.javaClass.simpleName))
                    try { session.close() } catch (_: Throwable) {}
                    return@launch
                }
                android.util.Log.i("StarHeadless", "post-launch displayId=" + session.displayId
                    + " currentApp=" + session.currentApp())

                // 3. wait for UI
                delay(4000L)

                // 4. Use Star's own accessibility service to drive the app on the hidden display.
                // The SDK's own accessibility agent does not start on HyperOS (app_process
                // is blocked from loading classes out of the app private APK), so we skip it
                // and read/act via Star's already-working AccessibilityService.
                val displayId = session.displayId
                android.util.Log.i("StarHeadless", "using Star a11y on display " + displayId)

                // wait for the app's UI to settle
                var lastTypeResult = ""
                var typed = false
                for (attempt in 0 until 12) {
                    kotlinx.coroutines.delay(1500L)
                    val tree = StarAccessibilityService.dumpDisplay(displayId)
                    android.util.Log.i("StarHeadless", "attempt " + attempt
                        + " tree: " + tree.take(400))
                    val r = StarAccessibilityService.typeOnDisplay(displayId, question)
                    android.util.Log.i("StarHeadless", "type result: " + r)
                    lastTypeResult = r
                    if (r.startsWith("Typed")) { typed = true; break }
                }
                if (!typed) {
                    cb.onErr("could not type into " + packageId + ": " + lastTypeResult)
                    try { session.close() } catch (_: Throwable) {}
                    return@launch
                }

                kotlinx.coroutines.delay(500L)
                val enterResult = StarAccessibilityService.enterOnDisplay(displayId)
                android.util.Log.i("StarHeadless", "enter result: " + enterResult)

                // 5. wait for the reply to stabilize
                var lastText = ""
                var stableCount = 0
                val deadline = System.currentTimeMillis() + 90_000L
                var finalText = ""
                while (System.currentTimeMillis() < deadline) {
                    kotlinx.coroutines.delay(1500L)
                    val t = StarAccessibilityService.dumpDisplay(displayId)
                    if (t == lastText && t.isNotEmpty()) {
                        stableCount++
                        if (stableCount >= 3) { finalText = t; break }
                    } else { stableCount = 0; lastText = t }
                }
                if (finalText.isEmpty()) finalText = lastText

                try { session.close() } catch (_: Throwable) {}

                if (finalText.isBlank() || finalText.startsWith("No windows")
                    || finalText.startsWith("ERROR")) {
                    cb.onErr("empty reply from " + packageId)
                } else {
                    cb.onOk(finalText)
                }
            } catch (t: Throwable) {
                try { session?.close() } catch (_: Throwable) {}
                cb.onErr(t.message ?: t.javaClass.simpleName)
            }
        }
    }
}
