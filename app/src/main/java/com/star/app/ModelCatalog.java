package com.star.app;

import android.content.Context;
import okhttp3.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Live model list from OpenRouter, cached to /sdcard/Star/models.json */
public class ModelCatalog {

    public static class Provider {
        public String name;
        public String providerName;  // display name
        public double priceIn;
        public double priceOut;
        public double cacheRead;
        public int context;
        public double latency;      // latency_last_30m, -1 if null
        public double throughput;   // throughput_last_30m, -1 if null
        public double uptime;       // uptime_last_30m, 0-100, -1 if null
        public String quantization;
        public int maxCompletionTokens;
        public boolean supportsTools;
    }

    /** cache: model id -> list of providers, with timestamps. */
    private static final java.util.Map<String, java.util.List<Provider>> provCache =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Long> provCacheAt =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final long PROVIDER_TTL_MS = 30 * 60 * 1000L; // 30 min

    // OpenRouter blocks these from any API client not on their app list.
    private static final String[] RESTRICTED_PREFIXES = {
        "thinkingmachines/"
    };

    private static boolean isRestricted(String id) {
        if (id == null) return false;
        for (String p : RESTRICTED_PREFIXES) {
            if (id.startsWith(p)) return true;
        }
        return false;
    }

    public static class Model {
        public String id;
        public String name;
        public boolean free;
        public double priceIn;   // per 1M tokens
        public double priceOut;
        public double priceCacheRead = -1;   // -1 means no cache pricing
        public double priceCacheWrite = -1;
        public boolean tools;
        public boolean vision;
        public int contextLength;
        public String priceTag;
        // Real benchmarks from Artificial Analysis via OpenRouter.
        // -1 means "no data for this model".
        public double intelligenceIndex = -1;
        public double codingIndex = -1;
        public double agenticIndex = -1;
        public double designElo = -1;
        public boolean hasBenchmarks() {
            return intelligenceIndex >= 0 || codingIndex >= 0 || agenticIndex >= 0;
        }
    }

    private static final String CACHE = "/sdcard/Star/models.json";
    private static final OkHttpClient http = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    private static List<Model> mem;
    private static long memAt;

