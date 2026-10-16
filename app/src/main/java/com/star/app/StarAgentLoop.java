package com.star.app;

import android.content.Context;
import com.star.app.candy.Candy;
import org.json.*;
import java.util.ArrayList;
import java.util.List;

/**
 * StarAgentLoop — a simplified Java port of DSH's ReactLoopAgent semantics.
 *
 * Key rules (borrowed from DSH):
 *  1. One tool call per step. No parallel tool chaos.
 *  2. Every result is appended to the session log BEFORE the next step.
 *  3. Validation runs before execution (guard phase).
 *  4. Turn ends only when the model produces a text reply (no tool calls).
 *  5. Hard step cap per turn (prevents infinite loops).
 *  6. Each turn knows when it's done.
 */
public class StarAgentLoop {

    public interface Listener {
        void onThinking();
        void onToolCall(String toolName, String argsJson);
        void onToolResult(String toolName, String result);
        void onReply(String reply);
        void onError(String error);
        void onTurnComplete();
    }

    private final Context ctx;
    private final Prefs prefs;
    private final JSONArray sessionLog;   // the whole turn log
    private final Listener listener;
    private final int maxStepsPerTurn;
    private int currentStep;
    private boolean cancelled;

    public StarAgentLoop(Context ctx, Prefs prefs, JSONArray sessionLog, Listener listener) {
        this(ctx, prefs, sessionLog, listener, 8);
    }

    public StarAgentLoop(Context ctx, Prefs prefs, JSONArray sessionLog,
                         Listener listener, int maxSteps) {
        this.ctx = ctx;
        this.prefs = prefs;
        this.sessionLog = sessionLog;
        this.listener = listener;
        this.maxStepsPerTurn = maxSteps;
        this.currentStep = 0;
        this.cancelled = false;
    }

    public void cancel() { this.cancelled = true; }

    private static String packageForApp(String name) {
        if (name == null) return null;
        switch (name.toLowerCase()) {
            case "chatgpt":     return "com.openai.chatgpt";
            case "gemini":      return "com.google.android.apps.bard";
            case "claude":      return "com.anthropic.claude";
            case "grok":        return "ai.x.grok";
            case "perplexity":  return "ai.perplexity.app.android";
            default:            return null;
        }
    }

    /** Begin a new turn. Appends the user prompt and steps forward. */
    public void runTurn(String userMessage) {
        currentStep = 0;
        cancelled = false;

        try {
            JSONObject m = new JSONObject();
            m.put("role", "user");
            m.put("content", userMessage);
            sessionLog.put(m);
        } catch (Exception e) {
            listener.onError("Log append failed: " + e.getMessage());
            return;
        }

        // Direct skill invocation shortcut
        SkillRunner.DirectCall dc = SkillRunner.parseDirect(userMessage, prefs);
        if (dc != null) {
            listener.onThinking();
            final Candy skill = dc.skill;
            final String sArgs = dc.args;
            new Thread(() -> {
                SkillRunner.Result r = SkillRunner.run(skill, sArgs);
                String reply = (r.output == null || r.output.trim().isEmpty())
                    ? "(no output)"
                    : r.output;
                if (reply.length() > 4000) reply = reply.substring(0, 4000) + "\n...(truncated)";
                reply = "skill: " + skill.name + "  (" + r.ms + "ms)\n\n" + reply;
                try {
                    JSONObject a = new JSONObject();
                    a.put("role", "assistant");
                    a.put("content", reply);
                    sessionLog.put(a);
                } catch (Exception ignored) {}
                listener.onReply(reply);
                listener.onTurnComplete();
            }).start();
            return;
        }

        // Multi-model shortcut (first turn only)
        String multiMode = prefs.getMultiMode();
        java.util.List<String> multiModels = prefs.getMultiModels();
        if (!"off".equals(multiMode) && multiModels.size() >= 2
            && currentStep == 0) {
            runMultiTurn(userMessage, multiMode, multiModels, prefs.getMultiJudge());
            return;
        }

        // Android constants the model keeps getting wrong
        try {
            JSONObject hints = new JSONObject();
            hints.put("role", "system");
            hints.put("content",
                "ANDROID FACTS — do not guess:\n" +
                "dumpsys battery status codes:\n" +
                "  1 = UNKNOWN\n" +
                "  2 = CHARGING\n" +
                "  3 = DISCHARGING\n" +
                "  4 = NOT_CHARGING\n" +
                "  5 = FULL\n" +
                "So status 3 means the device is DISCHARGING, NOT charging.\n" +
                "level/scale: percent = level*100/scale. 'Capacity level: -1' is normal.\n" +
                "Android versions: 13=Tiramisu, 14=UpsideDownCake, 15=VanillaIceCream.\n" +
                "When you report a value, quote the raw line and say what it means.");
            sessionLog.put(hints);
        } catch (Exception ignored) {}

        // Inject skills manifest as system message if any are installed
        try {
            String manifest = SkillRunner.systemPrompt(prefs);
            if (manifest != null && !manifest.isEmpty()) {
                JSONObject sys = new JSONObject();
                sys.put("role", "system");
                sys.put("content", manifest);
                sessionLog.put(sys);
            }
        } catch (Exception ignored) {}

        step();
    }

