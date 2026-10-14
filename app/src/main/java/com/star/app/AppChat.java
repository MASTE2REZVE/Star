package com.star.app;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/** Ask a question in an external AI app (ChatGPT, Gemini, Claude, ...). */
public class AppChat {

    public static class Target {
        public String name;        // "chatgpt"
        public String packageId;   // "com.openai.chatgpt"
        public String launchActivity; // optional, may be null
    }

    public static Target target(String name) {
        Target t = new Target();
        t.name = name;
        switch (name.toLowerCase()) {
            case "chatgpt":
                t.packageId = "com.openai.chatgpt"; break;
            case "gemini":
                t.packageId = "com.google.android.apps.bard"; break;
            case "claude":
                t.packageId = "com.anthropic.claude"; break;
            case "perplexity":
                t.packageId = "ai.perplexity.app.android"; break;
            case "copilot":
                t.packageId = "com.microsoft.copilot"; break;
            case "grok":
                t.packageId = "ai.x.grok"; break;
            default:
                t.packageId = null;
        }
        return t;
    }

    public interface Cb {
        void onReply(String text);
        void onError(String msg);
    }

    private static final Handler h = new Handler(Looper.getMainLooper());
    private static volatile String prevAppGlobal = "";

    /** Full pipeline: open app, type, send, wait, copy, read clipboard. */
    public static void ask(final android.content.Context ctx, Target t,
                           final String question, final Cb cb) {
        if (t.packageId == null) { cb.onError("unknown app: " + t.name); return; }
        final StarAccessibilityService svc = StarAccessibilityService.getInstance();
        if (svc == null) { cb.onError("accessibility service not running"); return; }

        // Save the app the user was looking at, so we can return to it
        final String prevApp = currentForegroundApp();
        prevAppGlobal = prevApp;

        // 0. Verify the target app is actually installed. Otherwise monkey fails
        //    silently and we end up typing into whatever is on top of the stack.
        Shell.Result installed = Shell.run(
            "pm list packages " + t.packageId + " 2>/dev/null");
        if (installed.output == null
            || !installed.output.contains(t.packageId)) {
            cb.onError(t.name + " is not installed (" + t.packageId + ")");
            return;
        }

        // 1. Launch
        Shell.run("monkey -p " + t.packageId +
                  " -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1");

        // 2. Wait for the app to appear, then verify it is on top
        h.postDelayed(() -> {
            String now = currentForegroundApp();
            if (now == null || !now.equals(t.packageId)) {
                // try one more time to launch, and give up if it still fails
                Shell.run("monkey -p " + t.packageId +
                          " -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1");
                h.postDelayed(() -> {
                    String now2 = currentForegroundApp();
                    if (now2 == null || !now2.equals(t.packageId)) {
                        cb.onError("could not bring " + t.name + " to front (now: "
                            + (now2 == null || now2.isEmpty() ? "unknown" : now2) + ")");
                        return;
                    }
                    continueTyping(ctx, t, question, cb, svc);
                }, 2500L);
                return;
            }
            continueTyping(ctx, t, question, cb, svc);
        }, 2500L);
    }

    private static void continueTyping(final android.content.Context ctx, final Target t,
                                       final String question, final Cb cb,
                                       final StarAccessibilityService svc) {
        h.postDelayed(() -> {
            clearClipboard(ctx);
            boolean focused = findAndFocusInput(svc);
            if (!focused) { cb.onError("could not find input field"); return; }

            h.postDelayed(() -> {
                String typed = StarAccessibilityService.typeText(question);
                if (typed == null || typed.toLowerCase().contains("error")
                    || typed.toLowerCase().contains("fail")) {
                    cb.onError("typing failed: " + typed); return;
                }

                h.postDelayed(() -> {
                    boolean sent = tapSendButton(svc);
                    if (!sent) { cb.onError("could not find send button"); return; }
                    // 3. Wait for the answer to finish streaming
                    waitForAnswerAndCopy(ctx, cb);
                }, 800L);
            }, 400L);
        }, 2500L);
    }

    // ---------- helpers ----------

    private static boolean findAndFocusInput(StarAccessibilityService svc) {
        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> edits = new ArrayList<>();
        collectEditable(root, edits);
        if (edits.isEmpty()) return false;
        // pick the deepest / bottom-most one
        AccessibilityNodeInfo best = edits.get(0);
        int bestY = -1;
        for (AccessibilityNodeInfo n : edits) {
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (r.top > bestY) { bestY = r.top; best = n; }
        }
        best.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        best.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        return true;
    }

