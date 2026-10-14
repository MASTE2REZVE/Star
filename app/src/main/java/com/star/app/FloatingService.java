package com.star.app;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.widget.*;
import org.json.*;
import java.util.ArrayList;

public class FloatingService extends Service {
    private WindowManager wm;
    private FrameLayout bubbleWrap;
    private ImageView bubbleLogo;
    private View panel;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams panelParams;
    private WindowManager.LayoutParams popupParams;
    private LinearLayout messagesCol;
    private EditText input;
    private JSONArray history = new JSONArray();
    private Prefs prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Voice
    private SpeechRecognizer sr;
    private boolean listening = false;
    private TextView statusPopup;

    // Radial menu
    private FrameLayout radialOverlay;
    private BubbleFaceView eyeView;
    private int currentState = 0;
    private String lastScreenB64 = null;
    private boolean followUpActive = false;
    private View shutterView;
    private WindowManager.LayoutParams shutterParams;
    private final View[] radialSlots = new View[4];
    private final RadialIconView[] radialIcons = new RadialIconView[4];
    private final int[] radialSlotX = new int[4];
    private final int[] radialSlotY = new int[4];
    private int highlightedSlot = -1;
    private Runnable longPressRunnable;
    private boolean longPressFired = false;
    private static final long LONG_PRESS_MS = 450;
    private static final int SLOT_VOICE = 0;
    private static final int SLOT_LIVE = 1;
    private static final int SLOT_SCREEN = 2;
    private static final int SLOT_HIDE = 3;

    private long lastDragEnd = 0;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (bubbleWrap == null) {
            prefs = new Prefs(this);
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            // wire the Live service so it blinks the bubble before every capture
            LiveService.onBeforeCapture = () -> ui(this::blinkEye);
            LiveService.onListeningChange = listening -> ui(() -> {
                if (listening) setListeningFace(true);
                else setListeningFace(false);
            });
            try { createBubble(); }
            catch (Exception e) {
                Toast.makeText(this, "Bubble failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        return START_STICKY;
    }

    private void createBubble() {
        int size = dp(52);
        bubbleWrap = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xCC0F0F0F);
        bg.setStroke(dp(2), 0xFFFFB800);
        bubbleWrap.setBackground(bg);
        bubbleWrap.setElevation(dp(10));

        int ls = dp(28);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ls, ls);
        lp.gravity = Gravity.CENTER;

        bubbleLogo = new ImageView(this);
        bubbleLogo.setLayoutParams(lp);
        bubbleLogo.setImageResource(R.drawable.ic_star_logo);
        bubbleLogo.setColorFilter(0xFFFFB800);
        bubbleWrap.addView(bubbleLogo);

        eyeView = new BubbleFaceView(this);
        eyeView.setLayoutParams(lp);
        eyeView.setVisibility(View.GONE);
        eyeView.setAccent(0xFFFFB800);
        bubbleWrap.addView(eyeView);

        bubbleParams = new WindowManager.LayoutParams(
            size, size,
            Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 30;
        bubbleParams.y = 260;

        bubbleWrap.setOnTouchListener(new DragListener());

        wm.addView(bubbleWrap, bubbleParams);
        setState(0);
    }

    // ================= RADIAL MENU =================

    private void openRadial() {
        if (radialOverlay != null) return;
        stopBubbleSpin();

        int bsize = dp(52);
        int cx = bubbleParams.x + bsize / 2;
        int cy = bubbleParams.y + bsize / 2;
        int R = dp(108);
        int slotSize = dp(46);
        int labelH = dp(18);

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;

        int[][] offsets = { {0, -R}, {R, 0}, {0, R}, {-R, 0} };
        // glyphs replaced by RadialIconView
        String[] labels = { "Voice", "Live", "Screen", "Hide" };
        int[] colors = { 0xFF7CB3FF, 0xFF7EE787, 0xFFFFB800, 0xFFF85149 };

        radialOverlay = new FrameLayout(this);
        radialOverlay.setBackgroundColor(0x00000000);

        WindowManager.LayoutParams rp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        rp.gravity = Gravity.TOP | Gravity.START;

        for (int i = 0; i < 4; i++) {
            int sx = cx + offsets[i][0];
            int sy = cy + offsets[i][1];
            sx = Math.max(slotSize / 2 + dp(8),
                Math.min(screenW - slotSize / 2 - dp(8), sx));
            sy = Math.max(slotSize / 2 + dp(8),
                Math.min(screenH - slotSize / 2 - labelH - dp(8), sy));
            radialSlotX[i] = sx;
            radialSlotY[i] = sy;

            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);

            RadialIconView icon = new RadialIconView(this);
            icon.setType(i);
            icon.setAccent(colors[i]);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(slotSize, slotSize);
            icon.setLayoutParams(clp);
            cell.addView(icon);
            radialIcons[i] = icon;

            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(10);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setTextColor(colors[i]);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, labelH);
            label.setLayoutParams(llp);
            cell.addView(label);

            FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(
                slotSize, slotSize + labelH);
            fp.leftMargin = sx - slotSize / 2;
            fp.topMargin = sy - slotSize / 2;
            radialOverlay.addView(cell, fp);
            radialSlots[i] = cell;
        }

