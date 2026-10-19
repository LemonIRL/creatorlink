package org.limitlesssmp.creatorlink.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent notes: variables that save to disk in the profile folder.
 * File format matches CCI's notes.json: an array of {name, note, notes, expiry} objects.
 */
public final class NoteStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final class Note {
        public String name;
        public String note;                 // "simple note" content
        public List<String> notes;          // notes array
        public Long expiry;                 // epoch millis; null = never expires

        public boolean expired() {
            return expiry != null && System.currentTimeMillis() > expiry;
        }
    }

    private final Map<String, Note> byName = new LinkedHashMap<>();
    private Path file;

    public synchronized void load(Path profileDir) {
        byName.clear();
        file = profileDir.resolve("notes.json");
        if (!Files.exists(file)) return;
        try {
            List<Note> list = GSON.fromJson(Files.readString(file), new TypeToken<List<Note>>() {}.getType());
            if (list != null) {
                for (Note n : list) {
                    if (n != null && n.name != null) byName.put(n.name, n);
                }
            }
        } catch (Exception e) {
            EngineRuntime.LOGGER.error("Failed to load notes.json", e);
        }
    }

    public synchronized void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(new ArrayList<>(byName.values())));
        } catch (IOException e) {
            EngineRuntime.LOGGER.error("Failed to save notes.json", e);
        }
    }

    public synchronized Note get(String name, boolean allowExpired) {
        Note n = byName.get(name);
        if (n == null) return null;
        if (n.expired() && !allowExpired) return null;
        return n;
    }

    public synchronized List<String> names() {
        List<String> out = new ArrayList<>();
        for (Note n : byName.values()) {
            if (!n.expired()) out.add(n.name);
        }
        return out;
    }

    public synchronized void put(String name, String content, boolean append, boolean putInNotesArray, String expiresIn) {
        Note n = byName.computeIfAbsent(name, k -> {
            Note nn = new Note();
            nn.name = k;
            return nn;
        });
        if (putInNotesArray) {
            if (n.notes == null) n.notes = new ArrayList<>();
            n.notes.add(content);
        } else if (append && n.note != null) {
            n.note = n.note + content;
        } else {
            n.note = content;
        }
        if (expiresIn != null) {
            if (expiresIn.equalsIgnoreCase("null") || expiresIn.isEmpty()) {
                n.expiry = null;
            } else {
                long dur = parseDuration(expiresIn);
                if (dur > 0) n.expiry = System.currentTimeMillis() + dur;
            }
        }
        save();
    }

    /**
     * Deletion types: 0 = entire note, 1 = simple note only, 2 = notes array only,
     * 3 = one piece of content within the notes array.
     */
    public synchronized void delete(String name, int deletionType, String content) {
        Note n = byName.get(name);
        if (n == null) return;
        switch (deletionType) {
            case 1 -> n.note = null;
            case 2 -> n.notes = null;
            case 3 -> {
                if (n.notes != null && content != null) n.notes.remove(content);
            }
            default -> byName.remove(name);
        }
        if (n.note == null && n.notes == null) byName.remove(name);
        save();
    }

    /** Parses durations like "2d7h32m19s" (y/d/h/m/s, any part omissible). */
    public static long parseDuration(String s) {
        long total = 0;
        long num = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                num = num * 10 + (c - '0');
            } else {
                long unit = switch (Character.toLowerCase(c)) {
                    case 'y' -> 365L * 24 * 60 * 60 * 1000;
                    case 'd' -> 24L * 60 * 60 * 1000;
                    case 'h' -> 60L * 60 * 1000;
                    case 'm' -> 60L * 1000;
                    case 's' -> 1000L;
                    default -> 0;
                };
                total += num * unit;
                num = 0;
            }
        }
        return total;
    }
}
