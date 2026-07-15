package com.typingsushi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the word/phrase lists used by the typing game, grouped by difficulty.
 *
 * The base content lives in plain text files under wordbank/ (one entry per
 * line; '#' starts a comment, blank lines are ignored) instead of being
 * hardcoded in this class, so the quizzed words can be edited by editing
 * those .txt files -- no Java changes or recompilation required.
 *
 * The pools can also be edited at runtime through the admin API (see
 * Main.AdminWordsHandler): words can be added, removed, or toggled between
 * enabled and disabled -- disabled words stay in the list but are never
 * quizzed. Those edits are persisted (to the Firebase Realtime Database
 * under /wordbank/{difficulty} when configured, to local files under data/
 * otherwise), so they survive restarts and redeploys. At startup, a saved
 * pool takes precedence over its .txt file; the admin "reset" action
 * discards the saved pool and reloads the .txt defaults.
 */
final class WordBank {

    // Small built-in safety net so the game still works if wordbank/ is
    // missing or a file fails to load, rather than serving empty quizzes.
    private static final Map<String, List<String>> FALLBACK = Map.of(
        "easy.txt", List.of("sushi", "wasabi", "green tea"),
        "medium.txt", List.of("Practice makes perfect.", "Better late than never."),
        "hard.txt", List.of("Please restart your computer and try logging in again."),
        "notion.txt", List.of("Practice makes perfect."),
        "notion-words.txt", List.of("practice", "improve", "succeed")
    );

    // difficulty -> source .txt file. notion-ai holds the seed words for the
    // Notion AI course; AiSentences turns a random sample of these into
    // fresh TOEIC-style sentences per round.
    private static final Map<String, String> FILES = Map.of(
        "easy", "easy.txt",
        "medium", "medium.txt",
        "hard", "hard.txt",
        "notion", "notion.txt",
        "notion-ai", "notion-words.txt"
    );

    // Each pool maps word -> enabled, in file order.
    private static final Map<String, LinkedHashMap<String, Boolean>> POOLS = loadAllPools();

    private static volatile FirebaseStore firebase;

    private WordBank() {
    }

    /**
     * Wires up persistence and overlays any previously saved pools on top of
     * the .txt defaults. Call once at startup, before serving requests.
     */
    static void initPersistence(FirebaseStore store) {
        firebase = store;
        for (String difficulty : FILES.keySet()) {
            String saved = loadPersisted(difficulty);
            if (saved == null) continue;
            LinkedHashMap<String, Boolean> pool = parsePool(saved);
            // Ignore empty/corrupt saved state rather than breaking a course.
            if (pool.isEmpty() || !pool.containsValue(true)) continue;
            synchronized (POOLS) {
                POOLS.put(difficulty, pool);
            }
        }
    }

    static List<String> get(String difficulty, int count) {
        List<String> shuffled;
        synchronized (POOLS) {
            LinkedHashMap<String, Boolean> pool =
                POOLS.getOrDefault(normalize(difficulty), POOLS.get("medium"));
            shuffled = enabledWords(pool);
            // The admin guards keep at least one word enabled per pool, but
            // never risk the infinite fill loop below on an empty list.
            if (shuffled.isEmpty()) shuffled = new ArrayList<>(pool.keySet());
        }

        List<String> result = new ArrayList<>();
        while (result.size() < count) {
            Collections.shuffle(shuffled);
            result.addAll(shuffled);
        }
        return result.subList(0, count);
    }

    /**
     * The current pool for a difficulty as a JSON array of
     * {"word":..,"enabled":..} objects in file order, or null if unknown.
     */
    static String listJson(String difficulty) {
        synchronized (POOLS) {
            LinkedHashMap<String, Boolean> pool = POOLS.get(normalize(difficulty));
            return pool == null ? null : toJson(pool);
        }
    }

    /** Adds a word (enabled) to a pool. Returns an error message, or null on success. */
    static String add(String difficulty, String word) {
        if (word == null || word.strip().isEmpty()) return "Word must not be empty.";
        word = word.strip();
        String snapshot;
        synchronized (POOLS) {
            LinkedHashMap<String, Boolean> pool = POOLS.get(normalize(difficulty));
            if (pool == null) return "Unknown difficulty.";
            if (pool.containsKey(word)) return "That word is already in this list.";
            pool.put(word, true);
            snapshot = toJson(pool);
        }
        persist(difficulty, snapshot);
        return null;
    }

    /** Removes a word from a pool. Returns an error message, or null on success. */
    static String remove(String difficulty, String word) {
        if (word == null) return "Word must not be empty.";
        String snapshot;
        synchronized (POOLS) {
            LinkedHashMap<String, Boolean> pool = POOLS.get(normalize(difficulty));
            if (pool == null) return "Unknown difficulty.";
            if (!pool.containsKey(word)) return "That word is not in this list.";
            if (Boolean.TRUE.equals(pool.get(word)) && enabledWords(pool).size() <= 1) {
                return "Cannot remove the last enabled word in a list.";
            }
            pool.remove(word);
            snapshot = toJson(pool);
        }
        persist(difficulty, snapshot);
        return null;
    }