    public static List<Model> cached() {
        if (mem != null && System.currentTimeMillis() - memAt < 24*3600*1000L) return mem;
        try {
            Shell.Result r = Shell.run("cat " + CACHE + " 2>/dev/null");
            if (r.output != null && r.output.trim().length() > 20) {
                mem = parse(r.output);
                memAt = System.currentTimeMillis();
                return mem;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public interface Cb { void onReady(List<Model> list); void onError(String e); }

    public static void refresh(Context ctx, Prefs prefs, Cb cb) {
        new Thread(() -> {
            try {
                String apiKey = prefs.getApiKey();
                String url = prefs.getBaseUrl() + "/models";
                Request.Builder rb = new Request.Builder().url(url).get();
                if (apiKey != null && !apiKey.isEmpty()) {
                    rb.header("Authorization", "Bearer " + apiKey);
                }
                rb.header("HTTP-Referer", "https://github.com/MASTE2REZVE/Star");
                rb.header("X-Title", "Star");
                Response resp = http.newCall(rb.build()).execute();
                String s = resp.body().string();
                List<Model> list = parse(s);
                if (list.isEmpty()) { cb.onError("empty model list"); return; }
                try {
                    String esc = s.replace("'", "'\\''");
                    Shell.run("mkdir -p /sdcard/Star");
                    Shell.run("printf '%s' '" + esc + "' > " + CACHE);
                } catch (Exception ignored) {}
                mem = list;
                memAt = System.currentTimeMillis();
                cb.onReady(list);
            } catch (Exception e) {
                List<Model> c = cached();
                if (c != null) cb.onReady(c);
                else cb.onError(e.getMessage());
            }
        }).start();
    }

    public interface ProviderCb {
        void onOk(java.util.List<Provider> providers);
        void onError(String err);
    }

    /** Fetch every provider serving this model, with real per-provider prices. */
    public static void fetchProviders(final Prefs prefs, final String modelId,
                                      final boolean useCache, final ProviderCb cb) {
        if (modelId == null || modelId.isEmpty()) { cb.onError("empty id"); return; }

        // cache hit?
        if (useCache) {
            java.util.List<Provider> cached = provCache.get(modelId);
            Long at = provCacheAt.get(modelId);
            if (cached != null && at != null && System.currentTimeMillis() - at < PROVIDER_TTL_MS) {
                cb.onOk(cached);
                return;
            }
        }

        new Thread(() -> {
            try {
                // OpenRouter endpoint: /models/{author}/{slug}/endpoints
                String url = prefs.getBaseUrl() + "/models/" + modelId + "/endpoints";
                Request.Builder rb = new Request.Builder().url(url)
                    .header("HTTP-Referer", "https://github.com/MASTE2REZVE/Star")
                    .header("X-Title", "Star");
                String key = prefs.getApiKey();
                if (key != null && !key.isEmpty()) rb.header("Authorization", "Bearer " + key);

                Response resp = http.newCall(rb.build()).execute();
                String body = resp.body().string();
                if (!resp.isSuccessful()) { cb.onError("HTTP " + resp.code()); return; }

                JSONObject root = new JSONObject(body);
                JSONObject data = root.optJSONObject("data");
                if (data == null) { cb.onError("no data"); return; }
                JSONArray arr = data.optJSONArray("endpoints");
                if (arr == null) { cb.onError("no endpoints"); return; }

                java.util.List<Provider> out = new java.util.ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject e = arr.getJSONObject(i);
                    Provider pv = new Provider();
                    pv.name = e.optString("name", "?");
                    pv.providerName = e.optString("provider_name", pv.name);
                    pv.context = e.optInt("context_length", 0);
                    pv.quantization = e.optString("quantization", "");
                    pv.maxCompletionTokens = e.optInt("max_completion_tokens", 0);

                    // real key names from OpenRouter endpoints API
                    if (e.has("latency_last_30m") && !e.isNull("latency_last_30m")) {
                        pv.latency = e.optDouble("latency_last_30m", -1);
                    } else {
                        pv.latency = -1;
                    }
                    if (e.has("throughput_last_30m") && !e.isNull("throughput_last_30m")) {
                        pv.throughput = e.optDouble("throughput_last_30m", -1);
                    } else {
                        pv.throughput = -1;
                    }
                    if (e.has("uptime_last_30m") && !e.isNull("uptime_last_30m")) {
                        pv.uptime = e.optDouble("uptime_last_30m", -1);
                    } else {
                        pv.uptime = -1;
                    }

                    // tools support
                    JSONArray sp = e.optJSONArray("supported_parameters");
                    if (sp != null) {
                        for (int k = 0; k < sp.length(); k++) {
                            if ("tools".equals(sp.optString(k))) { pv.supportsTools = true; break; }
                        }
                    }
                    JSONObject pr = e.optJSONObject("pricing");
                    if (pr != null) {
                        try { pv.priceIn = Double.parseDouble(pr.optString("prompt", "0")) * 1_000_000.0; }
                        catch (Exception ig) { pv.priceIn = 0; }
                        try { pv.priceOut = Double.parseDouble(pr.optString("completion", "0")) * 1_000_000.0; }
                        catch (Exception ig) { pv.priceOut = 0; }
                        String cr = pr.optString("input_cache_read", "");
                        if (!cr.isEmpty()) {
                            try { pv.cacheRead = Double.parseDouble(cr) * 1_000_000.0; }
                            catch (Exception ig) { pv.cacheRead = -1; }
                        } else pv.cacheRead = -1;
                    } else pv.cacheRead = -1;
                    out.add(pv);
                }
                // sort cheapest INPUT first (input is 93% of coding usage)
                java.util.Collections.sort(out, (a, b) -> {
                    int c = Double.compare(a.priceIn, b.priceIn);
                    if (c != 0) return c;
                    return Double.compare(a.priceOut, b.priceOut);
                });
                provCache.put(modelId, out);
                provCacheAt.put(modelId, System.currentTimeMillis());
                cb.onOk(out);
            } catch (Exception ex) {
                cb.onError(ex.getMessage());
            }
        }).start();
    }

    private static List<Model> parse(String json) throws JSONException {
        List<Model> out = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray data = root.optJSONArray("data");
        if (data == null) return out;
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.getJSONObject(i);
            Model m = new Model();
            m.id = o.optString("id", "");
            m.name = o.optString("name", m.id);
            m.contextLength = o.optInt("context_length", 0);
            JSONObject pr = o.optJSONObject("pricing");
            double pin = 0, pout = 0;
            if (pr != null) {
                try { pin = Double.parseDouble(pr.optString("prompt", "0")); } catch (Exception ignored) {}
                try { pout = Double.parseDouble(pr.optString("completion", "0")); } catch (Exception ignored) {}
                // real cache pricing from OpenRouter
                String cr = pr.optString("input_cache_read", "");
                if (!cr.isEmpty()) {
                    try { m.priceCacheRead = Double.parseDouble(cr) * 1_000_000.0; } catch (Exception ignored) {}
                }
                String cw = pr.optString("input_cache_write", "");
                if (!cw.isEmpty()) {
                    try { m.priceCacheWrite = Double.parseDouble(cw) * 1_000_000.0; } catch (Exception ignored) {}
                }
            }
            // prices from API are per token, multiply by 1M
            m.priceIn = pin * 1_000_000.0;
            m.priceOut = pout * 1_000_000.0;
            m.free = (pin == 0 && pout == 0) || m.id.endsWith(":free");
            if (m.free) m.priceTag = "FREE";
            else m.priceTag = String.format("$%.2f/M", m.priceOut);

            // tools capability
            JSONArray params = o.optJSONArray("supported_parameters");
            if (params != null) {
                for (int k = 0; k < params.length(); k++) {
                    if ("tools".equals(params.optString(k))) { m.tools = true; break; }
                }
            }
            // vision capability
            JSONObject arch = o.optJSONObject("architecture");
            if (arch != null) {
                String mod = arch.optString("modality", "");
                if (mod.contains("image")) m.vision = true;
                JSONArray inMods = arch.optJSONArray("input_modalities");
                if (inMods != null) {
                    for (int k = 0; k < inMods.length(); k++) {
                        if ("image".equals(inMods.optString(k))) { m.vision = true; break; }
                    }
                }
            }
            // real benchmark data (Artificial Analysis)
            JSONObject bm = o.optJSONObject("benchmarks");
            if (bm != null) {
                JSONObject aa = bm.optJSONObject("artificial_analysis");
                if (aa != null) {
                    m.intelligenceIndex = aa.optDouble("intelligence_index", -1);
                    m.codingIndex = aa.optDouble("coding_index", -1);
                    m.agenticIndex = aa.optDouble("agentic_index", -1);
                }
                JSONArray da = bm.optJSONArray("design_arena");
                if (da != null && da.length() > 0) {
                    // pick the highest ELO entry
                    double best = -1;
                    for (int d = 0; d < da.length(); d++) {
                        JSONObject entry = da.optJSONObject(d);
                        if (entry == null) continue;
                        double elo = entry.optDouble("elo", -1);
                        if (elo > best) best = elo;
                    }
                    m.designElo = best;
                }
            }

            // skip if no id, or restricted
            if (m.id.isEmpty()) continue;
            if (isRestricted(m.id)) continue;
            out.add(m);
        }
        // sort: free first, then price, then name
        Collections.sort(out, (a, b) -> {
            if (a.free != b.free) return a.free ? -1 : 1;
            double pa = a.free ? 0 : a.priceOut;
            double pb = b.free ? 0 : b.priceOut;
            if (pa != pb) return Double.compare(pa, pb);
            return a.id.compareToIgnoreCase(b.id);
        });
        return out;
    }
}
