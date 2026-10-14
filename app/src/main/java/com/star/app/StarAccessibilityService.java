package com.star.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.List;

public class StarAccessibilityService extends AccessibilityService {
    private static StarAccessibilityService inst;

    @Override public void onServiceConnected() {
        inst = this;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}
    @Override public boolean onUnbind(Intent i) { inst = null; return super.onUnbind(i); }
    @Override public void onDestroy() {
        inst = null;
        super.onDestroy();
    }

    public static boolean isRunning() { return inst != null; }
    public static StarAccessibilityService getInstance() { return inst; }

    public static String dump() {
        if (inst == null) return "ERROR: Accessibility not enabled.";
        AccessibilityNodeInfo root = inst.getRootInActiveWindow();
        if (root == null) return "No active window";
        StringBuilder sb = new StringBuilder();
        sb.append("SCREEN ELEMENTS:\n");
        walk(root, sb, 0);
        String out = sb.toString();
        if (out.length() > 3500) out = out.substring(0, 3500) + "\n...(trunc)";
        return out;
    }

    private static void walk(AccessibilityNodeInfo node, StringBuilder sb, int depth) {
        if (node == null || depth > 18) return;
        String text = node.getText() != null ? node.getText().toString().trim() : "";
        String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
        boolean clickable = node.isClickable();
        boolean editable = node.isEditable();
        boolean scrollable = node.isScrollable();

        if (!text.isEmpty() || !desc.isEmpty() || clickable || editable || scrollable) {
            Rect r = new Rect();
            node.getBoundsInScreen(r);
            if (r.right > r.left && r.bottom > r.top) {
                sb.append("(").append(r.left).append(",").append(r.top).append(")-(")
                  .append(r.right).append(",").append(r.bottom).append(")");
                if (!text.isEmpty()) sb.append(" text=\"").append(text.replace("\"", "'")).append("\"");
                if (!desc.isEmpty()) sb.append(" desc=\"").append(desc.replace("\"", "'")).append("\"");
                if (clickable) sb.append(" [click]");
                if (editable) sb.append(" [edit]");
                if (scrollable) sb.append(" [scroll]");
                sb.append("\n");
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) walk(node.getChild(i), sb, depth + 1);
    }

    public static boolean tap(int x, int y) {
        if (inst == null) return false;
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.StrokeDescription s = new GestureDescription.StrokeDescription(p, 0, 60);
        return inst.dispatchGesture(new GestureDescription.Builder().addStroke(s).build(), null, null);
    }

    public static boolean swipe(int x1, int y1, int x2, int y2) {
        if (inst == null) return false;
        Path p = new Path();
        p.moveTo(x1, y1); p.lineTo(x2, y2);
        GestureDescription.StrokeDescription s = new GestureDescription.StrokeDescription(p, 0, 250);
        return inst.dispatchGesture(new GestureDescription.Builder().addStroke(s).build(), null, null);
    }

    public static String findAndTap(String query) {
        if (inst == null) return "ERROR: Accessibility not enabled.";
        AccessibilityNodeInfo root = inst.getRootInActiveWindow();
        if (root == null) return "No active window";
        List<AccessibilityNodeInfo> matches = new ArrayList<>();
        findMatches(root, query.toLowerCase(), matches);
        if (matches.isEmpty()) return "Not found: \"" + query + "\"";
        AccessibilityNodeInfo node = matches.get(0);
        AccessibilityNodeInfo clickNode = node;
        while (clickNode != null && !clickNode.isClickable()) clickNode = clickNode.getParent();
        if (clickNode != null && clickNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return "Clicked: " + query;
        }
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        int cx = (r.left + r.right) / 2, cy = (r.top + r.bottom) / 2;
        if (tap(cx, cy)) return "Tapped \"" + query + "\" at " + cx + "," + cy;
        return "Failed to click \"" + query + "\"";
    }

    private static void findMatches(AccessibilityNodeInfo node, String query, List<AccessibilityNodeInfo> out) {
        if (node == null || out.size() > 5) return;
        String text = node.getText() != null ? node.getText().toString().toLowerCase() : "";
        String desc = node.getContentDescription() != null ? node.getContentDescription().toString().toLowerCase() : "";
        if (text.contains(query) || desc.contains(query)) {
            Rect r = new Rect();
            node.getBoundsInScreen(r);
            if (r.right > r.left && r.bottom > r.top) out.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) findMatches(node.getChild(i), query, out);
    }

    public static String typeText(String text) {
        if (inst == null) return "ERROR: Accessibility not enabled.";
        AccessibilityNodeInfo root = inst.getRootInActiveWindow();
        if (root == null) return "No active window";

        // 1. Try focused field
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null || !focused.isEditable()) {
            // 2. Hunt for any editable field on screen
            focused = findEditable(root);
        }
        if (focused == null) return "No text field found on screen";

        // 3. Try to focus it first
        focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        try { Thread.sleep(80); } catch (InterruptedException ignored) {}

        // 4. Set text
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        boolean ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        if (ok) return "Typed: " + text;

        // 5. Fallback: click + shell type
        Rect r = new Rect();
        focused.getBoundsInScreen(r);
        if (r.right > r.left && r.bottom > r.top) {
            int cx = (r.left + r.right) / 2;
            int cy = (r.top + r.bottom) / 2;
            tap(cx, cy);
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            Shell.run("input text '" + text.replace("'", "'\\''") + "'");
            return "Typed via shell: " + text;
        }
        return "Failed to type";
    }

