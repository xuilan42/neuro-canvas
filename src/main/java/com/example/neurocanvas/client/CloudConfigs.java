package com.example.neurocanvas.client;

import com.example.neurocanvas.module.NameTags;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/** Stores a share code on the project's server under a short code like "NRNA7VF3", and fetches it back. */
public final class CloudConfigs {
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(8)).build();
    private static final Pattern CODE = Pattern.compile("[A-HJ-NP-Za-hj-np-z2-9]{8}");

    private CloudConfigs() { }

    public static boolean looksLikeCode(String text) {
        return text != null && CODE.matcher(text.trim()).matches();
    }

    private static String base() throws IOException {
        String url = NameTags.mainUrl();
        if (url.isBlank()) {
            throw new IOException("this build has no server address");
        }
        return url;
    }

    private static String message(HttpResponse<String> r) {
        try {
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            if (o.has("error")) {
                return o.get("error").getAsString();
            }
        } catch (RuntimeException ignored) {
            // Not JSON.
        }
        return "server answered " + r.statusCode();
    }

    /** Uploads a share code; completes with the short code. */
    public static CompletableFuture<String> upload(String shareCode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/v1/cfg")).timeout(Duration.ofSeconds(15))
                        .POST(HttpRequest.BodyPublishers.ofString(shareCode)).build();
                HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() != 200) {
                    throw new IOException(message(r));
                }
                return JsonParser.parseString(r.body()).getAsJsonObject().get("code").getAsString();
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CompletionException(e);
            }
        });
    }

    /** Downloads the share code stored under a short code. */
    public static CompletableFuture<String> download(String code) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String c = code.trim().toUpperCase(Locale.ROOT);
                if (!looksLikeCode(c)) {
                    throw new IOException("a short code is 8 letters and digits, like NRNA7VF3");
                }
                HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/v1/cfg/" + c)).timeout(Duration.ofSeconds(15)).GET().build();
                HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() != 200) {
                    throw new IOException(r.statusCode() == 404 ? "no config with that code" : message(r));
                }
                return JsonParser.parseString(r.body()).getAsJsonObject().get("data").getAsString();
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CompletionException(e);
            }
        });
    }

    public static String reason(Throwable t) {
        Throwable c = t instanceof java.util.concurrent.CompletionException && t.getCause() != null ? t.getCause() : t;
        String m = c.getMessage();
        return m == null || m.isBlank() ? "could not reach the server" : m;
    }
}
