package com.star.app;

public class EngineRouter {
    private static final String[] UI_WORDS = {
        "open", "launch", "tap", "click", "scroll", "swipe", "press",
        "search for", "type ", "screenshot", "settings", "notification",
        "youtube", "facebook", "whatsapp", "instagram", "twitter", "chrome",
        "keyboard", "back button", "home button", "recents", "send message"
    };

    private static final String[] DSH_WORDS = {
        "analyze", "explain", "summarize", "count", "find all", "search files",
        "read file", "write file", "edit file", "grep", "list files",
        "run script", "code", "python", "javascript", "debug", "refactor",
        "compare", "calculate", "plan", "reason", "step by step", "workflow"
    };

    public static String pick(String userMessage, boolean dshAvailable) {
        if (userMessage == null) return "native";
        String m = userMessage.toLowerCase();

        for (String w : UI_WORDS) if (m.contains(w)) return "native";

        if (dshAvailable) {
            for (String w : DSH_WORDS) if (m.contains(w)) return "dsh";
            if (userMessage.length() > 120) return "dsh";
        }
        return "native";
    }
}
