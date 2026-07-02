package com.typingsushi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        Leaderboard leaderboard = new Leaderboard(Path.of("data", "leaderboard.json"));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));

        server.createContext("/api/words", new WordsHandler());
        server.createContext("/api/leaderboard", new LeaderboardHandler(leaderboard));
        server.createContext("/api/score", new ScoreHandler(leaderboard));
        server.createContext("/", new StaticFileHandler(frontendDir));

        server.start();
        System.out.println("Sushi Typing server running at http://localhost:" + port);
        System.out.println("Serving frontend from: " + frontendDir.toAbsolutePath());
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
