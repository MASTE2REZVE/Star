package com.star.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.graphics.Typeface;
import android.text.InputType;
import android.widget.*;
import android.view.ViewGroup;

public class SettingsActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs p = new Prefs(this);
        Memory mem = new Memory(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0D1117"));

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(50, 50, 50, 50);
        scroll.addView(l);

        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextSize(28);
        title.setTextColor(Color.parseColor("#58A6FF"));
        title.setPadding(0, 0, 0, 30);

        l.addView(title);

        // API key
        l.addView(hint("API Key"));
        EditText key = edit(p.getApiKey(), "sk-...", false);
        l.addView(key);

        // Base URL
        l.addView(hint("Base URL"));
        EditText url = edit(p.getBaseUrl(), "https://...", false);
        l.addView(url);

        // Model
        l.addView(hint("Model"));
        EditText model = edit(p.getModel(), "model name", false);
        l.addView(model);

        // Engine toggle
        l.addView(hint("AI Engine"));
        LinearLayout engineRow = new LinearLayout(this);
        engineRow.setOrientation(LinearLayout.HORIZONTAL);
        engineRow.setPadding(0, 0, 0, dp(8));

        TextView engineNative = engineBtn("Native", p.getEngine().equals("native"));
        TextView engineDsh = engineBtn("DSH", p.getEngine().equals("dsh"));
        final Prefs fp = p;

        engineNative.setOnClickListener(v -> {
            fp.setEngine("native");
            engineNative.setTextColor(0xFFECECEC);
            engineNative.setBackgroundColor(0xFF1A2436);
            engineDsh.setTextColor(0xFF8E8E8E);
            engineDsh.setBackgroundColor(0xFF161616);
            Toast.makeText(this, "Using native engine", Toast.LENGTH_SHORT).show();
        });
        engineDsh.setOnClickListener(v -> {
            fp.setEngine("dsh");
            engineDsh.setTextColor(0xFFECECEC);
            engineDsh.setBackgroundColor(0xFF1A2436);
            engineNative.setTextColor(0xFF8E8E8E);
            engineNative.setBackgroundColor(0xFF161616);
            Toast.makeText(this, "Using DSH engine", Toast.LENGTH_SHORT).show();
        });

        LinearLayout.LayoutParams elp1 = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        elp1.rightMargin = dp(6);
        engineNative.setLayoutParams(elp1);
        LinearLayout.LayoutParams elp2 = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        elp2.leftMargin = dp(6);
        engineDsh.setLayoutParams(elp2);

        engineRow.addView(engineNative);
        engineRow.addView(engineDsh);
        l.addView(engineRow);

        // Update URL
        l.addView(hint("Update Manifest URL"));
        EditText upd = edit(p.getUpdateUrl(), "https://raw...", false);
        l.addView(upd);

        // ---- Worker model (free OpenRouter model as second brain) ----
        l.addView(hint("Worker Model — free model used as a second brain"));
        final String startWorker = p.getWorkerModel();
        final TextView workerPill = new TextView(this);
        workerPill.setText(startWorker == null || startWorker.isEmpty()
            ? "tap to pick a free model"
            : startWorker);
        workerPill.setTextSize(13);
        workerPill.setTypeface(Typeface.MONOSPACE);
        workerPill.setTextColor(0xFFECECEC);
        workerPill.setPadding(dp(14), dp(14), dp(14), dp(14));
        android.graphics.drawable.GradientDrawable wpb = new android.graphics.drawable.GradientDrawable();
        wpb.setCornerRadius(dp(10));
        wpb.setColor(0xFF1E1E1E);
        wpb.setStroke(dp(1), 0xFF2A2A2A);
        workerPill.setBackground(wpb);
        workerPill.setClickable(true);
        workerPill.setOnClickListener(v -> {
            ModelPickerDialog.show(SettingsActivity.this, p, p.getWorkerModel(),
                m -> {
                    p.setWorkerModel(m);
                    workerPill.setText(m);
                });
        });
        l.addView(workerPill);

        TextView workerNote = new TextView(this);
        workerNote.setText("Free OpenRouter models only work for reasoning. Pick one with :free in the id.");
        workerNote.setTextSize(10);
        workerNote.setTextColor(0xFF8E8E8E);
        workerNote.setPadding(0, 0, 0, dp(8));
        l.addView(workerNote);

        // ---- Worker providers ----
        l.addView(hint("Worker Providers — API keys"));
        TextView workerSub = new TextView(this);
        workerSub.setText("Star can delegate heavy reasoning to external AIs. "
            + "Leave blank to disable a provider. Gemini has a free tier.");
        workerSub.setTextSize(10);
        workerSub.setTextColor(0xFF8E8E8E);
        workerSub.setPadding(0, 0, 0, dp(8));
        l.addView(workerSub);

        l.addView(hint("Gemini API Key (free tier)"));
        EditText kGem = edit(p.getGeminiKey(), "AIza...", true);
        l.addView(kGem);

        l.addView(hint("ChatGPT / OpenAI API Key"));
        EditText kOai = edit(p.getOpenAiKey(), "sk-...", true);
        l.addView(kOai);

        l.addView(hint("Grok / xAI API Key"));
        EditText kGrok = edit(p.getGrokKey(), "xai-...", true);
        l.addView(kGrok);

        l.addView(hint("Claude / Anthropic API Key"));
        EditText kClaude = edit(p.getClaudeKey(), "sk-ant-...", true);
        l.addView(kClaude);

        l.addView(hint("Perplexity API Key"));
        EditText kPplx = edit(p.getPerplexityKey(), "pplx-...", true);
        l.addView(kPplx);

        // GitHub token (Builder)
        l.addView(hint("GitHub Token (for Builder)"));
        EditText ghToken = edit(p.getGitHubToken(), "ghp_...", true);
        l.addView(ghToken);

        // GitHub billing token (optional, fine-grained, Plan:read only)
        l.addView(hint("GitHub Billing Token (optional, Plan: read only)"));
        EditText ghBilling = edit(p.getBillingToken(), "github_pat_...", true);
        l.addView(ghBilling);

        TextView ghBillingHint = new TextView(this);
        ghBillingHint.setText("Fine-grained token. Grants only Plan:read. "
            + "Cannot read profile, email, repos. Used only for the usage row.");
        ghBillingHint.setTextSize(10);
        ghBillingHint.setTextColor(0xFF8E8E8E);
        ghBillingHint.setPadding(0, 0, 0, dp(8));
        l.addView(ghBillingHint);

        // GitHub user (read-only info)
        TextView ghUser = new TextView(this);
        ghUser.setText("GitHub user: " + (p.getGitHubUser().isEmpty() ? "not set" : p.getGitHubUser()));
        ghUser.setTextSize(11);
        ghUser.setTextColor(0xFF8E8E8E);
        ghUser.setPadding(0, 0, 0, dp(8));
        l.addView(ghUser);

        // Sandbox repo (read-only info)
        TextView ghSandbox = new TextView(this);
        ghSandbox.setText("Sandbox repo: " + (p.getSandboxRepo().isEmpty() ? "not set" : p.getSandboxRepo()));
        ghSandbox.setTextSize(11);
        ghSandbox.setTextColor(0xFF8E8E8E);
        ghSandbox.setPadding(0, 0, 0, dp(8));
        l.addView(ghSandbox);

        // Custom instructions
        l.addView(hint("Custom Instructions (applied every message)"));
        EditText instr = edit(p.getInstructions(),
            "e.g. Always reply in English. My name is Rezve. Use metric units.", true);
        l.addView(instr);

        // Memory
        l.addView(hint("Long-term Memory (facts about me)"));
        EditText memory = edit(mem.readAll(),
            "e.g. I live in Bangladesh. I prefer dark mode.", true);
        l.addView(memory);

        // Max tool calls (speed)
        l.addView(hint("Max Tool Calls Per Message (higher = slower)"));
        EditText maxT = edit(String.valueOf(p.getMaxTools()), "12", false);
        maxT.setInputType(InputType.TYPE_CLASS_NUMBER);
        l.addView(maxT);

        // Buttons
        Button save = new Button(this);
        save.setText("SAVE ALL");
        save.setOnClickListener(v -> {
            p.setApiKey(key.getText().toString().trim());
            p.setBaseUrl(url.getText().toString().trim());
            p.setModel(model.getText().toString().trim());
            p.setUpdateUrl(upd.getText().toString().trim());
            p.setGitHubToken(ghToken.getText().toString().trim());
            p.setGeminiKey(kGem.getText().toString().trim());
            p.setOpenAiKey(kOai.getText().toString().trim());
            p.setGrokKey(kGrok.getText().toString().trim());
            p.setClaudeKey(kClaude.getText().toString().trim());
            p.setPerplexityKey(kPplx.getText().toString().trim());
            p.setBillingToken(ghBilling.getText().toString().trim());
            p.setInstructions(instr.getText().toString().trim());
            mem.writeAll(memory.getText().toString().trim());
            try { p.setMaxTools(Integer.parseInt(maxT.getText().toString().trim())); }
            catch (Exception e) { p.setMaxTools(12); }
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        });

        Button fetch = new Button(this);
        fetch.setText("FETCH MODELS");
        fetch.setOnClickListener(v -> {
            p.setApiKey(key.getText().toString().trim());
            p.setBaseUrl(url.getText().toString().trim());
            startActivity(new Intent(this, ModelsActivity.class));
        });

        Button check = new Button(this);
        check.setText("CHECK FOR UPDATES");
        check.setOnClickListener(v -> {
            p.setUpdateUrl(upd.getText().toString().trim());
            Toast.makeText(this, "Checking...", Toast.LENGTH_SHORT).show();
            Updater.checkAndUpdate(this, p, msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
        });

        Button clearMem = new Button(this);
        clearMem.setText("CLEAR MEMORY");
        clearMem.setOnClickListener(v -> {
            mem.writeAll("");
            memory.setText("");
            Toast.makeText(this, "Memory cleared", Toast.LENGTH_SHORT).show();
        });

        l.addView(save);
        l.addView(fetch);
        l.addView(check);
        l.addView(clearMem);

        setContentView(scroll);
    }

    private TextView hint(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.parseColor("#8B949E"));
        t.setTextSize(12);
        t.setPadding(0, 24, 0, 6);
        return t;
    }

    private EditText edit(String val, String ph, boolean multiline) {
        EditText e = new EditText(this);
        e.setText(val);
        e.setHint(ph);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.parseColor("#484F58"));
        if (multiline) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            e.setMinLines(3);
            e.setGravity(android.view.Gravity.TOP);
        }
        return e;
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density);
    }

    private TextView engineBtn(String label, boolean active) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        t.setClickable(true);
        t.setTextColor(active ? 0xFFECECEC : 0xFF8E8E8E);
        android.graphics.drawable.GradientDrawable bg =
            new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(active ? 0xFF1A2436 : 0xFF161616);
        bg.setStroke(dp(1), active ? 0xFF7CB3FF : 0xFF232323);
        t.setBackground(bg);
        return t;
    }
}