        wm.addView(radialOverlay, rp);
        bubbleWrap.startAnimation(new ScaleAnimation(1f, 1.05f, 1f, 1.05f,
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
            {{
                setDuration(180); setRepeatMode(Animation.REVERSE); setRepeatCount(Animation.INFINITE);
            }});
        radialOverlay.setAlpha(0f);
        radialOverlay.animate().alpha(1f).setDuration(160).start();
        for (View v : radialSlots) {
            v.setScaleX(0.7f); v.setScaleY(0.7f);
            v.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
        }
        // vibrate removed - tablet has no vibrator
    }

    private void highlightSlot(int idx) {
        if (highlightedSlot == idx) return;
        highlightedSlot = idx;
        for (int i = 0; i < 4; i++) {
            View cell = radialSlots[i];
            if (cell == null) continue;
            boolean on = (i == idx);
            cell.animate()
                .scaleX(on ? 1.15f : 1f)
                .scaleY(on ? 1.15f : 1f)
                .setDuration(120).start();
            cell.setAlpha(on ? 1f : (idx == -1 ? 1f : 0.6f));
            if (radialIcons[i] != null) radialIcons[i].setHighlighted(on);
        }
    }

    private void closeRadial() {
        if (radialOverlay == null) return;
        // restore spin
        if (bubbleWrap != null) {
            bubbleWrap.clearAnimation();
            setState(currentState);
        }
        final View v = radialOverlay;
        radialOverlay = null;
        highlightedSlot = -1;
        for (int i = 0; i < 4; i++) { radialSlots[i] = null; radialIcons[i] = null; }
        v.animate().alpha(0f).setDuration(140)
            .withEndAction(() -> {
                try { wm.removeView(v); } catch (Exception ignored) {}
            }).start();
    }

    private int slotUnder(int rx, int ry) {
        if (radialOverlay == null) return -1;
        int hit = dp(48);
        for (int i = 0; i < 4; i++) {
            int dx = rx - radialSlotX[i];
            int dy = ry - radialSlotY[i];
            if (dx * dx + dy * dy <= hit * hit) return i;
        }
        return -1;
    }

    private void fireSlot(int idx) {
        switch (idx) {
            case SLOT_VOICE:  startQuickListen(); break;
            case SLOT_LIVE:   toggleLive(); break;
            case SLOT_SCREEN: screenDescribeOnce(); break;
            case SLOT_HIDE:   hideBubble(); break;
        }
    }

        // vibrate removed - tablet has no vibrator

    private void hideBubble() {
        Toast.makeText(this, "Bubble hidden", Toast.LENGTH_SHORT).show();
        stopSelf();
    }

    private void toggleLive() {
        if (LiveService.running) {
            LiveService.stop(this);
            showStatusPopup("Live stopped", 0xFFF85149);
        } else {
            LiveService.start(this);
            showStatusPopup("Live started", 0xFF7EE787);
        }
        handler.postDelayed(this::dismissStatusPopup, 2000);
    }

    private void screenDescribeOnce() {
        blinkEye();
        showStatusPopup("Looking\u2026", 0xFF7CB3FF);
        new Thread(() -> {
            final String b64 = ScreenCapture.captureJpegBase64(
                getApplicationContext(), 1280, 90);
            if (b64 == null) {
                ui(() -> showStatusPopup("Capture failed", 0xFFF85149));
                handler.postDelayed(() -> ui(FloatingService.this::dismissStatusPopup), 2500);
                return;
            }
            lastScreenB64 = b64;
            AIClient.sendVision(this, prefs, b64, prefs.getLivePrompt(),
                prefs.getLiveModel(), new AIClient.Callback() {
                    @Override public void onReply(final String reply) {
                        ui(() -> {
                            showStatusPopup("\uD83D\uDCAC " + reply, 0xFF7EE787);
                            speakOnce(reply);
                        });
                        // after speaking, invite follow-up
                        handler.postDelayed(() -> ui(FloatingService.this::startScreenFollowUp), 3500);
                    }
                    @Override public void onReplyStats(String r, int i, int o, long ms) {}
                    @Override public void onToolCall(String n, String a) {}
                    @Override public void onError(String err) {
                        ui(() -> showStatusPopup("Error: " + err, 0xFFF85149));
                        handler.postDelayed(() -> ui(FloatingService.this::dismissStatusPopup), 3000);
                    }
                });
        }).start();
    }

