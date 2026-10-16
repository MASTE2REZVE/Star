package com.star.app;

import android.app.AlertDialog;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

/**
 * G7 — bottom-sheet motion for every dialog in the app.
 *
 * Enter: slide up 10% of window height + fade in (260ms decelerate).
 * Exit:  slide down 8% + fade out (200ms accelerate).
 *
 * Two call styles:
 *   builder chains that end in .show():
 *       SheetAnimator.show(new AlertDialog.Builder(this)
 *           .setTitle(.....setNegativeButton("Cancel", null));
 *   dialogs kept in a ref (dialogRef / out.dialog):
 *       SheetAnimator.install(dialogRef[0]);   // between create() and show()
 *
 * Exit animation needs no call-site code: the window-animation style
 * drives dismiss() automatically at every site.
 */
public final class SheetAnimator {

    private SheetAnimator() {}

    /** Wrap a builder chain: create, install, show. Returns the dialog. */
    public static AlertDialog show(AlertDialog.Builder b) {
        final AlertDialog d = b.create();
        install(d);
        d.show();
        return d;
    }

    /** Point the dialog window at the slide+fade animation style.
     *  No-op if the window is gone; falls back to a decor-level
     *  enter animation if the style resource is missing. */
    public static void install(AlertDialog d) {
        if (d == null) return;
        Window w = d.getWindow();
        if (w == null) return;
        try {
            w.getAttributes().windowAnimations = R.style.StarDialogAnimation;
        } catch (Throwable ignored) {
            d.setOnShowListener(dg -> {
                Animation a = AnimationUtils.loadAnimation(
                        dg instanceof android.content.Context
                                ? (android.content.Context) dg : d.getContext(),
                        R.anim.sheet_enter);
                if (a != null) w.getDecorView().startAnimation(a);
            });
        }
    }
}
