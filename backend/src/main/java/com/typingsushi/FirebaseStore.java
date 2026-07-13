package com.typingsushi;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;

/**
 * Persists the leaderboard JSON in a Firebase Realtime Database via its REST
 * API, so scores survive restarts on hosts without a persistent disk (e.g.
 * Render's free plan, whose filesystem is wiped on every restart).
 *
 * Authenticates as the Firebase service account by signing a JWT with the
 * account's private key and exchanging it for a Google OAuth2 access token --
 * all with JDK built-ins, keeping this project free of external dependencies.
 * Because the server talks to Firebase as an admin, the database's security
 * rules can (and should) deny all direct public access.
 *
 * Configured with two environment variables:
 *   FIREBASE_DB_URL          e.g. https://myproj-default-rtdb.asia-southeast1.firebasedatabase.app
 *   FIREBASE_SERVICE_ACCOUNT the service account key JSON, as a single line
 */
final class FirebaseStore {

    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.database"
        + " https://www.googleapis.com/auth/userinfo.email";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final String dataUrl;
    private final String clientEmail;
    private final String tokenUri;
    private final PrivateKey privateKey;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private String cachedToken;
    private long tokenExpiresAtMillis;

    /** Returns a store configured from the environment, or null when not configured. */
    static FirebaseStore fromEnv() {
        String dbUrl = System.getenv("FIREBASE_DB_URL");
        String serviceAccount = System.getenv("FIREBASE_SERVICE_ACCOUNT");
        if (dbUrl == null || dbUrl.isBlank() || serviceAccount == null || serviceAccount.isBlank()) {
            return null;
        }
        try {
            return new FirebaseStore(dbUrl.trim(), serviceAccount);
        } catch (Exception e) {
            System.err.println("Ignoring invalid Firebase configuration: " + e.getMessage());
            return null;
        }
    }

    private FirebaseStore(String dbUrl, String serviceAccountJson) throws GeneralSecurityException {
        if (dbUrl.endsWith("/")) dbUrl = dbUrl.substring(0, dbUrl.length() - 1);
        this.dataUrl = dbUrl + "/leaderboard.json";
        this.clientEmail = require(serviceAccountJson, "client_email");
        this.tokenUri = require(serviceAccountJson, "token_uri");
        this.privateKey = parsePrivateKey(require(serviceAccountJson, "private_key"));
    }

    private static String require(String json, String key) {
        String value = Json.getString(json, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("service account JSON is missing \"" + key + "\"");
        }
        return value;
    }

    private static PrivateKey parsePrivateKey(String pem) throws GeneralSecurityException {
        String base64 = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    /** Fetches the stored leaderboard JSON, or null when nothing is stored yet. */
    String load() throws IOException, InterruptedException {
        HttpResponse<String> response = send(request().GET().build());
        if (response.statusCode() != 200) {
            throw new IOException("Firebase load failed (" + response.statusCode() + "): " + response.body());
        }
        String body = response.body();
        return "null".equals(body.strip()) ? null : body;
    }

    /** Replaces the stored leaderboard with the given JSON array. */
    void save(String json) throws IOException, InterruptedException {
        HttpResponse<String> response = send(request()
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build());
        if (response.statusCode() != 200) {
            throw new IOException("Firebase save failed (" + response.statusCode() + "): " + response.body());
        }
    }

    private HttpRequest.Builder request() throws IOException, InterruptedException {
        return HttpRequest.newBuilder()
            .uri(URI.create(dataUrl))
            .timeout(TIMEOUT)
            .header("Authorization", "Bearer " + accessToken());
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Returns a valid OAuth2 access token, reusing the cached one until close
     * to expiry. Google's token endpoint expects a JWT signed with the service
     * account's key (RFC 7523 "JWT bearer" grant).
     */
    private synchronized String accessToken() throws IOException, InterruptedException {
        long now = System.currentTimeMillis();
        if (cachedToken != null && now < tokenExpiresAtMillis - 60_000) {
            return cachedToken;
        }

        long issuedAt = now / 1000;
        String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String claims = base64Url(("{\"iss\":\"" + Json.escape(clientEmail) + "\","
            + "\"scope\":\"" + SCOPE + "\","
            + "\"aud\":\"" + Json.escape(tokenUri) + "\","
            + "\"iat\":" + issuedAt + ",\"exp\":" + (issuedAt + 3600) + "}")
            .getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;
        String jwt = signingInput + "." + base64Url(sign(signingInput));

        // Base64url characters are URL-safe, so the assertion needs no extra encoding.
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(tokenUri))
            .timeout(TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(
                "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + jwt))
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() != 200) {
            throw new IOException("Firebase token request failed (" + response.statusCode() + "): "
                + response.body());
        }
        String token = Json.getString(response.body(), "access_token");
        if (token == null || token.isBlank()) {
            throw new IOException("Firebase token response had no access_token");
        }
        cachedToken = token;
        tokenExpiresAtMillis = now + 3600_000;
        return token;
    }

    private byte[] sign(String input) throws IOException {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(input.getBytes(StandardCharsets.UTF_8));
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IOException("Could not sign Firebase token request: " + e.getMessage(), e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