    /** A single step: ask the model, then either dispatch a tool or close the turn. */
    private void step() {
        if (cancelled) return;
        if (currentStep >= maxStepsPerTurn) {
            listener.onError("Step limit reached (" + maxStepsPerTurn + ")");
            listener.onTurnComplete();
            return;
        }
        currentStep++;
        listener.onThinking();

        // Smart routing
        String lastUser = "";
        try {
            for (int i = sessionLog.length() - 1; i >= 0; i--) {
                JSONObject m = sessionLog.getJSONObject(i);
                if ("user".equals(m.optString("role"))) {
                    Object c = m.opt("content");
                    lastUser = (c instanceof String) ? (String) c : String.valueOf(c);
                    break;
                }
            }
        } catch (Exception ignored) {}

        boolean dshAvailable = DshClient.isAvailable();
        String engine = EngineRouter.pick(lastUser, dshAvailable);
        if ("native".equals(prefs.getEngine())) engine = "native";

        if ("dsh".equals(engine)) {
            runViaDsh();
            return;
        }

        AIClient.send(ctx, prefs, sessionLog, new AIClient.Callback() {
            @Override public void onReply(String reply) {
                if (cancelled) return;
                // Filter bad replies
                if (reply == null || reply.trim().isEmpty()
                    || reply.trim().equals("null")
                    || reply.trim().equals("{}")) {
                    listener.onError("Model returned empty reply — try a stronger model " +
                        "(tap the model pill → Tools filter → DeepSeek V3.1)");
                    listener.onTurnComplete();
                    return;
                }
                try {
                    JSONObject a = new JSONObject();
                    a.put("role", "assistant");
                    a.put("content", reply);
                    sessionLog.put(a);
                } catch (Exception ignored) {}
                listener.onReply(reply);
                listener.onTurnComplete();
            }

            @Override public void onReplyStats(String reply, int inTok, int outTok, long ms) {
                onReply(reply);
            }

            @Override public void onToolCall(String toolName, String argsJson) {
                if (cancelled) return;

                // Validate first (DSH "guard" phase)
                String validationError = validateToolCall(toolName, argsJson);
                if (validationError != null) {
                    listener.onError("Tool validation failed: " + validationError);
                    // Append error as tool result, continue
                    appendToolResult(toolName, argsJson,
                        "VALIDATION_ERROR: " + validationError);
                    listener.onToolResult(toolName, "VALIDATION_ERROR: " + validationError);
                    step();
                    return;
                }

                // Log the assistant's tool_call
                appendAssistantToolCall(toolName, argsJson);
                listener.onToolCall(toolName, argsJson);

                // Execute (DSH "execute" phase)
                String result = executeTool(toolName, argsJson);
                String sanitized = AIClient.sanitize(result);

                // Append result BEFORE next step
                appendToolResult(toolName, argsJson, sanitized);
                listener.onToolResult(toolName, sanitized);

                // Next step
                step();
            }

            @Override public void onError(String err) {
                listener.onError(err);
                listener.onTurnComplete();
            }
        });
    }

