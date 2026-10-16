package com.star.app;

// G5 patch — shared button-press feedback for every clickable in the app.
// G1 finding F5: "All buttons are TextView/FrameLayout with a static
// GradientDrawable. setClickable(true) + listener, no ripple, no scale/alpha
// animation on press. The app feels unresponsive on tap."
//
// PressFeedback gives every button the same tactile response: on touch-down
// the view scales to 0.96x and fades to alpha 0.70 over 100ms; on touch-up
// or touch-cancel it returns to its pre-press state over 100ms. The pre-press
// state (scaleX/scaleY/alpha) is snapshotted at touch-down, so views that
// carry their own scale — Chat's micBtn 1.15x listening pulse
// (ChatActivity.setMicActive, line 930) — are restored to exactly where they
// were instead of being snapped back to 1.0.
//
// The listener never consumes events (returns false, or the chained
// listener's verdict when one is supplied), so existing OnClickListeners
// fire untouched.
//
// Integration:
//   Static tree built in onCreate:
//        PressFeedback.attachAll(root);          // recursive, one call
//   Views created after onCreate (adapter getView rows, chips, dialogs
//   opened later) are not in the tree yet:
//        PressFeedback.attach(row);              // after setClickable(true)
//   Views with their OWN OnTouchListener (Chat drawer rows, studio gesture
//   rows, FloatingService bubble/panel): Android has no public getter for
//   an installed touch listener, so attachAll would silently replace it.
//   At those sites set the sentinel tag first (skips attachAll) and either
//   keep the existing feedback as-is, or chain explicitly:
//        row.setTag(PressFeedback.KEEP);         // attachAll skips this view
//        PressFeedback.attach(row, myListener);  // bg-swap AND scale compose
//   ...or call press(row) / release(row) from the listener's DOWN / UP
//   branches for manual control.
//
// javac-verified against ~/android-sdk/android.jar.

import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.EditText;

class PressFeedback {

    /** Press animation spec (G5 deliverable: scale + alpha, 100ms). */
    public static final long  DURATION_MS = 100;
    public static final float PRESS_SCALE = 0.96f;
    public static final float PRESS_ALPHA = 0.70f;

    /**
     * Sentinel tag: attachAll() skips any view whose getTag() is this object,
     * for views that manage their own touch handling. Views that use
     * setTag() for data are unaffected — only an exact reference match skips.
     */
    public static final Object KEEP = new Object();

    private PressFeedback() {}

    /** Attach to one clickable view. Safe to call twice (re-installs clean). */
    public static void attach(View v) {
        attach(v, null);
    }

    /**
     * Attach, chaining an existing OnTouchListener. The chained listener runs
     * first and its return value decides whether the event is consumed —
     * pass the listener you would otherwise hand to setOnTouchListener().
     */
    public static void attach(final View v, final OnTouchListener existing) {
        if (v == null || !v.isClickable() || v.getTag() == KEEP) return;
        v.setOnTouchListener(new OnTouchListener() {
            float s0 = 1f, t0 = 1f, a0 = 1f;   // pre-press snapshot
            @Override public boolean onTouch(View view, MotionEvent e) {
                boolean consumed = false;
                if (existing != null) consumed = existing.onTouch(view, e);
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        // Snapshot BEFORE animating so a mid-pulse view
                        // (mic 1.15x) restores to its live value, not 1.0.
                        s0 = view.getScaleX();
                        t0 = view.getScaleY();
                        a0 = view.getAlpha();
                        view.animate().cancel();
                        view.animate()
                            .scaleX(PRESS_SCALE).scaleY(PRESS_SCALE)
                            .alpha(PRESS_ALPHA).setDuration(DURATION_MS).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().cancel();
                        view.animate()
                            .scaleX(s0).scaleY(t0).alpha(a0)
                            .setDuration(DURATION_MS).start();
                        break;
                }
                return consumed;
            }
        });
    }

    /**
     * Recursively attach to every clickable view in the tree. Call once at
     * the end of onCreate. Skips EditText (touch must reach the keyboard,
     * not a press animation) and KEEP-tagged views. ListView/RecyclerView
     * children are whatever exists right now — adapter rows created later
     * need attach(row) in getView.
     */
    public static void attachAll(View root) {
        if (root == null) return;
        if (root.isClickable() && !(root instanceof EditText)
                && root.getTag() != KEEP) {
            attach(root);
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                attachAll(g.getChildAt(i));
            }
        }
    }

    /** Manual press hook for views with custom touch handling. */
    public static void press(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.animate().scaleX(PRESS_SCALE).scaleY(PRESS_SCALE)
            .alpha(PRESS_ALPHA).setDuration(DURATION_MS).start();
    }

    /** Manual release hook. Restores to unity (1.0 / 1.0). */
    public static void release(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(DURATION_MS).start();
    }
}
