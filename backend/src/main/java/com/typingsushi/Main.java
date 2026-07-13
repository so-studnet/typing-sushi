package com.typingsushi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/** Entry point: serves the sushi typing game's static frontend and its small JSON API. */
public final class Main {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
        "html", "text/html; charset=utf-8",
        "css", "text/css; charset=utf-8",
        "js", "application/javascript; charset=utf-8",
        "json", "application/json; charset=utf-8",
        "ico", "image/x-icon",
        "svg", "image/svg+xml"
    );

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        Path frontendDir = resolveFrontendDir();
        FirebaseStore firebase = FirebaseStore.fromEnv();
        Leaderboard leaderboard = new Leaderboard(Path.of("data", "leaderboard.json"), firebase);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));

        server.createContext("/api/words", new WordsHandler());
        server.createContext("/api/leaderboard", new LeaderboardHandler(leaderboard));
        server.createContext("/api/score", new ScoreHandler(leaderboard));
        server.createContext("/api/explain", new ExplainHandler());
        server.createContext("/api/admin/words", new AdminWordsHandler());
        server.createContext("/api/admin/leaderboard", new AdminLeaderboardHandler(leaderboard));
        server.createContext("/", new StaticFileHandler(frontendDir));

        server.start();
        System.out.println("Sushi Typing server running at http://localhost:" + port);
        System.out.println("Serving frontend from: " + frontendDir.toAbsolutePath());
        System.out.println(firebase != null
            ? "Leaderboard persistence: Firebase Realtime Database"
            : "Leaderboard persistence: local file (data/leaderboard.json)");
    }

    private static Path resolveFrontendDir() {
        Path candidate = Path.of("frontend");
        if (Files.isDirectory(candidate)) return candidate;
        Path sibling = Path.of("..", "frontend");
        if (Files.isDirectory(sibling)) return sibling;
        return candidate;
    }

    /** GET /api/words?difficulty=easy|medium|hard&count=N */
    static final class WordsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendMethodNotAllowed(ex);
                return;
            }
            Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());
            String difficulty = query.getOrDefault("difficulty", "medium");
            int count = 30;
            try {
                count = Math.max(1, Math.min(200, Integer.parseInt(query.getOrDefault("count", "30"))));
            } catch (NumberFormatException ignored) {
                // keep default
            }
            List<String> words = WordBank.get(difficulty, count);
            sendJson(ex, 200, Json.stringArray(words));
        }
    }

    /** GET /api/leaderboard */
    static final class LeaderboardHandler implements HttpHandler {
        private final Leaderboard leaderboard;

        LeaderboardHandler(Leaderboard leaderboard) {
            this.leaderboard = leaderboard;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendMethodNotAllowed(ex);
                return;
            }
            sendJson(ex, 200, Leaderboard.toJson(leaderboard.top()));
        }
    }

    /** POST /api/score {"name":"...","course":"...","earned":12.34} */
    static final class ScoreHandler implements HttpHandler {
        private final Leaderboard leaderboard;

        ScoreHandler(Leaderboard leaderboard) {
            this.leaderboard = leaderboard;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendMethodNotAllowed(ex);
                return;
            }
            String body = readBody(ex);
            String name = Json.getString(body, "name");
            String course = Json.getString(body, "course");
            Double earned = Json.getNumber(body, "earned");

            if (name == null || name.isBlank() || earned == null || earned < 0) {
                sendJson(ex, 400, "{\"error\":\"invalid score payload\"}");
                return;
            }
            name = name.strip();
            if (name.length() > 20) name = name.substring(0, 20);

            List<Leaderboard.Entry> updated = leaderboard.submit(name, course == null ? "" : course, earned);
            sendJson(ex, 200, Leaderboard.toJson(updated));
        }
    }

    /**
     * POST /api/explain {"sentence":"..."} -- asks the Groq API (OpenAI-compatible
     * chat completions) to explain the given word/sentence in English, and
     * returns {"explanation":"..."}. Requires the GROQ_API_KEY environment
     * variable; the key never reaches the client. Without it, responds 503 so
     * the frontend can show a plain "not configured" message instead of a
     * generic error.
     */
    static final class ExplainHandler implements HttpHandler {
        private static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";
        private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
        private static final int MAX_SENTENCE_LENGTH = 500;
        private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendMethodNotAllowed(ex);
                return;
            }

            String apiKey = System.getenv("GROQ_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                sendJson(ex, 503, "{\"error\":\"AI explanations are not configured on this server.\"}");
                return;
            }

            String body = readBody(ex);
            String sentence = Json.getString(body, "sentence");
            if (sentence == null || sentence.isBlank()) {
                sendJson(ex, 400, "{\"error\":\"Missing 'sentence'.\"}");
                return;
            }
            if (sentence.length() > MAX_SENTENCE_LENGTH) {
                sentence = sentence.substring(0, MAX_SENTENCE_LENGTH);
            }

            String model = System.getenv().getOrDefault("GROQ_MODEL", DEFAULT_MODEL);
            String prompt = "Explain the meaning of the following sentence or word in simple English, "
                + "in at most two short sentences. Then, on a new line, write \"Similar ways to say it:\" "
                + "followed by exactly two rephrased example sentences, one per line, that express the "
                + "same meaning using different words. Use short sentences and basic, everyday vocabulary "
                + "suitable for a beginner English learner, both in the explanation and in the two examples. "
                + "Respond only in English and use no other language. "
                + "Text: \"" + sentence + "\"";
            String requestBody = "{\"model\":\"" + Json.escape(model) + "\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + Json.escape(prompt) + "\"}]}";

            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    System.err.println("Groq API error " + response.statusCode() + ": " + response.body());
                    sendJson(ex, 502, "{\"error\":\"Could not get an explanation right now.\"}");
                    return;
                }

                String explanation = Json.getString(response.body(), "content");
                if (explanation == null || explanation.isBlank()) {
                    System.err.println("Groq API response had no content: " + response.body());
                    sendJson(ex, 502, "{\"error\":\"Could not get an explanation right now.\"}");
                    return;
                }

                sendJson(ex, 200, "{\"explanation\":\"" + Json.escape(explanation.strip()) + "\"}");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sendJson(ex, 502, "{\"error\":\"Could not get an explanation right now.\"}");
            } catch (IOException e) {
                System.err.println("Groq API request failed: " + e.getMessage());
                sendJson(ex, 502, "{\"error\":\"Could not get an explanation right now.\"}");
            }
        }
    }

    /**
     * Gate for the admin API (see /admin.html). Requires the ADMIN_PASSWORD
     * environment variable to be set on the server and the same value sent by
     * the client in the X-Admin-Password header on every request. When it
     * does not pass, an error response has already been sent.
     */
    private static boolean requireAdmin(HttpExchange ex) throws IOException {
        String configured = System.getenv("ADMIN_PASSWORD");
        if (configured == null || configured.isBlank()) {
            sendJson(ex, 503, "{\"error\":\"Admin access is not configured on this server.\"}");
            return false;
        }
        String given = ex.getRequestHeaders().getFirst("X-Admin-Password");
        // Constant-time comparison, so response timing leaks nothing about the password.
        if (given == null || !MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) {
            sendJson(ex, 401, "{\"error\":\"Wrong password.\"}");
            return false;
        }
        return true;
    }

    /**
     * Admin API for editing the word pools at runtime (in-memory only; a
     * restart reloads the .txt files):
     *   GET    /api/admin/words?difficulty=easy            -> current word list
     *   POST   /api/admin/words {"difficulty":..,"word":..} -> add, returns updated list
     *   DELETE /api/admin/words?difficulty=easy&word=...    -> remove, returns updated list
     */
    static final class AdminWordsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!requireAdmin(ex)) return;
            switch (ex.getRequestMethod().toUpperCase()) {
                case "GET" -> list(ex);
                case "POST" -> add(ex);
                case "DELETE" -> remove(ex);
                default -> sendMethodNotAllowed(ex);
            }
        }

        private void list(HttpExchange ex) throws IOException {
            String difficulty = parseQuery(ex.getRequestURI().getRawQuery()).get("difficulty");
            List<String> words = WordBank.listAll(difficulty);
            if (words == null) {
                sendJson(ex, 404, "{\"error\":\"Unknown difficulty.\"}");
                return;
            }
            sendJson(ex, 200, Json.stringArray(words));
        }

        private void add(HttpExchange ex) throws IOException {
            String body = readBody(ex);
            String difficulty = Json.getString(body, "difficulty");
            String word = Json.getString(body, "word");
            String error = WordBank.add(difficulty, word);
            if (error != null) {
                sendJson(ex, 400, "{\"error\":\"" + Json.escape(error) + "\"}");
                return;
            }
            sendJson(ex, 200, Json.stringArray(WordBank.listAll(difficulty)));
        }

        private void remove(HttpExchange ex) throws IOException {
            Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());
            String difficulty = query.get("difficulty");
            String error = WordBank.remove(difficulty, query.get("word"));
            if (error != null) {
                sendJson(ex, 400, "{\"error\":\"" + Json.escape(error) + "\"}");
                return;
            }
            sendJson(ex, 200, Json.stringArray(WordBank.listAll(difficulty)));
        }
    }

    /** GET /api/admin/leaderboard -- top scores including each entry's recording time. */
    static final class AdminLeaderboardHandler implements HttpHandler {
        private final Leaderboard leaderboard;

        AdminLeaderboardHandler(Leaderboard leaderboard) {
            this.leaderboard = leaderboard;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!requireAdmin(ex)) return;
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendMethodNotAllowed(ex);
                return;
            }
            sendJson(ex, 200, Leaderboard.toDetailedJson(leaderboard.top()));
        }
    }

    /** Serves static files from the frontend directory, guarding against path traversal. */
    static final class StaticFileHandler implements HttpHandler {
        private final Path root;

        StaticFileHandler(Path root) {
            this.root = root.normalize();
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendMethodNotAllowed(ex);
                return;
            }
            String rawPath = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);
            String relative = rawPath.equals("/") ? "index.html" : rawPath.substring(1);

            Path resolved = root.resolve(relative).normalize();
            if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
                byte[] notFound = "404 Not Found".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                ex.sendResponseHeaders(404, notFound.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(notFound);
                }
                return;
            }

            String ext = extensionOf(resolved.getFileName().toString());
            String contentType = CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
            byte[] bytes = Files.readAllBytes(resolved);
            ex.getResponseHeaders().set("Content-Type", contentType);
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String extensionOf(String filename) {
            int dot = filename.lastIndexOf('.');
            return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return Map.of();
        Map<String, String> result = new java.util.HashMap<>();
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendMethodNotAllowed(HttpExchange ex) throws IOException {
        byte[] bytes = "405 Method Not Allowed".getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(405, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Main() {
    }
}