    private void startScreenFollowUp() {
        if (lastScreenB64 == null) return;
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;

        followUpActive = true;
        ui(() -> {
            setState(1);
            showStatusPopup("Ask about the screen\u2026", 0xFF7CB3FF);
        });

        final SpeechRecognizer s2 = SpeechRecognizer.createSpeechRecognizer(this);
        s2.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int code) {
                try { s2.destroy(); } catch (Throwable ignored) {}
                followUpActive = false;
                ui(() -> setState(0));
                handler.postDelayed(() -> ui(FloatingService.this::dismissStatusPopup), 1200);
            }
            @Override public void onResults(Bundle results) {
                try { s2.destroy(); } catch (Throwable ignored) {}
                ArrayList<String> a = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
                if (a == null || a.isEmpty()) {
                    followUpActive = false;
                    ui(() -> setState(0));
                    handler.postDelayed(() -> ui(FloatingService.this::dismissStatusPopup), 1200);
                    return;
                }
                final String q = a.get(0).trim();
                ui(() -> showStatusPopup("\uD83D\uDDE3 " + q, 0xFF7CB3FF));
                askAboutScreen(q);
            }
            @Override public void onPartialResults(Bundle b) {
                ArrayList<String> a = b.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
                if (a != null && !a.isEmpty()) {
                    final String p = a.get(0);
                    ui(() -> showStatusPopup("\uD83D\uDDE3 " + p, 0xFF7CB3FF));
                }
            }
            @Override public void onEvent(int t, Bundle b) {}
        });

        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
        s2.startListening(i);
    }

    private void askAboutScreen(final String question) {
        if (lastScreenB64 == null) return;
        ui(() -> {
            setState(2);
            showStatusPopup("Thinking\u2026", 0xFF7EE787);
        });

        String prompt = "The user is looking at the screen and asks: \"" + question +
            "\". Answer briefly in 1-2 sentences about the CURRENT screen. " +
            "If they ask about a specific button, icon, or item, describe exactly where it is.";

        AIClient.sendVision(this, prefs, lastScreenB64, prompt,
            prefs.getLiveModel(), new AIClient.Callback() {
                @Override public void onReply(final String reply) {
                    ui(() -> {
                        showStatusPopup("\uD83D\uDCAC " + reply, 0xFF7EE787);
                        setState(0);
                        speakOnce(reply);
                    });
                    handler.postDelayed(() -> ui(FloatingService.this::startScreenFollowUp), 3800);
                }
                @Override public void onReplyStats(String r, int i, int o, long ms) {}
                @Override public void onToolCall(String n, String a) {}
                @Override public void onError(String err) {
                    ui(() -> {
                        showStatusPopup("Error: " + err, 0xFFF85149);
                        setState(0);
                    });
                    followUpActive = false;
                    handler.postDelayed(() -> ui(FloatingService.this::dismissStatusPopup), 3000);
                }
            });
    }


    private android.speech.tts.TextToSpeech bubbleTts;

    private void flashShutter() {
        try {
            if (shutterView == null) {
                shutterView = new View(this);
                shutterView.setBackgroundColor(0xFFFFFFFF);
                shutterParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
                shutterParams.gravity = Gravity.TOP | Gravity.START;
            }
            if (shutterView.getParent() == null) {
                wm.addView(shutterView, shutterParams);
            }
            shutterView.setAlpha(0f);
            shutterView.animate()
                .alpha(0.85f).setDuration(60)
                .withEndAction(() -> shutterView.animate()
                    .alpha(0f).setDuration(180)
                    .withEndAction(() -> {
                        try {
                            if (shutterView.getParent() != null) wm.removeView(shutterView);
                        } catch (Exception ignored) {}
                    }).start())
                .start();

            // also pulse the bubble
            if (bubbleWrap != null) {
                bubbleWrap.animate()
                    .scaleX(1.35f).scaleY(1.35f).setDuration(80)
                    .withEndAction(() -> bubbleWrap.animate()
                        .scaleX(1f).scaleY(1f).setDuration(160).start())
                    .start();
            }
        } catch (Exception ignored) {}
    }


    private void stopBubbleSpin() {
        try { if (bubbleLogo != null) bubbleLogo.clearAnimation(); } catch (Throwable ignored) {}
    }

    private float dragRotation = 0f;

    /** Add rotation based on how far the finger moved this frame. */
    private void rotateByDrag(float dist) {
        if (bubbleLogo == null) return;
        bubbleLogo.clearAnimation();
        // one full turn every ~260 dp of finger travel
        float deg = dist / (2.0f * getResources().getDisplayMetrics().density);
        dragRotation += deg;
        bubbleLogo.setRotation(dragRotation % 360f);
    }

    private void setListeningFace(boolean on) {
        if (bubbleLogo == null || eyeView == null) return;
        if (on) {
            stopBubbleSpin();
            eyeView.setMode(BubbleFaceView.LISTEN);
            eyeView.setAccent(0xFF7CB3FF);
            eyeView.setAlpha(0f);
            eyeView.setVisibility(View.VISIBLE);
            eyeView.animate().alpha(1f).setDuration(200).start();
            bubbleLogo.setVisibility(View.GONE);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(0xCC0A1A2A);
            bg.setStroke(dp(2), 0xFF7CB3FF);
            bubbleWrap.setBackground(bg);
        } else {
            eyeView.animate().alpha(0f).setDuration(150)
                .withEndAction(() -> {
                    if (eyeView == null || bubbleLogo == null) return;
                    eyeView.setVisibility(View.GONE);
                    bubbleLogo.setAlpha(0f);
                    bubbleLogo.setVisibility(View.VISIBLE);
                    bubbleLogo.animate().alpha(1f).setDuration(180).start();
                    setState(currentState);
                }).start();
        }
    }

    private void speakOnce(final String text) {
        try {
            if (bubbleTts != null) {
                try {
                    android.os.Bundle p = new android.os.Bundle();
                    p.putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
                    bubbleTts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, p, "one");
                    return;
                } catch (Throwable ignored) {}
            }
            bubbleTts = new android.speech.tts.TextToSpeech(this, status -> {
                if (status != android.speech.tts.TextToSpeech.SUCCESS) return;
                try {
                    if (Build.VERSION.SDK_INT >= 21) {
                        android.media.AudioAttributes a =
                            new android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build();
                        bubbleTts.setAudioAttributes(a);
                    }
                    android.os.Bundle p = new android.os.Bundle();
                    p.putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
                    bubbleTts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, p, "one");
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    // ================= GESTURE =================

    private class DragListener implements View.OnTouchListener {
        int startX, startY;
        float touchX, touchY;
        float lastMoveX, lastMoveY;
        boolean dragging;

        @Override public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    startX = bubbleParams.x;
                    startY = bubbleParams.y;
                    touchX = e.getRawX();
                    touchY = e.getRawY();
                    lastMoveX = e.getRawX();
                    lastMoveY = e.getRawY();
                    dragging = false;
                    longPressFired = false;
                    stopBubbleSpin();
                    longPressRunnable = () -> {
                        longPressFired = true;
                        openRadial();
                    };
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - touchX;
                    float dy = e.getRawY() - touchY;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);

                    if (longPressFired) {
                        int idx = slotUnder((int) e.getRawX(), (int) e.getRawY());
                        highlightSlot(idx);
                        return true;
                    }

                    if (!dragging && dist > dp(10)) {
                        if (longPressRunnable != null) {
                            handler.removeCallbacks(longPressRunnable);
                            longPressRunnable = null;
                        }
                        dragging = true;
                    }

                    if (dragging) {
                        // rotate by the delta from the last move, not from the start
                        float stepX = e.getRawX() - lastMoveX;
                        float stepY = e.getRawY() - lastMoveY;
                        lastMoveX = e.getRawX();
                        lastMoveY = e.getRawY();
                        float stepDist = (float) Math.sqrt(stepX * stepX + stepY * stepY);
                        rotateByDrag(stepDist);

                        bubbleParams.x = startX + (int) dx;
                        bubbleParams.y = startY + (int) dy;
                        try { wm.updateViewLayout(bubbleWrap, bubbleParams); }
                        catch (Exception ignored) {}
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    if (longPressRunnable != null) {
                        handler.removeCallbacks(longPressRunnable);
                        longPressRunnable = null;
                    }

                    if (longPressFired) {
                        int idx = slotUnder((int) e.getRawX(), (int) e.getRawY());
                        closeRadial();
                        longPressFired = false;
                        if (idx >= 0) {
                            lastDragEnd = System.currentTimeMillis();
                            fireSlot(idx);
                        }
                        return true;
                    }

                    if (dragging) {
                        lastDragEnd = System.currentTimeMillis();
                        dragging = false;
                        snapToEdge();
                        if (bubbleLogo != null) bubbleLogo.setRotation(0f);
                        dragRotation = 0f;
                        setState(currentState);
                        return true;
                    }

                    if (System.currentTimeMillis() - lastDragEnd < 250) return true;
                    togglePanel();
                    return true;
                }
            }
            return false;
        }
    }

    private void snapToEdge() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int bsize = dp(52);
        if (bubbleParams.x + bsize / 2 < screenW / 2) {
            bubbleParams.x = dp(8);
        } else {
            bubbleParams.x = screenW - bsize - dp(8);
        }
        try {
            wm.updateViewLayout(bubbleWrap, bubbleParams);
        } catch (Exception ignored) {}
    }

    // ================= VOICE (unchanged) =================

    private void startQuickListen() {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            // try to grant silently via Shizuku. Never open an activity.
            try {
                Shell.run("pm grant com.star.app android.permission.RECORD_AUDIO");
            } catch (Throwable ignored) {}
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                showStatusPopup("Mic permission needed. Open Star once.", 0xFFF0883E);
                handler.postDelayed(() -> ui(this::dismissStatusPopup), 3500);
                return;
            }
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showStatusPopup("Speech not available", 0xFFF85149);
            return;
        }

        listening = true;
        ui(() -> {
            setState(1);
            autoDismissEnabled = false;
            showStatusPopup("Listening\u2026", 0xFF7CB3FF);
        });

        if (sr == null) {
            sr = SpeechRecognizer.createSpeechRecognizer(this);
            sr.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle p) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float v) {}
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int code) {
                    listening = false;
                    final String msg;
                    switch (code) {
                        case SpeechRecognizer.ERROR_NO_MATCH:
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                            msg = "Didn't catch that"; break;
                        case SpeechRecognizer.ERROR_NETWORK:
                        case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                            msg = "Network error"; break;
                        case SpeechRecognizer.ERROR_AUDIO:
                            msg = "Mic error"; break;
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                            msg = "Mic permission needed"; break;
                        default:
                            msg = "Try again"; break;
                    }
                    ui(() -> {
                        setState(0);
                        showStatusPopup(msg, 0xFFF0883E);
                    });
                }
                @Override public void onResults(Bundle results) {
                    listening = false;
                    ArrayList<String> a = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                    if (a == null || a.isEmpty()) {
                        ui(() -> {
                            setState(0);
                            showStatusPopup("Didn't catch that", 0xFFF0883E);
                        });
                        return;
                    }
                    final String said = a.get(0).trim();
                    autoDismissEnabled = true;
                    ui(() -> showStatusPopup("\uD83D\uDDE3 " + said, 0xFF7CB3FF));
                    handler.postDelayed(() -> runVoiceCommand(said), 400);
                }
                @Override public void onPartialResults(Bundle b) {
                    ArrayList<String> a = b.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                    if (a != null && !a.isEmpty()) {
                        final String p = a.get(0);
                        ui(() -> showStatusPopup("\uD83D\uDDE3 " + p, 0xFF7CB3FF));
                    }
                }
                @Override public void onEvent(int t, Bundle b) {}
            });
        }

        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
        sr.startListening(i);
    }

    private void runVoiceCommand(String cmd) {
        ui(() -> setState(2));
        ui(() -> showStatusPopup("\u26A1 Working\u2026", 0xFF7EE787));

        try {
            JSONObject m = new JSONObject();
            m.put("role", "user");
            m.put("content", cmd);
            history.put(m);
        } catch (Exception ignored) {}

        AIClient.send(this, prefs, history, new AIClient.Callback() {
            @Override public void onReply(String r) { onVoiceReply(r); }
            @Override public void onReplyStats(String r, int a, int b, long c) { onVoiceReply(r); }
            @Override public void onToolCall(String name, String args) {
                ui(() -> setState(2));
                ui(() -> showStatusPopup("\u26A1 " + name, 0xFF7EE787));
                new Thread(() -> {
                    String r = "ok";
                    try {
                        if (name.startsWith("ui_")) {
                            JSONObject a = new JSONObject(args);
                            switch (name) {
                                case "ui_seq": r = StarAccessibilityService.runSequence(a.getString("seq")); break;
                                case "ui_find": r = StarAccessibilityService.findAndTap(a.getString("text")); break;
                                case "ui_type": r = StarAccessibilityService.typeText(a.getString("text")); break;
                                case "ui_press": r = StarAccessibilityService.pressKey(a.getString("key")); break;
                                case "ui_dump": r = StarAccessibilityService.dump(); break;
                                case "ui_scroll": r = StarAccessibilityService.scroll(a.getString("direction")); break;
                                case "ui_tap": r = StarAccessibilityService.tap(a.getInt("x"), a.getInt("y")) ? "ok" : "fail"; break;
                            }
                        } else {
                            try {
                                String c = new JSONObject(args).getString("command");
                                r = Shell.run(c).output;
                            } catch (Exception ignored) {}
                        }
                        JSONObject am = new JSONObject();
                        am.put("role", "assistant"); am.put("content", JSONObject.NULL);
                        JSONArray tcs = new JSONArray();
                        JSONObject tc = new JSONObject();
                        tc.put("id", "c1"); tc.put("type", "function");
                        JSONObject fn = new JSONObject();
                        fn.put("name", name); fn.put("arguments", args);
                        tc.put("function", fn);
                        tcs.put(tc); am.put("tool_calls", tcs);
                        history.put(am);
                        JSONObject tm = new JSONObject();
                        tm.put("role", "tool");
                        tm.put("tool_call_id", "c1");
                        tm.put("content", AIClient.sanitize(r));
                        history.put(tm);
                    } catch (Exception ignored) {}
                    voiceFollowup();
                }).start();
            }
            @Override public void onError(String e) {
                final String msg = e == null ? "Error" : e;
                ui(() -> {
                    setState(3);
                    showStatusPopup("Error: " + msg, 0xFFF85149);
                });
                handler.postDelayed(() -> ui(() -> setState(0)), 3500);
            }
        });
    }

    private void voiceFollowup() {
        ui(() -> setState(1));
        AIClient.send(this, prefs, history, new AIClient.Callback() {
            @Override public void onReply(String r) { onVoiceReply(r); }
            @Override public void onReplyStats(String r, int a, int b, long c) { onVoiceReply(r); }
            @Override public void onToolCall(String n, String a) { runVoiceCommand(""); }
            @Override public void onError(String e) {
                ui(() -> {
                    setState(3);
                    showStatusPopup("Error", 0xFFF85149);
                });
                handler.postDelayed(() -> ui(() -> setState(0)), 3000);
            }
        });
    }

    private void onVoiceReply(String reply) {
        if (reply == null || reply.trim().isEmpty() || reply.equals("null")) {
            ui(() -> setState(0));
            handler.postDelayed(() -> ui(this::dismissStatusPopup), 1500);
            return;
        }
        try {
            JSONObject a = new JSONObject();
            a.put("role", "assistant"); a.put("content", reply);
            history.put(a);
        } catch (Exception ignored) {}
        final String short_ = reply.length() > 120 ? reply.substring(0, 120) + "\u2026" : reply;
        ui(() -> {
            showStatusPopup("\uD83D\uDCAC " + short_, 0xFF7EE787);
            setState(0);
        });
        handler.postDelayed(() -> ui(this::dismissStatusPopup), 4000);
    }

    // ================= STATUS POPUP =================

    private void showStatusPopup(String text, int color) {
        if (statusPopup == null) {
            statusPopup = new TextView(this);
            statusPopup.setTextSize(13);
            statusPopup.setTextColor(Color.WHITE);
            statusPopup.setPadding(dp(14), dp(10), dp(14), dp(10));
            statusPopup.setMaxWidth(dp(240));
            popupParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
            popupParams.gravity = Gravity.TOP | Gravity.START;
            // swipe the popup away to dismiss
            statusPopup.setOnTouchListener(new View.OnTouchListener() {
                float downX, downY;
                boolean moved;
                @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            downX = e.getRawX();
                            downY = e.getRawY();
                            moved = false;
                            return true;
                        case android.view.MotionEvent.ACTION_MOVE:
                            float dx = Math.abs(e.getRawX() - downX);
                            float dy = Math.abs(e.getRawY() - downY);
                            if (dx > dp(12) || dy > dp(12)) moved = true;
                            if (moved) v.setAlpha(0.5f);
                            return true;
                        case android.view.MotionEvent.ACTION_UP:
                        case android.view.MotionEvent.ACTION_CANCEL:
                            v.setAlpha(1f);
                            if (moved) {
                                dismissStatusPopup();
                                return true;
                            }
                            return false;
                    }
                    return false;
                }
            });
        }
        statusPopup.setText(text);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(0xF0101010);
        bg.setStroke(dp(1), color);
        statusPopup.setBackground(bg);
        statusPopup.setTextColor(color);

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int est = Math.min(dp(260), Math.max(dp(120), text.length() * dp(8)));
        popupParams.x = Math.min(bubbleParams.x, screenW - est - dp(10));
        popupParams.y = bubbleParams.y + dp(62);

        try {
            if (statusPopup.getParent() == null) wm.addView(statusPopup, popupParams);
            else wm.updateViewLayout(statusPopup, popupParams);
        } catch (Exception ignored) {}
        // auto-dismiss info popups after 7s unless they are actively listening/thinking
        handler.removeCallbacks(autoDismissRunnable);
        if (autoDismissEnabled) {
            handler.postDelayed(autoDismissRunnable, 7000L);
        }
    }

    private boolean autoDismissEnabled = true;
    private final Runnable autoDismissRunnable = () -> {
        try { dismissStatusPopup(); } catch (Throwable ignored) {}
    };

    private void dismissStatusPopup() {
        if (statusPopup != null && statusPopup.getParent() != null) {
            try { wm.removeView(statusPopup); } catch (Exception ignored) {}
        }
        statusPopup = null;
    }

    // ================= STATE =================

    private void setState(int state) {
        currentState = state;
        if (bubbleWrap == null || bubbleLogo == null) return;
        bubbleLogo.clearAnimation();
        bubbleWrap.clearAnimation();

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        int strokeColor, tint;
        long spinMs;

        switch (state) {
            case 1:
                bg.setColor(0xCC0A1A2A);
                strokeColor = 0xFF7CB3FF; tint = 0xFF7CB3FF; spinMs = 1800; break;
            case 2:
                bg.setColor(0xCC0A1F0A);
                strokeColor = 0xFF7EE787; tint = 0xFF7EE787; spinMs = 700; break;
            case 3:
                bg.setColor(0xCC2A0A0A);
                strokeColor = 0xFFF85149; tint = 0xFFF85149; spinMs = 0; break;
            default:
                bg.setColor(0xCC0F0F0F);
                strokeColor = 0xFFFFB800; tint = 0xFFFFB800; spinMs = 6000;
        }

        bg.setStroke(dp(2), strokeColor);
        bubbleWrap.setBackground(bg);

        // show star, hide eye (unless currently blinking)
        bubbleLogo.setVisibility(View.VISIBLE);
        eyeView.setVisibility(View.GONE);
        bubbleLogo.setColorFilter(tint);

        if (spinMs > 0) {
            RotateAnimation spin = new RotateAnimation(0, 360,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
            spin.setDuration(spinMs);
            spin.setRepeatCount(Animation.INFINITE);
            spin.setInterpolator(new LinearInterpolator());
            bubbleLogo.startAnimation(spin);
        }

        long pulseMs = state == 2 ? 400 : state == 1 ? 700 : 1400;
        ScaleAnimation pulse = new ScaleAnimation(1f, 1.08f, 1f, 1.08f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f);
        pulse.setDuration(pulseMs);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        bubbleWrap.startAnimation(pulse);
    }

    /** Blink the eye in the current state color, then restore the star. */
    private void blinkEye() {
        if (bubbleLogo == null || eyeView == null) return;
        int accent = 0xFFFFB800;
        if (currentState == 1) accent = 0xFF7CB3FF;
        else if (currentState == 2) accent = 0xFF7EE787;
        else if (currentState == 3) accent = 0xFFF85149;

        stopBubbleSpin();
        eyeView.setMode(BubbleFaceView.EYE);
        eyeView.setAccent(accent);
        eyeView.setBlink(0f);
        eyeView.setAlpha(1f);
        eyeView.setVisibility(View.VISIBLE);
        bubbleLogo.setVisibility(View.GONE);

        // hold the eye wide open for a visible moment, then blink, then fade back
        android.animation.ValueAnimator va = android.animation.ValueAnimator
            .ofFloat(0f, 1f, 0f, 1f, 0f);
        va.setDuration(320);
        va.setStartDelay(280);
        va.addUpdateListener(a -> eyeView.setBlink((Float) a.getAnimatedValue()));
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                eyeView.postDelayed(() -> {
                    if (eyeView == null || bubbleLogo == null) return;
                    eyeView.animate().alpha(0f).setDuration(160)
                        .withEndAction(() -> {
                            if (eyeView == null || bubbleLogo == null) return;
                            eyeView.setVisibility(View.GONE);
                            bubbleLogo.setAlpha(0f);
                            bubbleLogo.setVisibility(View.VISIBLE);
                            bubbleLogo.animate().alpha(1f).setDuration(200).start();
                            setState(currentState);
                        }).start();
                }, 220);
            }
        });
        va.start();
    }

    // ================= CHAT PANEL =================

    private void togglePanel() {
        if (panel != null) { closePanel(); return; }
        showPanel();
    }

    private void showPanel() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(0xF00F0F0F);
        bg.setStroke(dp(1), 0xFF2A2A2A);
        root.setBackground(bg);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Star \u00B7 tap to type");
        title.setTextSize(11);
        title.setTextColor(0xFF8E8E8E);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);

        TextView close = new TextView(this);
        close.setText("\u2715");
        close.setTextSize(16);
        close.setTextColor(0xFF8E8E8E);
        close.setPadding(dp(8), dp(4), dp(8), dp(4));
        close.setClickable(true);
        close.setOnClickListener(v -> closePanel());
        header.addView(close);
        root.addView(header);

        ScrollView sv = new ScrollView(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = dp(8);
        sv.setLayoutParams(slp);
        messagesCol = new LinearLayout(this);
        messagesCol.setOrientation(LinearLayout.VERTICAL);
        sv.addView(messagesCol);
        root.addView(sv);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);

        input = new EditText(this);
        input.setHint("Type\u2026");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF8E8E8E);
        input.setBackground(null);
        input.setTextSize(14);
        input.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(input);

        TextView sendBtn = new TextView(this);
        sendBtn.setText("\u2191");
        sendBtn.setTextColor(Color.WHITE);
        sendBtn.setTextSize(18);
        sendBtn.setGravity(Gravity.CENTER);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setShape(GradientDrawable.OVAL);
        sbg.setColor(0xFF7CB3FF);
        sendBtn.setBackground(sbg);
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(dp(40), dp(40));
        sendBtn.setLayoutParams(sblp);
        sendBtn.setClickable(true);
        sendBtn.setOnClickListener(v -> sendQuery());
        row.addView(sendBtn);
        root.addView(row);

        int w = (int)(getResources().getDisplayMetrics().widthPixels * 0.78);
        int h = (int)(getResources().getDisplayMetrics().heightPixels * 0.42);
        panelParams = new WindowManager.LayoutParams(
            w, h,
            Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = Math.max(20, bubbleParams.x - w + dp(60));
        panelParams.y = Math.min(bubbleParams.y + dp(60),
            getResources().getDisplayMetrics().heightPixels - h - 40);

        panel = root;
        wm.addView(panel, panelParams);
    }

    private void closePanel() {
        if (panel != null) {
            try { wm.removeView(panel); } catch (Exception ignored) {}
            panel = null; input = null; messagesCol = null;
        }
    }

    private void addMsg(String who, String text, int color) {
        if (messagesCol == null) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView w = new TextView(this);
        w.setText(who); w.setTextSize(10);
        w.setTypeface(Typeface.DEFAULT_BOLD); w.setTextColor(color);
        row.addView(w);
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(13); t.setTextColor(0xFFECECEC);
        row.addView(t);
        messagesCol.addView(row);
    }

    private void sendQuery() {
        if (input == null) return;
        String q = input.getText().toString().trim();
        if (q.isEmpty()) return;
        input.setText("");
        addMsg("You", q, 0xFF7CB3FF);
        try {
            JSONObject m = new JSONObject();
            m.put("role", "user"); m.put("content", q);
            history.put(m);
        } catch (Exception ignored) {}

        ui(() -> setState(1));
        AIClient.send(this, prefs, history, new AIClient.Callback() {
            @Override public void onReply(String r) { panelUpdate(r); ui(() -> setState(0)); }
            @Override public void onReplyStats(String r, int a, int b, long c) { panelUpdate(r); ui(() -> setState(0)); }
            @Override public void onToolCall(String name, String args) {
                setState(2);
                panelUpdate("\u2192 " + name);
                new Thread(() -> {
                    String r = "ok";
                    try {
                        if (name.startsWith("ui_")) {
                            JSONObject a = new JSONObject(args);
                            switch (name) {
                                case "ui_seq": r = StarAccessibilityService.runSequence(a.getString("seq")); break;
                                case "ui_find": r = StarAccessibilityService.findAndTap(a.getString("text")); break;
                                case "ui_type": r = StarAccessibilityService.typeText(a.getString("text")); break;
                                case "ui_press": r = StarAccessibilityService.pressKey(a.getString("key")); break;
                                case "ui_dump": r = StarAccessibilityService.dump(); break;
                                case "ui_scroll": r = StarAccessibilityService.scroll(a.getString("direction")); break;
                                case "ui_tap": r = StarAccessibilityService.tap(a.getInt("x"), a.getInt("y")) ? "ok" : "fail"; break;
                            }
                        } else {
                            try {
                                String cmd = new JSONObject(args).getString("command");
                                r = Shell.run(cmd).output;
                            } catch (Exception ignored) {}
                        }
                        JSONObject am = new JSONObject();
                        am.put("role", "assistant"); am.put("content", JSONObject.NULL);
                        JSONArray tcs = new JSONArray();
                        JSONObject tc = new JSONObject();
                        tc.put("id", "c1"); tc.put("type", "function");
                        JSONObject fn = new JSONObject();
                        fn.put("name", name); fn.put("arguments", args);
                        tc.put("function", fn);
                        tcs.put(tc); am.put("tool_calls", tcs);
                        history.put(am);
                        JSONObject tm = new JSONObject();
                        tm.put("role", "tool");
                        tm.put("tool_call_id", "c1");
                        tm.put("content", AIClient.sanitize(r));
                        history.put(tm);
                    } catch (Exception ignored) {}
                    sendFollowup();
                }).start();
            }
            @Override public void onError(String e) { panelUpdate("Error: " + e); ui(() -> setState(3)); }
        });
    }

    private void sendFollowup() {
        ui(() -> setState(1));
        AIClient.send(this, prefs, history, new AIClient.Callback() {
            @Override public void onReply(String r) { panelUpdate(r); setState(0); }
            @Override public void onReplyStats(String r, int a, int b, long c) { panelUpdate(r); setState(0); }
            @Override public void onToolCall(String n, String a) { sendFollowup(); }
            @Override public void onError(String e) { panelUpdate("Error: " + e); ui(() -> setState(3)); }
        });
    }

    private void panelUpdate(String reply) {
        if (messagesCol == null) return;
        new Handler(getMainLooper()).post(() -> {
            addMsg("Star", reply, 0xFF7EE787);
            try {
                JSONObject a = new JSONObject();
                a.put("role", "assistant"); a.put("content", reply);
                history.put(a);
            } catch (Exception ignored) {}
        });
    }

    @Override public void onDestroy() {
        super.onDestroy();
        if (LiveService.onBeforeCapture != null) LiveService.onBeforeCapture = null;
        LiveService.onListeningChange = null;
        if (sr != null) { sr.destroy(); sr = null; }
        closeRadial();
        try { if (panel != null) wm.removeView(panel); } catch (Exception ignored) {}
        try { if (bubbleWrap != null) wm.removeView(bubbleWrap); } catch (Exception ignored) {}
        try { if (statusPopup != null && statusPopup.getParent() != null) wm.removeView(statusPopup); } catch (Exception ignored) {}
        eyeView = null;
        bubbleLogo = null;
        try { if (shutterView != null && shutterView.getParent() != null) wm.removeView(shutterView); } catch (Exception ignored) {}
        try { if (shutterView != null && shutterView.getParent() != null) wm.removeView(shutterView); } catch (Exception ignored) {}
        panel = null;
        bubbleWrap = null;
        statusPopup = null;
    }

    private void ui(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else handler.post(r);
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