    /** Guard phase — basic sanity before running a tool. */
    private String validateToolCall(String name, String argsJson) {
        if (name == null || name.isEmpty()) return "empty tool name";
        try {
            JSONObject a = new JSONObject(argsJson);
            switch (name) {
                case "run_shell":
                    if (!a.has("command")) return "missing command";
                    String cmd = a.optString("command", "");
                    if (cmd.length() > 2000) return "command too long";
                    // Block dangerous ops unless explicitly allowed
                    String lower = cmd.toLowerCase();
                    if (lower.contains("rm -rf /") ||
                        lower.contains("mkfs.") ||
                        lower.contains("dd if=/dev/zero")) {
                        return "dangerous command blocked";
                    }
                    return null;
                case "ui_find":
                    if (!a.has("text")) return "missing text";
                    return null;
                case "ui_type":
                    if (!a.has("text")) return "missing text";
                    return null;
                case "ui_seq":
                    if (!a.has("seq")) return "missing seq";
                    return null;
                case "ui_tap":
                    if (!a.has("x") || !a.has("y")) return "missing coords";
                    return null;
                case "ui_press":
                    if (!a.has("key")) return "missing key";
                    return null;
                case "ui_scroll":
                    if (!a.has("direction")) return "missing direction";
                    return null;
                case "update_self":
                    return null;
                case "think_via":
                    if (!a.has("task")) return "missing task";
                    return null;
                case "think_via_app":
                    if (!a.has("app")) return "missing app";
                    if (!a.has("question")) return "missing question";
                    return null;
                case "run_skill":
                    if (!a.has("name")) return "missing skill name";
                    String sname = a.optString("name", "");
                    if (sname.isEmpty()) return "empty skill name";
                    if (SkillRunner.findByName(sname) == null) return "no such skill: " + sname;
                    return null;
                // === CONCAT VIDEO TOOLS (K-B6) ===
                case "video_project_create":
                    if (!a.has("name")) return "missing name";
                    return null;
                case "video_media_import":
                    if (!a.has("project")) return "missing project";
                    if (!a.has("filePath")) return "missing filePath";
                    return null;
                case "video_add_clip":
                    if (!a.has("project")) return "missing project";
                    if (!a.has("mediaId")) return "missing mediaId";
                    if (!a.has("startSec")) return "missing startSec";
                    return null;
                case "video_add_transition":
                    if (!a.has("project")) return "missing project";
                    if (!a.has("kind")) return "missing kind";
                    if (!a.has("durationSec")) return "missing durationSec";
                    return null;
                case "video_add_text":
                    if (!a.has("project")) return "missing project";
                    if (!a.has("text")) return "missing text";
                    if (!a.has("startSec")) return "missing startSec";
                    if (!a.has("endSec")) return "missing endSec";
                    return null;
                case "video_apply_effect":
                    if (!a.has("project")) return "missing project";
                    if (!a.has("effectId")) return "missing effectId";
                    return null;
                case "video_export":
                    if (!a.has("project")) return "missing project";
                    if (!a.has("outputPath")) return "missing outputPath";
                    return null;
                default:
                    return "unknown tool: " + name;
            }
        } catch (Exception e) {
            return "invalid args JSON";
        }
    }

