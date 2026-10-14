package com.star.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;

public class TerminalActivity extends Activity {
    private static final String PREFS = "star_terminal";
    private static final String KEY_OUTPUT = "output_history";
    private static final int MAX_HISTORY_CHARS = 200_000;

    private TextView output;
    private TextView status;
    private ScrollView scroll;
    private volatile boolean running = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0D1117"));

        // top bar
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(Color.parseColor("#161B22"));
        top.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView title = new TextView(this);
        title.setText("Terminal");
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#ECECEC"));
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(title);

        TextView copyBtn = pillButton("Copy", 0xFF7CB3FF);
        copyBtn.setOnClickListener(v -> copyAll());
        top.addView(copyBtn);

        TextView shareBtn = pillButton("Share", 0xFF7EE787);
        shareBtn.setOnClickListener(v -> shareAll());
        top.addView(shareBtn);

        TextView clearBtn = pillButton("Clear", 0xFF8E8E8E);
        clearBtn.setOnClickListener(v -> {
            output.setText("");
            saveHistory("");
            toast("Cleared");
        });
        top.addView(clearBtn);
        root.addView(top);

        // status bar
        status = new TextView(this);
        status.setTextSize(11);
        status.setTypeface(Typeface.MONOSPACE);
        status.setTextColor(Color.parseColor("#8B949E"));
        status.setBackgroundColor(Color.parseColor("#0D1117"));
        status.setPadding(dp(14), dp(6), dp(14), dp(6));
        status.setText("idle");
        root.addView(status);

        // output
        scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        output = new TextView(this);
        output.setTextColor(Color.parseColor("#3FB950"));
        output.setTextSize(13);
        output.setTypeface(Typeface.MONOSPACE);
        output.setPadding(dp(14), dp(14), dp(14), dp(14));
        output.setTextIsSelectable(true);

        String saved = loadHistory();
        if (saved == null || saved.isEmpty()) {
            output.setText("Star Terminal\nType a command below.\nTry: ls /sdcard\n\n");
        } else {
            output.setText(saved);
            // scroll to bottom after layout
            scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        }
        scroll.addView(output);
        root.addView(scroll);

        // input row
        LinearLayout row = new LinearLayout(this);
        row.setBackgroundColor(Color.parseColor("#161B22"));
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setGravity(Gravity.CENTER_VERTICAL);

        EditText input = new EditText(this);
        input.setHint("$ command");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#8B949E"));
        input.setTypeface(Typeface.MONOSPACE);
        input.setBackground(null);
        input.setTextSize(14);
        input.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView run = new TextView(this);
        run.setText("\u21B5");
        run.setTextSize(22);
        run.setTextColor(0xFF7CB3FF);
        run.setGravity(Gravity.CENTER);
        run.setBackground(null);
        run.setPadding(dp(12), dp(8), dp(12), dp(8));
        run.setClickable(true);
        run.setOnClickListener(v -> {
            if (running) { toast("Command still running"); return; }
            String cmd = input.getText().toString().trim();
            if (cmd.isEmpty()) return;
            input.setText("");
            runCommand(cmd);
        });

        row.addView(input);
        LinearLayout.LayoutParams rw = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rw.leftMargin = dp(8);
        run.setLayoutParams(rw);
        row.addView(run);
        root.addView(row);

        setContentView(root);
    }

    @Override protected void onPause() {
        super.onPause();
        saveHistory(output.getText().toString());
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        saveHistory(output.getText().toString());
    }

    private void runCommand(final String cmd) {
        running = true;
        append("$ " + cmd + "\n");
        setStatus("running: " + shorten(cmd) + "  ...", 0xFFF0883E);

        final long t0 = System.currentTimeMillis();
        new Thread(() -> {
            Shell.Result r;
            try {
                r = Shell.run(cmd);
            } catch (Throwable t) {
                r = new Shell.Result("Exception: " + t.getMessage(), -1);
            }
            final Shell.Result fr = r;
            final long dt = System.currentTimeMillis() - t0;
            runOnUiThread(() -> {
                String out = fr.output == null ? "" : fr.output;
                if (!out.endsWith("\n")) out += "\n";
                append(out);

                String tag = fr.exitCode == 0
                    ? "exit 0  ·  " + dt + "ms"
                    : "exit " + fr.exitCode + "  ·  " + dt + "ms";
                int color = fr.exitCode == 0 ? 0xFF7EE787 : 0xFFF85149;
                setStatus(tag, color);

                running = false;
                saveHistory(output.getText().toString());
            });
        }).start();
    }

    private void setStatus(String text, int color) {
        status.setText(text);
        status.setTextColor(color);
    }

    private String shorten(String s) {
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }

    private TextView pillButton(String label, int color) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(7), dp(12), dp(7));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(0xFF0F1A26);
        bg.setStroke(dp(1), 0xFF2A4A6A);
        t.setBackground(bg);
        t.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    private void append(String s) {
        output.append(s);
        // Cap total size so we do not blow up prefs
        CharSequence cur = output.getText();
        if (cur != null && cur.length() > MAX_HISTORY_CHARS) {
            int keepFrom = cur.length() - MAX_HISTORY_CHARS + 10_000;
            output.setText("...(older output trimmed)...\n" +
                cur.subSequence(keepFrom, cur.length()));
        }
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private String loadHistory() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        return sp.getString(KEY_OUTPUT, "");
    }

    private void saveHistory(String s) {
        try {
            if (s == null) s = "";
            if (s.length() > MAX_HISTORY_CHARS) {
                s = s.substring(s.length() - MAX_HISTORY_CHARS);
            }
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            sp.edit().putString(KEY_OUTPUT, s).apply();
        } catch (Throwable ignored) {}
    }

    private void copyAll() {
        String text = output.getText().toString();
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Star Terminal", text));
        toast("Copied " + text.length() + " chars");
    }

    private void shareAll() {
        String text = output.getText().toString();
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, "Share terminal output"));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density);
    }
}