    private static boolean tapSendButton(StarAccessibilityService svc) {
        // Strategy 1: IME enter on the focused editable (API 30+)
        try {
            AccessibilityNodeInfo root = svc.getRootInActiveWindow();
            if (root != null) {
                AccessibilityNodeInfo focused = root.findFocus(
                    AccessibilityNodeInfo.FOCUS_INPUT);
                if (focused != null) {
                    // API 30+ has ACTION_IME_ENTER. It is the correct way to
                    // tell the IME to press enter. Constant is 0x01000010.
                    int ACTION_IME_ENTER = 0x01000010;
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        if (focused.performAction(ACTION_IME_ENTER)) {
                            return true;
                        }
                    }
                    // strategy 2: click an ancestor that is clickable (some apps
                    // put the send action on the parent container)
                    AccessibilityNodeInfo p = focused.getParent();
                    while (p != null) {
                        if (p.isClickable()) {
                            if (p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                // clicking might just refocus; also fall through to enter
                                break;
                            }
                        }
                        p = p.getParent();
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Strategy 3: find send button by label (send, submit, ask)
        try {
            AccessibilityNodeInfo root = svc.getRootInActiveWindow();
            if (root != null) {
                List<AccessibilityNodeInfo> clicks = new ArrayList<>();
                collectClickable(root, clicks);
                AccessibilityNodeInfo best = null;
                int bestY = -1;
                for (AccessibilityNodeInfo n : clicks) {
                    String label = label(n);
                    if (label == null) continue;
                    String l = label.toLowerCase();
                    if (l.equals("send") || l.contains("send message")
                        || l.contains("send prompt") || l.equals("ask")
                        || l.contains("submit")) {
                        Rect r = new Rect();
                        n.getBoundsInScreen(r);
                        if (r.top > bestY) { bestY = r.top; best = n; }
                    }
                }
                if (best != null) {
                    if (best.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                        return true;
                }
            }
        } catch (Throwable ignored) {}

        // Strategy 4: raw key event ENTER via shell. This is the fallback that
        // works even when the send button has no accessible label.
        try {
            Shell.run("input keyevent 66");
            return true;
        } catch (Throwable ignored) {}

        return false;
    }

    /** Get the package name of the currently foreground app.
     *  Uses the accessibility service — does not need shell or dumpsys. */
    private static String currentForegroundApp() {
        try {
            StarAccessibilityService svc = StarAccessibilityService.getInstance();
            if (svc == null) return "";
            AccessibilityNodeInfo root = svc.getRootInActiveWindow();
            if (root == null) return "";
            CharSequence pkg = root.getPackageName();
            return pkg == null ? "" : pkg.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    /** Bring the previous app back to front. */
    private static void returnToApp(String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        try {
            Shell.run("monkey -p " + pkg +
                      " -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1");
        } catch (Throwable ignored) {}
    }

    private static void waitForAnswerAndCopy(final android.content.Context ctx, final Cb cb) {
        // poll: wait until the screen stops changing for 1.5s
        final long start = System.currentTimeMillis();
        final String[] lastText = { "" };
        final long[] lastChange = { System.currentTimeMillis() };
        final Runnable[] poll = new Runnable[1];

        poll[0] = () -> {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > 60_000L) {
                tryReadClipboard(ctx, cb, "timeout");
                return;
            }
            StarAccessibilityService svc = StarAccessibilityService.getInstance();
            if (svc == null) { cb.onError("svc lost"); return; }
            AccessibilityNodeInfo root = svc.getRootInActiveWindow();
            if (root != null) {
                String snap = snapshot(root);
                if (!snap.equals(lastText[0])) {
                    lastText[0] = snap;
                    lastChange[0] = System.currentTimeMillis();
                }
            }
            long stable = System.currentTimeMillis() - lastChange[0];
            if (stable > 2500L && elapsed > 4000L) {
                // quiet — try to copy
                boolean tappedCopy = tapCopyButton(svc);
                if (tappedCopy) {
                    h.postDelayed(() -> tryReadClipboard(ctx, cb, "copy"), 600L);
                } else {
                    // no copy button found. fall back to reading the tree.
                    cb.onError("no copy button; fallback needed");
                }
                return;
            }
            h.postDelayed(poll[0], 800L);
        };
        h.postDelayed(poll[0], 1500L);
    }

    private static void tryReadClipboard(android.content.Context ctx, Cb cb, String why) {
        // Android 12+ requires focus. Star must be foreground.
        // FloatingService brings itself to the front briefly.
        h.postDelayed(() -> {
            String t = ClipboardReader.read(ctx);
            // return to the app the user was using
            returnToApp(prevAppGlobal);
            if (t == null || t.trim().isEmpty()) {
                cb.onError("clipboard empty after " + why);
            } else {
                cb.onReply(t.trim());
            }
        }, 400L);
    }

    private static void clearClipboard(android.content.Context ctx) {
        try {
            android.content.ClipboardManager cm =
                (android.content.ClipboardManager) ctx.getSystemService(
                    android.content.Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(
                android.content.ClipData.newPlainText("star", ""));
        } catch (Exception ignored) {}
    }

    private static boolean tapCopyButton(StarAccessibilityService svc) {
        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> clicks = new ArrayList<>();
        collectClickable(root, clicks);
        AccessibilityNodeInfo best = null;
        int bestY = -1;
        for (AccessibilityNodeInfo n : clicks) {
            String label = label(n);
            if (label == null) continue;
            String l = label.toLowerCase();
            if (l.equals("copy") || l.contains("copy text") || l.equals("copy response")) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                // we want the bottom-most copy (last message)
                if (r.top > bestY) { bestY = r.top; best = n; }
            }
        }
        if (best == null) return false;
        return best.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    // ---------- tree walkers ----------

    private static void collectEditable(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out) {
        if (n == null) return;
        if (n.isEditable()) out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) {
            collectEditable(n.getChild(i), out);
        }
    }

    private static void collectClickable(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out) {
        if (n == null) return;
        if (n.isClickable()) out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) {
            collectClickable(n.getChild(i), out);
        }
    }

    private static String label(AccessibilityNodeInfo n) {
        CharSequence t = n.getText();
        if (t != null && t.length() > 0) return t.toString();
        CharSequence d = n.getContentDescription();
        if (d != null && d.length() > 0) return d.toString();
        return null;
    }

    private static String snapshot(AccessibilityNodeInfo n) {
        StringBuilder sb = new StringBuilder();
        walk(n, sb);
        return sb.toString();
    }

    private static void walk(AccessibilityNodeInfo n, StringBuilder sb) {
        if (n == null) return;
        CharSequence t = n.getText();
        if (t != null && t.length() > 0) sb.append(t).append('\n');
        for (int i = 0; i < n.getChildCount(); i++) walk(n.getChild(i), sb);
    }
}
