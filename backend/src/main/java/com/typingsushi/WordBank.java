package com.typingsushi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds the word/phrase lists used by the typing game, grouped by difficulty.
 *
 * The original sushida.net splits its huge word pool into three courses
 * purely by input length (roughly 2-8 / 5-10 / 9+ characters) and draws from
 * everyday vocabulary, idioms, and pop culture rather than sushi terms
 * specifically -- the sushi theme is just the visual wrapper. This bank
 * mirrors that length-tiered, subject-agnostic structure with original
 * English content instead of translating the source site's word list.
 */
final class WordBank {

    private static final List<String> EASY = List.of(
        "artist", "sunrise", "backpack", "penguin", "campfire", "umbrella",
        "notebook", "goldfish", "raincoat", "hot spring", "moonlight",
        "snowflake", "seashell", "otter", "lighthouse", "picnic", "bicycle",
        "waterfall", "firefly", "keyboard", "mouse click", "blue sky",
        "autumn leaves", "love letter", "ice cream", "guitar solo",
        "city lights", "morning coffee", "silver lining", "paper airplane"
    );

    private static final List<String> MEDIUM = List.of(
        "Better late than never.",
        "Actions speak louder than words.",
        "The early bird catches the worm.",
        "Practice makes perfect.",
        "It's raining cats and dogs.",
        "Piece of cake!",
        "Time flies when you're having fun.",
        "Curiosity killed the cat.",
        "Every cloud has a silver lining.",
        "Don't judge a book by its cover.",
        "Kill two birds with one stone.",
        "The ball is in your court.",
        "Break a leg out there!",
        "Once in a blue moon.",
        "When pigs fly.",
        "Barking up the wrong tree.",
        "Costs an arm and a leg.",
        "Let the cat out of the bag.",
        "Better safe than sorry.",
        "Please hold while we transfer your call.",
        "Your package has been delivered.",
        "Don't forget to water the plants.",
        "Wi-Fi password, please?",
        "I think we're out of coffee again.",
        "Can you send that file one more time?"
    );

    private static final List<String> HARD = List.of(
        "The grass is always greener on the other side of the fence.",
        "You can lead a horse to water, but you can't make it drink.",
        "Rome wasn't built in a day, so take your time.",
        "The early bird catches the worm, but the second mouse gets the cheese.",
        "Please restart your computer and try logging in again.",
        "I could have sworn I left my phone right here a minute ago.",
        "Would you like fries with that, or are you watching your diet today?",
        "The password you entered does not meet the security requirements.",
        "According to all known laws of aviation, bees really shouldn't be able to fly.",
        "We interrupt this program to bring you a special weather bulletin.",
        "Congratulations, you have been selected as our grand prize winner!",
        "Please remain seated until the seatbelt sign has been turned off.",
        "Somewhere between yesterday's mistakes and tomorrow's uncertainty lies today.",
        "Insert coin to continue, or press start to try again.",
        "The meeting that could have been an email is starting in five minutes.",
        "Warning: low battery, please connect your charger as soon as possible."
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
