package com.star.app;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import org.json.*;
import java.util.ArrayList;

public class ChatActivity extends Activity {
    private static final int BG = 0xFF0F0F0F;
    private static final int TOPBAR = 0xFF171717;
    private static final int CARD = 0xFF1E1E1E;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int USER_BUBBLE = 0xFF2A2A2A;
    private static final int AI_TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF8E8E8E;
    private static final int ACCENT = 0xFF7CB3FF;

    private FrameLayout rootFrame;
    private LinearLayout contentLayer, drawer, messages;
    private String currentSessionId = "";
    private Session currentSession = null;
    private ScrollView scroll;
    private View backdrop;
    private JSONArray history = new JSONArray();
    private Prefs prefs;
    private ImageView sendIcon, stopIcon;
    private TextView mic, modelPill, statusWarning;
    private PillIconView agentPill, skillsPill, multiPill;
    private WorkerIconView chatModelIcon, workerModelIcon;
    private EditText input;
    private LinearLayout emptyState;
    private FrameLayout sendBtn, micBtn;
    private boolean agentMode = false;
    private boolean running = false;
    private int toolCallDepth = 0;
    private static final int MAX_TOOL_DEPTH = 12;
    private SpeechRecognizer sr;
    private boolean listening = false;
    private boolean drawerOpen = false;
    private GestureDetector gesture;
    private boolean wantsVoice = false;
    private volatile boolean stopRequested = false;
    private boolean autoRepairTried = false;
    private LinearLayout thinkingRow;
    private Uri attachedUri = null;
    private LinearLayout attachBar;
    private static final int PICK_FILE = 9001;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        agentMode = prefs.getAgentMode();

        // Sessions: load or create
        currentSessionId = prefs.getCurrentSessionId();
        if (currentSessionId == null || currentSessionId.isEmpty()) {
            Session s0 = SessionStore.create("");
            currentSessionId = s0.id;
            prefs.setCurrentSessionId(currentSessionId);
        }
        for (Session s : SessionStore.listSessions()) {
            if (currentSessionId.equals(s.id)) { currentSession = s; break; }
        }
        wantsVoice = getIntent().getBooleanExtra("voice", false);

        // Silent self-heal before UI builds
        autoHeal();

        // Init headless-app runtime once per process
        try { HeadlessApp.start(getApplicationContext()); }
        catch (Throwable ignored) {}

        // Refresh live model catalog (async, best-effort)
        new Thread(() -> {
            try { ModelCatalog.refresh(this, prefs, new ModelCatalog.Cb() {
                @Override public void onReady(java.util.List<ModelCatalog.Model> l) {}
                @Override public void onError(String e) {}
            }); } catch (Throwable ignored) {}
        }).start();

        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(BG);

        contentLayer = new LinearLayout(this);
        contentLayer.setOrientation(LinearLayout.VERTICAL);
        contentLayer.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(BG);
        topBar.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView menu = new TextView(this);
        menu.setText("☰");
        menu.setTextSize(20);
        menu.setTextColor(AI_TEXT);
        menu.setPadding(dp(8), dp(6), dp(8), dp(6));
        menu.setClickable(true);
        menu.setOnClickListener(v -> openDrawer());
        topBar.addView(menu);

        modelPill = new TextView(this);
        modelPill.setTextSize(13);
        modelPill.setTextColor(AI_TEXT);
        modelPill.setTypeface(Typeface.DEFAULT_BOLD);
        modelPill.setPadding(dp(12), dp(6), dp(12), dp(6));
        modelPill.setClickable(true);
        GradientDrawable mp = new GradientDrawable();
        mp.setCornerRadius(dp(14));
        mp.setColor(CARD);
        mp.setStroke(dp(1), BORDER);
        modelPill.setBackground(mp);
        modelPill.setOnClickListener(v -> openSessionMenu());
        refreshModelPill();
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mlp.leftMargin = dp(8);
        modelPill.setLayoutParams(mlp);
        topBar.addView(modelPill);

        agentPill = new PillIconView(this);
        int sz = dp(38);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(sz, sz);
        alp.rightMargin = dp(6);
        agentPill.setLayoutParams(alp);
        agentPill.setClickable(true);
        agentPill.setOnClickListener(v -> {
            agentMode = !agentMode;
            prefs.setAgentMode(agentMode);
            refreshAgentPill();
            toast(agentMode ? "Agent ON" : "Agent OFF");
        });
        agentPill.setOnLongClickListener(v -> {
            showTopIconInfo("Agent", 
                "Turns the agent loop on or off.\n\n" +
                "ON — the model can run tools: shell, tap, type, scroll, launch apps.\n" +
                "OFF — plain chat only, no tools.",
                agentMode ? "currently ON" : "currently OFF",
                "Toggle", () -> {
                    agentMode = !agentMode;
                    prefs.setAgentMode(agentMode);
                    refreshAgentPill();
                },
                null, null);
            return true;
        });
        topBar.addView(agentPill);
        refreshAgentPill();

        skillsPill = new PillIconView(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(sz, sz);
        slp.rightMargin = dp(6);
        skillsPill.setLayoutParams(slp);
        skillsPill.setClickable(true);
        skillsPill.setOnClickListener(v -> pickSkills());
        skillsPill.setOnLongClickListener(v -> {
            int total = com.star.app.candy.CandyManager.listInstalled().size();
            boolean configured = prefs.isSkillsConfigured();
            int enabled = configured ? prefs.getEnabledSkills().size() : total;
            showTopIconInfo("Skills",
                "Chooses which installed Candy skills the model can call from chat.\n\n" +
                "Green — all skills on.\n" +
                "Number — partial. That many are enabled.\n" +
                "Red — none.",
                enabled + " of " + total + " enabled",
                "Open picker", this::pickSkills,
                null, null);
            return true;
        });
        topBar.addView(skillsPill);
        refreshSkillsPill();

        multiPill = new PillIconView(this);
        LinearLayout.LayoutParams mlp2 = new LinearLayout.LayoutParams(sz, sz);
        mlp2.rightMargin = dp(6);
        multiPill.setLayoutParams(mlp2);
        multiPill.setClickable(true);
        multiPill.setOnClickListener(v -> pickMulti());
        multiPill.setOnLongClickListener(v -> {
            String mode = prefs.getMultiMode();
            int n = prefs.getMultiModels().size();
            String state = "off".equals(mode) ? "single model" : mode + " · " + n + " models";
            showTopIconInfo("Multi-model",
                "Runs several models on the same prompt.\n\n" +
                "Single — one model answers.\n" +
                "Parallel — all answer, you pick.\n" +
                "Mix (MoA) — all answer, judge merges.\n" +
                "Chain — draft, critique, rewrite.",
                state,
                "Open picker", this::pickMulti,
                "Turn off", () -> {
                    prefs.setMultiMode("off");
                    prefs.setMultiModels(new java.util.ArrayList<>());
                    refreshMultiPill();
                });
            return true;
        });
        topBar.addView(multiPill);
        refreshMultiPill();

        chatModelIcon = new WorkerIconView(this);
        chatModelIcon.setMode(WorkerIconView.MODE_MAIN);
        LinearLayout.LayoutParams cmLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        cmLp.rightMargin = dp(6);
        chatModelIcon.setLayoutParams(cmLp);
        chatModelIcon.setClickable(true);
        chatModelIcon.setOnClickListener(v -> {
            chatModelIcon.setHighlighted(true);
            chatModelIcon.postDelayed(() -> chatModelIcon.setHighlighted(false), 220);
            ModelPickerDialog.show(ChatActivity.this, prefs, prefs.getModel(),
                m -> {
                    prefs.setModel(m);
                    refreshChatModelIcon();
                    toast("Chat model: " + m);
                });
        });
        chatModelIcon.setOnLongClickListener(v -> {
            showTopIconInfo("Chat Model",
                "The main model that answers your messages in this app.\n\n" +
                "Every chat and every tool call goes to this model.\n\n" +
                "Tap the bubble to pick a new one. Sorted by price, benchmark and context.",
                prefs.getModel(),
                "Change model", () -> ModelPickerDialog.show(ChatActivity.this, prefs,
                    prefs.getModel(),
                    m -> {
                        prefs.setModel(m);
                        refreshChatModelIcon();
                    }),
                null, null);
            return true;
        });
        topBar.addView(chatModelIcon);
        refreshChatModelIcon();

        workerModelIcon = new WorkerIconView(this);
        workerModelIcon.setMode(WorkerIconView.MODE_WORKER);
        LinearLayout.LayoutParams wmLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        wmLp.rightMargin = dp(6);
        workerModelIcon.setLayoutParams(wmLp);
        workerModelIcon.setClickable(true);
        workerModelIcon.setOnClickListener(v -> {
            workerModelIcon.setHighlighted(true);
            workerModelIcon.postDelayed(() -> workerModelIcon.setHighlighted(false), 220);
            ModelPickerDialog.show(ChatActivity.this, prefs, prefs.getWorkerModel(),
                m -> {
                    prefs.setWorkerModel(m);
                    refreshWorkerModelIcon();
                    toast("Worker model: " + m);
                });
        });
        workerModelIcon.setOnLongClickListener(v -> {
            String m = prefs.getWorkerModel();
            String state = (m == null || m.isEmpty()) ? "not set" : m;
            showTopIconInfo("Worker Model",
                "A free OpenRouter model that acts as a second brain.\n\n" +
                "When a task is heavy (deep reasoning, code, a second opinion), the " +
                "chat model can delegate it to this worker. The worker runs in the " +
                "background through your OpenRouter key.\n\n" +
                "Pick a model with :free in the id to keep it free.\n\n" +
                "Gold and slowly turning — a worker is set.\n" +
                "Gray and still — no worker.",
                state,
                "Change worker", () -> ModelPickerDialog.show(ChatActivity.this, prefs,
                    prefs.getWorkerModel(),
                    mm -> {
                        prefs.setWorkerModel(mm);
                        refreshWorkerModelIcon();
                    }),
                "Turn off", () -> {
                    prefs.setWorkerModel("");
                    refreshWorkerModelIcon();
                    toast("Worker cleared");
                });
            return true;
        });
        topBar.addView(workerModelIcon);
        refreshWorkerModelIcon();

        TextView newChat = new TextView(this);
        newChat.setText("✎");
        newChat.setTextSize(18);
        newChat.setTextColor(AI_TEXT);
        newChat.setPadding(dp(8), dp(6), dp(8), dp(6));
        newChat.setClickable(true);
        newChat.setOnClickListener(v -> {
            Session sNew = SessionStore.create("");
            currentSessionId = sNew.id;
            currentSession = sNew;
            prefs.setCurrentSessionId(sNew.id);
            history = new JSONArray();
            messages.removeAllViews();
            prefs.setHistory("");
            toolCallDepth = 0;
            stopRequested = false;
            showEmptyState();
            refreshSessionPill();
            toast("New chat");
        });
        topBar.addView(newChat);
        contentLayer.addView(topBar);

        statusWarning = new TextView(this);
        statusWarning.setTextSize(12);
        statusWarning.setPadding(dp(20), dp(8), dp(20), dp(8));
        statusWarning.setVisibility(View.GONE);
        statusWarning.setClickable(true);
        contentLayer.addView(statusWarning);

        FrameLayout chatFrame = new FrameLayout(this);
        chatFrame.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(20), dp(8), dp(20), dp(8));
        scroll.addView(messages);

