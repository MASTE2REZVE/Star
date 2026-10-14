package com.star.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ProviderIcons {

    private static final String[] LOBEHUB = {
        "https://unpkg.com/@lobehub/icons-static-png@latest/",
        "https://cdn.jsdelivr.net/npm/@lobehub/icons-static-png@latest/",
        "https://registry.npmmirror.com/@lobehub/icons-static-png/latest/files/"
    };
    private static final String[] VARIANTS = { "color/", "light/", "dark/" };

    private static final OkHttpClient http = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build();

    private static final Map<String, Bitmap> mem = new HashMap<>();
    private static final Set<String> failed = new HashSet<>();

    // provider prefix -> primary lobehub slug
    private static final Map<String, String> SLUG = new HashMap<>();
    // provider prefix -> domain for Google favicon fallback
    private static final Map<String, String> DOMAIN = new HashMap<>();
    // provider prefix -> extra lobehub slug candidates
    private static final Map<String, String[]> EXTRA = new HashMap<>();

    static {
        // ---- slug map ----
        SLUG.put("openai", "openai");
        SLUG.put("anthropic", "anthropic");
        SLUG.put("google", "gemini");
        SLUG.put("meta-llama", "meta");
        SLUG.put("meta", "meta");
        SLUG.put("deepseek", "deepseek");
        SLUG.put("nvidia", "nvidia");
        SLUG.put("mistralai", "mistral");
        SLUG.put("mistral", "mistral");
        SLUG.put("qwen", "qwen");
        SLUG.put("x-ai", "grok");
        SLUG.put("xai", "grok");
        SLUG.put("cohere", "cohere");
        SLUG.put("perplexity", "perplexity");
        SLUG.put("moonshotai", "moonshot");
        SLUG.put("amazon", "bedrock");
        SLUG.put("microsoft", "microsoft");
        SLUG.put("ai21", "ai21");
        SLUG.put("thudm", "chatglm");
        SLUG.put("nousresearch", "nousresearch");
        SLUG.put("nous", "nousresearch");
        SLUG.put("openrouter", "openrouter");
        SLUG.put("inclusionai", "inclusionai");
        SLUG.put("poolside", "poolside");
        SLUG.put("thinkingmachines", "thinkingmachines");
        SLUG.put("sao10k", "sao10k");
        SLUG.put("gryphe", "gryphe");
        SLUG.put("undi95", "undi95");
        SLUG.put("liquid", "liquid");
        SLUG.put("z-ai", "zhipu");
        SLUG.put("zai", "zhipu");
        SLUG.put("baidu", "baidu");
        SLUG.put("bytedance", "bytedance");
        SLUG.put("tencent", "tencent");
        SLUG.put("minimax", "minimax");
        SLUG.put("01-ai", "yi");
        SLUG.put("reka", "reka");
        SLUG.put("rekaai", "reka");
        SLUG.put("reka-ai", "reka");
        SLUG.put("nex-agi", "nex");
        SLUG.put("nexagi", "nex");
        SLUG.put("nex", "nex");
        SLUG.put("inflection", "inflection");
        SLUG.put("databricks", "databricks");
        SLUG.put("neversleep", "neversleep");
        SLUG.put("alpindale", "alpindale");
        SLUG.put("agentica", "agentica");
        SLUG.put("aion-labs", "aionlabs");
        SLUG.put("arcee-ai", "arcee");
        SLUG.put("featherless", "featherless");
        SLUG.put("infermatic", "infermatic");
        SLUG.put("eva-unit-01", "evaunit01");
        SLUG.put("raifle", "raifle");
        SLUG.put("the-drummer", "thedrummer");
        SLUG.put("switchpoint", "switchpoint");
        SLUG.put("tngtech", "tng");
        SLUG.put("opengvlab", "opengvlab");
        SLUG.put("anthracite-org", "anthracite");
        SLUG.put("alibaba", "alibaba");
        SLUG.put("stepfun", "stepfun");
        SLUG.put("stepfun-ai", "stepfun");
        SLUG.put("baichuan", "baichuan");
        SLUG.put("kwaipilot", "kwaipilot");
        SLUG.put("sarvam", "sarvam");
        SLUG.put("prime-intellect", "primeintellect");
        SLUG.put("shisa-ai", "shisa");
        SLUG.put("arliai", "arliai");
        SLUG.put("ibm-granite", "ibm");
        SLUG.put("ibm", "ibm");
        SLUG.put("xiaomi", "xiaomi");
        SLUG.put("all-hands", "allhands");
        SLUG.put("sdaia", "sdaia");

        // ---- domain map for Google favicon fallback ----
        DOMAIN.put("openai", "openai.com");
        DOMAIN.put("anthropic", "anthropic.com");
        DOMAIN.put("google", "google.com");
        DOMAIN.put("meta-llama", "meta.com");
        DOMAIN.put("meta", "meta.com");
        DOMAIN.put("deepseek", "deepseek.com");
        DOMAIN.put("nvidia", "nvidia.com");
        DOMAIN.put("mistralai", "mistral.ai");
        DOMAIN.put("mistral", "mistral.ai");
        DOMAIN.put("qwen", "qwen.ai");
        DOMAIN.put("x-ai", "x.ai");
        DOMAIN.put("xai", "x.ai");
        DOMAIN.put("cohere", "cohere.com");
        DOMAIN.put("perplexity", "perplexity.ai");
        DOMAIN.put("moonshotai", "moonshot.ai");
        DOMAIN.put("amazon", "amazon.com");
        DOMAIN.put("microsoft", "microsoft.com");
        DOMAIN.put("ai21", "ai21.com");
        DOMAIN.put("nousresearch", "nousresearch.com");
        DOMAIN.put("nous", "nousresearch.com");
        DOMAIN.put("openrouter", "openrouter.ai");
        DOMAIN.put("inclusionai", "inclusionai.org");
        DOMAIN.put("poolside", "poolside.ai");
        DOMAIN.put("thinkingmachines", "thinkingmachines.ai");
        DOMAIN.put("liquid", "liquid.ai");
        DOMAIN.put("z-ai", "zhipuai.cn");
        DOMAIN.put("zai", "zhipuai.cn");
        DOMAIN.put("baidu", "baidu.com");
        DOMAIN.put("bytedance", "bytedance.com");
        DOMAIN.put("tencent", "tencent.com");
        DOMAIN.put("minimax", "minimax.io");
        DOMAIN.put("01-ai", "01.ai");
        DOMAIN.put("reka", "reka.ai");
        DOMAIN.put("rekaai", "reka.ai");
        DOMAIN.put("reka-ai", "reka.ai");
        DOMAIN.put("nex-agi", "nex-agi.com");
        DOMAIN.put("nexagi", "nex-agi.com");
        DOMAIN.put("inflection", "inflection.ai");
        DOMAIN.put("databricks", "databricks.com");
        DOMAIN.put("agentica", "agentica.ai");
        DOMAIN.put("aion-labs", "aionlabs.ai");
        DOMAIN.put("arcee-ai", "arcee.ai");
        DOMAIN.put("featherless", "featherless.ai");
        DOMAIN.put("infermatic", "infermatic.ai");
        DOMAIN.put("switchpoint", "switchpoint.ai");
        DOMAIN.put("tngtech", "tngtech.com");
        DOMAIN.put("opengvlab", "opengvlab.org");
        DOMAIN.put("alibaba", "alibaba.com");
        DOMAIN.put("stepfun", "stepfun.com");
        DOMAIN.put("stepfun-ai", "stepfun.com");
        DOMAIN.put("baichuan", "baichuan-ai.com");
        DOMAIN.put("kwaipilot", "kwaipilot.com");
        DOMAIN.put("sarvam", "sarvam.ai");
        DOMAIN.put("prime-intellect", "primeintellect.ai");
        DOMAIN.put("shisa-ai", "shisa.ai");
        DOMAIN.put("arliai", "arliai.com");
        DOMAIN.put("ibm-granite", "ibm.com");
        DOMAIN.put("ibm", "ibm.com");
        DOMAIN.put("xiaomi", "xiaomi.com");
        DOMAIN.put("all-hands", "all-hands.dev");
        DOMAIN.put("sdaia", "sdaia.gov.sa");

        // ---- extra lobehub candidates ----
        EXTRA.put("nex-agi", new String[]{"nexagi", "nex-agi"});
        EXTRA.put("ibm-granite", new String[]{"ibmgranite", "ibm-granite"});
        EXTRA.put("z-ai", new String[]{"zai"});
        EXTRA.put("01-ai", new String[]{"01ai"});
        EXTRA.put("aion-labs", new String[]{"aionlabs", "aion-labs"});
        EXTRA.put("arcee-ai", new String[]{"arceeai", "arcee-ai"});
        EXTRA.put("eva-unit-01", new String[]{"evaunit01", "eva-unit-01"});
        EXTRA.put("the-drummer", new String[]{"thedrummer", "the-drummer"});
        EXTRA.put("anthracite-org", new String[]{"anthracite", "anthracite-org"});
        EXTRA.put("stepfun-ai", new String[]{"stepfun"});
        EXTRA.put("prime-intellect", new String[]{"primeintellect", "prime-intellect"});
        EXTRA.put("shisa-ai", new String[]{"shisa", "shisa-ai"});
        EXTRA.put("all-hands", new String[]{"allhands", "all-hands"});
    }

    public static void load(final Context ctx, final String provider,
                            final ImageView logo, final TextView initials) {
        if (provider == null) return;
        final String p = provider.toLowerCase().trim();
        if (failed.contains(p)) return;

        Bitmap cached = mem.get(p);
        if (cached != null) { show(logo, initials, cached); return; }

        final File dir = new File(ctx.getFilesDir(), "icons");
        final File f = new File(dir, p + ".png");
        if (f.exists()) {
            Bitmap d = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (d != null) {
                mem.put(p, d);
                show(logo, initials, d);
                return;
            }
        }

        new Thread(() -> {
            Bitmap got = null;

            // 1. try lobehub CDNs with all slug candidates
            List<String> slugs = new ArrayList<>();
            if (SLUG.containsKey(p)) slugs.add(SLUG.get(p));
            if (EXTRA.containsKey(p)) for (String x : EXTRA.get(p)) slugs.add(x);
            slugs.add(p.replaceAll("[^a-z0-9]", ""));
            slugs.add(p);

            for (String slug : slugs) {
                if (slug == null || slug.isEmpty()) continue;
                got = tryLobehub(dir, slug, f);
                if (got != null) break;
            }

            // 2. try Google favicon by domain
            if (got == null && DOMAIN.containsKey(p)) {
                got = tryGoogle(dir, p, DOMAIN.get(p), f);
            }

            if (got == null) {
                failed.add(p);
                return;
            }

            final Bitmap bmp = got;
            mem.put(p, bmp);
            logo.post(() -> show(logo, initials, bmp));
        }).start();
    }

    private static void show(ImageView logo, TextView initials, Bitmap bmp) {
        logo.setImageBitmap(bmp);
        logo.setVisibility(View.VISIBLE);
        if (initials != null) initials.setVisibility(View.GONE);
    }

    private static Bitmap tryLobehub(File dir, String slug, File f) {
        for (String cdn : LOBEHUB) {
            for (String v : VARIANTS) {
                try {
                    String url = cdn + v + slug + ".png";
                    Request req = new Request.Builder().url(url).build();
                    Response resp = http.newCall(req).execute();
                    if (!resp.isSuccessful()) { resp.close(); continue; }
                    InputStream in = resp.body().byteStream();
                    if (!dir.exists()) dir.mkdirs();
                    FileOutputStream fos = new FileOutputStream(f);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                    fos.close();
                    in.close();
                    resp.close();
                    Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                    if (bmp == null) { f.delete(); continue; }
                    return bmp;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static Bitmap tryGoogle(File dir, String provider, String domain, File f) {
        String[] szs = { "128", "64" };
        for (String sz : szs) {
            try {
                String url = "https://www.google.com/s2/favicons?domain=" +
                    domain + "&sz=" + sz;
                Request req = new Request.Builder().url(url).build();
                Response resp = http.newCall(req).execute();
                if (!resp.isSuccessful()) { resp.close(); continue; }
                InputStream in = resp.body().byteStream();
                if (!dir.exists()) dir.mkdirs();
                FileOutputStream fos = new FileOutputStream(f);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                fos.close();
                in.close();
                resp.close();
                Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (bmp == null) { f.delete(); continue; }
                return bmp;
            } catch (Exception ignored) {}
        }
        return null;
    }
}
