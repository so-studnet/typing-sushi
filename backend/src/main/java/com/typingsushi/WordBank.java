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
 * The actual content lives in plain text files under wordbank/ (one
 * entry per line; '#' starts a comment, blank lines are ignored) instead of
 * being hardcoded in this class. That way the game's quizzed words can be
 * edited and maintained -- add, remove, or rebalance entries -- by editing
 * those .txt files and restarting the server, with no Java changes or
 * recompilation required.
 *
 * The pools can also be edited at runtime through the admin API (see
 * Main.AdminWordsHandler): words can be added, removed, or toggled between
 * enabled and disabled -- disabled words stay in the list but are never
 * quizzed. Those edits are deliberately in-memory only: they apply to the
 * running server immediately but are gone after a restart, when the pools
 * reload from the .txt files (everything enabled).
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

    // Each pool maps word -> enabled, in file order.
    private static final Map<String, LinkedHashMap<String, Boolean>> POOLS = loadAllPools();

    private WordBank() {
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
            if (pool == null) return null;
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
    }

    /** Adds a word (enabled) to a pool at runtime. Returns an error message, or null on success. */
    static String add(String difficulty, String word) {
        if (word == null || word.strip().isEmpty()) return "Word must not be empty.";
        word = word.strip();
        synchronized (POOLS) {
            LinkedHashMap<String, Boolean> pool = POOLS.get(normalize(difficulty));
            if (pool == null) return "Unknown difficulty.";
            if (pool.containsKey(word)) return "That word is already in this list.";
            pool.put(word, true);
            return null;
        }
    }

    /** Removes a word from a pool at runtime. Returns an error message, or null on success. */
    static String remove(String difficulty, String word) {
        if (word == null) return "Word must not be empty.";
        synchronized (POOLS) {
            LinkedHashMap<String, Boolean> pool = POOLS.get(normalize(difficulty));
            if (pool == null) return "Unknown difficulty.";
            if (!pool.containsKey(word)) return "That word is not in this list.";
            if (Boolean.TRUE.equals(pool.get(word)) && enabledWords(pool).size() <= 1) {
                return "Cannot remove the last enabled word in a list.";
            }
            pool.remove(word);
            return null;
        }
    }

    /**
     * Enables or disables a word at runtime; disabled words stay listed but
     * are never quizzed. Returns an error message, or null on success.
     */
    static String setEnabled(String difficulty, String word, boolean enabled) {
        if (word == null) return "Word must not be empty.";
        synchronized (POOLS) {
            LinkedHashMap<String, Boolean> pool = POOLS.get(normalize(difficulty));
            if (pool == null) return "Unknown difficulty.";
            if (!pool.containsKey(word)) return "That word is not in this list.";
            if (!enabled && Boolean.TRUE.equals(pool.get(word)) && enabledWords(pool).size() <= 1) {
                return "Cannot disable the last enabled word in a list.";
            }
            pool.put(word, enabled);
            return null;
        }
    }

    private static List<String> enabledWords(LinkedHashMap<String, Boolean> pool) {
        List<String> words = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : pool.entrySet()) {
            if (Boolean.TRUE.equals(e.getValue())) words.add(e.getKey());
        }
        return words;
    }

    private static String normalize(String difficulty) {
        return difficulty == null ? "" : difficulty.strip().toLowerCase();
    }

    private static Map<String, LinkedHashMap<String, Boolean>> loadAllPools() {
        Path dir = resolveWordBankDir();
        Map<String, LinkedHashMap<String, Boolean>> pools = new HashMap<>();
        pools.put("easy", loadPool(dir, "easy.txt"));
        pools.put("medium", loadPool(dir, "medium.txt"));
        pools.put("hard", loadPool(dir, "hard.txt"));
        pools.put("notion", loadPool(dir, "notion.txt"));
        // Seed words for the Notion AI course; AiSentences turns a random
        // sample of these into fresh TOEIC-style sentences per round.
        pools.put("notion-ai", loadPool(dir, "notion-words.txt"));
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
        // Mutable on purpose: the admin API edits these pools at runtime.
        // Everything starts enabled; the admin API can disable entries.
        LinkedHashMap<String, Boolean> pool = new LinkedHashMap<>();
        for (String line : lines) {
            pool.put(line, true);
        }
        return pool;
    }
}
