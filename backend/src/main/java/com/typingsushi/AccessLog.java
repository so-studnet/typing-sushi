package com.typingsushi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thread-safe log of visits to the game's top page (time, IP, browser),
 * capped at the most recent {@link #MAX_ENTRIES}. Like the leaderboard, it
 * persists to a Firebase Realtime Database when one is configured and to a
 * local JSON file otherwise, so it survives restarts on hosts without a
 * persistent disk.
 *
 * Persisting happens on a background thread so recording a visit never slows
 * down serving the page; a pending-save flag coalesces bursts of visits into
 * a single write.
 */
final class AccessLog {

    static final int MAX_ENTRIES = 200;
    private static final Pattern ENTRY_PATTERN = Pattern.compile("\\{[^{}]*}");

    private final Path storageFile;
    private final FirebaseStore firebase;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private final AtomicBoolean savePending = new AtomicBoolean();
    private final ExecutorService saver = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "access-log-saver");
        t.setDaemon(true);
        return t;
    });

    AccessLog(Path storageFile, FirebaseStore firebase) {
        this.storageFile = storageFile;
        this.firebase = firebase;
        load();
    }

    static final class Entry {
        final String time;
        final String ip;
        final String userAgent;

        Entry(String time, String ip, String userAgent) {
            this.time = time;
            this.ip = ip;
            this.userAgent = userAgent;
        }
    }

    /** Records a visit (newest first) and schedules a background save. */
    void record(String ip, String userAgent) {
        synchronized (this) {
            entries.addFirst(new Entry(Instant.now().toString(),
                ip == null ? "" : ip,
                userAgent == null ? "" : userAgent));
            while (entries.size() > MAX_ENTRIES) {
                entries.removeLast();
            }
        }
        // Coalesce bursts: only one save needs to be queued at a time, since
        // each save writes a full snapshot taken when it runs.
        if (savePending.compareAndSet(false, true)) {
            saver.submit(() -> {
                savePending.set(false);
                persist(toJson(snapshot()));
            });
        }
    }

    synchronized List<Entry> snapshot() {
        return new ArrayList<>(entries);
    }

    private void load() {
        String content;
        if (firebase != null) {
            try {
                content = firebase.load("accessLog");
            } catch (Exception e) {
                System.err.println("Could not load access log from Firebase: " + e.getMessage());
                return;
            }
        } else {
            try {
                if (!Files.exists(storageFile)) return;
                content = Files.readString(storageFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.err.println("Could not load access log: " + e.getMessage());
                return;
            }
        }
        if (content == null) return;

        Matcher m = ENTRY_PATTERN.matcher(content);
        while (m.find() && entries.size() < MAX_ENTRIES) {
            String obj = m.group();
            String time = Json.getString(obj, "time");
            String ip = Json.getString(obj, "ip");
            String userAgent = Json.getString(obj, "userAgent");
            if (time != null) {
                entries.addLast(new Entry(time, ip == null ? "" : ip, userAgent == null ? "" : userAgent));
            }
        }
    }

    private void persist(String json) {
        if (firebase != null) {
            try {
                firebase.save("accessLog", json);
            } catch (Exception e) {
                System.err.println("Could not save access log to Firebase: " + e.getMessage());
            }
            return;
        }
        try {
            Path parent = storageFile.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(storageFile, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not save access log: " + e.getMessage());
        }
    }

    static String toJson(List<Entry> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"time\":\"").append(Json.escape(e.time))
              .append("\",\"ip\":\"").append(Json.escape(e.ip))
              .append("\",\"userAgent\":\"").append(Json.escape(e.userAgent))
              .append("\"}");
        }
        return sb.append(']').toString();
    }
}
