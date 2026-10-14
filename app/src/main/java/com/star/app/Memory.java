package com.star.app;

import android.content.Context;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Memory {
    private final File file;

    public Memory(Context c) {
        file = new File(c.getFilesDir(), "star_memory.txt");
    }

    public String readAll() {
        if (!file.exists()) return "";
        try {
            BufferedReader r = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            r.close();
            return sb.toString().trim();
        } catch (Exception e) { return ""; }
    }

    public void writeAll(String text) {
        try {
            FileWriter w = new FileWriter(file, false);
            w.write(text);
            w.close();
        } catch (Exception ignored) {}
    }

    // Append a fact if not already present (deduped)
    public void remember(String fact) {
        String f = fact.trim();
        if (f.isEmpty()) return;
        String all = readAll();
        for (String line : all.split("\n")) {
            if (line.trim().equalsIgnoreCase(f)) return;
        }
        String updated = all.isEmpty() ? f : (all + "\n" + f);
        writeAll(updated);
    }
}
