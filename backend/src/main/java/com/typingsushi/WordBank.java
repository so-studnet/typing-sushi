package com.typingsushi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Holds the sushi-themed word/phrase lists used by the typing game, grouped by difficulty. */
final class WordBank {

    // Mirrors the original sushida.net difficulty curve: easy is single
    // words, medium is short natural sentences, hard is long, complex ones.
    private static final List<String> EASY = List.of(
        "sushi", "wasabi", "ginger", "salmon", "shrimp", "unagi", "sashimi",
        "chopsticks", "soy sauce", "rice ball", "green tea", "seaweed",
        "avocado", "cucumber", "octopus", "tuna", "roll", "plate", "miso soup",
        "rice vinegar", "tempura", "nori sheet", "fish roe", "sea urchin",
        "sushi chef", "raw fish", "crab", "squid", "sesame seeds"
    );

    private static final List<String> MEDIUM = List.of(
        "The chef slices the tuna with a sharp knife.",
        "Fresh wasabi adds a spicy kick.",
        "This roll is wrapped in crisp seaweed.",
        "Please pass the soy sauce.",
        "The rice is seasoned with vinegar.",
        "Salmon nigiri melts in your mouth.",
        "We ordered a plate of spicy tuna rolls.",
        "The conveyor belt carries fresh plates by.",
        "Dip the sushi lightly in soy sauce.",
        "Pickled ginger cleanses the palate between bites.",
        "The chef presses the rice by hand.",
        "Green tea pairs well with sushi.",
        "This restaurant is famous for its sashimi.",
        "The eel sauce is sweet and rich.",
        "A good sushi chef trains for years."
    );

    private static final List<String> HARD = List.of(
        "The sushi chef carefully selects the freshest tuna from the market every single morning.",
        "A skilled itamae balances rice temperature, vinegar ratio, and fish freshness in every piece.",
        "Diners often watch in awe as the chef's knife glides through the silver skin of the mackerel.",
        "Traditional sushi rice is seasoned with a delicate blend of rice vinegar, sugar, and salt.",
        "The omakase menu changes daily depending on what the fish market has to offer.",
        "Some sushi restaurants age their tuna for several days to deepen its flavor.",
        "A great piece of nigiri should have a perfect balance between the rice and the fish.",
        "The conveyor belt sushi restaurant lets customers pick any plate that catches their eye.",
        "Learning to properly cook sushi rice can take years of daily practice.",
        "The chef garnished the platter with delicate slices of pickled ginger and fresh shiso leaves."
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
