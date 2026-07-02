package com.typingsushi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Thread-safe, file-backed top-scores list for the typing game. */
final class Leaderboard {

    static final int MAX_ENTRIES = 10;
    private static final Pattern ENTRY_PATTERN = Pattern.compile("\\{[^{}]*}");

    private final Path storageFile;
    private final List<Entry> entries = new ArrayList<>();

    Leaderboard(Path storageFile) {
        this.storageFile = storageFile;
        load();
    }

    static final class Entry {
        final String name;
        final String course;
        final double earned;
        final String recordedAt;

        Entry(String name, String course, double earned, String recordedAt) {
            this.name = name;
            this.course = course;
            this.earned = earned;
            this.recordedAt = recordedAt;
        }
    }

    synchronized List<Entry> submit(String name, String course, double earned) {
        entries.add(new Entry(name, course, earned, Instant.now().toString()));
        entries.sort(Comparator.comparingDouble((Entry e) -> e.earned).reversed());
        if (entries.size() > MAX_ENTRIES) {
            entries.subList(MAX_ENTRIES, entries.size()).clear();
        }
        save();
        return top();
    }

    synchronized List<Entry> top() {
        return new ArrayList<>(entries);
    }

    private void load() {
        try {
            if (!Files.exists(storageFile)) return;
            String content = Files.readString(storageFile, StandardCharsets.UTF_8);
            Matcher m = ENTRY_PATTERN.matcher(content);
            while (m.find()) {
                String obj = m.group();
                String name = Json.getString(obj, "name");
                String course = Json.getString(obj, "course");
                Double earned = Json.getNumber(obj, "earned");
                String recordedAt = Json.getString(obj, "recordedAt");
                if (name != null && earned != null) {
                    entries.add(new Entry(name, course == null ? "" : course, earned,
                        recordedAt == null ? "" : recordedAt));
                }
            }
            entries.sort(Comparator.comparingDouble((Entry e) -> e.earned).reversed());
        } catch (IOException e) {
            System.err.println("Could not load leaderboard: " + e.getMessage());
        }
    }

    private void save() {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            sb.append("  {\"name\":\"").append(Json.escape(e.name))
              .append("\",\"course\":\"").append(Json.escape(e.course))
              .append("\",\"earned\":").append(e.earned)
              .append(",\"recordedAt\":\"").append(Json.escape(e.recordedAt))
              .append("\"}");
            if (i < entries.size() - 1) sb.append(',');
            sb.append('\n');
        }
        sb.append(']');
        try {
            Path parent = storageFile.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(storageFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not save leaderboard: " + e.getMessage());
        }
    }

    static String toJson(List<Entry> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"name\":\"").append(Json.escape(e.name))
              .append("\",\"course\":\"").append(Json.escape(e.course))
              .append("\",\"earned\":").append(e.earned)
              .append('}');
        }
        return sb.append(']').toString();
    }
}
