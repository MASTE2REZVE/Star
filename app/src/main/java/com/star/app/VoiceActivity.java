package com.star.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.*;
import org.json.*;
import java.util.ArrayList;
import java.util.Locale;

public class VoiceActivity extends Activity {
    private static final int BG = 0xFF0A0A0A;
    private static final int CARD = 0xFF181818;
    private static final int BORDER = 0xFF262626;
    private static final int TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF8E8E8E;
    private static final int ACCENT = 0xFF7CB3FF;
    private static final int GOLD = 0xFFFFB800;
    private static final int GREEN = 0xFF7EE787;

    private Prefs prefs;
    private JSONArray history = new JSONArray();
    private TextView orb, statusText, transcript;
    private LinearLayout log;
    private FrameLayout orbWrap;
    private SpeechRecognizer sr;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean listening = false;
    private boolean speaking = false;
    private boolean thinking = false;
    private boolean active = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ScaleAnimation pulse;
    private String lastPartial = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        // Top bar
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(16), dp(16), dp(8));
        top.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextSize(22);
        close.setTextColor(TEXT);
        close.setPadding(dp(10), dp(6), dp(10), dp(6));
        close.setClickable(true);
        close.setOnClickListener(v -> finish());
        top.addView(close);

        TextView title = new TextView(this);
        title.setText("Voice Mode");
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        title.setGravity(Gravity.CENTER);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(title);

        TextView spacer = new TextView(this);
        spacer.setText("   ");
        spacer.setTextSize(22);
        top.addView(spacer);
        root.addView(top);

        // Orb area
        orbWrap = new FrameLayout(this);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        orbWrap.setLayoutParams(olp);

        // Glow ring behind orb


        // Outer soft ring
        View ring1 = new View(this);
        int r1s = dp(280);
        FrameLayout.LayoutParams r1p = new FrameLayout.LayoutParams(r1s, r1s);
        r1p.gravity = Gravity.CENTER;
        ring1.setLayoutParams(r1p);
        GradientDrawable r1bg = new GradientDrawable();
        r1bg.setShape(GradientDrawable.OVAL);
        r1bg.setColor(0x0AFFFFFF);
        ring1.setBackground(r1bg);
        orbWrap.addView(ring1);

        // Middle ring
        View ring2 = new View(this);
        int r2s = dp(220);
        FrameLayout.LayoutParams r2p = new FrameLayout.LayoutParams(r2s, r2s);
        r2p.gravity = Gravity.CENTER;
        ring2.setLayoutParams(r2p);
        GradientDrawable r2bg = new GradientDrawable();
        r2bg.setShape(GradientDrawable.OVAL);
        r2bg.setColor(0x14FFFFFF);
        ring2.setBackground(r2bg);
        orbWrap.addView(ring2);

        // The main orb — custom hex logo on gold-gradient circle
        LinearLayout orbCircle = new LinearLayout(this);
        int os = dp(170);
        FrameLayout.LayoutParams orbp = new FrameLayout.LayoutParams(os, os);
        orbp.gravity = Gravity.CENTER;
        orbCircle.setLayoutParams(orbp);
        orbCircle.setGravity(Gravity.CENTER);
        orbCircle.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable obg = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{0xFF1B1310, 0xFF22160A});
        obg.setShape(GradientDrawable.OVAL);
        obg.setStroke(dp(2), 0x66FFB800);
        orbCircle.setBackground(obg);
        orbCircle.setClickable(true);
        orbCircle.setOnClickListener(v -> toggleListening());

        // Logo inside
        ImageView logo = new ImageView(this);
        int ls = dp(84);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(ls, ls);
        logoLp.gravity = Gravity.CENTER;
        logo.setLayoutParams(logoLp);
        logo.setImageResource(R.drawable.ic_star_logo);
        orbCircle.addView(logo);

        // Small color hints below logo
        TextView hint = new TextView(this);
        hint.setText("TAP TO SPEAK");
        hint.setTextSize(9);
        hint.setLetterSpacing(0.2f);
        hint.setTextColor(0xAAFFB800);
        hint.setTypeface(Typeface.DEFAULT_BOLD);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(6), 0, 0);
        orbCircle.addView(hint);

        orbWrap.addView(orbCircle);

        // Keep the old TextView field for the pulse / text but keep it hidden
        orb = new TextView(this);
        orb.setVisibility(View.GONE);
        orbWrap.addView(orb);

        // Store ref so pulse uses the circle
        orbCircle.setTag("orbCircle");
        orb.setTag(orbCircle);

        root.addView(orbWrap);

        // Status text
        statusText = new TextView(this);
        statusText.setText("Tap the orb to speak");
        statusText.setTextSize(14);
        statusText.setTextColor(MUTED);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(dp(24), dp(8), dp(24), dp(8));
        root.addView(statusText);

        // Live transcript
        transcript = new TextView(this);
        transcript.setTextSize(20);
        transcript.setTextColor(TEXT);
        transcript.setGravity(Gravity.CENTER);
        transcript.setPadding(dp(24), dp(8), dp(24), dp(8));
        transcript.setMinLines(2);
        transcript.setMaxLines(3);
        root.addView(transcript);

        // Mini log (last 3 messages)
        log = new LinearLayout(this);
        log.setOrientation(LinearLayout.VERTICAL);
        log.setPadding(dp(16), dp(16), dp(16), dp(24));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        log.setLayoutParams(llp);
        root.addView(log);

        setContentView(root);

        // Setup TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                tts.setSpeechRate(1.0f);
                tts.setPitch(1.0f);
                ttsReady = true;
            }
        });

        // Setup SpeechRecognizer
        sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) {
                statusText.setText("Listening…");
                startPulse();
            }
            @Override public void onBeginningOfSpeech() {
                transcript.setText("");
            }
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int code) {
                listening = false;
                stopPulse();
                String msg;
                switch (code) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        msg = "Didn't catch that — tap orb"; break;
                    case SpeechRecognizer.ERROR_NETWORK:
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        msg = "Network error"; break;
                    case SpeechRecognizer.ERROR_AUDIO:
                        msg = "Mic error"; break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        msg = "Busy — wait a moment"; break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                        msg = "Mic permission needed"; break;
                    default:
                        msg = "Error " + code + " — tap orb";
                }
                statusText.setText(msg);
                orb.setTextColor(GOLD);
            }
            @Override public void onResults(Bundle results) {
                listening = false;
                stopPulse();
                ArrayList<String> list = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
                String said = "";
                if (list != null && !list.isEmpty()) said = list.get(0);
                if (said.isEmpty()) said = lastPartial;
                lastPartial = "";
                if (said.isEmpty() || said.trim().isEmpty()) {
                    statusText.setText("Didn't catch that — try again");
                    orb.setTextColor(GOLD);
                    return;
                }
                transcript.setText("\"" + said + "\"");
                askAI(said.trim());
            }
            @Override public void onPartialResults(Bundle b) {
                ArrayList<String> list = b.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) {
                    lastPartial = list.get(0);
                    transcript.setText(lastPartial);
                }
            }
            @Override public void onEvent(int t, Bundle b) {}
        });
    }

    @Override protected void onResume() {
        super.onResume();
        active = true;
    }

    @Override protected void onPause() {
        super.onPause();
        active = false;
        stopEverything();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (sr != null) { sr.destroy(); sr = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
    }

    private void stopEverything() {
        if (sr != null && listening) sr.stopListening();
        if (tts != null && speaking) tts.stop();
        listening = false;
        speaking = false;
        stopPulse();
    }

    private void toggleListening() {
        if (thinking || speaking) return;
        if (listening) {
            sr.stopListening();
            listening = false;
            stopPulse();
            return;
        }
        requestMic();
    }

    private void requestMic() {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 77);
            return;
        }
        startListening();
    }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        super.onRequestPermissionsResult(rc, p, g);
        if (rc == 77 && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED)
            startListening();
    }

    private void startListening() {
        if (sr == null || tts == null) return;
        if (tts.isSpeaking()) tts.stop();
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L);
        listening = true;
        lastPartial = "";
        transcript.setText("");
        statusText.setText("Listening…");
        orb.setTextColor(ACCENT);
        sr.startListening(i);
    }

    private void startPulse() {
        if (pulse != null) return;
        View target = (View) orb.getTag();
        if (target == null) target = orb;
        pulse = new ScaleAnimation(1f, 1.1f, 1f, 1.1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f);
        pulse.setDuration(800);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        target.startAnimation(pulse);
    }

    private void stopPulse() {
        View target = (View) orb.getTag();
        if (target != null) target.clearAnimation();
        orb.clearAnimation();
        pulse = null;
    }

    private void askAI(String userText) {
        thinking = true;
        statusText.setText("Thinking…");
        orb.setTextColor(MUTED);

        try {
            JSONObject m = new JSONObject();
            m.put("role", "user"); m.put("content", userText);
            history.put(m);
        } catch (Exception ignored) {}

        addLog("You", userText, ACCENT);

        AIClient.send(this, prefs, history, new AIClient.Callback() {
            @Override public void onReply(String reply) {
                runOnUiThread(() -> onAIResponse(reply));
            }
            @Override public void onReplyStats(String reply, int inTok, int outTok, long ms) {
                runOnUiThread(() -> onAIResponse(reply));
            }
            @Override public void onToolCall(String name, String args) {
                // In voice mode, auto-run tools silently
                runOnUiThread(() -> {
                    statusText.setText("Running: " + name);
                    new Thread(() -> {
                        String r;
                        if (name.startsWith("ui_")) {
                            r = runUi(name, args);
                        } else {
                            try {
                                String cmd = new JSONObject(args).getString("command");
                                r = Shell.run(cmd).output;
                            } catch (Exception e) { r = "error"; }
                        }
                        final String rr = r;
                        runOnUiThread(() -> {
                            try {
                                JSONObject am = new JSONObject();
                                am.put("role", "assistant");
                                am.put("content", JSONObject.NULL);
                                JSONArray tcs = new JSONArray();
                                JSONObject tc = new JSONObject();
                                tc.put("id", "call_1");
                                tc.put("type", "function");
                                JSONObject fn = new JSONObject();
                                fn.put("name", name); fn.put("arguments", args);
                                tc.put("function", fn);
                                tcs.put(tc); am.put("tool_calls", tcs);
                                history.put(am);
                                JSONObject tm = new JSONObject();
                                tm.put("role", "tool");
                                tm.put("tool_call_id", "call_1");
                                tm.put("content", AIClient.sanitize(rr));
                                history.put(tm);
                            } catch (Exception ignored) {}
                            askAI("");
                        });
                    }).start();
                });
            }
            @Override public void onError(String err) {
                runOnUiThread(() -> {
                    thinking = false;
                    statusText.setText("Error: " + err);
                    orb.setTextColor(GOLD);
                });
            }
        });
    }

    private void onAIResponse(String reply) {
        thinking = false;
        if (reply == null || reply.isEmpty()) return;
        try {
            JSONObject a = new JSONObject();
            a.put("role", "assistant"); a.put("content", reply);
            history.put(a);
        } catch (Exception ignored) {}

        addLog("Star", reply, GREEN);

        // Speak it
        if (ttsReady && tts != null) {
            speaking = true;
            statusText.setText("Speaking…");
            orb.setTextColor(GOLD);
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) {
                    speaking = false;
                    runOnUiThread(() -> {
                        statusText.setText("Tap the orb to speak");
                        orb.setTextColor(GOLD);
                        // Auto-listen for continuous conversation
                        if (active) {
                            handler.postDelayed(VoiceActivity.this::startListening, 400);
                        }
                    });
                }
                @Override public void onError(String id) {
                    speaking = false;
                }
            });
            tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "star_" + System.currentTimeMillis());
        } else {
            statusText.setText("Tap the orb to speak");
            orb.setTextColor(GOLD);
        }
    }

    private String runUi(String name, String args) {
        try {
            JSONObject a = new JSONObject(args);
            switch (name) {
                case "ui_seq":    return StarAccessibilityService.runSequence(a.getString("seq"));
                case "ui_find":   return StarAccessibilityService.findAndTap(a.getString("text"));
                case "ui_type":   return StarAccessibilityService.typeText(a.getString("text"));
                case "ui_press": {
                    String k = StarAccessibilityService.pressKey(a.getString("key"));
                    if (k.startsWith("SHELL:")) { Shell.run(k.substring(6)); return "ok"; }
                    return k;
                }
                case "ui_tap":    return StarAccessibilityService.tap(a.getInt("x"), a.getInt("y")) ? "ok" : "fail";
                case "ui_scroll": return StarAccessibilityService.scroll(a.getString("direction"));
                default: return "ok";
            }
        } catch (Exception e) { return "err"; }
    }

    private void addLog(String who, String text, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(CARD);
        bg.setStroke(dp(1), BORDER);
        row.setBackground(bg);

        TextView w = new TextView(this);
        w.setText(who);
        w.setTextSize(10);
        w.setTextColor(color);
        w.setTypeface(Typeface.DEFAULT_BOLD);
        w.setLetterSpacing(0.15f);
        row.addView(w);

        TextView t = new TextView(this);
        String shown = text.length() > 200 ? text.substring(0, 200) + "…" : text;
        t.setText(shown);
        t.setTextSize(13);
        t.setTextColor(TEXT);
        t.setPadding(0, dp(3), 0, 0);
        row.addView(t);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        row.setLayoutParams(lp);

        log.addView(row, 0);
        // Keep only last 3
        while (log.getChildCount() > 3) log.removeViewAt(log.getChildCount() - 1);
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
