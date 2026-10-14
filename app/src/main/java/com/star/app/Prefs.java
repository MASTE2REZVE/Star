package com.star.app;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    public java.util.Set<String> getEnabledSkills() {
        String raw = sp.getString("enabled_skills", "");
        java.util.Set<String> out = new java.util.HashSet<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String s : raw.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    public void setEnabledSkills(java.util.Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() > 0) sb.append(",");
            sb.append(s);
        }
        sp.edit().putString("enabled_skills", sb.toString())
                 .putBoolean("skills_configured", true)
                 .apply();
    }

    public boolean isSkillEnabled(String name) {
        if (!sp.getBoolean("skills_configured", false)) return true;
        return getEnabledSkills().contains(name);
    }

    public String getCurrentSessionId() {
        return sp.getString("current_session_id", "");
    }

    public void setCurrentSessionId(String id) {
        sp.edit().putString("current_session_id", id == null ? "" : id).apply();
    }

    public String getLiveModel() {
        String m = sp.getString("live_model", "");
        // the old default no longer exists on OpenRouter
        if ("google/gemini-2.0-flash-exp:free".equals(m)) m = "";
        if (m == null || m.isEmpty()) m = "google/gemini-flash-1.5-8b";
        return m;
    }
    public void setLiveModel(String m) { sp.edit().putString("live_model", m).apply(); }

    public int getLiveIntervalSec() {
        return sp.getInt("live_interval", 10);
    }
    public void setLiveIntervalSec(int s) { sp.edit().putInt("live_interval", s).apply(); }

    public String getLivePrompt() {
        return sp.getString("live_prompt",
            "You are watching the tablet screen. Say in ONE short sentence " +
            "what is on screen. If you see an error, dialog, or notification, " +
            "say it first. Do not repeat what you said before. Be plain and direct.");
    }
    public void setLivePrompt(String p) { sp.edit().putString("live_prompt", p).apply(); }

    public boolean getLiveVoice() { return sp.getBoolean("live_voice", true); }
    public void setLiveVoice(boolean v) { sp.edit().putBoolean("live_voice", v).apply(); }

    public String getGitHubToken() {
        return sp.getString("github_token", "");
    }
    public void setGitHubToken(String t) {
        sp.edit().putString("github_token", t == null ? "" : t).apply();
    }

    public String getGitHubUser() {
        return sp.getString("github_user", "");
    }
    public void setGitHubUser(String u) {
        sp.edit().putString("github_user", u == null ? "" : u).apply();
    }

    /** Sandbox repo where Star drops workflow files. Format: owner/repo */
    public String getSandboxRepo() {
        return sp.getString("github_sandbox", "");
    }
    public void setSandboxRepo(String r) {
        sp.edit().putString("github_sandbox", r == null ? "" : r).apply();
    }

    public String getBuilderModel() {
        return sp.getString("builder_model", "");
    }
    public void setBuilderModel(String m) {
        sp.edit().putString("builder_model", m == null ? "" : m).apply();
    }

    public String getBuilderFallback() {
        return sp.getString("builder_fallback", "openai/gpt-4o-mini");
    }
    public void setBuilderFallback(String m) {
        sp.edit().putString("builder_fallback", m == null ? "" : m).apply();
    }

    public String getBillingToken() {
        return sp.getString("billing_token", "");
    }
    public void setBillingToken(String t) {
        sp.edit().putString("billing_token", t == null ? "" : t).apply();
    }

    public String getModelProvider(String modelId) {
        if (modelId == null || modelId.isEmpty()) return "";
        return sp.getString("prov_" + modelId, "");
    }
    public void setModelProvider(String modelId, String provider) {
        if (modelId == null || modelId.isEmpty()) return;
        sp.edit().putString("prov_" + modelId, provider == null ? "" : provider).apply();
    }

    public String getGeminiKey()     { return sp.getString("k_gemini", ""); }
    public void setGeminiKey(String v) { sp.edit().putString("k_gemini", v).apply(); }

    public String getOpenAiKey()     { return sp.getString("k_openai", ""); }
    public void setOpenAiKey(String v) { sp.edit().putString("k_openai", v).apply(); }

    public String getGrokKey()       { return sp.getString("k_grok", ""); }
    public void setGrokKey(String v) { sp.edit().putString("k_grok", v).apply(); }

    public String getClaudeKey()     { return sp.getString("k_claude", ""); }
    public void setClaudeKey(String v) { sp.edit().putString("k_claude", v).apply(); }

    public String getPerplexityKey()     { return sp.getString("k_perplexity", ""); }
    public void setPerplexityKey(String v) { sp.edit().putString("k_perplexity", v).apply(); }

    public String getWorkerModel() {
        return sp.getString("worker_model", "");
    }
    public void setWorkerModel(String m) {
        sp.edit().putString("worker_model", m == null ? "" : m).apply();
    }

    public String getWorkerSystemPrompt() {
        String p = sp.getString("worker_prompt", "");
        if (p == null || p.isEmpty()) {
            p = "You are a focused reasoning worker for Star. Answer the user's task "
              + "directly and completely. No preamble, no filler. If the task is to "
              + "write code, produce only the code with a short header comment. If the "
              + "task is analysis, give the answer and nothing else. Keep it dense.";
        }
        return p;
    }
    public void setWorkerSystemPrompt(String v) {
        sp.edit().putString("worker_prompt", v == null ? "" : v).apply();
    }

    public boolean isSkillsConfigured() {
        return sp.getBoolean("skills_configured", false);
    }

    // ---------- multi-model ----------

    public java.util.List<String> getMultiModels() {
        String raw = sp.getString("multi_models", "");
        java.util.List<String> out = new java.util.ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String s : raw.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    public void setMultiModels(java.util.List<String> models) {
        StringBuilder sb = new StringBuilder();
        for (String s : models) {
            if (sb.length() > 0) sb.append(",");
            sb.append(s);
        }
        sp.edit().putString("multi_models", sb.toString()).apply();
    }

    public String getMultiMode() {
        return sp.getString("multi_mode", "off");  // off | parallel | mix | chain
    }

    public void setMultiMode(String m) {
        sp.edit().putString("multi_mode", m == null ? "off" : m).apply();
    }

    public String getMultiJudge() {
        return sp.getString("multi_judge", "");
    }

    public void setMultiJudge(String m) {
        sp.edit().putString("multi_judge", m == null ? "" : m).apply();
    }

    private final SharedPreferences sp;
    public Prefs(Context c) { sp = c.getSharedPreferences("star", Context.MODE_PRIVATE); }

    public String getEngine()      { return sp.getString("engine", "dsh"); }
    public String getApiKey()      { return sp.getString("api_key", ""); }
    public String getBaseUrl()     { return sp.getString("base_url", "https://api.deepseek.com/v1"); }
    public String getModel()       {
        String m = sp.getString("model", "deepseek/deepseek-chat-v3.1:free");
        boolean fix = false;
        if (m == null || m.isEmpty() || m.indexOf('/') < 0) fix = true;
        // OpenRouter rejects these exact IDs as ambiguous now.
        if ("deepseek/deepseek-chat".equals(m)
         || "deepseek/deepseek-reasoner".equals(m)
         || "meta-llama/llama-3.3-70b-instruct".equals(m)) fix = true;
        if (fix) {
            m = "deepseek/deepseek-chat-v3.1:free";
            sp.edit().putString("model", m).apply();
        }
        return m;
    }
    public String getHistory()     { return sp.getString("history", ""); }
    public String getUpdateUrl()   { return sp.getString("update_url", ""); }
    public String getInstructions(){ return sp.getString("instructions", ""); }
    public int    getMaxTools()    { return sp.getInt("max_tools", 12); }
    public boolean getAgentMode()  { return sp.getBoolean("agent_mode", false); }
    public boolean getAutoRepair() { return sp.getBoolean("auto_repair", true); }
    public long getLastHeal() { return sp.getLong("last_heal", 0L); }
    public void setLastHeal(long v) { sp.edit().putLong("last_heal", v).apply(); }

    public void setEngine(String v)       { sp.edit().putString("engine", v).apply(); }
    public void setApiKey(String v)       { sp.edit().putString("api_key", v).apply(); }
    public void setBaseUrl(String v)      { sp.edit().putString("base_url", v).apply(); }
    public void setModel(String v)        { sp.edit().putString("model", v).apply(); }
    public void setHistory(String v)      { sp.edit().putString("history", v).apply(); }
    public void setUpdateUrl(String v)    { sp.edit().putString("update_url", v).apply(); }
    public void setInstructions(String v) { sp.edit().putString("instructions", v).apply(); }
    public void setMaxTools(int v)        { sp.edit().putInt("max_tools", v).apply(); }
    public void setAgentMode(boolean v)   { sp.edit().putBoolean("agent_mode", v).apply(); }
}
