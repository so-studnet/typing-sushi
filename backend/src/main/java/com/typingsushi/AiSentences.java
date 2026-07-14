package com.typingsushi;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates fresh TOEIC-style example sentences for the "Notion AI" course.
 *
 * Each call samples a handful of seed words/phrases from the "notion-ai"
 * word pool (backend/wordbank/notion-words.txt, imported from the Notion
 * 未知英単語帳 database's 単語 column) and asks the Groq API to write one
 * TOEIC-flavored example sentence per seed, so every round quizzes new
 * sentences built around the player's own vocabulary list.
 *
 * Requires the same GROQ_API_KEY (and optional GROQ_MODEL) environment
 * variables as the explain feature. Returns null when the key is missing
 * or the API call fails, so the caller can fall back to the static Notion
 * sentence pool instead of breaking the game.
 */
final class AiSentences {

    private static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    // One Groq call generates this many sentences; the words API cycles them
    // to fill larger count requests, same as the static pools repeat.
    private static final int SEED_COUNT = 12;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private AiSentences() {
    }

    /**
     * Generates a fresh batch of TOEIC-style sentences from random seed
     * words, or returns null when generation is unavailable (no API key,
     * API error, or an unusable response).
     */
    static List<String> generate() {
        String apiKey = System.getenv("GROQ_API_KEY");
        if (apiKey == null || apiKey.isBlank()) return null;

        List<String> seeds = WordBank.get("notion-ai", SEED_COUNT);
        String model = System.getenv().getOrDefault("GROQ_MODEL", DEFAULT_MODEL);
        String prompt = "You are writing typing-practice sentences for an English learner "
            + "preparing for the TOEIC test. For each word or phrase in the list below, write "
            + "exactly one TOEIC-style example sentence set in a business, office, travel, or "
            + "everyday situation that naturally uses that word or phrase. Each sentence must be "
            + "8 to 16 words long and use only plain ASCII characters with straight quotes and "
            + "apostrophes. Output one sentence per line, in the same order as the list, with no "
            + "numbering, bullets, translations, or any other text. List: " + String.join("; ", seeds);
        String requestBody = "{\"model\":\"" + Json.escape(model) + "\","
            + "\"messages\":[{\"role\":\"user\",\"content\":\"" + Json.escape(prompt) + "\"}]}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Groq API error " + response.statusCode()
                    + " while generating sentences: " + response.body());
                return null;
            }

            String content = Json.getString(response.body(), "content");
            if (content == null || content.isBlank()) {
                System.err.println("Groq API sentence response had no content: " + response.body());
                return null;
            }

            List<String> sentences = parseSentences(content);
            return sentences.isEmpty() ? null : sentences;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException e) {
            System.err.println("Groq API sentence request failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * One sentence per response line, cleaned up for typability: numbering
     * and bullets stripped, smart punctuation straightened, and anything the
     * player couldn't type on a plain keyboard dropped.
     */
    private static List<String> parseSentences(String content) {
        List<String> sentences = new ArrayList<>();
        for (String line : content.split("\n")) {
            String sentence = line.strip()
                .replaceFirst("^(\\d+[.)]|[-*•])\\s*", "")
                .replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-')
                .strip();
            if (sentence.isEmpty() || sentence.length() > 200) continue;
            if (!sentence.matches("\\p{ASCII}+")) continue;
            sentences.add(sentence);
        }
        return sentences;
    }
}
