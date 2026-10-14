package com.star.app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

public class Markdown {
    private static final int CODE_BG = 0xFF141414;
    private static final int CODE_FG = 0xFFE2B36B;
    private static final int INLINE_BG = 0xFF1E1E1E;
    private static final int INLINE_FG = 0xFFA5D6FF;

    public static CharSequence render(String text) {
        if (text == null) return "";
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] parts = text.split("```", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i % 2 == 1) {
                String code = parts[i];
                int nl = code.indexOf('\n');
                if (nl > 0 && !code.substring(0, nl).contains(" ")) {
                    code = code.substring(nl + 1);
                }
                if (out.length() > 0) out.append("\n");
                int start = out.length();
                out.append(code);
                int end = out.length();
                out.setSpan(new TypefaceSpan("monospace"), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new BackgroundColorSpan(CODE_BG),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new ForegroundColorSpan(CODE_FG),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.append("\n");
            } else {
                appendInline(out, parts[i]);
            }
        }
        return out;
    }

    private static void appendInline(SpannableStringBuilder out, String text) {
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '`') {
                int end = text.indexOf('`', i + 1);
                if (end > 0) {
                    int start = out.length();
                    out.append(text, i + 1, end);
                    int stop = out.length();
                    out.setSpan(new TypefaceSpan("monospace"), start, stop,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new BackgroundColorSpan(INLINE_BG),
                        start, stop, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new ForegroundColorSpan(INLINE_FG),
                        start, stop, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 1;
                    continue;
                }
            } else if (c == '*' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                int end = text.indexOf("**", i + 2);
                if (end > 0) {
                    int start = out.length();
                    out.append(text, i + 2, end);
                    int stop = out.length();
                    out.setSpan(new StyleSpan(Typeface.BOLD), start, stop,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 2;
                    continue;
                }
            } else if (c == '*' || c == '_') {
                int end = text.indexOf(c, i + 1);
                if (end > 0) {
                    int start = out.length();
                    out.append(text, i + 1, end);
                    int stop = out.length();
                    out.setSpan(new StyleSpan(Typeface.ITALIC), start, stop,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 1;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
    }
}
