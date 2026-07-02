package com.typingsushi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Holds the sushi-themed word/phrase lists used by the typing game, grouped by difficulty. */
final class WordBank {

    private static final List<String> EASY = List.of(
        "sushi", "wasabi", "ginger", "salmon", "shrimp", "unagi", "sashimi",
        "chopsticks", "soy sauce", "rice ball", "green tea", "seaweed",
        "avocado", "cucumber", "octopus", "tuna", "roll", "plate", "miso soup",
        "rice vinegar", "tempura", "nori sheet", "fish roe", "sea urchin"
    );

    private static final List<String> MEDIUM = List.of(
        "california roll", "spicy tuna roll", "salmon nigiri", "dragon roll sushi",
        "rainbow roll", "tempura shrimp roll", "cucumber avocado roll",
        "pickled ginger", "wasabi paste", "soy sauce dip", "conveyor belt",
        "sushi chef", "fresh wasabi root", "seasoned sushi rice", "eel sauce drizzle",
        "crab stick roll", "sesame seed topping", "green tea ice cream"
    );

    private static final List<String> HARD = List.of(
        "yellowtail sashimi platter", "spicy mayo drizzle sauce",
        "pickled ginger side dish", "soy sauce dipping bowl",
        "conveyor belt sushi restaurant", "master sushi chef technique",
        "freshly caught bluefin tuna", "traditional Japanese cuisine",
        "hand rolled nori seaweed wrap", "seasoned sushi rice recipe",
        "grilled eel with sweet sauce", "assorted nigiri sushi platter",
        "delicate raw fish preparation", "authentic omakase tasting menu"
    );

    private WordBank() {
    }

    static List<String> get(String difficulty, int count) {
        List<String> pool;
        switch (difficulty == null ? "" : difficulty.toLowerCase()) {
            case "easy":
                pool = EASY;
                break;
            case "hard":
                pool = HARD;
                break;
            case "medium":
            default:
                pool = MEDIUM;
                break;
        }

        List<String> result = new ArrayList<>();
        List<String> shuffled = new ArrayList<>(pool);
        while (result.size() < count) {
            Collections.shuffle(shuffled);
            result.addAll(shuffled);
        }
        return result.subList(0, count);
    }
}