        emptyState = new LinearLayout(this);
        emptyState.setOrientation(LinearLayout.VERTICAL);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(dp(24), dp(24), dp(24), dp(24));

        chatFrame.addView(scroll);
        chatFrame.addView(emptyState);
        contentLayer.addView(chatFrame);

        LinearLayout inputOuter = new LinearLayout(this);
        inputOuter.setOrientation(LinearLayout.HORIZONTAL);
        inputOuter.setGravity(Gravity.CENTER_VERTICAL);
        inputOuter.setPadding(dp(12), dp(8), dp(12), dp(16));
        inputOuter.setBackgroundColor(BG);

        LinearLayout inputBox = new LinearLayout(this);
        inputBox.setOrientation(LinearLayout.HORIZONTAL);
        inputBox.setGravity(Gravity.CENTER_VERTICAL);
        inputBox.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable ib = new GradientDrawable();
        ib.setCornerRadius(dp(26));
        ib.setColor(CARD);
        ib.setStroke(dp(1), BORDER);
        inputBox.setBackground(ib);
        inputBox.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        micBtn = new FrameLayout(this);
        micBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        micBtn.setClickable(true);
        micBtn.setOnClickListener(v -> toggleVoice());

        ImageView micIcon = new ImageView(this);
        micIcon.setImageResource(R.drawable.ic_wave);
        FrameLayout.LayoutParams milp = new FrameLayout.LayoutParams(dp(24), dp(24));
        milp.gravity = Gravity.CENTER;
        micIcon.setLayoutParams(milp);
        micBtn.addView(micIcon);
        micBtn.setTag(micIcon);

        input = new EditText(this);
        input.setHint("Message Star…");
        input.setTextColor(AI_TEXT);
        input.setHintTextColor(MUTED);
        input.setTextSize(16);
        input.setBackground(null);
        input.setPadding(dp(8), dp(8), dp(8), dp(8));
        input.setMaxLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_FLAG_MULTI_LINE |
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        sendBtn = new FrameLayout(this);
        sendBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        sendBtn.setClickable(true);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setShape(GradientDrawable.OVAL);
        sbg.setColor(ACCENT);
        sendBtn.setBackground(sbg);
        sendBtn.setOnClickListener(v -> {
            if (running) stopAI();
            else onSend();
        });
        PressFeedback.attach(sendBtn);

        sendIcon = new ImageView(this);
        sendIcon.setImageResource(R.drawable.ic_send);
        FrameLayout.LayoutParams silp = new FrameLayout.LayoutParams(dp(22), dp(22));
        silp.gravity = Gravity.CENTER;
        sendIcon.setLayoutParams(silp);

        stopIcon = new ImageView(this);
        stopIcon.setImageResource(R.drawable.ic_stop);
        stopIcon.setLayoutParams(silp);
        stopIcon.setVisibility(View.GONE);

        sendBtn.addView(sendIcon);
        sendBtn.addView(stopIcon);