    /** Execute phase — runs the tool and returns its output. */
    private String executeTool(String name, String argsJson) {
        try {
            JSONObject a = new JSONObject(argsJson);
            switch (name) {
                case "run_shell":
                    return Shell.run(a.getString("command")).output;
                case "ui_seq":
                    return StarAccessibilityService.runSequence(a.getString("seq"));
                case "ui_find":
                    return StarAccessibilityService.findAndTap(a.getString("text"));
                case "ui_type":
                    return StarAccessibilityService.typeText(a.getString("text"));
                case "ui_press": {
                    String kres = StarAccessibilityService.pressKey(a.getString("key"));
                    if (kres.startsWith("SHELL:")) {
                        Shell.run(kres.substring(6));
                        return "Pressed " + a.getString("key") + " via shell";
                    }
                    return kres;
                }
                case "ui_dump":
                    return StarAccessibilityService.dump();
                case "ui_scroll":
                    return StarAccessibilityService.scroll(a.getString("direction"));
                case "ui_tap":
                    return StarAccessibilityService.tap(a.getInt("x"), a.getInt("y"))
                        ? "Tapped " + a.getInt("x") + "," + a.getInt("y")
                        : "Tap failed";
                case "ui_swipe":
                    return StarAccessibilityService.swipe(
                        a.getInt("x1"), a.getInt("y1"),
                        a.getInt("x2"), a.getInt("y2")) ? "Swiped" : "Swipe failed";
                case "update_self":
                    return "Self-update triggered";
                case "think_via_app": {
                    final java.util.concurrent.CountDownLatch latch =
                        new java.util.concurrent.CountDownLatch(1);
                    final String[] out = { "" };
                    // Some models double-wrap: {"app":{"app":"gemini"}}
                    String app;
                    Object rawApp = a.opt("app");
                    if (rawApp instanceof org.json.JSONObject) {
                        app = ((org.json.JSONObject) rawApp).optString("app",
                              ((org.json.JSONObject) rawApp).optString("name",
                              ((org.json.JSONObject) rawApp).optString("value", "")));
                    } else {
                        app = a.optString("app", "");
                    }
                    if (app == null || app.isEmpty()) { return "empty app"; }
                    String pkg = packageForApp(app);
                    if (pkg == null) { return "unknown app: " + app; }
                    String question;
                    Object rawQ = a.opt("question");
                    if (rawQ instanceof org.json.JSONObject) {
                        question = ((org.json.JSONObject) rawQ).optString("question",
                                   ((org.json.JSONObject) rawQ).optString("value", ""));
                    } else {
                        question = a.optString("question", "");
                    }
                    FreeformApp.ask(ctx, pkg, question, new FreeformApp.Cb() {
                        @Override public void onOk(String text) {
                            out[0] = text; latch.countDown();
                        }
                        @Override public void onErr(String msg) {
                            out[0] = "APP ERROR: " + msg; latch.countDown();
                        }
                    });
                    long end = System.currentTimeMillis() + 150_000L;
                    while (System.currentTimeMillis() < end && !cancelled) {
                        if (latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)) break;
                    }
                    if (cancelled) return "CANCELLED";
                    if (out[0].isEmpty()) out[0] = "APP TIMEOUT";
                    if (out[0].length() > 6000) out[0] = out[0].substring(0, 6000) + "\n...";
                    return out[0];
                }
                case "think_via": {
                    final java.util.concurrent.CountDownLatch latch =
                        new java.util.concurrent.CountDownLatch(1);
                    final String[] out = { "" };
                    Worker.ask(prefs, a.getString("task"), new Worker.Cb() {
                        @Override public void onReply(String text) {
                            out[0] = text; latch.countDown();
                        }
                        @Override public void onError(String msg) {
                            out[0] = "WORKER ERROR: " + msg; latch.countDown();
                        }
                    });
                    try { latch.await(180, java.util.concurrent.TimeUnit.SECONDS); }
                    catch (Exception ignored) {}
                    if (out[0].isEmpty()) out[0] = "WORKER TIMEOUT";
                    if (out[0].length() > 6000) out[0] = out[0].substring(0, 6000) + "\n...";
                    return out[0];
                }
                case "run_skill": {
                    String sname = a.getString("name");
                    String sargs = a.optString("args", "");
                    Candy skill = SkillRunner.findByName(sname);
                    if (skill == null) return "no such skill: " + sname;
                    SkillRunner.Result r = SkillRunner.run(skill, sargs);
                    if (r.output == null || r.output.trim().isEmpty()) return "(no output)";
                    if (r.output.length() > 4000) return r.output.substring(0, 4000) + "\n...(truncated)";
                    return r.output;
                }
                // === CONCAT VIDEO TOOLS (K-B6) ===
                case "video_project_create":
                    return ConcatTools.video_project_create(a.getString("name"));
                case "video_media_import":
                    return ConcatTools.video_media_import(
                        a.getString("project"), a.getString("filePath"));
                case "video_add_clip":
                    return ConcatTools.video_add_clip(
                        a.getString("project"), a.getString("mediaId"),
                        a.optDouble("startSec", 0.0));
                case "video_add_transition":
                    return ConcatTools.video_add_transition(
                        a.getString("project"), a.getString("kind"),
                        a.optDouble("durationSec", 1.0));
                case "video_add_text":
                    return ConcatTools.video_add_text(
                        a.getString("project"), a.getString("text"),
                        a.optDouble("startSec", 0.0), a.optDouble("endSec", 3.0));
                case "video_apply_effect":
                    return ConcatTools.video_apply_effect(
                        a.getString("project"), a.getString("effectId"));
                case "video_export":
                    return ConcatTools.video_export(
                        a.getString("project"), a.getString("outputPath"));
                default:
                    return "Unknown tool: " + name;
            }
        } catch (Exception e) {
            return "Tool error: " + e.getMessage();
        }
    }

    /** Append the assistant's tool_call to the session log. */
    private void appendAssistantToolCall(String name, String argsJson) {
        try {
            JSONObject am = new JSONObject();
            am.put("role", "assistant");
            am.put("content", JSONObject.NULL);
            JSONArray tcs = new JSONArray();
            JSONObject tc = new JSONObject();
            tc.put("id", "call_" + System.currentTimeMillis());
            tc.put("type", "function");
            JSONObject fn = new JSONObject();
            fn.put("name", name);
            fn.put("arguments", argsJson);
            tc.put("function", fn);
            tcs.put(tc);
            am.put("tool_calls", tcs);
            sessionLog.put(am);
        } catch (Exception ignored) {}
    }

    /** Append a tool result to the session log. */
    private void appendToolResult(String name, String argsJson, String result) {
        try {
            JSONObject tm = new JSONObject();
            tm.put("role", "tool");
            tm.put("tool_call_id", "call_" + System.currentTimeMillis());
            tm.put("content", result);
            sessionLog.put(tm);
        } catch (Exception ignored) {}
    }

    // ---------- DSH routing ----------

    private void runViaDsh() {
        String userMsg = "";
        try {
            for (int i = sessionLog.length() - 1; i >= 0; i--) {
                JSONObject m = sessionLog.getJSONObject(i);
                if ("user".equals(m.optString("role"))) {
                    Object c = m.opt("content");
                    userMsg = (c instanceof String) ? (String) c : String.valueOf(c);
                    break;
                }
            }
        } catch (Exception ignored) {}

        final String msg = userMsg;

        new Thread(() -> {
            if (cancelled) return;
            DshClient.Result r = DshClient.run(msg, 180_000L);  // 3 min timeout
            if (cancelled) return;

            String reason = DshClient.formatReasoning(r.reasoning);
            if (!reason.isEmpty()) {
                double secs = r.ms / 1000.0;
                String header = String.format("%.1fs  ·  %s",
                    secs,
                    DshClient.isBridgeAlive() ? "warm" : "cold");
                listener.onToolResult("DSH THINKING  ·  " + header, reason);
            }

            try {
                JSONObject a = new JSONObject();
                a.put("role", "assistant");
                a.put("content", r.answer);
                sessionLog.put(a);
            } catch (Exception ignored) {}

            listener.onReply(r.answer);
            listener.onTurnComplete();
        }).start();
    }

    // ---------- Multi-model turn ----------

    private void runMultiTurn(final String userMessage, String mode,
                              java.util.List<String> models, String judge) {
        listener.onThinking();
        try {
            JSONObject info = new JSONObject();
            info.put("role", "system");
            info.put("content", "multi: " + mode + " · " + models.size() + " models");
            sessionLog.put(info);
        } catch (Exception ignored) {}

        AIClient.sendMulti(ctx, prefs, sessionLog, userMessage, models, mode, judge,
            new AIClient.MultiCallback() {
                @Override public void onDone(java.util.List<AIClient.ModelReply> replies,
                                             String finalReply, boolean merged, String judgeModel) {
                    if (cancelled) return;
                    // show each reply as a card with role tag
                    AIClient.ModelReply lastGood = null;
                    for (AIClient.ModelReply r : replies) {
                        String body;
                        if (r.error != null && (r.reply == null || r.reply.isEmpty()))
                            body = "[error] " + r.error;
                        else if (r.reply == null || r.reply.trim().isEmpty())
                            body = "(empty)";
                        else body = r.reply;
                        if (body.length() > 1400) body = body.substring(0, 1400) + "...";
                        String label = "MODEL:" + r.role + "|" + r.model + "|" + r.statsLine();
                        listener.onToolResult(label, body);
                        if (r.reply != null && !r.reply.trim().isEmpty()) lastGood = r;
                    }
                    // always log an assistant message so history is not empty
                    String mode = prefs.getMultiMode();
                    if ("parallel".equals(mode)) {
                        StringBuilder sb = new StringBuilder();
                        for (AIClient.ModelReply r : replies) {
                            if (r.reply != null && !r.reply.trim().isEmpty()) {
                                sb.append("[").append(r.model).append("]\n")
                                  .append(r.reply).append("\n\n");
                            }
                        }
                        try {
                            JSONObject a = new JSONObject();
                            a.put("role", "assistant");
                            a.put("content", sb.toString());
                            sessionLog.put(a);
                        } catch (Exception ignored) {}
                    } else {
                        AIClient.ModelReply forLog = null;
                        for (AIClient.ModelReply r : replies) {
                            if ("final".equals(r.role) || "merge".equals(r.role)) { forLog = r; break; }
                        }
                        if (forLog == null) forLog = lastGood;
                        if (forLog != null && forLog.reply != null) {
                            try {
                                JSONObject a = new JSONObject();
                                a.put("role", "assistant");
                                a.put("content", forLog.reply);
                                sessionLog.put(a);
                            } catch (Exception ignored) {}
                        }
                    }
                    listener.onTurnComplete();
                }
                @Override public void onError(String err) {
                    listener.onError(err);
                    listener.onTurnComplete();
                }
            });
    }

    // ---------- Static helpers ----------

    /** Snapshot of the loop for debugging. */
    public String snapshot() {
        return "step=" + currentStep + "/" + maxStepsPerTurn +
               " cancelled=" + cancelled +
               " logSize=" + sessionLog.length();
    }
}
