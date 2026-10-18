package com.example.neurocanvas.client;

import com.example.neurocanvas.module.NameTags;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

/** Shares one of your cosmetics on the project's server (with limits) and takes it back down. */
public final class CommunityClient {
    public record Shared(String code, String token) { }

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(8)).build();
    public static final long MAX_FILE = 5L * 1024 * 1024;
    public static final long MAX_TOTAL = 8L * 1024 * 1024;
    public static final int MAX_FILES = 8;

    private CommunityClient() { }

    /** The files of an item folder that may be shared. Throws when something about it is over the limits. */
    public static Map<String, byte[]> readFolder(Path folder) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        try (Stream<Path> s = Files.list(folder)) {
            for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile).sorted()::iterator) {
                String name = p.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                if (name.equals("meta.json") || !(lower.endsWith(".bbmodel") || lower.endsWith(".png") || lower.endsWith(".json"))) {
                    continue;
                }
                byte[] bytes = Files.readAllBytes(p);
                if (bytes.length > MAX_FILE) {
                    throw new IOException(name + " is larger than 5 MB");
                }
                total += bytes.length;
                if (total > MAX_TOTAL) {
                    throw new IOException("the item is larger than 8 MB");
                }
                files.put(name, bytes);
            }
        }
        if (files.isEmpty()) {
            throw new IOException("nothing to share in that folder");
        }
        if (files.size() > MAX_FILES) {
            throw new IOException("more than " + MAX_FILES + " files");
        }
        return files;
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

    /** Shares an item as the signed-in account (the author is the account name). Without an account nothing is sent. */
    public static CompletableFuture<Shared> upload(String type, String name, String author, Map<String, byte[]> files) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String bearer = AccountClient.bearer();
                if (bearer == null) {
                    throw new IOException("sign in first: press Sign in / Register in the menu");
                }
                JsonObject body = new JsonObject();
                body.addProperty("type", type);
                body.addProperty("name", name);
                JsonObject f = new JsonObject();
                for (Map.Entry<String, byte[]> e : files.entrySet()) {
                    f.addProperty(e.getKey(), Base64.getEncoder().encodeToString(e.getValue()));
                }
                body.add("files", f);
                HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/v1/acc/items")).timeout(Duration.ofSeconds(30))
                        .header("content-type", "application/json").header("x-nc", "1").header("Authorization", bearer)
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
                HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() != 200) {
                    throw new IOException(message(r));
                }
                JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                return new Shared(o.get("code").getAsString(), "account");
            } catch (IOException | RuntimeException e) {
                throw new CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
        });
    }

    private static String contentHash(Map<String, byte[]> files) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            for (String n : new java.util.TreeSet<>(files.keySet())) {
                md.update((n + ":" + files.get(n).length + ":").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                md.update(files.get(n));
            }
            return java.util.HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonObject postTemp(JsonObject body, String bearer) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/v1/acc/temp")).timeout(Duration.ofSeconds(40))
                .header("content-type", "application/json").header("x-nc", "1").header("Authorization", bearer)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            throw new IOException(message(r));
        }
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }

    /**
     * Puts a temporary copy of an item on the server so that other users can see it on you. It is not listed anywhere and the server
     * forgets it after a few days. When the server already has this exact content only a short question is sent, not the files.
     */
    public static CompletableFuture<String> uploadTemp(String type, String name, Map<String, byte[]> files) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String bearer = AccountClient.bearer();
                if (bearer == null) {
                    throw new IOException("sign in first");
                }
                JsonObject body = new JsonObject();
                body.addProperty("type", type);
                body.addProperty("name", name);
                body.addProperty("hash", contentHash(files));
                JsonObject o = postTemp(body, bearer);
                if (!o.has("code")) {
                    JsonObject f = new JsonObject();
                    for (Map.Entry<String, byte[]> e : files.entrySet()) {
                        f.addProperty(e.getKey(), Base64.getEncoder().encodeToString(e.getValue()));
                    }
                    body.add("files", f);
                    o = postTemp(body, bearer);
                }
                return o.get("code").getAsString();
            } catch (IOException | RuntimeException e) {
                throw new CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
        });
    }

    /** Reports a shared item; after enough reports from different people it is hidden from the list. */
    public static CompletableFuture<Void> report(String code) {
        return CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/v1/community/" + code + "/report")).timeout(Duration.ofSeconds(15))
                        .POST(HttpRequest.BodyPublishers.noBody()).build();
                HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() != 200) {
                    throw new IOException(message(r));
                }
            } catch (IOException | RuntimeException ex) {
                throw new CompletionException(ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new CompletionException(ex);
            }
        });
    }

    /** Removes an item of the Market or the Community with the admin key. */
    public static CompletableFuture<Void> adminDelete(MarketClient.Entry e, String key) {
        return CompletableFuture.runAsync(() -> {
            try {
                String path = e.code() != null ? "/v1/community/" + e.code()
                        : "/v1/market/" + e.type() + "/" + java.net.URLEncoder.encode(e.name(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
                HttpRequest req = HttpRequest.newBuilder(URI.create(base() + path)).timeout(Duration.ofSeconds(15))
                        .header("x-admin-key", key).DELETE().build();
                HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() != 200) {
                    throw new IOException(r.statusCode() == 403 ? "the server does not accept this key" : message(r));
                }
            } catch (IOException | RuntimeException ex) {
                throw new CompletionException(ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new CompletionException(ex);
            }
        });
    }

    public static CompletableFuture<Void> remove(String code, String token) {
        return CompletableFuture.runAsync(() -> {
            try {
                String bearer = AccountClient.bearer();
                HttpRequest req = "account".equals(token) && bearer != null
                        ? HttpRequest.newBuilder(URI.create(base() + "/v1/acc/items/" + code)).timeout(Duration.ofSeconds(15))
                                .header("Authorization", bearer).header("x-nc", "1").DELETE().build()
                        : HttpRequest.newBuilder(URI.create(base() + "/v1/community/" + code + "?token=" + token))
                                .timeout(Duration.ofSeconds(15)).DELETE().build();
                HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() != 200 && r.statusCode() != 403) {
                    throw new IOException(message(r));
                }
            } catch (IOException | RuntimeException e) {
                throw new CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
        });
    }
}