        // Attach button
        FrameLayout attachBtn = new FrameLayout(this);
        attachBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(44)));
        attachBtn.setClickable(true);
        attachBtn.setOnClickListener(v -> pickAttachment());
        ImageView attachIcon = new ImageView(this);
        attachIcon.setImageResource(R.drawable.ic_attach);
        FrameLayout.LayoutParams ailp = new FrameLayout.LayoutParams(dp(22), dp(22));
        ailp.gravity = Gravity.CENTER;
        attachIcon.setLayoutParams(ailp);
        attachBtn.addView(attachIcon);

        inputBox.addView(attachBtn);
        inputBox.addView(micBtn);
        inputBox.addView(input);
        inputBox.addView(sendBtn);
        // Attachment preview bar (hidden by default)
        attachBar = new LinearLayout(this);
        attachBar.setOrientation(LinearLayout.HORIZONTAL);
        attachBar.setGravity(Gravity.CENTER_VERTICAL);
        attachBar.setPadding(dp(16), dp(8), dp(16), dp(4));
        attachBar.setBackgroundColor(BG);
        attachBar.setVisibility(View.GONE);
        contentLayer.addView(attachBar);

        inputOuter.addView(inputBox);
        contentLayer.addView(inputOuter);

        rootFrame.addView(contentLayer);

        backdrop = new View(this);
        backdrop.setBackgroundColor(0xCC000000);
        backdrop.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        backdrop.setVisibility(View.GONE);
        backdrop.setOnClickListener(v -> closeDrawer());
        rootFrame.addView(backdrop);

        drawer = buildDrawer();
        FrameLayout.LayoutParams dly = new FrameLayout.LayoutParams(
            dp(280), ViewGroup.LayoutParams.MATCH_PARENT);
        drawer.setLayoutParams(dly);
        drawer.setVisibility(View.GONE);
        rootFrame.addView(drawer);

        setContentView(rootFrame);

        gesture = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                if (e1.getX() < dp(24) && dx > dp(80)) { openDrawer(); return true; }
                if (drawerOpen && dx < -dp(80)) { closeDrawer(); return true; }
                return false;
            }
        });

        String saved = prefs.getHistory();
        if (!saved.isEmpty()) {
            try {
                history = new JSONArray(saved);
                for (int i = 0; i < history.length(); i++) {
                    JSONObject m = history.getJSONObject(i);
                    String role = m.optString("role");
                    String content = m.optString("content", "");
                    if (role.equals("user")) addUserBubble(content);
                    else if (role.equals("assistant") && !content.isEmpty())
                        addAIBubble(content);
                }
                scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
            } catch (Exception ignored) {}
        } else showEmptyState();

        if (wantsVoice) input.postDelayed(this::toggleVoice, 400);
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefs != null && modelPill != null) refreshModelPill();
        refreshStatusWarning();
    }

    private void refreshStatusWarning() {
        if (statusWarning == null) return;
        boolean shizukuOk = ShizukuHelper.isAvailable();
        boolean accOk = StarAccessibilityService.isRunning();

        // Try to auto-repair accessibility once per screen open
        if (!accOk && shizukuOk && prefs.getAutoRepair() && !autoRepairTried) {
            autoRepairTried = true;
            new Thread(() -> {
                SelfHeal.repair(ChatActivity.this, 4000);
                runOnUiThread(() -> {
                    if (StarAccessibilityService.isRunning()) {
                        statusWarning.setVisibility(View.GONE);
                    } else {
                        showWarningBanner(shizukuOk, false);
                    }
                });
            }).start();
            return;
        }

        if (shizukuOk && accOk) {
            statusWarning.setVisibility(View.GONE);
        } else {
            showWarningBanner(shizukuOk, accOk);
        }

        // Bridge health check (background)
        new Thread(() -> {
            boolean bridgeOk = DshClient.isBridgeAlive();
            if (!bridgeOk) {
                runOnUiThread(() -> {
                    statusWarning.setText("DSH bridge offline · open Termux and run: start-bridge");
                    statusWarning.setTextColor(0xFFF0883E);
                    GradientDrawable wbg = new GradientDrawable();
                    wbg.setColor(0xFF1F1610);
                    wbg.setStroke(dp(1), 0x44F0883E);
                    statusWarning.setBackground(wbg);
                    statusWarning.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void showWarningBanner(boolean shizukuOk, boolean accOk) {
        StringBuilder msg = new StringBuilder();
        if (!shizukuOk) msg.append("Shizuku not active");
        if (!accOk) {
            if (msg.length() > 0) msg.append("  ·  ");
            msg.append("Accessibility off");
        }
        msg.append("  ·  Tap to fix");
        statusWarning.setText(msg.toString());
        statusWarning.setTextColor(0xFFF0883E);
        GradientDrawable wbg = new GradientDrawable();
        wbg.setColor(0xFF1F1610);
        wbg.setStroke(dp(1), 0x44F0883E);
        statusWarning.setBackground(wbg);
        statusWarning.setVisibility(View.VISIBLE);
        statusWarning.setOnClickListener(v -> {
            if (!ShizukuHelper.isRunning()) {
                toast("Open Shizuku app and restart it");
            } else if (!ShizukuHelper.hasPermission()) {
                ShizukuHelper.requestPermission();
            }
            if (!StarAccessibilityService.isRunning()) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gesture != null) gesture.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }


    private void refreshModelPill() {
        refreshSessionPill();
    }

    private void refreshSessionPill() {
        if (modelPill == null) return;
        String title = (currentSession != null && currentSession.title != null)
            ? currentSession.title : "";
        if (title.isEmpty() || title.equals("Untitled")) title = "New chat";
        modelPill.setText(title);
    }

    private static final int PICK_MODEL = 7777;

    private void pickModel() {
        ModelPickerDialog.show(this, prefs, prefs.getModel(),
            m -> {
                prefs.setModel(m);
                refreshChatModelIcon();
                refreshModelPill();
                toast("Chat model: " + m);
            });
    }

    private void refreshChatModelIcon() {
        String m = prefs.getModel();
        boolean active = m != null && !m.isEmpty();
        if (chatModelIcon != null) chatModelIcon.setActive(active);
    }

    private void refreshWorkerModelIcon() {
        String m = prefs.getWorkerModel();
        boolean active = m != null && !m.isEmpty();
        if (workerModelIcon != null) workerModelIcon.setActive(active);
    }



    private LinearLayout buildDrawer() {
        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.VERTICAL);
        d.setBackgroundColor(TOPBAR);
        d.setPadding(dp(20), dp(32), dp(20), dp(20));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), 0, dp(4), dp(20));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_star_logo);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(44), dp(44));
        slp.rightMargin = dp(12);
        logo.setLayoutParams(slp);
        header.addView(logo);

        LinearLayout nameCol = new LinearLayout(this);
        nameCol.setOrientation(LinearLayout.VERTICAL);

        TextView nm = new TextView(this);
        nm.setText("Star");
        nm.setTextSize(18);
        nm.setTypeface(Typeface.DEFAULT_BOLD);
        nm.setTextColor(AI_TEXT);
        nameCol.addView(nm);

        TextView tag = new TextView(this);
        tag.setText("AI Agent");
        tag.setTextSize(12);
        tag.setTextColor(MUTED);
        nameCol.addView(tag);

        header.addView(nameCol);
        d.addView(header);

        View div = new View(this);
        LinearLayout.LayoutParams divlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        divlp.bottomMargin = dp(12);
        div.setLayoutParams(divlp);
        div.setBackgroundColor(BORDER);
        d.addView(div);

        d.addView(drawerItem("✎", "New Chat", v1 -> {
            closeDrawer();
            history = new JSONArray();
            messages.removeAllViews();
            prefs.setHistory("");
            toolCallDepth = 0;
            stopRequested = false;
            showEmptyState();
        }));
        d.addView(drawerItem("▤", "Voice Mode", v1 -> {
            closeDrawer();
            startActivity(new Intent(this, VoiceActivity.class));
        }));
        d.addView(drawerItem("◎", "Live Mode", v1 -> {
            closeDrawer();
            toast("Live AI — next step");
        }));
        d.addView(drawerItem("◐", "Floating Bubble", v1 -> {
            closeDrawer();
            if (android.os.Build.VERSION.SDK_INT >= 23 &&
                !android.provider.Settings.canDrawOverlays(this)) {
                startActivity(new Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName())));
                toast("Allow 'Display over other apps', then tap again");
                return;
            }
            startService(new Intent(this, FloatingService.class));
            toast("Bubble on");
        }));
        d.addView(drawerItem("⚒", "Builder", v1 -> {
            closeDrawer();
            startActivity(new Intent(this, BuilderActivity.class));
        }));

        View studioRow = drawerItem("❖", "Studio", null);
        installStudioGesture(studioRow);
        d.addView(studioRow);

        d.addView(drawerItem("▣", "Image Studio", v1 -> {
            closeDrawer();
            startActivity(new Intent(this, ImageStudioActivity.class));
        }));
        d.addView(drawerItem("▶", "Terminal", v1 -> {
            closeDrawer();
            startActivity(new Intent(this, TerminalActivity.class));
        }));
        d.addView(drawerItem("◈", "Candy Skills", v1 -> {
            closeDrawer();
            startActivity(new Intent(this, CandyActivity.class));
        }));
        d.addView(drawerItem("⚙", "Settings", v1 -> {
            closeDrawer();
            startActivity(new Intent(this, SettingsActivity.class));
        }));
        d.addView(drawerItem("ⓘ", "System Info", v1 -> {
            closeDrawer();
            startActivity(new Intent(this, MainActivity.class));
        }));

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(1, 0, 1f));
        d.addView(spacer);

        TextView version = new TextView(this);
        version.setText("v3 · Xiaomi Pad 7");
        version.setTextSize(11);
        version.setTextColor(0xFF484F58);
        version.setPadding(dp(8), 0, 0, 0);
        d.addView(version);

        return d;
    }

    private View drawerItem(String icon, String label, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(14), dp(12), dp(14));
        row.setClickable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(0x00000000);
        row.setBackground(bg);

        row.setOnTouchListener((v, ev) -> {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN: bg.setColor(0xFF262626); v.setBackground(bg); break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: bg.setColor(0x00000000); v.setBackground(bg); break;
            }
            return false;
        });

        TextView ic = new TextView(this);
        ic.setText(icon);
        ic.setTextSize(15);
        ic.setTextColor(AI_TEXT);
        ic.setPadding(0, 0, dp(14), 0);
        ic.setMinWidth(dp(22));
        ic.setGravity(Gravity.CENTER);
        row.addView(ic);

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(15);
        t.setTextColor(AI_TEXT);
        row.addView(t);

        row.setOnClickListener(click);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(2);
        row.setLayoutParams(lp);
        return row;
    }

    private void openDrawer() {
        if (drawerOpen) return;
        drawerOpen = true;
        backdrop.setVisibility(View.VISIBLE);
        backdrop.setAlpha(0f);
        backdrop.animate().alpha(1f).setDuration(200).start();
        drawer.setVisibility(View.VISIBLE);
        drawer.setTranslationX(-dp(280));
        drawer.animate().translationX(0).setDuration(220)
            .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        backdrop.animate().alpha(0f).setDuration(180)
            .withEndAction(() -> backdrop.setVisibility(View.GONE)).start();
        drawer.animate().translationX(-dp(280)).setDuration(200)
            .withEndAction(() -> drawer.setVisibility(View.GONE)).start();
    }

    @Override public void onBackPressed() {
        if (drawerOpen) { closeDrawer(); return; }
        super.onBackPressed();
    }

    private void showEmptyState() {
        emptyState.removeAllViews();
        emptyState.setVisibility(View.VISIBLE);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_star_logo);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(96), dp(96));
        logo.setLayoutParams(llp);
        emptyState.addView(logo);

        TextView hello = new TextView(this);
        hello.setText("Hi, I'm Star");
        hello.setTextSize(26);
        hello.setTypeface(Typeface.DEFAULT_BOLD);
        hello.setTextColor(AI_TEXT);
        hello.setGravity(Gravity.CENTER);
        hello.setPadding(0, dp(12), 0, 0);
        emptyState.addView(hello);

        TextView sub = new TextView(this);
        sub.setText("Your autonomous Android agent");
        sub.setTextSize(14);
        sub.setTextColor(MUTED);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(28));
        emptyState.addView(sub);

        String[] suggestions = {
            "Open YouTube and search cats",
            "Take a screenshot",
            "What's my battery level?",
            "Update yourself"
        };
        for (String s : suggestions) emptyState.addView(suggestionChip(s));
    }

    private View suggestionChip(String text) {
        TextView c = new TextView(this);
        c.setText(text);
        c.setTextSize(14);
        c.setTextColor(AI_TEXT);
        c.setPadding(dp(18), dp(12), dp(18), dp(12));
        c.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(CARD);
        bg.setStroke(dp(1), BORDER);
        c.setBackground(bg);
        c.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        c.setLayoutParams(lp);
        c.setOnClickListener(v -> { input.setText(text); onSend(); });
        return c;
    }

    private void toggleVoice() {
        if (listening) { stopVoice(); return; }
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 42);
            return;
        }
        startVoice();
    }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        super.onRequestPermissionsResult(rc, p, g);
        if (rc == 42 && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) startVoice();
        if (rc == 88 && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) {
            try {
                startService(new Intent(this, FloatingService.class));
                toast("Bubble started");
            } catch (Exception e) { toast("Failed: " + e.getMessage()); }
        }
    }

    private void startVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Speech not available"); return;
        }
        if (sr == null) {
            sr = SpeechRecognizer.createSpeechRecognizer(this);
            sr.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle p) { setMicActive(true); }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float v) {}
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEndOfSpeech() { setMicActive(false); listening = false; }
                @Override public void onError(int c) { setMicActive(false); listening = false; }
                @Override public void onResults(Bundle r) {
                    setMicActive(false); listening = false;
                    ArrayList<String> a = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (a != null && !a.isEmpty()) { input.setText(a.get(0)); onSend(); }
                }
                @Override public void onPartialResults(Bundle b) {
                    ArrayList<String> a = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (a != null && !a.isEmpty()) input.setText(a.get(0));
                }
                @Override public void onEvent(int t, Bundle b) {}
            });
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        listening = true;
        sr.startListening(i);
    }

    private void setMicActive(boolean active) {
        ImageView iv = (ImageView) micBtn.getTag();
        if (active) {
            iv.setColorFilter(ACCENT);
            micBtn.animate().scaleX(1.15f).scaleY(1.15f).setDuration(200).start();
        } else {
            iv.setColorFilter(null);
            micBtn.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
        }
    }

    private void stopVoice() {
        if (sr != null) sr.stopListening();
        setMicActive(false); listening = false;
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (sr != null) { sr.destroy(); sr = null; }
    }

    private void onSend() {
        String t = input.getText().toString().trim();
        if (t.isEmpty() && attachedUri == null) return;
        input.setText("");
        if (emptyState.getVisibility() == View.VISIBLE)
            emptyState.setVisibility(View.GONE);
        String bubbleText = t;
        if (attachedUri != null) {
            String nm = Attachments.getFileName(this, attachedUri);
            bubbleText = t.isEmpty() ? ("[📎 " + nm + "]") : (t + "\n📎 " + nm);
        }
        addUserBubble(bubbleText);
        try {
            history.put(buildUserMessage(t));
            saveHistory();
        } catch (Exception ignored) {}
        attachedUri = null;
        attachBar.setVisibility(View.GONE);
        toolCallDepth = 0;
        stopRequested = false;
        askAI();
    }

    private void saveHistory() {
        try {
            JSONArray clean = new JSONArray();
            for (int i = 0; i < history.length(); i++) {
                JSONObject m = history.getJSONObject(i);
                String role = m.optString("role");
                if ("ui_card".equals(role)) { clean.put(m); continue; }
                String content = m.optString("content", "");
                if (content == null) content = "";
                String t = content.trim();
                if (t.isEmpty()) continue;
                if (t.equals("null") || t.equals("{}") || t.equals("undefined")) continue;
                if (t.equalsIgnoreCase("null")) continue;
                if ((role.equals("user") || role.equals("assistant"))) {
                    JSONObject c = new JSONObject();
                    c.put("role", role);
                    c.put("content", content);
                    clean.put(c);
                }
            }
            prefs.setHistory(clean.toString());
        } catch (Exception ignored) {}
        try {
            SessionStore.saveMessages(currentSessionId, history);
            String firstUser = "";
            for (int i = 0; i < history.length(); i++) {
                JSONObject m = history.getJSONObject(i);
                if ("user".equals(m.optString("role"))) {
                    firstUser = m.optString("content", "");
                    break;
                }
            }
            SessionStore.touch(currentSessionId, history.length(), firstUser);
            for (Session s : SessionStore.listSessions()) {
                if (currentSessionId.equals(s.id)) { currentSession = s; break; }
            }
            refreshSessionPill();
        } catch (Exception ignored) {}
    }

    private void addUserBubble(String text) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.setGravity(Gravity.END);
        wrap.setPadding(0, dp(6), 0, dp(6));

        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(16);
        b.setTextColor(AI_TEXT);
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        b.setTextIsSelectable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(USER_BUBBLE);
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.END;
        b.setLayoutParams(lp);
        b.setOnLongClickListener(v -> { copyToClip(text); return true; });
        wrap.addView(b);
        messages.addView(wrap);
        scrollDown();
    }

    private void addAIBubble(String text) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.setGravity(Gravity.START);
        wrap.setPadding(0, dp(10), 0, dp(10));

        TextView b = new TextView(this);
        b.setTextSize(16);
        b.setTextColor(AI_TEXT);
        b.setLineSpacing(dp(4), 1f);
        b.setTextIsSelectable(true);
        b.setText(Markdown.render(text));
        b.setOnLongClickListener(v -> { copyToClip(text); return true; });

        wrap.addView(b);
        messages.addView(wrap);
        scrollDown();
    }

    private void addSystemLine(String label, String content, int color) {
        saveUiCardSystem(label, content, color);
        renderSystemLine(label, content, color);
    }

    private void renderSystemLine(String label, String content, int color) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.setPadding(0, dp(4), 0, dp(4));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(0xFF141414);
        bg.setStroke(dp(1), 0xFF232323);
        card.setBackground(bg);

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextSize(10);
        lbl.setTextColor(color);
        lbl.setLetterSpacing(0.15f);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(lbl);

        TextView t = new TextView(this);
        t.setText(content);
        t.setTextSize(13);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextColor(0xFFB0B0B0);
        t.setPadding(0, dp(6), 0, 0);
        t.setTextIsSelectable(true);
        card.addView(t);

        wrap.addView(card);
        messages.addView(wrap);
        scrollDown();
    }

    private void addStatsLine(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(10);
        t.setTextColor(0xFF5A5A5A);
        t.setTypeface(Typeface.MONOSPACE);
        t.setPadding(0, 0, 0, dp(6));
        LinearLayout wrap = new LinearLayout(this);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.setGravity(Gravity.START);
        wrap.addView(t);
        messages.addView(wrap);
        scrollDown();
    }

    private void scrollDown() {
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void copyToClip(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Star", s));
        toast("Copied");
    }

    private void setRunning(boolean r) {
        running = r;
        if (r) {
            sendIcon.setVisibility(View.GONE);
            stopIcon.setVisibility(View.VISIBLE);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(0xFFF85149);
            sendBtn.setBackground(bg);
        } else {
            stopIcon.setVisibility(View.GONE);
            sendIcon.setVisibility(View.VISIBLE);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(ACCENT);
            sendBtn.setBackground(bg);
        }
    }

    private void stopAI() {
        stopRequested = true;
        toolCallDepth = MAX_TOOL_DEPTH + 1;
        if (currentLoop != null) {
            try { currentLoop.cancel(); } catch (Throwable ignored) {}
        }
        hideThinking();
        setRunning(false);
        addSystemLine("STOPPED", "Generation cancelled", 0xFFF0883E);
    }

    private StarAgentLoop currentLoop;

    private void askAI() {
        if (stopRequested) return;
        setRunning(true);

        currentLoop = new StarAgentLoop(this, prefs, history, new StarAgentLoop.Listener() {
            @Override public void onThinking() {
                runOnUiThread(() -> {
                    if (stopRequested) return;
                    showThinking();
                    setThinkingStage("thinking");
                });
            }

            @Override public void onToolCall(String toolName, String argsJson) {
                runOnUiThread(() -> {
                    if (stopRequested) return;
                    setThinkingStage("calling " + toolName);
                    String label = toolName.replace("ui_", "").toUpperCase();
                    if (toolName.equals("run_shell")) {
                        try {
                            String cmd = new JSONObject(argsJson).getString("command");
                            addSystemLine("SHELL", cmd, 0xFFF0883E);
                        } catch (Exception e) {
                            addSystemLine("SHELL", argsJson, 0xFFF0883E);
                        }
                    } else if (toolName.equals("update_self")) {
                        addSystemLine("ACTION", "Checking updates…", 0xFFF0883E);
                    } else {
                        addSystemLine(label, argsJson, 0xFFF0883E);
                    }
                });
            }

            @Override public void onToolResult(String toolName, String result) {
                runOnUiThread(() -> {
                    if (stopRequested) return;
                    setThinkingStage("thinking");
                    if (toolName != null && toolName.startsWith("MODEL:")) {
                        String[] parts = toolName.substring(6).split("\\|", 3);
                        String role = parts.length > 0 ? parts[0] : "answer";
                        String modelName = parts.length > 1 ? parts[1] : "";
                        String stats = parts.length > 2 ? parts[2] : "";
                        addModelBubble(role, modelName, stats, result);
                    } else {
                        addSystemLine("RESULT", result, 0xFF8E8E8E);
                    }
                });
            }

            @Override public void onReply(String reply) {
                final String replyFinal = (reply == null) ? "" : reply;
                runOnUiThread(() -> {
                    if (stopRequested) return;
                    hideThinking();
                    String t = replyFinal.trim();
                    String tl = t.toLowerCase();
                    if (t.isEmpty()
                        || tl.equals("null")
                        || tl.equals("undefined")
                        || t.equals("{}")
                        || t.equals("[]")
                        || tl.startsWith("null\n")) {
                        addSystemLine("EMPTY REPLY",
                            "Model returned nothing. Try another model " +
                            "or rephrase.", 0xFFF0883E);
                        setRunning(false);
                        return;
                    }
                    addAIBubble(replyFinal);
                    saveHistory();
                    setRunning(false);
                });
            }

            @Override public void onError(String err) {
                runOnUiThread(() -> {
                    addSystemLine("ERROR", err, 0xFFF85149);
                    setRunning(false);
                });
            }

            @Override public void onTurnComplete() {
                runOnUiThread(() -> {
                    hideThinking();
                    try { saveHistory(); } catch (Throwable ignored) {}
                    setRunning(false);
                });
            }
        });

        // Extract the last user message from history to start the turn
        try {
            String userMsg = "";
            for (int i = history.length() - 1; i >= 0; i--) {
                JSONObject m = history.getJSONObject(i);
                if ("user".equals(m.optString("role"))) {
                    Object c = m.opt("content");
                    if (c instanceof String) userMsg = (String) c;
                    else userMsg = String.valueOf(c);
                    break;
                }
            }
            // Remove the user message we just added (loop will add it)
            if (history.length() > 0) history.remove(history.length() - 1);
            currentLoop.runTurn(userMsg);
        } catch (Exception e) {
            addSystemLine("ERROR", "Loop start failed: " + e.getMessage(), 0xFFF85149);
            setRunning(false);
        }
    }

    private String runUiTool(String toolName, String argsJson) {
        try {
            JSONObject a = new JSONObject(argsJson);
            switch (toolName) {
                case "ui_seq":    return StarAccessibilityService.runSequence(a.getString("seq"));
                case "ui_dump":   return StarAccessibilityService.dump();
                case "ui_find":   return StarAccessibilityService.findAndTap(a.getString("text"));
                case "ui_type":   return StarAccessibilityService.typeText(a.getString("text"));
                case "ui_press": {
                    String kres = StarAccessibilityService.pressKey(a.getString("key"));
                    if (kres.startsWith("SHELL:")) {
                        Shell.run(kres.substring(6));
                        return "Pressed " + a.getString("key") + " via shell";
                    }
                    return kres;
                }
                case "ui_scroll": return StarAccessibilityService.scroll(a.getString("direction"));
                case "ui_tap":
                    return StarAccessibilityService.tap(a.getInt("x"), a.getInt("y"))
                        ? "Tapped " + a.getInt("x") + "," + a.getInt("y") : "Tap failed";
                case "ui_swipe":
                    return StarAccessibilityService.swipe(a.getInt("x1"), a.getInt("y1"),
                        a.getInt("x2"), a.getInt("y2")) ? "Swiped" : "Swipe failed";
                default: return "Unknown: " + toolName;
            }
        } catch (Exception e) { return "Tool error: " + e.getMessage(); }
    }

    private void executeCommand(String toolName, String argsJson, String cmd) {
        addSystemLine("SHELL", cmd, 0xFFF0883E);
        try {
            JSONObject am = new JSONObject();
            am.put("role", "assistant"); am.put("content", JSONObject.NULL);
            JSONArray tcs = new JSONArray();
            JSONObject tc = new JSONObject();
            tc.put("id", "call_" + toolCallDepth);
            tc.put("type", "function");
            JSONObject fn = new JSONObject();
            fn.put("name", toolName); fn.put("arguments", argsJson);
            tc.put("function", fn);
            tcs.put(tc); am.put("tool_calls", tcs);
            history.put(am);
        } catch (Exception ignored) {}

        new Thread(() -> {
            Shell.Result r = Shell.run(cmd);
            String out = r.output.isEmpty() ? "(no output)" : r.output;
            if (out.length() > 3000) out = out.substring(0, 3000) + "\n...(trunc)";
            final String fo = out;
            if (stopRequested) return;
            runOnUiThread(() -> {
                if (stopRequested) return;
                addSystemLine("OUTPUT", fo.trim(), 0xFFB0B0B0);
                try {
                    JSONObject tm = new JSONObject();
                    tm.put("role", "tool");
                    tm.put("tool_call_id", "call_" + toolCallDepth);
                    tm.put("content", AIClient.sanitize(fo));
                    history.put(tm);
                } catch (Exception ignored) {}
                askAI();
            });
        }).start();
    }

    private void pickAttachment() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "image/*", "application/pdf", "text/*",
            "application/json", "application/zip"
        });
        try {
            startActivityForResult(Intent.createChooser(intent, "Attach file"), PICK_FILE);
        } catch (Exception e) { toast("No file picker available"); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_FILE && res == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            String mime = Attachments.getMime(this, uri);
            long size = Attachments.getSize(this, uri);
            if (size > Attachments.MAX_BYTES) {
                toast("File too large (max 4 MB)"); return;
            }
            attachedUri = uri;
            showAttachBar();
        }
    }

    private void showAttachBar() {
        attachBar.removeAllViews();
        attachBar.setVisibility(View.VISIBLE);

        String name = Attachments.getFileName(this, attachedUri);
        String mime = Attachments.getMime(this, attachedUri);
        long size = Attachments.getSize(this, attachedUri);

        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable cbg = new GradientDrawable();
        cbg.setCornerRadius(dp(14));
        cbg.setColor(CARD);
        cbg.setStroke(dp(1), BORDER);
        chip.setBackground(cbg);

        TextView icon = new TextView(this);
        icon.setText(Attachments.isImage(mime) ? "\uD83D\uDDBC" : "\uD83D\uDCC4");
        icon.setTextSize(14);
        icon.setPadding(0, 0, dp(8), 0);
        chip.addView(icon);

        String label = name;
        if (label.length() > 22) label = label.substring(0, 22) + "…";
        if (size > 0) label += "  (" + (size / 1024) + " KB)";

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(12);
        tv.setTextColor(AI_TEXT);
        chip.addView(tv);

        TextView x = new TextView(this);
        x.setText("  ✕");
        x.setTextSize(12);
        x.setTextColor(MUTED);
        x.setPadding(dp(6), 0, 0, 0);
        x.setClickable(true);
        x.setOnClickListener(v -> {
            attachedUri = null;
            attachBar.setVisibility(View.GONE);
        });
        chip.addView(x);

        attachBar.addView(chip);
    }

    private JSONObject buildUserMessage(String text) throws JSONException {
        JSONObject m = new JSONObject();
        m.put("role", "user");
        if (attachedUri == null) {
            m.put("content", text);
            return m;
        }
        String mime = Attachments.getMime(this, attachedUri);
        JSONArray parts = new JSONArray();

        if (text != null && !text.isEmpty()) {
            JSONObject tp = new JSONObject();
            tp.put("type", "text");
            tp.put("text", text);
            parts.put(tp);
        }

        if (Attachments.isImage(mime)) {
            String dataUrl = Attachments.toDataUrl(this, attachedUri);
            if (dataUrl == null) {
                toast("Could not read image");
            } else {
                JSONObject ip = new JSONObject();
                ip.put("type", "image_url");
                JSONObject url = new JSONObject();
                url.put("url", dataUrl);
                ip.put("image_url", url);
                parts.put(ip);
            }
        } else {
            // Non-image: pass path so AI can use run_shell to read it
            String name = Attachments.getFileName(this, attachedUri);
            JSONObject fp = new JSONObject();
            fp.put("type", "text");
            fp.put("text", "[Attached file: " + name + " — URI: " + attachedUri.toString() +
                ". To read its contents, ask user to grant access or use run_shell if path is under /sdcard.]");
            parts.put(fp);
        }

        m.put("content", parts);
        return m;
    }

    private void autoHeal() {
        if (StarAccessibilityService.isRunning() && ShizukuHelper.hasSecureSettings(this)) return;
        if (!ShizukuHelper.isRunning() || !ShizukuHelper.hasPermission()) return;

        // Throttle: don't run more than once every 30 seconds
        long last = prefs.getLastHeal();
        long now = System.currentTimeMillis();
        if (now - last < 30000) return;
        prefs.setLastHeal(now);

        new Thread(() -> {
            try {
                String pkg = getPackageName();
                Shell.run("pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS");
                SelfHeal.repair(ChatActivity.this, 4000);
            } catch (Exception ignored) {}
        }).start();
    }






    private int withAlpha(int color, int a) {
        return (a << 24) | (color & 0x00FFFFFF);
    }









    private void pickMulti() {
        // ensure catalog is loaded
        if (ModelCatalog.cached() == null) {
            toast("Loading model list...");
            ModelCatalog.refresh(this, prefs, new ModelCatalog.Cb() {
                @Override public void onReady(java.util.List<ModelCatalog.Model> list) {
                    runOnUiThread(() -> showMultiDialog(list));
                }
                @Override public void onError(String e) {
                    runOnUiThread(() -> toast("Model list failed: " + e));
                }
            });
        } else {
            showMultiDialog(ModelCatalog.cached());
        }
    }

    private void showMultiDialog(java.util.List<ModelCatalog.Model> catalog) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF161616);
        root.setPadding(dp(16), dp(16), dp(16), dp(8));

        // ---- Mode selector ----
        TextView modeLabel = new TextView(this);
        modeLabel.setText("MODE");
        modeLabel.setTextSize(10);
        modeLabel.setTextColor(MUTED);
        modeLabel.setPadding(0, 0, 0, dp(6));
        root.addView(modeLabel);

        final String[] modes = {"off", "parallel", "mix", "chain"};
        final String[] modeLabels = {"Off", "Parallel", "Mix", "Chain"};
        final int[] selectedMode = {0};
        String cur = prefs.getMultiMode();
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(cur)) selectedMode[0] = i;

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        final java.util.List<TextView> modeBtns = new java.util.ArrayList<>();
        for (int i = 0; i < modes.length; i++) {
            final int idx = i;
            TextView mb = new TextView(this);
            mb.setText(modeLabels[i]);
            mb.setTextSize(12);
            mb.setTypeface(Typeface.DEFAULT_BOLD);
            mb.setGravity(Gravity.CENTER);
            mb.setPadding(dp(4), dp(9), dp(4), dp(9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = dp(4);
            mb.setLayoutParams(lp);
            mb.setClickable(true);
            mb.setOnClickListener(v -> {
                selectedMode[0] = idx;
                for (int k = 0; k < modeBtns.size(); k++) styleModeBtn(modeBtns.get(k), k == idx);
            });
            modeBtns.add(mb);
            modeRow.addView(mb);
        }
        for (int i = 0; i < modeBtns.size(); i++) styleModeBtn(modeBtns.get(i), i == selectedMode[0]);
        root.addView(modeRow);

        // single-model row (visible always, highlighted when Off)
        final TextView singleRow = new TextView(this);
        singleRow.setText("Single model: " + prefs.getModel());
        singleRow.setTextSize(11);
        singleRow.setTypeface(Typeface.MONOSPACE);
        singleRow.setTextColor(ACCENT);
        singleRow.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable srb = new GradientDrawable();
        srb.setCornerRadius(dp(8));
        srb.setColor(0x1A7CB3FF);
        singleRow.setBackground(srb);
        singleRow.setClickable(true);
        LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        srp.topMargin = dp(8);
        singleRow.setLayoutParams(srp);
        root.addView(singleRow);

        // separator
        TextView sp = new TextView(this);
        sp.setHeight(dp(12));
        root.addView(sp);

        // ---- Search ----
        TextView searchLabel = new TextView(this);
        searchLabel.setText("MODELS");
        searchLabel.setTextSize(10);
        searchLabel.setTextColor(MUTED);
        searchLabel.setPadding(0, 0, 0, dp(6));
        root.addView(searchLabel);

        EditText search = new EditText(this);
        search.setHint("search models...");
        search.setTextColor(AI_TEXT);
        search.setHintTextColor(MUTED);
        search.setBackgroundColor(0xFF1E1E1E);
        search.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(search);

        // ---- List ----
        LinearLayout listCol = new LinearLayout(this);
        listCol.setOrientation(LinearLayout.VERTICAL);

        final java.util.Map<String, CheckBox> boxes = new java.util.LinkedHashMap<>();
        final java.util.List<String> current = prefs.getMultiModels();

        final Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            listCol.removeAllViews();
            boxes.clear();
            String q = search.getText().toString().trim().toLowerCase();
            int shown = 0;
            for (ModelCatalog.Model m : catalog) {
                if (!q.isEmpty()) {
                    if (!m.id.toLowerCase().contains(q) && !(m.name != null && m.name.toLowerCase().contains(q))) continue;
                }
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(2), dp(6), dp(2), dp(6));

                CheckBox cb = new CheckBox(this);
                cb.setChecked(current.contains(m.id));
                boxes.put(m.id, cb);
                row.addView(cb);

                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView name = new TextView(this);
                name.setText(m.name == null ? m.id : m.name);
                name.setTextSize(13);
                name.setTextColor(AI_TEXT);
                name.setMaxLines(1);
                col.addView(name);

                TextView idTv = new TextView(this);
                idTv.setText(m.id);
                idTv.setTextSize(10);
                idTv.setTextColor(MUTED);
                idTv.setMaxLines(1);
                idTv.setTypeface(Typeface.MONOSPACE);
                col.addView(idTv);

                row.addView(col);

                TextView tag = new TextView(this);
                tag.setText(m.priceTag);
                tag.setTextSize(10);
                tag.setTypeface(Typeface.DEFAULT_BOLD);
                tag.setTextColor(m.free ? 0xFF7EE787 : 0xFFF0883E);
                tag.setPadding(dp(8), dp(3), dp(8), dp(3));
                GradientDrawable tbg = new GradientDrawable();
                tbg.setCornerRadius(dp(6));
                tbg.setColor(m.free ? 0x1A7EE787 : 0x1AF0883E);
                tag.setBackground(tbg);
                row.addView(tag);

                listCol.addView(row);
                shown++;
                if (shown >= 200) break;
            }
            if (shown == 0) {
                TextView empty = new TextView(this);
                empty.setText("No models match.");
                empty.setTextColor(MUTED);
                empty.setTextSize(12);
                empty.setPadding(dp(8), dp(20), dp(8), dp(20));
                listCol.addView(empty);
            }
        };
        rebuild[0].run();

        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
            public void onTextChanged(CharSequence c, int a, int b, int d) { rebuild[0].run(); }
            public void afterTextChanged(android.text.Editable e) {}
        });

        ScrollView sv = new ScrollView(this);
        sv.addView(listCol);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = dp(8);
        sv.setLayoutParams(slp);
        root.addView(sv);

        // ---- Bottom buttons ----
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.END);
        bottom.setPadding(0, dp(8), 0, 0);

        TextView cancel = new TextView(this);
        cancel.setText("CANCEL");
        cancel.setTextSize(13);
        cancel.setTypeface(Typeface.DEFAULT_BOLD);
        cancel.setTextColor(MUTED);
        cancel.setPadding(dp(16), dp(10), dp(16), dp(10));
        cancel.setClickable(true);

        TextView save = new TextView(this);
        save.setText("SAVE");
        save.setTextSize(13);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setTextColor(ACCENT);
        save.setPadding(dp(16), dp(10), dp(16), dp(10));
        save.setClickable(true);

        bottom.addView(cancel);
        bottom.addView(save);
        root.addView(bottom);

        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
            .setView(root)
            .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            java.util.List<String> sel = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, CheckBox> e : boxes.entrySet()) {
                if (e.getValue().isChecked()) sel.add(e.getKey());
            }
            String chosenMode = modes[selectedMode[0]];
            if ("off".equals(chosenMode)) {
                // Off = single model. Forget the multi list so ticks don't confuse later.
                sel = new java.util.ArrayList<>();
            }
            prefs.setMultiModels(sel);
            prefs.setMultiMode(chosenMode);
            refreshMultiPill();
            dialog.dismiss();
        });
        singleRow.setOnClickListener(v -> {
            try { dialog.dismiss(); } catch (Throwable ignored) {}
            postDelayed(() -> pickModel(), 220);
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                (int)(getResources().getDisplayMetrics().widthPixels * 0.92),
                (int)(getResources().getDisplayMetrics().heightPixels * 0.85));
        }
    }

    private void styleModeBtn(TextView tv, boolean active) {
        tv.setTextColor(active ? ACCENT : MUTED);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(active ? 0x1A7CB3FF : CARD);
        bg.setStroke(dp(1), active ? ACCENT : BORDER);
        tv.setBackground(bg);
    }

    private void refreshAgentPill() {
        agentPill.setAgent(agentMode);
    }

    private void refreshSkillsPill() {
        int total = com.star.app.candy.CandyManager.listInstalled().size();
        boolean configured = prefs.isSkillsConfigured();
        int enabled = configured ? prefs.getEnabledSkills().size() : total;

        if (total == 0) {
            skillsPill.setSkills(PillIconView.SK_NONE, 0);
        } else if (!configured || enabled >= total) {
            skillsPill.setSkills(PillIconView.SK_ALL, total);
        } else if (enabled == 0) {
            skillsPill.setSkills(PillIconView.SK_OFF, 0);
        } else {
            skillsPill.setSkills(PillIconView.SK_SOME, enabled);
        }
    }

    private void refreshMultiPill() {
        String mode = prefs.getMultiMode();
        int n = prefs.getMultiModels().size();
        boolean on = !"off".equals(mode) && n >= 2;
        if (!on) multiPill.setMulti(PillIconView.MM_SINGLE);
        else if ("parallel".equals(mode)) multiPill.setMulti(PillIconView.MM_PARALLEL);
        else if ("mix".equals(mode)) multiPill.setMulti(PillIconView.MM_MIX);
        else if ("chain".equals(mode)) multiPill.setMulti(PillIconView.MM_CHAIN);
        else multiPill.setMulti(PillIconView.MM_SINGLE);
        syncModelPillWithMulti();
    }

    private void syncModelPillWithMulti() {
        if (modelPill == null) return;
        String mode = prefs.getMultiMode();
        int n = prefs.getMultiModels().size();
        if (!"off".equals(mode) && n >= 2) {
            modelPill.setText("Multi " + mode + " \u00D7" + n);
        } else {
            try { refreshModelPill(); } catch (Throwable ignored) {}
        }
    }

    private void pickSkills() {
        final android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];
        final java.util.List<com.star.app.candy.Candy> all =
            com.star.app.candy.CandyManager.listInstalled();
        if (all.isEmpty()) { toast("No skills installed."); return; }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF161616);
        root.setPadding(dp(16), dp(16), dp(16), dp(8));

        // header row: title + quick all/none
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Skills");
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(AI_TEXT);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(title);

        TextView allBtn = smallTextBtn("ALL", ACCENT);
        TextView noneBtn = smallTextBtn("NONE", MUTED);
        head.addView(allBtn);
        head.addView(noneBtn);
        root.addView(head);

        TextView sub = new TextView(this);
        sub.setText("Tick the skills you want active in chat.");
        sub.setTextSize(11);
        sub.setTextColor(MUTED);
        sub.setPadding(0, dp(4), 0, dp(10));
        root.addView(sub);

        final java.util.Map<String, CheckBox> boxes = new java.util.LinkedHashMap<>();
        final java.util.Set<String> current = prefs.getEnabledSkills();
        final boolean firstTime = !prefs.isSkillsConfigured();

        LinearLayout listCol = new LinearLayout(this);
        listCol.setOrientation(LinearLayout.VERTICAL);
        for (com.star.app.candy.Candy c : all) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(8), dp(4), dp(8));

            CheckBox cb = new CheckBox(this);
            cb.setChecked(firstTime || current.contains(c.name));
            boxes.put(c.name, cb);
            row.addView(cb);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView name = new TextView(this);
            name.setText(c.name);
            name.setTextSize(14);
            name.setTextColor(AI_TEXT);
            col.addView(name);

            TextView tag = new TextView(this);
            tag.setText(c.repo == null ? "" : c.repo);
            tag.setTextSize(10);
            tag.setTextColor(MUTED);
            tag.setTypeface(Typeface.MONOSPACE);
            tag.setMaxLines(1);
            col.addView(tag);

            row.addView(col);

            // language chip
            if (c.language != null && !c.language.isEmpty()) {
                TextView chip = new TextView(this);
                chip.setText(c.language.toUpperCase());
                chip.setTextSize(9);
                chip.setTypeface(Typeface.DEFAULT_BOLD);
                int col2 = c.isPkg() ? 0xFF7EE787 : 0xFF7CB3FF;
                chip.setTextColor(col2);
                chip.setPadding(dp(6), dp(2), dp(6), dp(2));
                GradientDrawable cg = new GradientDrawable();
                cg.setCornerRadius(dp(6));
                cg.setColor(withAlpha(col2, 0x22));
                chip.setBackground(cg);
                row.addView(chip);
            }

            listCol.addView(row);
        }

        ScrollView sv = new ScrollView(this);
        sv.addView(listCol);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        svlp.topMargin = dp(4);
        sv.setLayoutParams(svlp);
        root.addView(sv);

        // bottom
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.END);
        bottom.setPadding(0, dp(8), 0, 0);

        TextView cancel = smallTextBtn("CANCEL", MUTED);
        TextView save = smallTextBtn("SAVE", ACCENT);
        bottom.addView(cancel);
        bottom.addView(save);
        root.addView(bottom);

        dialogRef[0] = new android.app.AlertDialog.Builder(this)
            .setView(root).create();

        allBtn.setOnClickListener(v -> {
            for (CheckBox cb : boxes.values()) cb.setChecked(true);
        });
        noneBtn.setOnClickListener(v -> {
            for (CheckBox cb : boxes.values()) cb.setChecked(false);
        });
        cancel.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
        save.setOnClickListener(v -> {
            java.util.Set<String> set = new java.util.HashSet<>();
            for (java.util.Map.Entry<String, CheckBox> e : boxes.entrySet()) {
                if (e.getValue().isChecked()) set.add(e.getKey());
            }
            prefs.setEnabledSkills(set);
            refreshSkillsPill();
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });

        dialogRef[0].setCanceledOnTouchOutside(true);
        dialogRef[0].setCancelable(true);
        dialogRef[0].show();
        if (dialogRef[0].getWindow() != null) {
            dialogRef[0].getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialogRef[0].getWindow().setLayout(
                (int)(getResources().getDisplayMetrics().widthPixels * 0.92),
                (int)(getResources().getDisplayMetrics().heightPixels * 0.85));
        }
    }

    private TextView smallTextBtn(String text, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(color);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        t.setClickable(true);
        return t;
    }

    private TextView thinkingLabel;
    private android.os.Handler thinkingHandler;
    private Runnable thinkingTick;
    private long thinkingStart = 0L;
    private String thinkingStage = "thinking";

    private void showThinking() {
        if (thinkingRow != null) {
            // already visible — just reset the stage
            return;
        }
        thinkingRow = new LinearLayout(this);
        thinkingRow.setOrientation(LinearLayout.HORIZONTAL);
        thinkingRow.setGravity(Gravity.CENTER_VERTICAL);
        thinkingRow.setPadding(dp(6), dp(10), dp(6), dp(10));

        TextView dot = new TextView(this);
        dot.setText("\u25CF");
        dot.setTextSize(10);
        dot.setTextColor(ACCENT);
        thinkingRow.addView(dot);

        thinkingLabel = new TextView(this);
        thinkingLabel.setText("  thinking  0s");
        thinkingLabel.setTextSize(12);
        thinkingLabel.setTextColor(MUTED);
        thinkingLabel.setTypeface(Typeface.MONOSPACE);
        thinkingRow.addView(thinkingLabel);

        final android.animation.ValueAnimator va = android.animation.ValueAnimator
            .ofFloat(0.3f, 1f);
        va.setDuration(700);
        va.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        va.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        va.addUpdateListener(a -> dot.setAlpha((Float) a.getAnimatedValue()));
        va.start();
        thinkingRow.setTag(va);

        thinkingStart = System.currentTimeMillis();
        thinkingStage = "thinking";
        thinkingHandler = new android.os.Handler(getMainLooper());
        thinkingTick = new Runnable() {
            @Override public void run() {
                if (thinkingRow == null || thinkingLabel == null) return;
                long sec = (System.currentTimeMillis() - thinkingStart) / 1000L;
                thinkingLabel.setText("  " + thinkingStage + "  " + sec + "s");
                thinkingHandler.postDelayed(this, 500L);
            }
        };
        thinkingHandler.post(thinkingTick);

        messages.addView(thinkingRow);
        scrollDown();
    }

    private void setThinkingStage(String stage) {
        thinkingStage = stage;
        if (thinkingLabel != null) {
            long sec = (System.currentTimeMillis() - thinkingStart) / 1000L;
            thinkingLabel.setText("  " + stage + "  " + sec + "s");
        }
    }

    private void hideThinking() {
        if (thinkingHandler != null && thinkingTick != null) {
            thinkingHandler.removeCallbacks(thinkingTick);
        }
        thinkingHandler = null;
        thinkingTick = null;
        if (thinkingRow != null) {
            Object tag = thinkingRow.getTag();
            if (tag instanceof android.animation.ValueAnimator) {
                ((android.animation.ValueAnimator) tag).cancel();
            }
            messages.removeView(thinkingRow);
            thinkingRow = null;
        }
        thinkingLabel = null;
    }

    private void addModelBubble(String role, String modelName, String stats, String body) {
        saveUiCardModel(role, modelName, stats, body);
        renderModelBubble(role, modelName, stats, body);
    }

    private void renderModelBubble(String role, String modelName, String stats, String body) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.setPadding(0, dp(6), 0, dp(6));

        // header
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(4), 0, dp(4), dp(6));

        String provider = providerOf(modelName);
        int badgeColor = providerColor(provider);

        // badge: initials behind, logo on top
        int bsz = dp(26);
        FrameLayout badgeFrame = new FrameLayout(this);
        LinearLayout.LayoutParams bflp = new LinearLayout.LayoutParams(bsz, bsz);
        bflp.rightMargin = dp(10);
        badgeFrame.setLayoutParams(bflp);

        GradientDrawable bd = new GradientDrawable();
        bd.setShape(GradientDrawable.OVAL);
        bd.setColor(withAlpha(badgeColor, 0x22));
        bd.setStroke(dp(1), badgeColor);
        badgeFrame.setBackground(bd);

        TextView initials = new TextView(this);
        initials.setText(providerInitials(provider));
        initials.setTextSize(9);
        initials.setTypeface(Typeface.DEFAULT_BOLD);
        initials.setTextColor(badgeColor);
        initials.setGravity(Gravity.CENTER);
        badgeFrame.addView(initials, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView logo = new ImageView(this);
        logo.setVisibility(View.GONE);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = dp(3);
        logo.setPadding(pad, pad, pad, pad);
        badgeFrame.addView(logo, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ProviderIcons.load(this, provider, logo, initials);
        head.addView(badgeFrame);

        // role + model name
        LinearLayout nameCol = new LinearLayout(this);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        nameCol.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (role != null && !role.equals("answer") && !role.isEmpty()) {
            TextView roleTv = new TextView(this);
            roleTv.setText(role.toUpperCase());
            roleTv.setTextSize(9);
            roleTv.setTypeface(Typeface.DEFAULT_BOLD);
            roleTv.setLetterSpacing(0.15f);
            roleTv.setTextColor(
                "merge".equals(role) ? 0xFFA78BFA :
                "final".equals(role) ? 0xFF7EE787 :
                "critique".equals(role) ? 0xFFF0883E : ACCENT);
            nameCol.addView(roleTv);
        }

        TextView name = new TextView(this);
        name.setText(modelName);
        name.setTextSize(11);
        name.setTypeface(Typeface.MONOSPACE);
        name.setTextColor(ACCENT);
        name.setMaxLines(1);
        nameCol.addView(name);
        head.addView(nameCol);

        if (stats != null && !stats.isEmpty()) {
            TextView st = new TextView(this);
            st.setText(stats);
            st.setTextSize(10);
            st.setTextColor(MUTED);
            st.setTypeface(Typeface.MONOSPACE);
            head.addView(st);
        }
        wrap.addView(head);

        // body card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(0xFF141414);
        bg.setStroke(dp(1), 0xFF232323);
        card.setBackground(bg);

        TextView t = new TextView(this);
        t.setTextSize(14);
        t.setTextColor(AI_TEXT);
        t.setLineSpacing(dp(3), 1f);
        t.setTextIsSelectable(true);
        t.setText(Markdown.render(body));
        t.setOnLongClickListener(v -> { copyToClip(body); return true; });
        card.addView(t);
        wrap.addView(card);

        messages.addView(wrap);
        scrollDown();
    }


    private String providerOf(String model) {
        if (model == null) return "";
        int slash = model.indexOf('/');
        if (slash < 0) return model.toLowerCase();
        return model.substring(0, slash).toLowerCase();
    }

    private int providerColor(String provider) {
        switch (provider) {
            case "openai":       return 0xFF10A37F;
            case "anthropic":    return 0xFFD97757;
            case "google":       return 0xFF4285F4;
            case "meta-llama":
            case "meta":         return 0xFF0866FF;
            case "deepseek":     return 0xFF4D6BFE;
            case "nvidia":       return 0xFF76B900;
            case "mistralai":
            case "mistral":      return 0xFFFF7000;
            case "qwen":         return 0xFF7C3AED;
            case "x-ai":
            case "xai":          return 0xFFE5E7EB;
            case "cohere":       return 0xFF39594D;
            case "perplexity":   return 0xFF20B8CD;
            case "moonshotai":   return 0xFF111111;
            case "amazon":       return 0xFFFF9900;
            case "microsoft":    return 0xFF00A4EF;
            case "ai21":         return 0xFFE0234E;
            case "liquid":       return 0xFF00D4FF;
            case "thudm":        return 0xFF3B82F6;
            case "nousresearch": return 0xFFD4A574;
            case "sao10k":       return 0xFFEC4899;
            case "gryphe":       return 0xFF8B5CF6;
            case "undi95":       return 0xFFF59E0B;
            case "openrouter":   return 0xFF7CB3FF;
            default:             return 0xFF7CB3FF;
        }
    }

    private String providerInitials(String provider) {
        switch (provider) {
            case "openai":       return "AI";
            case "anthropic":    return "AN";
            case "google":       return "G";
            case "meta-llama":
            case "meta":         return "M";
            case "deepseek":     return "DS";
            case "nvidia":       return "NV";
            case "mistralai":
            case "mistral":      return "MI";
            case "qwen":         return "Q";
            case "x-ai":
            case "xai":          return "X";
            case "cohere":       return "CO";
            case "perplexity":   return "PX";
            case "moonshotai":   return "MO";
            case "amazon":       return "AM";
            case "microsoft":    return "MS";
            case "ai21":         return "21";
            case "liquid":       return "LQ";
            case "thudm":        return "GL";
            case "nousresearch": return "NR";
            case "sao10k":       return "SA";
            case "gryphe":       return "GR";
            case "undi95":       return "U9";
            case "openrouter":   return "OR";
            default:
                if (provider.length() >= 2) return provider.substring(0, 2).toUpperCase();
                return provider.toUpperCase();
        }
    }

    private void saveUiCardSystem(String label, String content, int color) {
        try {
            JSONObject c = new JSONObject();
            c.put("role", "ui_card");
            c.put("kind", "system");
            c.put("label", label == null ? "" : label);
            c.put("color", color);
            c.put("content", content == null ? "" : content);
            history.put(c);
        } catch (Exception ignored) {}
    }

    private void saveUiCardModel(String role, String modelName, String stats, String body) {
        try {
            JSONObject c = new JSONObject();
            c.put("role", "ui_card");
            c.put("kind", "model");
            c.put("mRole", role == null ? "answer" : role);
            c.put("model", modelName == null ? "" : modelName);
            c.put("stats", stats == null ? "" : stats);
            c.put("content", body == null ? "" : body);
            history.put(c);
        } catch (Exception ignored) {}
    }

    private void openSessionMenu() {
        final android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF161616);
        root.setPadding(dp(16), dp(16), dp(16), dp(8));

        // --- New chat row ---
        TextView newRow = new TextView(this);
        newRow.setText("+  New chat");
        newRow.setTextSize(14);
        newRow.setTypeface(Typeface.DEFAULT_BOLD);
        newRow.setTextColor(ACCENT);
        newRow.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable nb = new GradientDrawable();
        nb.setCornerRadius(dp(10));
        nb.setColor(0x1A7CB3FF);
        newRow.setBackground(nb);
        newRow.setClickable(true);
        root.addView(newRow);

        // --- Sections label ---
        TextView lbl = new TextView(this);
        lbl.setText("RECENT");
        lbl.setTextSize(10);
        lbl.setTextColor(MUTED);
        lbl.setLetterSpacing(0.15f);
        lbl.setPadding(dp(4), dp(16), dp(4), dp(6));
        root.addView(lbl);

        // --- Session list ---
        LinearLayout listCol = new LinearLayout(this);
        listCol.setOrientation(LinearLayout.VERTICAL);
        java.util.List<Session> sessions = SessionStore.listSessions();
        if (sessions.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No conversations yet.");
            empty.setTextSize(12);
            empty.setTextColor(MUTED);
            empty.setPadding(dp(12), dp(12), dp(12), dp(12));
            listCol.addView(empty);
        } else {
            for (Session sess : sessions) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));
                GradientDrawable rb = new GradientDrawable();
                rb.setCornerRadius(dp(10));
                boolean isCur = sess.id != null && sess.id.equals(currentSessionId);
                rb.setColor(isCur ? 0x1A7CB3FF : 0x00000000);
                row.setBackground(rb);
                row.setClickable(true);

                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView title = new TextView(this);
                String t = sess.title == null ? "" : sess.title;
                if (t.isEmpty() || t.equals("Untitled")) t = "New chat";
                title.setText(t);
                title.setTextSize(13);
                title.setTextColor(AI_TEXT);
                title.setMaxLines(1);
                col.addView(title);

                TextView meta = new TextView(this);
                meta.setText(sess.count + " messages  ·  " + ago(sess.updated));
                meta.setTextSize(10);
                meta.setTextColor(MUTED);
                meta.setTypeface(Typeface.MONOSPACE);
                col.addView(meta);

                row.addView(col);

                TextView del = new TextView(this);
                del.setText("\u2715");
                del.setTextSize(14);
                del.setTextColor(MUTED);
                del.setPadding(dp(8), dp(8), dp(8), dp(8));
                del.setClickable(true);

                final String sid = sess.id;
                final String stitle = t;
                row.setOnClickListener(v -> {
                    switchSession(sid);
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                });
                row.setOnLongClickListener(v -> {
                    new android.app.AlertDialog.Builder(this)
                        .setItems(new String[]{"Rename", "Delete"}, (d, w) -> {
                            if (w == 0) {
                                EditText in = new EditText(this);
                                in.setTextColor(AI_TEXT);
                                in.setText(stitle);
                                new android.app.AlertDialog.Builder(this)
                                    .setTitle("Rename")
                                    .setView(in)
                                    .setPositiveButton("Save", (d2, w2) -> {
                                        SessionStore.rename(sid, in.getText().toString());
                                        if (sid.equals(currentSessionId)) {
                                            for (Session ss : SessionStore.listSessions())
                                                if (sid.equals(ss.id)) currentSession = ss;
                                            refreshSessionPill();
                                        }
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                            } else {
                                SessionStore.delete(sid);
                                if (sid.equals(currentSessionId)) {
                                    Session s2 = SessionStore.create("");
                                    currentSessionId = s2.id;
                                    currentSession = s2;
                                    prefs.setCurrentSessionId(s2.id);
                                    history = new JSONArray();
                                    messages.removeAllViews();
                                    showEmptyState();
                                    refreshSessionPill();
                                }
                            }
                        })
                        .show();
                    return true;
                });
                del.setOnClickListener(v -> {
                    SessionStore.delete(sid);
                    if (sid.equals(currentSessionId)) {
                        Session s2 = SessionStore.create("");
                        currentSessionId = s2.id;
                        currentSession = s2;
                        prefs.setCurrentSessionId(s2.id);
                        history = new JSONArray();
                        messages.removeAllViews();
                        showEmptyState();
                        refreshSessionPill();
                    }
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                });
                row.addView(del);

                listCol.addView(row);
            }
        }

        ScrollView sv = new ScrollView(this);
        sv.addView(listCol);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = dp(4);
        sv.setLayoutParams(slp);
        root.addView(sv);

        // --- Change model row ---
        TextView modelRow = new TextView(this);
        modelRow.setText("Model: " + prefs.getModel());
        modelRow.setTextSize(12);
        modelRow.setTypeface(Typeface.MONOSPACE);
        modelRow.setTextColor(MUTED);
        modelRow.setPadding(dp(12), dp(14), dp(12), dp(14));
        modelRow.setClickable(true);
        modelRow.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            postDelayed(() -> pickModel(), 220);
        });
        root.addView(modelRow);

        TextView videoRow = new TextView(this);
        videoRow.setText("Video Studio  \u2192  text to video");
        videoRow.setTextSize(12);
        videoRow.setTypeface(Typeface.MONOSPACE);
        videoRow.setTextColor(0xFFF0883E);
        videoRow.setPadding(dp(12), dp(14), dp(12), dp(14));
        videoRow.setClickable(true);
        videoRow.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            try {
                startActivity(new android.content.Intent(
                    ChatActivity.this, VideoStudioActivity.class));
            } catch (Throwable ignored) {}
        });
        root.addView(videoRow);

        TextView liveRow = new TextView(this);
        liveRow.setText("Live Mode  \u2192  watch screen + speak");
        liveRow.setTextSize(12);
        liveRow.setTypeface(Typeface.MONOSPACE);
        liveRow.setTextColor(0xFF7EE787);
        liveRow.setPadding(dp(12), dp(14), dp(12), dp(14));
        liveRow.setClickable(true);
        liveRow.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            try {
                startActivity(new android.content.Intent(
                    ChatActivity.this, LiveModeActivity.class));
            } catch (Throwable ignored) {}
        });
        root.addView(liveRow);

        dialogRef[0] = new android.app.AlertDialog.Builder(this)
            .setView(root).create();
        newRow.setOnClickListener(v -> {
            Session sNew = SessionStore.create("");
            currentSessionId = sNew.id;
            currentSession = sNew;
            prefs.setCurrentSessionId(sNew.id);
            history = new JSONArray();
            messages.removeAllViews();
            prefs.setHistory("");
            showEmptyState();
            refreshSessionPill();
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });

        dialogRef[0].setCanceledOnTouchOutside(true);
        dialogRef[0].setCancelable(true);
        dialogRef[0].show();
        if (dialogRef[0].getWindow() != null) {
            dialogRef[0].getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialogRef[0].getWindow().setLayout(
                (int)(getResources().getDisplayMetrics().widthPixels * 0.9),
                (int)(getResources().getDisplayMetrics().heightPixels * 0.75));
        }
    }

    private void postDelayed(Runnable r, long ms) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(r, ms);
    }

    private boolean dialogDismiss = false;

    private void switchSession(String id) {
        if (id == null || id.equals(currentSessionId)) return;
        // save current
        try {
            SessionStore.saveMessages(currentSessionId, history);
        } catch (Exception ignored) {}
        // switch
        currentSessionId = id;
        prefs.setCurrentSessionId(id);
        for (Session s : SessionStore.listSessions()) {
            if (id.equals(s.id)) { currentSession = s; break; }
        }
        history = SessionStore.loadMessages(id);
        messages.removeAllViews();
        toolCallDepth = 0;
        stopRequested = false;
        // re-render
        for (int i = 0; i < history.length(); i++) {
            try {
                JSONObject m = history.getJSONObject(i);
                String role = m.optString("role");
                String content = m.optString("content", "");
                if (content == null) content = "";
                String t = content.trim();
                if (t.isEmpty() || t.equalsIgnoreCase("null") || t.equals("{}")) continue;
                if ("user".equals(role)) addUserBubble(content);
                else if ("assistant".equals(role)) addAIBubble(content);
                else if ("ui_card".equals(role)) {
                    String kind = m.optString("kind");
                    if ("system".equals(kind)) {
                        int cc = m.optInt("color", 0xFF8E8E8E);
                        renderSystemLine(m.optString("label"), content, cc);
                    } else if ("model".equals(kind)) {
                        renderModelBubble(
                            m.optString("mRole", "answer"),
                            m.optString("model"),
                            m.optString("stats"),
                            content);
                    }
                }
            } catch (Exception ignored) {}
        }
        refreshSessionPill();
    }

    private String ago(long t) {
        long d = System.currentTimeMillis() - t;
        if (d < 60_000L) return "now";
        if (d < 3_600_000L) return (d / 60_000L) + "m";
        if (d < 86_400_000L) return (d / 3_600_000L) + "h";
        return (d / 86_400_000L) + "d";
    }

    // ============ STUDIO RADIAL ============
    private View studioRadial = null;
    private final java.util.List<View> studioSlotViews = new java.util.ArrayList<>();
    private final java.util.List<int[]> studioSlotCenters = new java.util.ArrayList<>();
    private Runnable studioLongPress = null;
    private boolean studioLongPressFired = false;
    private int studioHighlight = -1;

    private static final String[] STUDIO_NAMES = { "Image", "Video", "Audio", "3D", "2D" };
    private static final int[] STUDIO_COLORS = {
        0xFF7EE787, 0xFFF0883E, 0xFF7CB3FF, 0xFFA78BFA, 0xFFE5E7EB
    };
    private static final String[] STUDIO_GLYPHS = { "\u25A3", "\u25B6", "\u266A", "\u25C8", "\u25C7" };

    private void installStudioGesture(final View row) {
        row.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        studioLongPressFired = false;
                        studioLongPress = () -> {
                            studioLongPressFired = true;
                            showStudioRadial(row);
                        };
                        row.postDelayed(studioLongPress, 400);
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE: {
                        if (studioLongPressFired) {
                            int idx = studioSlotUnder(e.getRawX(), e.getRawY());
                            studioHighlightSlot(idx);
                            return true;
                        }
                        float dx = Math.abs(e.getRawX() - downX);
                        float dy = Math.abs(e.getRawY() - downY);
                        if (dx > dp(16) || dy > dp(16)) {
                            if (studioLongPress != null) {
                                row.removeCallbacks(studioLongPress);
                                studioLongPress = null;
                            }
                        }
                        return true;
                    }
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL: {
                        if (studioLongPress != null) {
                            row.removeCallbacks(studioLongPress);
                            studioLongPress = null;
                        }
                        if (studioLongPressFired) {
                            int idx = studioSlotUnder(e.getRawX(), e.getRawY());
                            hideStudioRadial();
                            studioLongPressFired = false;
                            if (idx >= 0) fireStudioSlot(idx);
                            return true;
                        }
                        float dx = Math.abs(e.getRawX() - downX);
                        float dy = Math.abs(e.getRawY() - downY);
                        if (dx < dp(12) && dy < dp(12)) {
                            closeDrawer();
                            startActivity(new Intent(ChatActivity.this, StudioActivity.class));
                        }
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void showStudioRadial(View row) {
        hideStudioRadial();

        int[] rowLoc = new int[2];
        row.getLocationOnScreen(rowLoc);
        int anchorRightScreen = rowLoc[0] + row.getWidth() + dp(4);
        int anchorTopScreen = rowLoc[1];

        int[] rootLoc = new int[2];
        rootFrame.getLocationOnScreen(rootLoc);
        int anchorX = anchorRightScreen - rootLoc[0];
        int anchorY = anchorTopScreen - rootLoc[1];

        int rowH = dp(52);
        int gap = dp(2);
        int N = STUDIO_NAMES.length;
        int colH = N * rowH + (N - 1) * gap;
        int width = dp(220);

        int screenH = rootFrame.getHeight();
        int startY = anchorY;
        if (startY + colH > screenH - dp(20)) startY = screenH - colH - dp(20);
        if (startY < dp(20)) startY = dp(20);

        FrameLayout overlay = new FrameLayout(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.setBackgroundColor(0x66000000);
        overlay.setClickable(true);
        overlay.setAlpha(0f);

        studioSlotViews.clear();
        studioSlotCenters.clear();

        // container with a subtle dark rounded background (like a menu)
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable colBg = new GradientDrawable();
        colBg.setCornerRadius(dp(14));
        colBg.setColor(0xFF0F0F0F);
        colBg.setStroke(dp(1), 0xFF2A2A2A);
        column.setBackground(colBg);
        column.setAlpha(0f);
        column.setTranslationX(-dp(20));
        column.setScaleX(0.9f);
        column.setScaleY(0.9f);

        for (int i = 0; i < N; i++) {
            final int idx = i;
            int sy = startY + i * (rowH + gap);
            studioSlotCenters.add(new int[]{ anchorX + width / 2, sy + rowH / 2 });

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(14), dp(10), dp(14), dp(10));
            item.setLayoutParams(new LinearLayout.LayoutParams(
                width, rowH));
            item.setBackgroundColor(0x00000000);

            // icon (Canvas glyph, monochrome)
            StudioGlyphView glyph = new StudioGlyphView(this);
            glyph.setType(i);
            glyph.setAccent(0xFFECECEC);      // white icon
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                dp(32), dp(32));
            glp.rightMargin = dp(12);
            glyph.setLayoutParams(glp);
            item.addView(glyph);

            TextView label = new TextView(this);
            label.setText(STUDIO_NAMES[i]);
            label.setTextSize(15);
            label.setTextColor(0xFFECECEC);
            label.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            item.addView(label);

            column.addView(item);
            studioSlotViews.add(item);
        }

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(width,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = anchorX;
        clp.topMargin = startY;
        overlay.addView(column, clp);

        rootFrame.addView(overlay);
        studioRadial = overlay;
        studioHighlight = -1;

        overlay.animate().alpha(1f).setDuration(160).start();
        column.animate()
            .alpha(1f)
            .translationX(0f)
            .scaleX(1f).scaleY(1f)
            .setDuration(260)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();
    }

    private int studioSlotUnder(float rawX, float rawY) {
        if (studioRadial == null) return -1;
        int[] rootLoc = new int[2];
        rootFrame.getLocationOnScreen(rootLoc);
        float rx = rawX - rootLoc[0];
        float ry = rawY - rootLoc[1];
        int hit = dp(60);
        for (int i = 0; i < studioSlotCenters.size(); i++) {
            int[] c = studioSlotCenters.get(i);
            int dx = (int)(rx - c[0]);
            int dy = (int)(ry - c[1]);
            if (dx * dx + dy * dy <= hit * hit) return i;
        }
        return -1;
    }

    private void studioHighlightSlot(int idx) {
        if (studioHighlight == idx) return;
        studioHighlight = idx;
        for (int i = 0; i < studioSlotViews.size(); i++) {
            View cell = studioSlotViews.get(i);
            boolean on = (i == idx);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(10));
            bg.setColor(on ? 0xFF1E1E1E : 0x00000000);
            cell.setBackground(bg);
            cell.setAlpha(on ? 1f : (idx == -1 ? 1f : 0.85f));
            // toggle inner glyph hover state
            try {
                LinearLayout ll = (LinearLayout) cell;
                for (int k = 0; k < ll.getChildCount(); k++) {
                    View ch = ll.getChildAt(k);
                    if (ch instanceof StudioGlyphView) {
                        ((StudioGlyphView) ch).setHighlighted(on);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private void hideStudioRadial() {
        if (studioRadial == null) return;
        final View v = studioRadial;
        studioRadial = null;
        studioSlotViews.clear();
        studioSlotCenters.clear();
        studioHighlight = -1;
        v.animate().alpha(0f).setDuration(140)
            .withEndAction(() -> {
                try { rootFrame.removeView(v); } catch (Throwable ignored) {}
            }).start();
    }

    private void fireStudioSlot(int idx) {
        closeDrawer();
        try {
            switch (idx) {
                case 0: startActivity(new Intent(this, ImageStudioActivity.class)); break;
                case 1: startActivity(new Intent(this, VideoStudioActivity.class)); break;
                case 2: startActivity(new Intent(this, AudioStudioActivity.class)); break;
                case 3:
                case 4:
                    startActivity(new Intent(this, StudioActivity.class));
                    Toast.makeText(this, STUDIO_NAMES[idx] + " tab", Toast.LENGTH_SHORT).show();
                    break;
            }
        } catch (Throwable t) {
            Toast.makeText(this, "Open failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showTopIconInfo(String title, String description, String state,
                                 String actionLabel, Runnable action,
                                 String clearLabel, Runnable clearAction) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF161616);
        root.setPadding(dp(18), dp(18), dp(18), dp(8));

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextSize(18);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setTextColor(0xFFECECEC);
        root.addView(titleTv);

        TextView desc = new TextView(this);
        desc.setText(description);
        desc.setTextSize(13);
        desc.setTextColor(0xFFB8B8B8);
        desc.setLineSpacing(dp(3), 1f);
        desc.setPadding(0, dp(10), 0, dp(10));
        root.addView(desc);

        LinearLayout stateRow = new LinearLayout(this);
        stateRow.setOrientation(LinearLayout.HORIZONTAL);
        stateRow.setGravity(Gravity.CENTER_VERTICAL);
        stateRow.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable sbg = new GradientDrawable();
        sbg.setCornerRadius(dp(10));
        sbg.setColor(0xFF1E1E1E);
        sbg.setStroke(dp(1), 0xFF2A2A2A);
        stateRow.setBackground(sbg);
        LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        srlp.topMargin = dp(4);
        srlp.bottomMargin = dp(14);
        stateRow.setLayoutParams(srlp);

        TextView stateLabel = new TextView(this);
        stateLabel.setText("CURRENT  ·  ");
        stateLabel.setTextSize(10);
        stateLabel.setTextColor(0xFF8E8E8E);
        stateLabel.setLetterSpacing(0.15f);
        stateRow.addView(stateLabel);

        TextView stateVal = new TextView(this);
        stateVal.setText(state);
        stateVal.setTextSize(12);
        stateVal.setTypeface(Typeface.MONOSPACE);
        stateVal.setTextColor(0xFF7CB3FF);
        stateVal.setTextIsSelectable(true);
        stateRow.addView(stateVal);
        root.addView(stateRow);

        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];

        if (actionLabel != null && action != null) {
            TextView ab = makeInfoButton(actionLabel, 0xFF7CB3FF);
            ab.setOnClickListener(v -> {
                action.run();
                if (ref[0] != null) ref[0].dismiss();
            });
            root.addView(ab);
        }

        if (clearLabel != null && clearAction != null) {
            TextView cb = makeInfoButton(clearLabel, 0xFFF0883E);
            LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cblp.topMargin = dp(6);
            cb.setLayoutParams(cblp);
            cb.setOnClickListener(v -> {
                clearAction.run();
                if (ref[0] != null) ref[0].dismiss();
            });
            root.addView(cb);
        }

        TextView cancel = new TextView(this);
        cancel.setText("CLOSE");
        cancel.setTextSize(12);
        cancel.setTypeface(Typeface.DEFAULT_BOLD);
        cancel.setTextColor(0xFF8E8E8E);
        cancel.setGravity(Gravity.END);
        cancel.setPadding(dp(16), dp(14), dp(16), dp(14));
        cancel.setClickable(true);
        root.addView(cancel);

        ref[0] = new android.app.AlertDialog.Builder(this)
            .setView(root).create();
        if (ref[0].getWindow() != null) {
            ref[0].getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        cancel.setOnClickListener(v -> ref[0].dismiss());
        ref[0].show();
        if (ref[0].getWindow() != null) {
            ref[0].getWindow().setLayout(
                (int)(getResources().getDisplayMetrics().widthPixels * 0.9),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private TextView makeInfoButton(String text, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(0x12FFFFFF);
        bg.setStroke(dp(1), color);
        t.setBackground(bg);
        t.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        t.setLayoutParams(lp);
        return t;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
