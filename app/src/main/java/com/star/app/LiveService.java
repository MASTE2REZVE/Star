package com.star.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Consumer;

/** On-demand live observer. No background capture. Only listens + answers. */
public class LiveService extends Service {
    public static final String ACTION_START = "com.star.app.LIVE_START";
    public static final String ACTION_STOP  = "com.star.app.LIVE_STOP";
    private static final String CH = "star_live";
    private static final int NOTIF_ID = 4411;

    public interface Listener {
        void onLine(String text, int color);
        void onStatus(String status);
    }

    public static volatile Listener listener = null;
    public static volatile boolean running = false;
    public static volatile Runnable onBeforeCapture = null;
    public static volatile Consumer<Boolean> onListeningChange = null;
    private static volatile boolean userStopped = false;

    private Handler handler;
    private PowerManager.WakeLock wake;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private AudioManager am;
    private AudioFocusRequest focusReq;

    private volatile boolean busy = false;
    private volatile boolean listening = false;
    private SpeechRecognizer currentSr = null;
    private SpeechRecognizer stopWordSr = null;
    private String lastSpokenText = "";

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        createChannel();
        initTts();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            userStopped = true;
            running = false;
            handler.removeCallbacksAndMessages(null);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (userStopped) return START_NOT_STICKY;
        if (running) return START_NOT_STICKY;
        running = true;

        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(NOTIF_ID, buildNotif("Ready · ask anything"), 0x40000080);
        } else {
            startForeground(NOTIF_ID, buildNotif("Ready · ask anything"));
        }
        acquireWake();
        requestAudioFocus();

        emit("— ready · ask anything about the screen —", 0xFF7EE787);
        emitStatus("Listening");
        startListeningLoop();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        cleanupSr();
        cleanupStopSr();
        releaseWake();
        try {
            if (am != null) {
                if (Build.VERSION.SDK_INT >= 26 && focusReq != null) am.abandonAudioFocusRequest(focusReq);
                else am.abandonAudioFocus(null);
            }
        } catch (Throwable ignored) {}
        try { if (tts != null) { tts.stop(); tts.shutdown(); } } catch (Throwable ignored) {}
        emit("— stopped —", 0xFFF85149);
        super.onDestroy();
        if (!userStopped) {
            Intent again = new Intent(this, LiveService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(again);
            else startService(again);
        }
    }

    // ================= TTS =================

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            try {
                tts.setLanguage(Locale.US);
                tts.setSpeechRate(1.0f);
                if (Build.VERSION.SDK_INT >= 21) {
                    AudioAttributes a = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                    tts.setAudioAttributes(a);
                }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {
                        // listen for "stop" while speaking
                        listenForStopWord();
                    }
                    @Override public void onDone(String id) {
                        cleanupStopSr();
                        lastSpokenText = "";
                        resumeListeningSoon();
                    }
                    @Override public void onError(String id) {
                        cleanupStopSr();
                        lastSpokenText = "";
                        resumeListeningSoon();
                    }
                });
                ttsReady = true;
            } catch (Throwable ignored) {}
        });
    }

    private void speak(String text) {
        if (text == null || text.isEmpty() || !ttsReady || tts == null) {
            resumeListeningSoon();
            return;
        }
        lastSpokenText = text.toLowerCase(Locale.US);
        try {
            Bundle p = new Bundle();
            p.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
            p.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, p, "live");
        } catch (Throwable t) {
            resumeListeningSoon();
        }
    }

    private void stopSpeaking() {
        try { if (tts != null) tts.stop(); } catch (Throwable ignored) {}
        cleanupStopSr();
    }

    private void requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
                focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setWillPauseWhenDucked(false)
                    .build();
                am.requestAudioFocus(focusReq);
            } else {
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            }
        } catch (Throwable ignored) {}
    }

    // ================= STOP-WORD LISTENER (during TTS) =================

    private void listenForStopWord() {
        if (!running) return;
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;

        cleanupStopSr();
        try {
            stopWordSr = SpeechRecognizer.createSpeechRecognizer(this);
            stopWordSr.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle p) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float v) {}
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int code) {
                    // restart as long as TTS is still speaking
                    if (isTtsSpeaking()) {
                        handler.postDelayed(LiveService.this::listenForStopWord, 300L);
                    }
                }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> a = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                    if (a != null && !a.isEmpty()) {
                        String said = a.get(0).toLowerCase(Locale.US).trim();
                        if (isStopWord(said) && !isLikelyEchoOfSpoken(said)) {
                            emit("(stop)", 0xFFF0883E);
                            stopSpeaking();
                            resumeListeningSoon();
                            return;
                        }
                    }
                    if (isTtsSpeaking()) {
                        handler.postDelayed(LiveService.this::listenForStopWord, 200L);
                    }
                }
                @Override public void onPartialResults(Bundle b) {
                    ArrayList<String> a = b.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                    if (a != null && !a.isEmpty()) {
                        String said = a.get(0).toLowerCase(Locale.US).trim();
                        if (isStopWord(said) && !isLikelyEchoOfSpoken(said)) {
                            emit("(stop)", 0xFFF0883E);
                            stopSpeaking();
                            resumeListeningSoon();
                        }
                    }
                }
                @Override public void onEvent(int t, Bundle b) {}
            });

            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
            stopWordSr.startListening(i);
        } catch (Throwable ignored) {}
    }

    private boolean isTtsSpeaking() {
        try { return tts != null && tts.isSpeaking(); } catch (Throwable t) { return false; }
    }

    private boolean isStopWord(String s) {
        if (s == null) return false;
        String t = s.toLowerCase(Locale.US).trim();
        // only short utterances; avoid matching "stop" inside a longer answer the model said
        String[] words = t.split("\\s+");
        if (words.length > 3) return false;
        return t.equals("stop") || t.equals("stop it") || t.equals("stop talking")
            || t.equals("shut up") || t.equals("silence") || t.equals("be quiet")
            || t.equals("quiet") || t.equals("pause") || t.equals("halt")
            || t.startsWith("stop ") || t.equals("cancel");
    }

    /** If the model's own reply contained "stop", avoid matching that as a user command. */
    private boolean isLikelyEchoOfSpoken(String said) {
        if (lastSpokenText == null || lastSpokenText.isEmpty()) return false;
        return lastSpokenText.contains(said);
    }

    private void cleanupStopSr() {
        try { if (stopWordSr != null) { stopWordSr.stopListening(); stopWordSr.destroy(); } } catch (Throwable ignored) {}
        stopWordSr = null;
    }

    // ================= MAIN LISTENING =================

    private void startListeningLoop() {
        if (!running) return;
        if (listening) return;
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            emit("mic permission needed — open Star once", 0xFFF0883E);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            emit("speech not available", 0xFFF0883E);
            return;
        }
        listenOnce();
    }

    private void listenOnce() {
        if (!running) return;
        if (listening || busy) return;
        if (isTtsSpeaking()) { handler.postDelayed(this::listenOnce, 500L); return; }
        listening = true;
        Consumer<Boolean> lc = onListeningChange;
        if (lc != null) { try { lc.accept(Boolean.TRUE); } catch (Throwable ignored) {} }

        try {
            currentSr = SpeechRecognizer.createSpeechRecognizer(this);
            currentSr.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle p) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float v) {}
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int code) {
                    cleanupSr();
                    listening = false;
                    Consumer<Boolean> lc2 = onListeningChange;
                    if (lc2 != null) { try { lc2.accept(Boolean.FALSE); } catch (Throwable ignored) {} }
                    if (running && !busy) handler.postDelayed(LiveService.this::listenOnce, 400L);
                }
                @Override public void onResults(Bundle results) {
                    cleanupSr();
                    listening = false;
                    Consumer<Boolean> lc2 = onListeningChange;
                    if (lc2 != null) { try { lc2.accept(Boolean.FALSE); } catch (Throwable ignored) {} }
                    ArrayList<String> a = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                    if (a == null || a.isEmpty()) {
                        if (running && !busy) handler.postDelayed(LiveService.this::listenOnce, 400L);
                        return;
                    }
                    String said = a.get(0).trim();
                    handleUserSaid(said);
                }
                @Override public void onPartialResults(Bundle b) {
                    ArrayList<String> a = b.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                    if (a != null && !a.isEmpty()) emit("🗣 " + a.get(0), 0xFF7CB3FF);
                }
                @Override public void onEvent(int t, Bundle b) {}
            });

            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
            currentSr.startListening(i);
        } catch (Throwable t) {
            listening = false;
            Consumer<Boolean> lc3 = onListeningChange;
            if (lc3 != null) { try { lc3.accept(Boolean.FALSE); } catch (Throwable ignored) {} }
            if (running) handler.postDelayed(LiveService.this::listenOnce, 800L);
        }
    }

    private void cleanupSr() {
        try { if (currentSr != null) { currentSr.stopListening(); currentSr.destroy(); } } catch (Throwable ignored) {}
        currentSr = null;
    }

    // ================= USER SPEECH HANDLER =================

    private void handleUserSaid(String said) {
        if (said == null) { resumeListeningSoon(); return; }
        String s = said.toLowerCase(Locale.US).trim();

        // stop command
        if (isStopWord(s)) {
            emit("(stop)", 0xFFF0883E);
            stopSpeaking();
            emitStatus("Listening");
            resumeListeningSoon();
            return;
        }

        emit("🗣 " + said, 0xFF7CB3FF);
        // user asked — stop TTS if running
        stopSpeaking();
        busy = true;
        emitStatus("Looking…");

        Runnable hook = onBeforeCapture;
        if (hook != null) { try { hook.run(); } catch (Throwable ignored) {} }

        new Thread(() -> {
            String b64 = ScreenCapture.captureJpegBase64(getApplicationContext(), 1280, 90);
            if (b64 == null) {
                handler.post(() -> {
                    emit("[error] capture failed", 0xFFF85149);
                    busy = false;
                    resumeListeningSoon();
                });
                return;
            }
            String prompt = "The user is looking at the screen and asks: \"" + said +
                "\". Answer in 1-2 short sentences about the CURRENT screen. " +
                "If they ask where something is, name its position.";
            Prefs prefs = new Prefs(this);
            AIClient.sendVision(this, prefs, b64, prompt, prefs.getLiveModel(),
                new AIClient.Callback() {
                    @Override public void onReply(final String reply) {
                        handler.post(() -> {
                            emit(reply, 0xFFECECEC);
                            updateNotif(reply);
                            busy = false;
                            emitStatus("Speaking");
                            if (new Prefs(LiveService.this).getLiveVoice()) {
                                speak(reply);
                                // listening resumes on TTS onDone
                            } else {
                                resumeListeningSoon();
                            }
                        });
                    }
                    @Override public void onReplyStats(String r, int i, int o, long ms) {}
                    @Override public void onToolCall(String n, String a) {}
                    @Override public void onError(String err) {
                        handler.post(() -> {
                            emit("[error] " + err, 0xFFF85149);
                            busy = false;
                            resumeListeningSoon();
                        });
                    }
                });
        }).start();
    }

    private void resumeListeningSoon() {
        if (!running) return;
        emitStatus("Listening");
        handler.postDelayed(LiveService.this::listenOnce, 300L);
    }

    // ================= MISC =================

    private void emit(String text, int color) {
        Listener l = listener;
        if (l != null) { try { l.onLine(text, color); } catch (Throwable ignored) {} }
        Log.d("Star", "live: " + text);
    }

    private void emitStatus(String s) {
        Listener l = listener;
        if (l != null) { try { l.onStatus(s); } catch (Throwable ignored) {} }
    }

    private void acquireWake() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Star::LiveWatch");
            wake.setReferenceCounted(false);
            wake.acquire(4 * 60 * 60 * 1000L);
        } catch (Throwable ignored) {}
    }

    private void releaseWake() {
        try { if (wake != null && wake.isHeld()) wake.release(); } catch (Throwable ignored) {}
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CH, "Star Live",
                NotificationManager.IMPORTANCE_LOW);
            c.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(c);
        }
    }

    private Notification buildNotif(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
            new Intent(this, LiveModeActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
            new Intent(this, LiveService.class).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(this, CH);
        else b = new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_menu_view)
         .setContentTitle("Star Live")
         .setContentText(text)
         .setContentIntent(open)
         .setOngoing(true)
         .addAction(new Notification.Action.Builder(null, "Stop", stop).build());
        return b.build();
    }

    private void updateNotif(String text) {
        if (text == null) return;
        String shortText = text.length() > 100 ? text.substring(0, 100) + "…" : text;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotif(shortText));
    }

    public static void start(android.content.Context ctx) {
        userStopped = false;
        Intent i = new Intent(ctx, LiveService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    public static void stop(android.content.Context ctx) {
        Intent i = new Intent(ctx, LiveService.class).setAction(ACTION_STOP);
        ctx.startService(i);
    }
}
