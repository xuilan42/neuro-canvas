package com.example.neurocanvas.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Asks GitHub once per launch whether a newer release exists. Only reads public data; sends nothing about you. */
public final class Updater {
    private static final String API = "https://api.github.com/repos/xuilan42/neuro-canvas/releases/latest";
    private static volatile String latest;
    private static volatile String url;
    private static volatile boolean notified;

    private Updater() { }

    public static void check(String current) {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(8)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(API)).timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json").header("User-Agent", "neurocanvas-update-check").GET().build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
            if (response.statusCode() != 200) {
                return;
            }
            try {
                JsonObject o = JsonParser.parseString(response.body()).getAsJsonObject();
                String tag = o.get("tag_name").getAsString();
                if (newer(tag, current)) {
                    url = o.get("html_url").getAsString();
                    latest = tag.startsWith("v") ? tag.substring(1) : tag;
                }
            } catch (RuntimeException ignored) {
                // Unexpected answer: stay silent.
            }
        }).exceptionally(e -> null);
    }

    /** True when {@code tag} (like "v0.9.0") is a higher version than {@code current}. */
    static boolean newer(String tag, String current) {
        int[] a = parse(tag);
        int[] b = parse(current);
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return a[i] > b[i];
            }
        }
        return false;
    }

    private static int[] parse(String v) {
        String[] parts = v.replaceFirst("^v", "").split("[.\\-+]");
        int[] out = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("\\D", ""));
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    public static String latest() {
        return latest;
    }

    public static String url() {
        return url;
    }

    /** True once, the first time it is asked after a newer version was found. */
    public static boolean takeNotice() {
        if (latest != null && !notified) {
            notified = true;
            return true;
        }
        return false;
    }
}