    /**
     * Enables or disables a word; disabled words stay listed but are never
     * quizzed. Returns an error message, or null on success.
     */
    static String setEnabled(String difficulty, String word, boolean enabled) {
        if (word == null) return "Word must not be empty.";
        String snapshot;
        synchronized (POOLS) {
            LinkedHashMap<String, Boolean> pool = POOLS.get(normalize(difficulty));
            if (pool == null) return "Unknown difficulty.";
            if (!pool.containsKey(word)) return "That word is not in this list.";
            if (!enabled && Boolean.TRUE.equals(pool.get(word)) && enabledWords(pool).size() <= 1) {
                return "Cannot disable the last enabled word in a list.";
            }
            pool.put(word, enabled);
            snapshot = toJson(pool);
        }
        persist(difficulty, snapshot);
        return null;
    }

    /**
     * Discards a pool's saved state and reloads its .txt defaults
     * (everything enabled). Returns an error message, or null on success.
     */
    static String reset(String difficulty) {
        String key = normalize(difficulty);
        String filename = FILES.get(key);
        if (filename == null) return "Unknown difficulty.";
        LinkedHashMap<String, Boolean> fresh = loadPool(resolveWordBankDir(), filename);
        synchronized (POOLS) {
            POOLS.put(key, fresh);
        }
        clearPersisted(key);
        return null;
    }

    private static List<String> enabledWords(LinkedHashMap<String, Boolean> pool) {
        List<String> words = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : pool.entrySet()) {
            if (Boolean.TRUE.equals(e.getValue())) words.add(e.getKey());
        }
        return words;
    }

    private static String toJson(LinkedHashMap<String, Boolean> pool) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, Boolean> e : pool.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"word\":\"").append(Json.escape(e.getKey()))
              .append("\",\"enabled\":").append(e.getValue()).append('}');
        }
        return sb.append(']').toString();
    }

    private static LinkedHashMap<String, Boolean> parsePool(String json) {
        LinkedHashMap<String, Boolean> pool = new LinkedHashMap<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{[^{}]*}").matcher(json);
        while (m.find()) {
            String obj = m.group();
            String word = Json.getString(obj, "word");
            if (word == null || word.isBlank()) continue;
            Boolean enabled = Json.getBoolean(obj, "enabled");
            pool.put(word, enabled == null || enabled);
        }
        return pool;
    }

    // --- persistence (Firebase when configured, local files otherwise) ---

    private static String node(String difficulty) {
        return "wordbank/" + normalize(difficulty);
    }

    private static Path localFile(String difficulty) {
        return Path.of("data", "wordbank-" + normalize(difficulty) + ".json");
    }

    private static String loadPersisted(String difficulty) {
        if (firebase != null) {
            try {
                return firebase.load(node(difficulty));
            } catch (Exception e) {
                System.err.println("Could not load word list '" + difficulty
                    + "' from Firebase: " + e.getMessage());
                return null;
            }
        }
        try {
            Path file = localFile(difficulty);
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            System.err.println("Could not load word list '" + difficulty + "': " + e.getMessage());
            return null;
        }
    }

    private static void persist(String difficulty, String json) {
        if (firebase != null) {
            try {
                firebase.save(node(difficulty), json);
            } catch (Exception e) {
                System.err.println("Could not save word list '" + difficulty
                    + "' to Firebase: " + e.getMessage());
            }
            return;
        }
        try {
            Path file = localFile(difficulty);
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not save word list '" + difficulty + "': " + e.getMessage());
        }
    }

    private static void clearPersisted(String difficulty) {
        if (firebase != null) {
            try {
                // Writing null deletes the node.
                firebase.save(node(difficulty), "null");
            } catch (Exception e) {
                System.err.println("Could not clear saved word list '" + difficulty
                    + "' in Firebase: " + e.getMessage());
            }
            return;
        }
        try {
            Files.deleteIfExists(localFile(difficulty));
        } catch (IOException e) {
            System.err.println("Could not clear saved word list '" + difficulty + "': " + e.getMessage());
        }
    }

    // --- .txt defaults ---

    private static Map<String, LinkedHashMap<String, Boolean>> loadAllPools() {
        Path dir = resolveWordBankDir();
        Map<String, LinkedHashMap<String, Boolean>> pools = new HashMap<>();
        for (Map.Entry<String, String> e : FILES.entrySet()) {
            pools.put(e.getKey(), loadPool(dir, e.getValue()));
        }
        return pools;
    }

    private static Path resolveWordBankDir() {
        Path here = Path.of("wordbank");
        if (Files.isDirectory(here)) return here;
        Path fromProjectRoot = Path.of("backend", "wordbank");
        if (Files.isDirectory(fromProjectRoot)) return fromProjectRoot;
        return here;
    }

    private static LinkedHashMap<String, Boolean> loadPool(Path dir, String filename) {
        Path file = dir.resolve(filename);
        List<String> lines = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                lines.add(trimmed);
            }
        } catch (IOException e) {
            System.err.println("Could not load word bank file " + file + ": " + e.getMessage());
        }
        if (lines.isEmpty()) {
            System.err.println("Word bank file " + file + " is empty or missing; using fallback words.");
            lines.addAll(FALLBACK.getOrDefault(filename, List.of("sushi")));
        }
        // Everything starts enabled; the admin API can disable entries.
        LinkedHashMap<String, Boolean> pool = new LinkedHashMap<>();
        for (String line : lines) {
            pool.put(line, true);
        }
        return pool;
    }

    private static String normalize(String difficulty) {
        return difficulty == null ? "" : difficulty.strip().toLowerCase();
    }
}
