package com.star.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

/** Read the system clipboard. Android 12+ requires focus, so call
 *  this from the foreground (FloatingService brings itself to front briefly). */
public class ClipboardReader {
    public static String read(Context ctx) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return "";
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return "";
            CharSequence cs = clip.getItemAt(0).coerceToText(ctx);
            return cs == null ? "" : cs.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
