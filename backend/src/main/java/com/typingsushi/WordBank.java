package com.typingsushi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
 */
final class WordBank {

    // Small built-in safety net so the game still works if wordbank/ is
    // missing or a file fails to load, rather than serving empty quizzes.
    private static final Map<String, List<String>> FALLBACK = Map.of(
        "easy.txt", List.of("sushi", "wasabi", "green tea"),
        "medium.txt", List.of("Practice makes perfect.", "Better late than never."),
        "hard.txt", List.of("Please restart your computer and try logging in again.")
    );

    private static final Map<String, List<String>> POOLS = loadAllPools();

    private WordBank() {
    }

    static List<String> get(String difficulty, int count) {
        List<String> pool = POOLS.getOrDefault(
            difficulty == null ? "" : difficulty.toLowerCase(),
            POOLS.get("medium")
        );

        List<String> result = new ArrayList<>();
        List<String> shuffled = new ArrayList<>(pool);
        while (result.size() < count) {
            Collections.shuffle(shuffled);
            result.addAll(shuffled);
        }
        return result.subList(0, count);
    }

    private static Map<String, List<String>> loadAllPools() {
        Path dir = resolveWordBankDir();
        Map<String, List<String>> pools = new HashMap<>();
        pools.put("easy", loadPool(dir, "easy.txt"));
        pools.put("medium", loadPool(dir, "medium.txt"));
        pools.put("hard", loadPool(dir, "hard.txt"));
        return pools;
    }

    private static Path resolveWordBankDir() {
        Path here = Path.of("wordbank");
        if (Files.isDirectory(here)) return here;
        Path fromProjectRoot = Path.of("backend", "wordbank");
        if (Files.isDirectory(fromProjectRoot)) return fromProjectRoot;
        return here;
    }

    private static List<String> loadPool(Path dir, String filename) {
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
        return List.copyOf(lines);
    }
}
