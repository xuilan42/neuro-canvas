package com.example.neurocanvas.client;

import com.example.neurocanvas.avatar.CosmeticPack;
import com.example.neurocanvas.module.NameTags;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

/**
 * The catalog on the project's server and the one-request download of an item. Everything that comes back is checked before it
 * touches the disk: the kind of cosmetic, the folder name, every file name and extension, and the sizes.
 */
public final class MarketClient {
    /** {@code code} is set for items shared by users and null for the official ones. */
    public record Entry(String type, String name, String author, long size, String code, int reports) {
        public Entry(String type, String name, String author, long size, String code) {
            this(type, name, author, size, code, 0);
        }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(8)).build();
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} _.-]{0,39}");
    private static final Pattern FILE = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,59}");
    private static final Pattern CODE = Pattern.compile("[A-HJ-NP-Z2-9]{8}");
    private static final long MAX_FILE = 5L * 1024 * 1024;
    private static final long MAX_TOTAL = 8L * 1024 * 1024;
    private static final int MAX_FILES = 8;

    private MarketClient() { }

    private static String base() throws IOException {
        String url = NameTags.mainUrl();
        if (url.isBlank()) {
            throw new IOException("this build has no server address");
        }
        return url;
    }

    private static boolean knownType(String type) {
        for (CosmeticPack.Type t : CosmeticPack.Type.values()) {
            if (t.folder.equals(type)) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject get(String path) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base() + path)).timeout(Duration.ofSeconds(20)).GET();
        if (path.equals("/v1/community") && AdminStore.has()) {
            b.header("x-admin-key", AdminStore.key());
        }
        HttpRequest req = b.build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            throw new IOException(r.statusCode() == 404 ? "not found on the server" : "server answered " + r.statusCode());
        }
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }

    public static CompletableFuture<List<Entry>> list(boolean community) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonArray items = get(community ? "/v1/community" : "/v1/market").getAsJsonArray("items");
                List<Entry> out = new ArrayList<>();
                for (JsonElement e : items) {
                    JsonObject o = e.getAsJsonObject();
                    String type = o.get("type").getAsString();
                    String name = o.get("name").getAsString();
                    if (knownType(type) && NAME.matcher(name).matches()) {
                        out.add(new Entry(type, name, o.has("author") ? o.get("author").getAsString() : "", o.has("size") ? o.get("size").getAsLong() : 0,
                                community && o.has("code") ? o.get("code").getAsString() : null, o.has("reports") ? o.get("reports").getAsInt() : 0));
                    }
                }
                return out;
            } catch (IOException | RuntimeException e) {
                throw new CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
        });
    }

    /** Downloads the files of an item (no disk is touched) and checks them: names, extensions and sizes. */
    public static Map<String, byte[]> fetch(Entry entry) throws IOException, InterruptedException {
        if (!knownType(entry.type()) || !NAME.matcher(entry.name()).matches()) {
            throw new IOException("bad item name");
        }
        JsonObject o = get(entry.code() != null ? "/v1/community/" + entry.code()
                : "/v1/market/" + entry.type() + "/" + URLEncoder.encode(entry.name(), StandardCharsets.UTF_8).replace("+", "%20"));
        JsonObject files = o.getAsJsonObject("files");
        if (files == null || files.size() == 0 || files.size() > MAX_FILES) {
            throw new IOException("the item has no files");
        }
        Map<String, byte[]> data = new java.util.LinkedHashMap<>();
        long total = 0;
        for (Map.Entry<String, JsonElement> f : files.entrySet()) {
            String name = f.getKey();
            String lower = name.toLowerCase();
            if (!FILE.matcher(name).matches() || !(lower.endsWith(".bbmodel") || lower.endsWith(".png") || lower.endsWith(".json"))) {
                throw new IOException("unexpected file \"" + name + "\"");
            }
            byte[] bytes = Base64.getDecoder().decode(f.getValue().getAsString());
            total += bytes.length;
            if (bytes.length > MAX_FILE || total > MAX_TOTAL) {
                throw new IOException("the item is too large");
            }
            data.put(name, bytes);
        }
        return data;
    }

    /** The files of an item, fetched in the background (for previews). */
    public static CompletableFuture<Map<String, byte[]>> fetchAsync(Entry entry) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fetch(entry);
            } catch (IOException | RuntimeException e) {
                throw new CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
        });
    }

    /** The name an item has in your wardrobe, or null when it is not there yet. */
    public static String installedName(Entry e, Path wardrobe) {
        for (String candidate : candidates(e)) {
            Path folder = wardrobe.resolve(e.type()).resolve(candidate);
            if (Files.isDirectory(folder) && (e.code() == null || e.code().equals(sourceOf(folder)))) {
                return candidate;
            }
        }
        return null;
    }

    private static String sourceOf(Path folder) {
        try {
            Path f = folder.resolve("source.txt");
            return Files.exists(f) ? Files.readString(f).trim() : null;
        } catch (IOException ex) {
            return null;
        }
    }

    /** The names an item may be saved under: its own, then with the author, then with its code, so nothing of yours is replaced. */
    private static List<String> candidates(Entry e) {
        List<String> out = new ArrayList<>();
        out.add(e.name());
        if (e.code() != null) {
            String withAuthor = e.name() + " - " + e.author().replaceAll("[^A-Za-z0-9_]", "_");
            out.add(withAuthor.length() > 40 ? withAuthor.substring(0, 40).trim() : withAuthor);
            out.add((e.name().length() > 30 ? e.name().substring(0, 30).trim() : e.name()) + " - " + e.code());
        }
        return out;
    }

    /** Downloads one item into wardrobe/&lt;type&gt;/&lt;name&gt;/ and completes with that name. Nothing is left behind when anything about it looks wrong. */
    public static CompletableFuture<String> install(Entry entry, Path wardrobe) {
        return CompletableFuture.supplyAsync(() -> {
            if (!knownType(entry.type()) || !NAME.matcher(entry.name()).matches()
                    || (entry.code() != null && !CODE.matcher(entry.code()).matches())) {
                throw new CompletionException(new IOException("bad item"));
            }
            String chosen = null;
            for (String candidate : candidates(entry)) {
                Path folder = wardrobe.resolve(entry.type()).resolve(candidate);
                if (!Files.exists(folder) || (entry.code() != null && entry.code().equals(sourceOf(folder)))) {
                    chosen = candidate;
                    break;
                }
            }
            if (chosen == null) {
                throw new CompletionException(new IOException("you have items with all the names this one could get"));
            }
            Path target = wardrobe.resolve(entry.type()).resolve(chosen).normalize();
            try {
                if (!knownType(entry.type()) || !target.startsWith(wardrobe.normalize())) {
                    throw new IOException("bad item name");
                }
                Map<String, byte[]> data = fetch(entry);
                Files.createDirectories(target);
                try {
                    for (Map.Entry<String, byte[]> f : data.entrySet()) {
                        Files.write(target.resolve(f.getKey()), f.getValue());
                    }
                    if (entry.code() != null) {
                        Files.writeString(target.resolve("source.txt"), entry.code());
                    }
                } catch (IOException e) {
                    try (var s = Files.walk(target)) {
                        s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                    }
                    throw e;
                }
                return chosen;
            } catch (IOException | RuntimeException e) {
                throw new CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
        });
    }

    public static String reason(Throwable t) {
        Throwable c = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
        String m = c.getMessage();
        return m == null || m.isBlank() ? "could not reach the server" : m;
    }
}
