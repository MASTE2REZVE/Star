package com.star.app;

import org.json.*;
import java.util.*;

/** File-based multi-session storage. Files under /sdcard/Star/sessions/. */
public class SessionStore {
    private static final String DIR = "/sdcard/Star/sessions";
    private static final String INDEX = DIR + "/index.json";

    public static void ensureDir() {
        Shell.run("mkdir -p " + DIR);
    }

    public static String newId() {
        return Long.toHexString(System.currentTimeMillis()) + "-" +
               Integer.toHexString(new Random().nextInt(0xFFFF));
    }

    public static List<Session> listSessions() {
        List<Session> out = new ArrayList<>();
        try {
            Shell.Result r = Shell.run("cat " + INDEX + " 2>/dev/null");
            if (r.output == null || r.output.trim().isEmpty()) return out;
            JSONArray arr = new JSONArray(r.output);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Session s = new Session();
                s.id = o.optString("id");
                s.title = o.optString("title", "Untitled");
                s.created = o.optLong("created");
                s.updated = o.optLong("updated");
                s.count = o.optInt("count");
                out.add(s);
            }
        } catch (Exception ignored) {}
        // newest first
        Collections.sort(out, (a, b) -> Long.compare(b.updated, a.updated));
        return out;
    }

    public static void saveIndex(List<Session> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Session s : list) {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("title", s.title == null ? "" : s.title);
                o.put("created", s.created);
                o.put("updated", s.updated);
                o.put("count", s.count);
                arr.put(o);
            }
            String json = arr.toString(2).replace("'", "'\\''");
            ensureDir();
            Shell.run("printf '%s' '" + json + "' > " + INDEX);
        } catch (Exception ignored) {}
    }

    public static Session create(String firstMessage) {
        ensureDir();
        Session s = new Session();
        s.id = newId();
        s.created = System.currentTimeMillis();
        s.updated = s.created;
        s.count = 0;
        s.title = titleFrom(firstMessage);

        List<Session> list = listSessions();
        list.add(s);
        saveIndex(list);
        saveMessages(s.id, new JSONArray());
        return s;
    }

    public static void delete(String id) {
        List<Session> list = listSessions();
        list.removeIf(s -> s.id != null && s.id.equals(id));
        saveIndex(list);
        Shell.run("rm -f " + DIR + "/" + id + ".json");
    }

    public static void rename(String id, String title) {
        List<Session> list = listSessions();
        for (Session s : list) {
            if (s.id != null && s.id.equals(id)) {
                s.title = title;
                s.updated = System.currentTimeMillis();
                break;
            }
        }
        saveIndex(list);
    }

    public static void touch(String id, int count, String firstUserMessage) {
        List<Session> list = listSessions();
        for (Session s : list) {
            if (s.id != null && s.id.equals(id)) {
                s.updated = System.currentTimeMillis();
                s.count = count;
                if ((s.title == null || s.title.isEmpty() || s.title.equals("Untitled"))
                    && firstUserMessage != null && !firstUserMessage.isEmpty()) {
                    s.title = titleFrom(firstUserMessage);
                }
                break;
            }
        }
        saveIndex(list);
    }

    public static JSONArray loadMessages(String id) {
        if (id == null) return new JSONArray();
        try {
            Shell.Result r = Shell.run("cat " + DIR + "/" + id + ".json 2>/dev/null");
            if (r.output == null || r.output.trim().isEmpty()) return new JSONArray();
            return new JSONArray(r.output);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static void saveMessages(String id, JSONArray msgs) {
        if (id == null) return;
        try {
            ensureDir();
            String json = msgs.toString().replace("'", "'\\''");
            Shell.run("printf '%s' '" + json + "' > " + DIR + "/" + id + ".json");
        } catch (Exception ignored) {}
    }

    public static String titleFrom(String msg) {
        if (msg == null) return "Untitled";
        String t = msg.trim().replaceAll("\\s+", " ");
        if (t.length() > 40) t = t.substring(0, 40) + "...";
        return t.isEmpty() ? "Untitled" : t;
    }
}
