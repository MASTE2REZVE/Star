package com.star.app;

import android.content.Context;
import okhttp3.*;
import org.json.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class AIClient {
    private static final String FALLBACK_MODEL = "deepseek/deepseek-chat-v3.1:free";
    private static String lastUsedModel = null;
    private static int failCount = 0;
    public interface Callback {
        void onReply(String reply);
        void onReplyStats(String reply, int inTok, int outTok, long ms);
        void onToolCall(String toolName, String argsJson);
        void onError(String error);
    }

    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build();

    public static String sanitize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\t') sb.append("  ");
            else if (c == '\n' || c == '\r') sb.append(c);
            else if (c < 32 || c == 127) { }
            else sb.append(c);
        }
        String out = sb.toString();
        if (out.length() > 1200) out = out.substring(0, 1200) + "\n...(trunc)";
        return out;
    }

    private static final String ANTI_NARRATION =
        "HARD RULES — never break these:\n" +
        "- Never write your reasoning, plan, or thinking as chat text.\n" +
        "- Never write \"The user is asking\" or \"Let me re-read\" or \"I should use...\".\n" +
        "- If a tool call is needed, emit the tool call directly. No preamble.\n" +
        "- If no tool is needed, answer the user in one shot.\n" +
        "- Chat text is only the final answer to the user, never internal thoughts.\n";

    private static String sys(String memory, String instructions) {
        // Hard rules: no reasoning narration, no preamble.
        String base = ANTI_NARRATION;

        StringBuilder p = new StringBuilder();
        p.append("Star = Android agent on Xiaomi Pad 7. Shell via Shizuku + UI via Accessibility.\n\n");

        if (memory != null && !memory.isEmpty())
            p.append("USER FACTS:\n").append(memory).append("\n\n");
        if (instructions != null && !instructions.isEmpty())
            p.append("USER RULES:\n").append(instructions).append("\n\n");

        p.append("=== SPEED MODE — MINIMIZE EVERYTHING ===\n");
        p.append("Every action costs time. Be aggressive.\n\n");

        p.append("=== ui_seq (BATCH — ALWAYS USE) ===\n");
        p.append("Format: action|action|action\n");
        p.append("Actions: find:TEXT | type:TEXT | tap:X,Y | enter | back | home | wait:MS | scroll:up|down\n\n");

        p.append("=== WAIT TIME RULES (BE STINGY) ===\n");
        p.append("• App launch: wait:600   (not 1500)\n");
        p.append("• After tap/find: no wait or wait:150\n");
        p.append("• After enter: wait:400\n");
        p.append("• Between scrolls: NO wait (omit entirely)\n");
        p.append("• Never use wait >800 unless app is clearly slow\n\n");

        p.append("=== EXAMPLES ===\n");
        p.append("Search YouTube for cats + scroll:\n");
        p.append("  run_shell: am start -n $(cmd package resolve-activity --brief -c android.intent.category.LAUNCHER com.google.android.youtube | tail -1)\n");
        p.append("  ui_seq: wait:600|find:Search|type:cats|enter|wait:400|scroll:down|scroll:down|scroll:down|scroll:down|scroll:down\n");
        p.append("Total: 2 calls, ~2 seconds.\n\n");

        p.append("Open app only (fastest):\n");
        p.append("  run_shell: am start -n $(cmd package resolve-activity --brief -c android.intent.category.LAUNCHER <pkg> | tail -1)\n");
        p.append("Done. 1 call.\n\n");

        p.append("=== OTHER TOOLS ===\n");
        p.append("run_shell | ui_dump | ui_find | ui_tap | ui_type | ui_press | ui_scroll | update_self\n\n");

        p.append("=== CONTEXT-AWARE ACTIONS ===\n");
        p.append("When user says 'scroll', 'click that', 'tap X', 'change Y to Z' — DO NOT open any app.\n");
        p.append("You are ALREADY on the right screen (user sees it). Just act on it:\n");
        p.append("  'scroll down' → ui_scroll down\n");
        p.append("  'scroll up' → ui_scroll up\n");
        p.append("  'click X' / 'tap X' → ui_find X (or ui_dump first if unsure)\n");
        p.append("  'change resolution to 720p' → ui_dump, find 'Resolution'/'Quality', tap it, find '720p', tap it\n");
        p.append("  'like this video' → ui_dump, find heart/like, tap it\n");
        p.append("Use ui_dump FIRST if you don't know what's on screen. Then act.\n\n");

        p.append("=== RULES ===\n");
        p.append("1. Never write reasoning, thinking, or analysis. Answer directly.\n");
        p.append("2. Finish EVERY part of the user request. If they say \"open X and search Y\", do BOTH.\n");
        p.append("3. Do NOT add steps the user didn't ask for (no extra scrolls, no verification loops).\n");
        p.append("4. Example: \"open Facebook and search Android\" = open app, tap Search, type Android, press enter. Then done.\n");
        p.append("5. Example: \"open YouTube\" = open app. Done.\n");
        p.append("6. Max 2 tool calls per user request. Reply in 1 short sentence.\n");
        p.append("7. No apt/pkg/sudo. Root at 100% is normal.\n\n");

        p.append("=== ATTACHMENTS ===\n");
        p.append("User may attach images (you see them) or files (path given). For files, " +
                 "use run_shell to read if path starts with /sdcard/.\n\n");
        p.append("PACKAGES: youtube=com.google.android.youtube, facebook=com.facebook.katana, chrome=com.android.chrome, whatsapp=com.whatsapp, settings=com.android.settings, play=com.android.vending\n");
        return p.toString();
    }

    private static JSONArray buildTools() throws JSONException {
        JSONArray tools = new JSONArray();
        tools.put(func("run_shell", "Android shell command.",
            prop("command", "string", "Command")));
        tools.put(func("ui_seq",
            "BATCH UI actions. Format 'find:Search|type:cats|enter|scroll:down'. " +
            "Actions: find:TEXT|type:TEXT|tap:X,Y|enter|back|home|wait:MS|scroll:up|down. " +
            "ALWAYS prefer this. Use minimal waits.",
            prop("seq", "string", "Pipe-separated actions")));
        tools.put(func("ui_dump", "List UI elements. Avoid.", null));
        tools.put(func("ui_find", "Find and tap text.",
            prop("text", "string", "Text")));

        JSONObject tp = new JSONObject();
        tp.put("x", propObj("integer", "X"));
        tp.put("y", propObj("integer", "Y"));
        tools.put(funcWithProps("ui_tap", "Tap.", tp, new String[]{"x","y"}));

        tools.put(func("ui_type", "Type text.",
            prop("text", "string", "Text")));
        tools.put(func("ui_press", "Press key.",
            prop("key", "string", "back|home|enter|tab|escape")));
        tools.put(func("ui_scroll", "Scroll.",
            prop("direction", "string", "up|down")));
        tools.put(func("update_self", "Self-update.", null));
                // think_via: route a focused task to the configured worker model
        // (a free OpenRouter model). Runs entirely in the background.
        JSONObject thinkProps = new JSONObject();
        thinkProps.put("task", prop("task", "string",
            "The complete task or question for the worker. Include all context."));
        tools.put(funcWithProps("think_via",
            "Delegate a focused task to the worker model — a free OpenRouter model " +
            "used as a separate brain. Use it for heavy reasoning, code generation, " +
            "or when the current model is not strong enough. Take its answer and use " +
            "or refine it.",
            thinkProps, new String[]{ "task" }));

        // think_via_app: route a question through an installed AI app running on
        // a hidden virtual display. Runs in background. User keeps using the tablet.
        JSONObject appProps = new JSONObject();
        appProps.put("app", prop("app", "string",
            "One of: chatgpt, gemini, claude, grok. Uses the installed app on a hidden display."));
        appProps.put("question", prop("question", "string",
            "The prompt to send to that app."));
        tools.put(funcWithProps("think_via_app",
            "Ask another AI app (ChatGPT, Gemini, Claude, Grok) running invisibly on a " +
            "hidden virtual display. Requires Shizuku. Use when the user names an app " +
            "or wants a second opinion. Returns the app's full screen text.",
            appProps, new String[]{ "app", "question" }));

return tools;

    }

    private static JSONObject propObj(String t, String d) throws JSONException {
        JSONObject o = new JSONObject(); o.put("type", t); o.put("description", d); return o;
    }
    private static JSONObject prop(String n, String t, String d) throws JSONException {
        JSONObject props = new JSONObject(); props.put(n, propObj(t, d));
        JSONObject o = new JSONObject();
        o.put("type", "object"); o.put("properties", props);
        o.put("required", new JSONArray().put(n));
        return o;
    }
    private static JSONObject func(String n, String d, JSONObject p) throws JSONException {
        JSONObject fn = new JSONObject();
        fn.put("name", n); fn.put("description", d);
        fn.put("parameters", p == null
            ? new JSONObject().put("type", "object").put("properties", new JSONObject()) : p);
        return new JSONObject().put("type", "function").put("function", fn);
    }
    private static JSONObject funcWithProps(String n, String d, JSONObject props, String[] req) throws JSONException {
        JSONObject p = new JSONObject();
        p.put("type", "object"); p.put("properties", props);
        JSONArray r = new JSONArray(); for (String x : req) r.put(x);
        p.put("required", r);
        return func(n, d, p);
    }

    // Strip any leaked chain-of-thought blocks the model sneaks through
    public static String stripThinking(String s) {
        if (s == null) return "";
        String out = s.trim();

        // If it looks like a thinking dump, try to salvage the final answer
        String[] triggers = {
            "Here's a thinking process",
            "Here is a thinking process",
            "Let me think step by step",
            "Thinking Process",
            "Analyze User Input",
            "Identify Constraints",
            "Step 1:",
            "Let me analyze"
        };
        boolean looksLikeThinking = false;
        for (String t : triggers) {
            if (out.startsWith(t) || out.contains(t)) {
                looksLikeThinking = true;
                break;
            }
        }

        if (looksLikeThinking) {
            // Try to find the real answer after the reasoning
            // Look for "Answer:", "Final:", "Response:", "So,", "Done." as break points
            String[] answerMarkers = {
                "\nAnswer:", "\nFinal answer:", "\nFinal:", "\nResponse:",
                "\nDone.", "\nDone:", "\nLet's ask user?", "\nSo,"
            };
            for (String m : answerMarkers) {
                int idx = out.lastIndexOf(m);
                if (idx > 20) {
                    out = out.substring(idx + m.length()).trim();
                    break;
                }
            }
            // If still starts with thinking-ish content, keep only last paragraph
            if (out.startsWith("Here") || out.startsWith("The UI") || out.startsWith("Let me")) {
                int lastNl = out.lastIndexOf("\n\n");
                if (lastNl > 0 && lastNl < out.length() - 20) {
                    out = out.substring(lastNl).trim();
                }
            }
        }

        // Hard cut: if it's absurdly long, just take last 2 paragraphs
        if (out.length() > 1500) {
            int cut = out.lastIndexOf("\n\n", 1200);
            if (cut > 0) out = out.substring(cut).trim();
        }

        return out;
    }

    private static JSONArray trimHistory(JSONArray h, int max) {
        // Drop UI-only card entries (role=ui_card) then cap the last max messages.
        JSONArray kept = new JSONArray();
        for (int i = 0; i < h.length(); i++) {
            JSONObject m = h.optJSONObject(i);
            if (m == null) continue;
            String role = m.optString("role", "");
            if ("ui_card".equals(role)) continue;
            kept.put(m);
        }
        if (kept.length() <= max) return kept;
        JSONArray out = new JSONArray();
        for (int i = kept.length() - max; i < kept.length(); i++) out.put(kept.opt(i));
        return out;
    }

    public static void send(Context ctx, Prefs prefs, JSONArray history, Callback cb) {
        String apiKey = prefs.getApiKey();
        if (apiKey.isEmpty()) { cb.onError("No API key."); return; }

        // Track failures per model
        String currentModel = prefs.getModel();
        if (!currentModel.equals(lastUsedModel)) {
            lastUsedModel = currentModel;
            failCount = 0;
        }

        try {
            Memory mem = new Memory(ctx);

            JSONObject body = new JSONObject();
            body.put("model", prefs.getModel());
            body.put("stream", false);
            body.put("temperature", 0.1);
            body.put("max_tokens", 300);
            body.put("parallel_tool_calls", true);

            JSONArray messages = new JSONArray();
            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", sys(mem.readAll(), prefs.getInstructions()));
            messages.put(sysMsg);

            JSONArray trimmed = trimHistory(history, 6);
            for (int i = 0; i < trimmed.length(); i++) messages.put(trimmed.get(i));

            body.put("messages", messages);
            body.put("tools", buildTools());
            body.put("tool_choice", "auto");

            Request req = new Request.Builder()
                .url(prefs.getBaseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("HTTP-Referer", "https://github.com/MASTE2REZVE/Star")
                .header("X-Title", "Star")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();

            client.newCall(req).enqueue(new okhttp3.Callback() {
                @Override public void onFailure(Call c, IOException e) { cb.onError(e.getMessage()); }
                @Override public void onResponse(Call c, Response r) throws IOException {
                    String s = r.body().string();
                    try {
                        // Strip SSE prefix if present
                        if (s.trim().startsWith("data:")) {
                            // Try to extract the JSON after "data:"
                            int idx = s.indexOf('{');
                            if (idx >= 0) s = s.substring(idx);
                        }
                        // Strip BOM / control chars
                        s = s.replaceAll("[\\u0000-\\u001F]", "").trim();
                        // If multiple JSON objects, take first
                        int lastBrace = s.lastIndexOf('}');
                        if (lastBrace > 0 && lastBrace < s.length() - 1) {
                            s = s.substring(0, lastBrace + 1);
                        }
                        JSONObject j = new JSONObject(s);
                        if (j.has("error")) {
                            cb.onError(j.getJSONObject("error").optString("message", s)); return;
                        }
                        JSONArray choices = j.optJSONArray("choices");
                        if (choices == null || choices.length() == 0) {
                            cb.onError("Empty response");
                            return;
                        }
                        JSONObject msg = choices.getJSONObject(0)
                            .optJSONObject("message");
                        if (msg == null) {
                            cb.onError("No message");
                            return;
                        }
                        if (msg.has("tool_calls") && msg.getJSONArray("tool_calls").length() > 0) {
                            JSONArray tcs = msg.getJSONArray("tool_calls");
                            for (int i = 0; i < tcs.length(); i++) {
                                JSONObject tc = tcs.getJSONObject(i);
                                cb.onToolCall(tc.getJSONObject("function").getString("name"),
                                    tc.getJSONObject("function").optString("arguments", "{}"));
                            }
                            return;
                        }
                        cb.onReply(stripThinking(msg.optString("content", "")));
                    } catch (Exception e) {
                        failCount++;
                        String snippet = s.length() > 300 ? s.substring(0, 300) + "…" : s;
                        if (failCount >= 2 && !lastUsedModel.equals(FALLBACK_MODEL)) {
                            cb.onError("Model keeps failing. Switch to DeepSeek V3.1 " +
                                "(free) via the model pill for reliable tool calling.");
                        } else {
                            cb.onError("Parse error. Try switching to a " +
                                "tool-capable model like DeepSeek V3.1.");
                        }
                    }
                }
            });
        } catch (Exception e) { cb.onError(e.getMessage()); }
    }

    // ---------- multi-model ----------

    public static class ModelReply {
        public String model;
        public String reply;
        public String error;
        public int inTok;
        public int outTok;
        public long ms;
        public String role = "answer";   // answer | draft | critique | final | merge
        public ModelReply(String m, String r, String e) { model = m; reply = r; error = e; }
        public String statsLine() {
            return String.format(java.util.Locale.US, "%.1fs · %d in · %d out",
                ms / 1000.0, inTok, outTok);
        }
    }

    public interface MultiCallback {
        void onDone(java.util.List<ModelReply> replies, String finalReply,
                    boolean merged, String judge);
        void onError(String err);
    }

    public static java.util.List<String> listModelsForPicker(Context ctx) {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.List<ModelCatalog.Model> cached = ModelCatalog.cached();
        if (cached != null) {
            for (ModelCatalog.Model m : cached) {
                if (m.tools) out.add(m.id);
            }
        }
        if (out.isEmpty()) {
            // fallback: common free tier
            out.add("deepseek/deepseek-chat-v3.1:free");
            out.add("meta-llama/llama-3.3-70b-instruct:free");
            out.add("google/gemini-2.0-flash-exp:free");
            out.add("qwen/qwen-2.5-72b-instruct:free");
        }
        return out;
    }

    /** One-shot sync HTTP call to a specific model. No tools. */
    private static ModelReply callModel(Context ctx, Prefs prefs, JSONArray history,
                                        String model, String extraSystem, int maxTokens) {
        ModelReply out = new ModelReply(model, null, null);
        long t0 = System.currentTimeMillis();
        try {
            String apiKey = prefs.getApiKey();
            if (apiKey.isEmpty()) { out.error = "no api key"; return out; }

            Memory mem = new Memory(ctx);
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("stream", false);
            body.put("temperature", 0.2);
            body.put("max_tokens", maxTokens);

            JSONArray messages = new JSONArray();
            JSONObject sysMsg = new JSONObject();
            String baseSys = sys(mem.readAll(), prefs.getInstructions());
            if (extraSystem != null && !extraSystem.isEmpty()) {
                baseSys = baseSys + "\n\n" + extraSystem;
            }
            sysMsg.put("role", "system");
            sysMsg.put("content", baseSys);
            messages.put(sysMsg);

            JSONArray trimmed = trimHistory(history, 6);
            for (int i = 0; i < trimmed.length(); i++) messages.put(trimmed.get(i));

            body.put("messages", messages);

            Request req = new Request.Builder()
                .url(prefs.getBaseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("HTTP-Referer", "https://github.com/MASTE2REZVE/Star")
                .header("X-Title", "Star")
                .post(RequestBody.create(body.toString(),
                    MediaType.parse("application/json")))
                .build();

            Response r = client.newCall(req).execute();
            String s = r.body().string();

            if (s.trim().startsWith("data:")) {
                int idx2 = s.indexOf('{');
                if (idx2 >= 0) s = s.substring(idx2);
            }
            s = s.replaceAll("[\\u0000-\\u001F]", "").trim();
            int lastBrace = s.lastIndexOf('}');
            if (lastBrace > 0 && lastBrace < s.length() - 1) s = s.substring(0, lastBrace + 1);

            JSONObject j = new JSONObject(s);
            if (j.has("error")) {
                out.error = j.getJSONObject("error").optString("message", "error");
                return out;
            }
            JSONArray choices = j.optJSONArray("choices");
            if (choices == null || choices.length() == 0) { out.error = "empty"; return out; }
            JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
            if (msg == null) { out.error = "no message"; return out; }
            String bodyTxt = stripThinking(msg.optString("content", ""));
            if (bodyTxt == null) bodyTxt = "";
            String tcheck = bodyTxt.trim();
            if (tcheck.isEmpty() || tcheck.equals("null") || tcheck.equals("{}")) {
                out.error = "empty reply";
            } else {
                out.reply = bodyTxt;
            }
            JSONObject usage = j.optJSONObject("usage");
            if (usage != null) {
                out.inTok = usage.optInt("prompt_tokens", 0);
                out.outTok = usage.optInt("completion_tokens", 0);
            }
        } catch (Exception e) {
            out.error = e.getMessage();
        }
        out.ms = System.currentTimeMillis() - t0;
        return out;
    }

    /** Run several models in parallel. */
    private static java.util.List<ModelReply> runParallel(Context ctx, Prefs prefs,
                                                          JSONArray history,
                                                          java.util.List<String> models,
                                                          String extraSystem, int maxTokens) {
        final java.util.List<ModelReply> out = java.util.Collections.synchronizedList(
            new java.util.ArrayList<ModelReply>());
        java.util.concurrent.ExecutorService pool =
            java.util.concurrent.Executors.newFixedThreadPool(Math.min(models.size(), 5));
        java.util.List<java.util.concurrent.Future<?>> futures =
            new java.util.ArrayList<>();
        for (final String m : models) {
            futures.add(pool.submit(() -> {
                ModelReply r = callModel(ctx, prefs, history, m, extraSystem, maxTokens);
                out.add(r);
            }));
        }
        for (java.util.concurrent.Future<?> f : futures) {
            try { f.get(); } catch (Exception ignored) {}
        }
        pool.shutdown();
        return out;
    }

    /** Build a merge prompt from N model replies. */
    private static JSONArray buildMergeHistory(String userQuestion,
                                               java.util.List<ModelReply> replies) {
        StringBuilder sb = new StringBuilder();
        sb.append("User question:\n").append(userQuestion).append("\n\n");
        sb.append("Answers from different models:\n");
        for (ModelReply r : replies) {
            sb.append("\n--- ").append(r.model).append(" ---\n");
            if (r.error != null) sb.append("[error: ").append(r.error).append("]\n");
            else sb.append(r.reply == null ? "" : r.reply).append("\n");
        }
        JSONArray h = new JSONArray();
        try {
            JSONObject u = new JSONObject();
            u.put("role", "user");
            u.put("content", sb.toString());
            h.put(u);
        } catch (Exception ignored) {}
        return h;
    }

    /** Full multi-model run. mode = parallel | mix | chain. */
    public static void sendMulti(final Context ctx, final Prefs prefs,
                                 final JSONArray history,
                                 final String userQuestion,
                                 final java.util.List<String> models,
                                 final String mode,
                                 final String judgeModel,
                                 final MultiCallback cb) {
        new Thread(() -> {
            try {
                if (models == null || models.size() < 2) {
                    cb.onError("Need at least 2 models."); return;
                }

                if ("chain".equals(mode)) {
                    // draft -> critique -> rewrite
                    String draftM = models.get(0);
                    String critiqueM = models.get(1);

                    ModelReply draft = callModel(ctx, prefs, history, draftM, null, 800);
                    if (draft.reply == null) { cb.onError("draft: " + draft.error); return; }

                    JSONArray cHist = new JSONArray();
                    try {
                        JSONObject c = new JSONObject();
                        c.put("role", "user");
                        c.put("content",
                            "Question:\n" + userQuestion +
                            "\n\nDraft answer:\n" + draft.reply +
                            "\n\nList what is wrong or missing in this draft. " +
                            "Be specific. 5 bullets max.");
                        cHist.put(c);
                    } catch (Exception ignored) {}

                    ModelReply critique = callModel(ctx, prefs, cHist, critiqueM,
                        "You are a strict reviewer. Do not rewrite. Only list problems.", 500);

                    JSONArray rHist = new JSONArray();
                    try {
                        JSONObject c2 = new JSONObject();
                        c2.put("role", "user");
                        c2.put("content",
                            "Question:\n" + userQuestion +
                            "\n\nDraft:\n" + draft.reply +
                            "\n\nReviewer notes:\n" +
                            (critique.reply == null ? "(none)" : critique.reply) +
                            "\n\nWrite the final improved answer. Be concise.");
                        rHist.put(c2);
                    } catch (Exception ignored) {}

                    ModelReply finalR = callModel(ctx, prefs, rHist, draftM, null, 800);

                    draft.role = "draft";
                    critique.role = "critique";
                    finalR.role = "final";

                    java.util.List<ModelReply> all = new java.util.ArrayList<>();
                    all.add(draft);
                    all.add(critique);
                    all.add(finalR);

                    String finalBody = finalR.reply != null ? finalR.reply
                        : (finalR.error != null ? "[final failed: " + finalR.error + "]" : "");
                    cb.onDone(all, finalBody, true, draftM);

                } else {
                    // parallel or mix
                    java.util.List<ModelReply> all = runParallel(ctx, prefs, history,
                        models, null, 700);

                    if ("parallel".equals(mode)) {
                        for (ModelReply r : all) r.role = "answer";
                        String anyReply = null;
                        for (ModelReply r : all) if (r.reply != null) { anyReply = r.reply; break; }
                        cb.onDone(all, anyReply, false, null);
                    } else {
                        // mix: judge merges
                        String judge = (judgeModel != null && !judgeModel.isEmpty())
                            ? judgeModel : models.get(0);
                        JSONArray mHist = buildMergeHistory(userQuestion, all);
                        for (ModelReply r : all) r.role = "answer";
                        ModelReply merged = callModel(ctx, prefs, mHist, judge,
                            "You are a meta-reasoner. Several models answered the same " +
                            "question. Merge them into ONE best answer. Skip any errors. " +
                            "Do not mention the models. Be concise. No preamble.",
                            900);
                        merged.role = "merge";
                        all.add(merged);
                        cb.onDone(all,
                            merged.reply != null ? merged.reply : "",
                            true, judge);
                    }
                }
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }


    /** Send one image + text to a vision model. Retries with tools if needed. */
    public static void sendVision(final Context ctx, final Prefs prefs,
                                  final String base64Jpeg, final String userPrompt,
                                  final String model, final Callback cb) {
        new Thread(() -> {
            String firstErr = tryVision(ctx, prefs, base64Jpeg, userPrompt, model, false, cb);
            if (firstErr == null) return;
            // retry with tools only for the "agentic harness" error
            if (firstErr.toLowerCase().contains("agentic")
                || firstErr.toLowerCase().contains("harness")) {
                String second = tryVision(ctx, prefs, base64Jpeg, userPrompt, model, true, cb);
                if (second == null) return;
                cb.onError(second);
                return;
            }
            cb.onError(firstErr);
        }).start();
    }

    /** Return null on success. Return error string on failure. */
    private static String tryVision(Context ctx, Prefs prefs,
                                    String base64Jpeg, String userPrompt,
                                    String model, boolean withTools, Callback cb) {
        try {
            String apiKey = prefs.getApiKey();
            if (apiKey.isEmpty()) return "No API key";

            JSONArray messages = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            JSONArray content = new JSONArray();

            JSONObject textPart = new JSONObject();
            textPart.put("type", "text");
            textPart.put("text", userPrompt == null ? "Describe the screen." : userPrompt);
            content.put(textPart);

            JSONObject imgPart = new JSONObject();
            imgPart.put("type", "image_url");
            JSONObject imgUrl = new JSONObject();
            imgUrl.put("url", "data:image/jpeg;base64," + base64Jpeg);
            imgPart.put("image_url", imgUrl);
            content.put(imgPart);

            userMsg.put("content", content);
            messages.put(userMsg);

            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("stream", false);
            body.put("temperature", 0.2);
            body.put("max_tokens", 250);
            body.put("messages", messages);
            if (withTools) {
                try {
                    body.put("tools", buildTools());
                    body.put("tool_choice", "none");
                } catch (Exception ignored) {}
            }
            // debug: image size
            android.util.Log.d("Star", "vision: model=" + model +
                " b64len=" + (base64Jpeg == null ? 0 : base64Jpeg.length()) +
                " tools=" + withTools);

            Request req = new Request.Builder()
                .url(prefs.getBaseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("HTTP-Referer", "https://github.com/MASTE2REZVE/Star")
                .header("X-Title", "Star")
                .post(RequestBody.create(body.toString(),
                    MediaType.parse("application/json")))
                .build();

            Response resp = client.newCall(req).execute();
            String s = resp.body().string();
            if (s.trim().startsWith("data:")) {
                int i2 = s.indexOf('{');
                if (i2 >= 0) s = s.substring(i2);
            }
            s = s.replaceAll("[\u0000-\u001F]", "").trim();
            int lb = s.lastIndexOf('}');
            if (lb > 0 && lb < s.length() - 1) s = s.substring(0, lb + 1);

            JSONObject j;
            try {
                j = new JSONObject(s);
            } catch (Exception e) {
                String snippet = s.length() > 400 ? s.substring(0, 400) : s;
                return "HTTP " + resp.code() + " body: " + snippet;
            }
            if (j.has("error")) {
                Object err = j.opt("error");
                String m;
                if (err instanceof JSONObject) m = ((JSONObject) err).optString("message", err.toString());
                else m = String.valueOf(err);
                return "HTTP " + resp.code() + " err: " + m;
            }
            JSONArray choices = j.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                String snippet = s.length() > 400 ? s.substring(0, 400) : s;
                return "HTTP " + resp.code() + " no choices: " + snippet;
            }
            JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
            if (msg == null) return "no message";
            String text = stripThinking(msg.optString("content", ""));
            if (text == null) text = "";
            String t = text.trim();
            if (t.isEmpty() || t.equals("null") || t.equals("{}")) {
                return "model returned nothing";
            }
            cb.onReply(t);
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }


    public interface RawCb {
        void onText(String text);
        void onError(String err);
    }

    public static void sendMessages(final Prefs prefs, final JSONArray messages, final RawCb cb) {
        new Thread(() -> {
            try {
                String apiKey = prefs.getApiKey();
                if (apiKey.isEmpty()) { cb.onError("No API key"); return; }

                JSONObject body = new JSONObject();
                body.put("model", prefs.getModel());
                body.put("stream", false);
                body.put("temperature", 0.2);
                body.put("max_tokens", 4000);
                body.put("messages", messages);

                Request req = new Request.Builder()
                    .url(prefs.getBaseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "https://github.com/MASTE2REZVE/Star")
                    .header("X-Title", "Star-Builder")
                    .post(RequestBody.create(body.toString(),
                        MediaType.parse("application/json")))
                    .build();

                Response resp = client.newCall(req).execute();
                String s = resp.body().string();
                if (s.trim().startsWith("data:")) {
                    int i2 = s.indexOf('{');
                    if (i2 >= 0) s = s.substring(i2);
                }
                s = s.replaceAll("[\\u0000-\\u001F]", "").trim();
                int lb = s.lastIndexOf('}');
                if (lb > 0 && lb < s.length() - 1) s = s.substring(0, lb + 1);

                JSONObject j = new JSONObject(s);
                if (j.has("error")) {
                    cb.onError(j.getJSONObject("error").optString("message", "error"));
                    return;
                }
                JSONArray choices = j.optJSONArray("choices");
                if (choices == null || choices.length() == 0) { cb.onError("empty"); return; }
                JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
                if (msg == null) { cb.onError("no message"); return; }
                String content = msg.optString("content", "");
                if (content == null) content = "";
                cb.onText(content);
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }


    /** Same as sendMessages but with an explicit model. */
    public static void sendMessagesWithModel(final Prefs prefs, final String modelOverride,
                                             final JSONArray messages, final RawCb cb) {
        new Thread(() -> {
            try {
                String apiKey = prefs.getApiKey();
                if (apiKey.isEmpty()) { cb.onError("No API key"); return; }
                String model = (modelOverride == null || modelOverride.isEmpty())
                    ? prefs.getModel() : modelOverride;

                JSONObject body = new JSONObject();
                body.put("model", model);
                body.put("stream", false);
                body.put("temperature", 0.2);
                body.put("max_tokens", 4000);
                body.put("messages", messages);
                String pinned = prefs.getModelProvider(model);
                if (pinned != null && !pinned.isEmpty()) {
                    JSONObject prov = new JSONObject();
                    prov.put("order", new JSONArray().put(pinned));
                    prov.put("allow_fallbacks", true);
                    body.put("provider", prov);
                }

                Request req = new Request.Builder()
                    .url(prefs.getBaseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "https://github.com/MASTE2REZVE/Star")
                    .header("X-Title", "Star-Builder")
                    .post(RequestBody.create(body.toString(),
                        MediaType.parse("application/json")))
                    .build();

                Response resp = client.newCall(req).execute();
                String s = resp.body().string();
                if (s.trim().startsWith("data:")) {
                    int i2 = s.indexOf('{');
                    if (i2 >= 0) s = s.substring(i2);
                }
                s = s.replaceAll("[\\u0000-\\u001F]", "").trim();
                int lb = s.lastIndexOf('}');
                if (lb > 0 && lb < s.length() - 1) s = s.substring(0, lb + 1);

                JSONObject j = new JSONObject(s);
                if (j.has("error")) {
                    cb.onError(j.getJSONObject("error").optString("message", "error"));
                    return;
                }
                JSONArray choices = j.optJSONArray("choices");
                if (choices == null || choices.length() == 0) { cb.onError("empty"); return; }
                JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
                if (msg == null) { cb.onError("no message"); return; }
                String content = msg.optString("content", "");
                if (content == null) content = "";
                cb.onText(content);
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }

}
