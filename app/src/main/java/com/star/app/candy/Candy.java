package com.star.app.candy;

public class Candy {
    public String name;
    public String repo;
    public String language;
    public String[] buildSteps = new String[0];
    public String output = "";
    public String installAs;
    public String[] deps = new String[0];
    public String commit = "";
    public String installedAt = "";
    public String status;
    public String pkg = "";   // Termux package name, empty for repo skills

    public Candy() {}

    public boolean isPkg() {
        return pkg != null && !pkg.isEmpty();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"name\":\"").append(esc(name)).append("\",");
        sb.append("\"repo\":\"").append(esc(repo)).append("\",");
        sb.append("\"language\":\"").append(esc(language)).append("\",");
        sb.append("\"build\":[");
        if (buildSteps != null) {
            for (int i = 0; i < buildSteps.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(esc(buildSteps[i])).append("\"");
            }
        }
        sb.append("],");
        sb.append("\"output\":\"").append(esc(output)).append("\",");
        sb.append("\"install_as\":\"").append(esc(installAs)).append("\",");
        sb.append("\"deps\":[");
        if (deps != null) {
            for (int i = 0; i < deps.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(esc(deps[i])).append("\"");
            }
        }
        sb.append("],");
        sb.append("\"commit\":\"").append(esc(commit)).append("\",");
        sb.append("\"installed\":\"").append(esc(installedAt)).append("\",");
        sb.append("\"status\":\"").append(esc(status)).append("\",");
        sb.append("\"pkg\":\"").append(esc(pkg)).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
