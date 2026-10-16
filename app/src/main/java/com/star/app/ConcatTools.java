package com.star.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConcatTools {

    public static final long TIMEOUT_MS = 30000L;
    public static final String DEFAULT_LOCATION = "/sdcard/Star/concat/projects";

    private static final Pattern CREATED_ID =
            Pattern.compile("\"createdId\"\\s*:\\s*\"([^\"]+)\"");

    private static volatile String lastClipId = null;

    private ConcatTools() {}

    public static String video_project_create(String name) {
        try {
            JSONObject p = new JSONObject();
            p.put("location", DEFAULT_LOCATION);
            p.put("name", name);
            String r = run("project.create", p);
            rememberClip(r);
            return r;
        } catch (Throwable t) {
            return err(t);
        }
    }

    public static String video_media_import(String project, String filePath) {
        try {
            JSONObject p = new JSONObject();
            p.put("path", project);
            p.put("file", filePath);
            return run("media.import", p);
        } catch (Throwable t) {
            return err(t);
        }
    }

    public static String video_add_clip(String project, String mediaId, double startSec) {
        try {
            JSONObject cmd = new JSONObject();
            cmd.put("op", "addClipAtFirstFree");
            cmd.put("mediaId", mediaId);
            cmd.put("start", startSec);
            String r = run("edit.apply", editParams(project, cmd));
            rememberClip(r);
            return r;
        } catch (Throwable t) {
            return err(t);
        }
    }

    public static String video_add_transition(String project, String kind, double durationSec) {
        try {
            String clip = requireClip();
            if (clip == null) {
                return "ERR: no clip to apply a transition to (call video_add_clip first)";
            }
            JSONObject transition = new JSONObject();
            transition.put("id", kind);
            transition.put("duration", durationSec);
            JSONObject patch = new JSONObject();
            patch.put("transitionIn", transition);
            return updateClip(project, clip, patch);
        } catch (Throwable t) {
            return err(t);
        }
    }

    public static String video_add_text(String project, String text, double startSec, double endSec) {
        try {
            JSONObject cmd = new JSONObject();
            cmd.put("op", "addTextClip");
            cmd.put("above", true);
            cmd.put("start", startSec);
            cmd.put("duration", Math.max(0.0, endSec - startSec));
            JSONObject style = new JSONObject();
            style.put("content", text);
            cmd.put("style", style);
            String r = run("edit.apply", editParams(project, cmd));
            rememberClip(r);
            return r;
        } catch (Throwable t) {
            return err(t);
        }
    }

    public static String video_apply_effect(String project, String effectId) {
        try {
            String clip = requireClip();
            if (clip == null) {
                return "ERR: no clip to apply an effect to (call video_add_clip first)";
            }
            JSONObject effect = new JSONObject();
            effect.put("id", effectId);
            effect.put("enabled", true);
            effect.put("params", new JSONObject());
            JSONArray chain = new JSONArray();
            chain.put(effect);
            JSONObject patch = new JSONObject();
            patch.put("videoEffects", chain);
            return updateClip(project, clip, patch);
        } catch (Throwable t) {
            return err(t);
        }
    }

    public static String video_export(String project, String outputPath) {
        try {
            JSONObject p = new JSONObject();
            p.put("path", project);
            p.put("output", outputPath);
            return run("export.run", p);
        } catch (Throwable t) {
            return err(t);
        }
    }

    public static String schemaJson() {
        try {
            JSONArray tools = new JSONArray();
            add(tools, "video_project_create",
                    "Create a new Concat video project folder and open it.",
                    params("name", "string", "Project name; the folder is named after it."),
                    "name");
            add(tools, "video_media_import",
                    "Import a media file into a project's bin.",
                    merge(params("project", "string", "Project folder path."),
                          params("filePath", "string", "Absolute path of the media file.")),
                    "project", "filePath");
            add(tools, "video_add_clip",
                    "Place a clip of an imported media item on the timeline.",
                    merge(params("project", "string", "Project folder path."),
                          params("mediaId", "string", "Bin media id, e.g. \"m1\"."),
                          params("startSec", "number", "Timeline position in seconds.")),
                    "project", "mediaId", "startSec");
            add(tools, "video_add_transition",
                    "Add a transition on the cut into the most recently added clip.",
                    merge(params("project", "string", "Project folder path."),
                          params("kind", "string", "Catalogue transition id, e.g. \"cross-fade\"."),
                          params("durationSec", "number", "Seconds the transition covers.")),
                    "project", "kind", "durationSec");
            add(tools, "video_add_text",
                    "Place a title clip over the timeline.",
                    merge(params("project", "string", "Project folder path."),
                          params("text", "string", "The words to show."),
                          params("startSec", "number", "Start of the title, in seconds."),
                          params("endSec", "number", "End of the title, in seconds.")),
                    "project", "text", "startSec", "endSec");
            add(tools, "video_apply_effect",
                    "Apply a video effect to the most recently added clip.",
                    merge(params("project", "string", "Project folder path."),
                          params("effectId", "string", "Catalogue effect id, e.g. \"concat.warm\".")),
                    "project", "effectId");
            add(tools, "video_export",
                    "Render a project's active timeline to a file.",
                    merge(params("project", "string", "Project folder path."),
                          params("outputPath", "string", "File to write, e.g. /sdcard/out.mp4.")),
                    "project", "outputPath");
            return tools.toString();
        } catch (Throwable t) {
            return "[]";
        }
    }

    private static String run(String method, JSONObject params) {
        String json = params == null ? "{}" : params.toString();
        ConcatBridge.Result r = ConcatBridge.call(method, json, TIMEOUT_MS);
        if (r != null && r.ok) {
            return "OK: " + r.raw;
        }
        String error = (r == null) ? "no result" : r.error;
        return "ERR: " + error;
    }

    private static JSONObject editParams(String project, JSONObject command) throws Exception {
        JSONObject p = new JSONObject();
        p.put("path", project);
        p.put("command", command);
        return p;
    }

    private static String updateClip(String project, String clipId, JSONObject patch) throws Exception {
        JSONObject cmd = new JSONObject();
        cmd.put("op", "updateClip");
        cmd.put("clipId", clipId);
        cmd.put("patch", patch);
        return run("edit.apply", editParams(project, cmd));
    }

    private static void rememberClip(String result) {
        if (result == null || !result.startsWith("OK:")) {
            return;
        }
        Matcher m = CREATED_ID.matcher(result);
        if (m.find()) {
            lastClipId = m.group(1);
        }
    }

    private static String requireClip() {
        return lastClipId;
    }

    private static String err(Throwable t) {
        String msg = t == null ? "unknown error" : (t.getClass().getSimpleName() + ": " + t.getMessage());
        return "ERR: " + msg;
    }

    private static JSONObject params(String name, String type, String description) {
        JSONObject props = new JSONObject();
        try {
            props.put(name, new JSONObject().put("type", type).put("description", description));
        } catch (Throwable ignored) {
        }
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            schema.put("properties", props);
        } catch (Throwable ignored) {
        }
        return schema;
    }

    private static JSONObject merge(JSONObject base, JSONObject... more) {
        try {
            JSONObject target = base.getJSONObject("properties");
            for (JSONObject other : more) {
                JSONObject props = other.getJSONObject("properties");
                java.util.Iterator<String> it = props.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    target.put(k, props.get(k));
                }
            }
        } catch (Throwable ignored) {
        }
        return base;
    }

    private static void add(JSONArray tools, String name, String description,
                            JSONObject schema, String... required) {
        try {
            JSONArray req = new JSONArray();
            for (String r : required) {
                req.put(r);
            }
            schema.put("required", req);
            JSONObject fn = new JSONObject();
            fn.put("name", name);
            fn.put("description", description);
            fn.put("parameters", schema);
            JSONObject tool = new JSONObject();
            tool.put("type", "function");
            tool.put("function", fn);
            tools.put(tool);
        } catch (Throwable ignored) {
        }
    }

    public static void main(String[] args) {
        System.out.println(schemaJson());
    }
}