    private static AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && node.isVisibleToUser()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findEditable(node.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    // Returns "SHELL:input keyevent N" for enter/tab/escape (handled by caller via Shell)
    public static String pressKey(String key) {
        if (inst == null) return "ERROR: Accessibility not enabled.";
        String lower = key.toLowerCase();
        switch (lower) {
            case "back": return inst.performGlobalAction(GLOBAL_ACTION_BACK) ? "Pressed back" : "Failed";
            case "home": return inst.performGlobalAction(GLOBAL_ACTION_HOME) ? "Pressed home" : "Failed";
            case "recents": return inst.performGlobalAction(GLOBAL_ACTION_RECENTS) ? "Pressed recents" : "Failed";
            case "notifications": return inst.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ? "Pressed notifications" : "Failed";
            case "enter": case "search": case "go": return "SHELL:input keyevent 66";
            case "tab": return "SHELL:input keyevent 61";
            case "escape": return "SHELL:input keyevent 111";
            case "delete": return "SHELL:input keyevent 67";
            default: return "Unknown key: " + key;
        }
    }

    public static String scroll(String direction) {
        if (inst == null) return "ERROR: Accessibility not enabled.";
        AccessibilityNodeInfo root = inst.getRootInActiveWindow();
        if (root == null) return "No active window";
        AccessibilityNodeInfo s = findScrollable(root);
        if (s == null) return "No scrollable element";
        int action = direction.equalsIgnoreCase("up")
            ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
        return s.performAction(action) ? "Scrolled " + direction : "Failed to scroll";
    }

    private static AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findScrollable(node.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    // ★★★ NEW: Batch multiple UI actions in one shot. Massive speedup. ★★★
    // Format: "find:Search|type:cats|enter|wait:600|find:Search"
    // Actions: find:TEXT | type:TEXT | tap:X,Y | enter | back | home | wait:MS | scroll:down | scroll:up
    public static String runSequence(String seq) {
        if (inst == null) return "ERROR: Accessibility not enabled.";
        StringBuilder log = new StringBuilder();
        String[] steps = seq.split("\\|");
        for (String raw : steps) {
            String s = raw.trim();
            if (s.isEmpty()) continue;
            try {
                if (s.startsWith("find:")) {
                    String q = s.substring(5).trim();
                    log.append(findAndTap(q)).append("; ");
                    Thread.sleep(350);
                } else if (s.startsWith("type:")) {
                    String t = s.substring(5);
                    log.append(typeText(t)).append("; ");
                    Thread.sleep(150);
                } else if (s.startsWith("tap:")) {
                    String[] xy = s.substring(4).split(",");
                    int x = Integer.parseInt(xy[0].trim());
                    int y = Integer.parseInt(xy[1].trim());
                    log.append(tap(x, y) ? "Tapped " + x + "," + y : "Tap failed").append("; ");
                    Thread.sleep(150);
                } else if (s.equals("enter") || s.equals("search")) {
                    Shell.run("input keyevent 66");
                    log.append("Enter; ");
                    Thread.sleep(600);
                } else if (s.equals("back")) {
                    inst.performGlobalAction(GLOBAL_ACTION_BACK);
                    log.append("Back; ");
                    Thread.sleep(250);
                } else if (s.equals("home")) {
                    inst.performGlobalAction(GLOBAL_ACTION_HOME);
                    log.append("Home; ");
                    Thread.sleep(250);
                } else if (s.startsWith("wait:")) {
                    int ms = Integer.parseInt(s.substring(5).trim());
                    Thread.sleep(Math.min(ms, 3000));
                    log.append("Waited ").append(ms).append("ms; ");
                } else if (s.startsWith("scroll:")) {
                    log.append(scroll(s.substring(7).trim())).append("; ");
                    Thread.sleep(200);
                } else {
                    log.append("Unknown: ").append(s).append("; ");
                }
            } catch (Exception e) {
                log.append("Error(").append(s).append("): ").append(e.getMessage()).append("; ");
            }
        }
        return log.toString();
    }

    // ===== display-aware methods (added for headless pipeline) =====

    /** Returns the root node of the first window on the given display.
     *  Display 0 (physical screen) is refused. */
    public static AccessibilityNodeInfo rootOnDisplay(int displayId) {
        if (inst == null) return null;
        if (displayId <= 0) return null;
        try {
            android.util.SparseArray<java.util.List<AccessibilityWindowInfo>> all =
                inst.getWindowsOnAllDisplays();
            if (all != null) {
                java.util.List<AccessibilityWindowInfo> windows = all.get(displayId);
                if (windows != null) {
                    for (AccessibilityWindowInfo w : windows) {
                        AccessibilityNodeInfo r = w.getRoot();
                        if (r != null) return r;
                    }
                }
            }
        } catch (Throwable ignored) {}
        // fallback to getWindows() (works if displayId happens to be the service's own display)
        try {
            java.util.List<AccessibilityWindowInfo> windows = inst.getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo w : windows) {
                    if (w.getDisplayId() == displayId) {
                        AccessibilityNodeInfo r = w.getRoot();
                        if (r != null) return r;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Full list of display IDs the service can see windows for. Useful for diagnostics. */
    public static String listDisplays() {
        if (inst == null) return "ERROR: Accessibility not enabled.";
        StringBuilder sb = new StringBuilder();
        try {
            android.util.SparseArray<java.util.List<AccessibilityWindowInfo>> all =
                inst.getWindowsOnAllDisplays();
            if (all == null) return "no windows map";
            for (int i = 0; i < all.size(); i++) {
                int id = all.keyAt(i);
                java.util.List<AccessibilityWindowInfo> ws = all.valueAt(i);
                sb.append("display ").append(id)
                  .append(" windows=").append(ws == null ? 0 : ws.size())
                  .append("\n");
            }
        } catch (Throwable t) {
            return "ERR: " + t.getMessage();
        }
        return sb.toString();
    }

    /** Dump the full element tree of one display as text. Display 0 is refused. */
    public static String dumpDisplay(int displayId) {
        if (inst == null) return "ERROR: Accessibility not enabled.";
        if (displayId <= 0) return "ERROR: display 0 is the physical screen";
        AccessibilityNodeInfo root = rootOnDisplay(displayId);
        if (root == null) return "No windows on display " + displayId;
        StringBuilder sb = new StringBuilder();
        sb.append("DISPLAY ").append(displayId).append(" ELEMENTS:\n");
        walk(root, sb, 0);
        String out = sb.toString();
        if (out.length() > 5000) out = out.substring(0, 5000) + "\n...(trunc)";
        return out;
    }

    /** Find by text on a specific display and tap it. Display 0 is refused. */
    public static String findAndTapOnDisplay(int displayId, String query) {
        if (inst == null) return "ERROR: Accessibility not enabled.";
        if (displayId <= 0) return "ERROR: display 0 is the physical screen";
        AccessibilityNodeInfo root = rootOnDisplay(displayId);
        if (root == null) return "No windows on display " + displayId;
        List<AccessibilityNodeInfo> matches = new ArrayList<>();
        findMatches(root, query.toLowerCase(), matches);
        if (matches.isEmpty()) return "Not found: \"" + query + "\"";
        AccessibilityNodeInfo node = matches.get(0);
        AccessibilityNodeInfo clickNode = node;
        while (clickNode != null && !clickNode.isClickable()) clickNode = clickNode.getParent();
        if (clickNode != null && clickNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return "Clicked: " + query;
        }
        return "Failed to click \"" + query + "\"";
    }

    /** Type text into the focused editable on the given display.
     *  Uses ACTION_SET_TEXT first, falls back to shell input -d. Display 0 refused. */
    public static String typeOnDisplay(int displayId, String text) {
        if (inst == null) return "ERROR: Accessibility not enabled.";
        if (displayId <= 0) return "ERROR: display 0 is the physical screen";
        AccessibilityNodeInfo root = rootOnDisplay(displayId);
        if (root == null) return "No windows on display " + displayId;

        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null || !focused.isEditable()) {
            focused = findEditable(root);
        }
        if (focused == null) return "No text field on display " + displayId;

        focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        try { Thread.sleep(80); } catch (InterruptedException ignored) {}

        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        boolean ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        if (ok) return "Typed: " + text;

        // Fallback via shell (works on hidden displays too)
        try {
            Shell.run("input -d " + displayId + " text '" + text.replace("'", "'\\''") + "'");
            return "Typed via shell: " + text;
        } catch (Throwable t) {
            return "Failed to type: " + t.getMessage();
        }
    }

    /** Send ENTER to the given display. Uses ACTION_IME_ENTER first, then shell input. */
    public static String enterOnDisplay(int displayId) {
        if (inst == null) return "ERROR: Accessibility not enabled.";
        if (displayId <= 0) return "ERROR: display 0 is the physical screen";
        AccessibilityNodeInfo root = rootOnDisplay(displayId);
        if (root == null) return "No windows on display " + displayId;

        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null) {
            int ACTION_IME_ENTER = 0x01000010;
            try {
                if (focused.performAction(ACTION_IME_ENTER)) return "Enter (ime)";
            } catch (Throwable ignored) {}
        }
        try {
            Shell.run("input -d " + displayId + " keyevent 66");
            return "Enter (shell)";
        } catch (Throwable t) {
            return "Enter failed: " + t.getMessage();
        }
    }

    /** Find the send button by content description or by proximity to the input.
     *  Used for apps like ChatGPT where the input is multiline and Enter only
     *  inserts a newline. */
    public static String tapSendButton() {
        if (inst == null) return "ERROR: Accessibility not enabled.";
        AccessibilityNodeInfo root = inst.getRootInActiveWindow();
        if (root == null) return "No active window";
        // 1. classic content descriptions
        String[] labels = { "Send", "Send message", "Send prompt", "Submit", "Ask", "Go" };
        for (String lb : labels) {
            java.util.List<AccessibilityNodeInfo> hits = new ArrayList<>();
            findMatches(root, lb.toLowerCase(), hits);
            for (AccessibilityNodeInfo n : hits) {
                AccessibilityNodeInfo c = n;
                while (c != null && !c.isClickable()) c = c.getParent();
                if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return "Clicked send (" + lb + ")";
                }
            }
        }
        // 2. hunt for a clickable near the bottom of the window whose desc contains "send"
        java.util.List<AccessibilityNodeInfo> clickables = new ArrayList<>();
        collectClickables(root, clickables);
        AccessibilityNodeInfo best = null;
        int bestBottom = -1;
        for (AccessibilityNodeInfo n : clickables) {
            CharSequence d = n.getContentDescription();
            CharSequence t = n.getText();
            String hay = (d == null ? "" : d.toString()) + " " + (t == null ? "" : t.toString());
            String low = hay.toLowerCase();
            if (low.contains("send") || low.contains("submit")) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                if (r.bottom > bestBottom) { bestBottom = r.bottom; best = n; }
            }
        }
        if (best != null && best.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return "Clicked send (bottom-most)";
        }
        return "Send button not found";
    }

    private static void collectClickables(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out) {
        if (node == null) return;
        if (node.isClickable()) out.add(node);
        for (int i = 0; i < node.getChildCount(); i++) collectClickables(node.getChild(i), out);
    }

    /** Dump the tree of a specific app's window, regardless of which window is
     *  currently focused. Used when we drove another app into a freeform window. */
    public static String dumpPackage(String pkg) {
        if (inst == null) return "ERROR: Accessibility not enabled.";
        if (pkg == null || pkg.isEmpty()) return dump();

        java.util.List<AccessibilityWindowInfo> windows;
        try { windows = inst.getWindows(); } catch (Throwable t) { return "ERR: " + t.getMessage(); }
        if (windows == null) return "no windows";

        android.util.Log.i("StarA11y", "windows count = " + windows.size());
        for (AccessibilityWindowInfo w : windows) {
            AccessibilityNodeInfo r = w.getRoot();
            if (r == null) {
                android.util.Log.i("StarA11y", "  win type=" + w.getType() + " root=null");
                continue;
            }
            CharSequence rp = r.getPackageName();
            android.util.Log.i("StarA11y", "  win type=" + w.getType()
                + " pkg=" + (rp == null ? "?" : rp.toString()));
        }

        for (AccessibilityWindowInfo w : windows) {
            AccessibilityNodeInfo r = w.getRoot();
            if (r == null) continue;
            CharSequence p = r.getPackageName();
            if (p != null && p.toString().equals(pkg)) {
                StringBuilder sb = new StringBuilder();
                sb.append("APP ").append(pkg).append(" ELEMENTS:\n");
                walk(r, sb, 0);
                String out = sb.toString();
                if (out.length() > 5000) out = out.substring(0, 5000) + "\n...(trunc)";
                return out;
            }
        }
        // fallback: active window
        AccessibilityNodeInfo root = inst.getRootInActiveWindow();
        if (root == null) return "no active window";
        CharSequence p = root.getPackageName();
        StringBuilder sb = new StringBuilder();
        sb.append("APP ").append(p == null ? "?" : p.toString()).append(" ELEMENTS:\n");
        walk(root, sb, 0);
        String out = sb.toString();
        if (out.length() > 5000) out = out.substring(0, 5000) + "\n...(trunc)";
        return out;
    }

    /** Is a window belonging to pkg currently visible? */
    public static boolean hasWindowFor(String pkg) {
        if (inst == null || pkg == null) return false;
        try {
            java.util.List<AccessibilityWindowInfo> windows = inst.getWindows();
            if (windows == null) return false;
            for (AccessibilityWindowInfo w : windows) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r == null) continue;
                CharSequence p = r.getPackageName();
                if (p != null && p.toString().equals(pkg)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** Dump text nodes of the given package's window, for diagnostics. */
    public static String dumpTextNodes(String pkg) {
        if (inst == null || pkg == null) return "ERR: no service";
        StringBuilder sb = new StringBuilder();
        try {
            java.util.List<AccessibilityWindowInfo> windows = inst.getWindows();
            if (windows == null) return "ERR: no windows";
            for (AccessibilityWindowInfo w : windows) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r == null) continue;
                CharSequence p = r.getPackageName();
                if (p == null || !p.toString().equals(pkg)) continue;
                walkText(r, sb, 0);
                break;
            }
        } catch (Throwable t) {
            return "ERR: " + t.getMessage();
        }
        if (sb.length() == 0) return "EMPTY";
        if (sb.length() > 4000) return sb.substring(0, 4000) + "\n...trunc";
        return sb.toString();
    }

    private static void walkText(AccessibilityNodeInfo node, StringBuilder sb, int depth) {
        if (node == null || depth > 40) return;
        CharSequence t = node.getText();
        if (t != null) {
            String s = t.toString().trim();
            if (!s.isEmpty()) {
                sb.append(node.getClassName()).append(" :: ").append(s.replace("\n", " | ")).append("\n");
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            walkText(node.getChild(i), sb, depth + 1);
        }
    }

    /** Screen rect of the first window belonging to pkg. Format: "l,t,r,b" or "" */
    public static String boundsOfPackage(String pkg) {
        if (inst == null || pkg == null) return "";
        try {
            java.util.List<AccessibilityWindowInfo> windows = inst.getWindows();
            if (windows == null) return "";
            for (AccessibilityWindowInfo w : windows) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r == null) continue;
                CharSequence p = r.getPackageName();
                if (p == null || !p.toString().equals(pkg)) continue;
                Rect b = new Rect();
                r.getBoundsInScreen(b);
                if (b.right > b.left && b.bottom > b.top) {
                    return b.left + "," + b.top + "," + b.right + "," + b.bottom;
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }
}
