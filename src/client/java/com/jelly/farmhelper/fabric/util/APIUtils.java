package com.jelly.farmhelper.fabric.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

public final class APIUtils {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private APIUtils() {
    }

    public static Optional<JsonObject> readJsonFromUrl(String url, String headerKey, String headerValue) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json");
            if (headerKey != null && !headerKey.isBlank() && headerValue != null && !headerValue.isBlank()) {
                builder.header(headerKey, headerValue);
            }
            HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            return Optional.of(JsonParser.parseString(response.body()).getAsJsonObject());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | IllegalStateException ex) {
            return Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public static Optional<JsonObject> readHypixelProfile(String apiKey, String uuid) {
        if (apiKey == null || apiKey.isBlank() || uuid == null || uuid.isBlank()) {
            return Optional.empty();
        }
        String encodedUuid = URLEncoder.encode(uuid, StandardCharsets.UTF_8);
        String url = "https://api.hypixel.net/v2/skyblock/profiles?uuid=" + encodedUuid;
        return readJsonFromUrl(url, "API-Key", apiKey);
    }

    public static Optional<JsonObject> readHypixelBazaar(String apiKey) {
        String url = "https://api.hypixel.net/v2/skyblock/bazaar";
        return readJsonFromUrl(url, "API-Key", apiKey);
    }
}
