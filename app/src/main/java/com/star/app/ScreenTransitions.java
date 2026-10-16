package com.star.app;

import android.app.Activity;

/**
 * G6 — screen transitions.
 *
 * All in-app activity slides come from the theme: StarTheme carries
 * android:windowAnimationStyle -> @style/StarWindowAnimation, which defines
 * activityOpenEnter/Exit (slide in from right) and activityCloseEnter/Exit
 * (slide back out to the right on back-press). Every internal
 * startActivity therefore animates with zero call-site code.
 *
 * This helper covers the one case the theme can't: launches that leave the
 * app (system Settings, ACTION_MANAGE_OVERLAY_PERMISSION, file choosers,
 * APK install intents). There the target activity has no slide animation,
 * so Star's outgoing activity would slide out against a mismatched
 * non-sliding window. Calling external(this) right after those
 * startActivity calls pins both sides to no animation.
 *
 * minSdk 30: overridePendingTransition is API 5.
 */
public final class ScreenTransitions {

    private ScreenTransitions() {}

    /** Call immediately after a startActivity that targets outside the app. */
    public static void external(Activity a) {
        try {
            a.overridePendingTransition(0, 0);
        } catch (Throwable ignored) {
            // Never let a transition cosmetic break a launch.
        }
    }

    /**
     * Optional explicit hook for an internal launch when the theme is not
     * desired (e.g. a dialog-hosted navigation). Equivalent to what the
     * theme already does; kept for call sites that opt out of the theme.
     */
    public static void slideIn(Activity a) {
        try {
            a.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        } catch (Throwable ignored) {
        }
    }
}
