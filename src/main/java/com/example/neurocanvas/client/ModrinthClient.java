package com.example.neurocanvas.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Shader packs and resource packs from Modrinth. Nothing is stored on our server: the catalogue is Modrinth's search, and a pack
 * is downloaded from Modrinth's own CDN. A file is kept only when its size is sane, it matches the SHA-512 Modrinth publishes, and
 * it is a real zip of the right kind. The pack is never unpacked by us, so nothing it contains can reach outside its file.
 */
public final class ModrinthClient {
    public enum Kind {
        SHADER("shader", "shaderpacks"), RESOURCEPACK("resourcepack", "resourcepacks");

        public final String type;
        public final String folder;

        Kind(String type, String folder) {
            this.type = type;
            this.folder = folder;
        }
    }

    public record Hit(String id, String title, String author, String description, long downloads, String iconUrl, int color) { }

    public record Download(String fileName, long size, String url, String sha512, String versionName) { }

    public static final String GAME_VERSION = "1.20.1";
    private static final String API = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "xuilan42/neuro-canvas (Fabric mod; https://github.com/xuilan42/neuro-canvas)";
    private static final long MAX_SIZE = 250L * 1024 * 1024;
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9]{8}");
    private static final Pattern FILE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 _.()+\\-]{0,118}\\.zip", Pattern.CASE_INSENSITIVE);
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();

    private ModrinthClient() { }

    private static JsonElement getJson(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", USER_AGENT).GET().build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 429) {
            throw new IOException("Modrinth asks to slow down, try again in a minute");
        }
        if (r.statusCode() != 200) {
            throw new IOException("Modrinth answered " + r.statusCode());
        }
        return JsonParser.parseString(r.body());
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    public static CompletableFuture<List<Hit>> search(Kind kind, String query, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String q = query.trim();
                String facets = "[[\"project_type:" + kind.type + "\"],[\"versions:" + GAME_VERSION + "\"]]";
                JsonObject o = getJson(API + "/search?limit=20&offset=" + Math.max(0, offset) + "&index=" + (q.isEmpty() ? "downloads" : "relevance")
                        + (q.isEmpty() ? "" : "&query=" + enc(q.length() > 60 ? q.substring(0, 60) : q)) + "&facets=" + enc(facets)).getAsJsonObject();
                List<Hit> out = new ArrayList<>();
                for (JsonElement e : o.getAsJsonArray("hits")) {
                    JsonObject h = e.getAsJsonObject();
                    String id = str(h, "project_id");
                    if (ID.matcher(id).matches()) {
                        out.add(new Hit(id, str(h, "title"), str(h, "author"), str(h, "description"), h.has("downloads") ? h.get("downloads").getAsLong() : 0,
                                str(h, "icon_url"), h.has("color") && h.get("color").isJsonPrimitive() ? h.get("color").getAsInt() : 0));
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

    /** The newest file of a project that fits this game version. */
    static Download latest(String projectId) throws IOException, InterruptedException {
        if (!ID.matcher(projectId).matches()) {
            throw new IOException("bad project");
        }
        JsonArray versions = getJson(API + "/project/" + projectId + "/version?game_versions=" + enc("[\"" + GAME_VERSION + "\"]")).getAsJsonArray();
        for (JsonElement ve : versions) {
            JsonObject v = ve.getAsJsonObject();
            JsonObject pick = null;
            for (JsonElement fe : v.getAsJsonArray("files")) {
                JsonObject f = fe.getAsJsonObject();
                if (str(f, "filename").toLowerCase(Locale.ROOT).endsWith(".zip") && (pick == null || (f.has("primary") && f.get("primary").getAsBoolean()))) {
                    pick = f;
                }
            }
            if (pick == null) {
                continue;
            }
            String name = str(pick, "filename");
            String url = str(pick, "url");
            String sha = pick.has("hashes") && pick.getAsJsonObject("hashes").has("sha512") ? pick.getAsJsonObject("hashes").get("sha512").getAsString() : "";
            long size = pick.has("size") ? pick.get("size").getAsLong() : -1;
            if (!FILE.matcher(name).matches() || !url.startsWith("https://cdn.modrinth.com/") || sha.length() != 128 || size <= 0) {
                continue;
            }
            return new Download(name, size, url, sha.toLowerCase(Locale.ROOT), str(v, "version_number"));
        }
        throw new IOException("no file for Minecraft " + GAME_VERSION);
    }

    /**
     * Downloads the newest file of a project into {@code folder} and returns its name. An existing file of the same name is kept
     * when it already is the right one; a different file of that name is never replaced.
     */
    public static CompletableFuture<String> install(Kind kind, String projectId, Path folder, IntConsumer progress) {
        return CompletableFuture.supplyAsync(() -> {
            Path part = null;
            try {
                Download d = latest(projectId);
                if (d.size() > MAX_SIZE) {
                    throw new IOException("the pack is larger than " + (MAX_SIZE >> 20) + " MB");
                }
                Files.createDirectories(folder);
                Path target = folder.resolve(d.fileName()).normalize();
                if (!target.getParent().equals(folder.normalize())) {
                    throw new IOException("bad file name");
                }
                if (Files.exists(target)) {
                    if (sha512(target).equals(d.sha512())) {
                        return d.fileName();
                    }
                    throw new IOException("a different file named " + d.fileName() + " is already in the folder");
                }
                part = folder.resolve(d.fileName() + ".part");
                MessageDigest md = MessageDigest.getInstance("SHA-512");
                HttpRequest req = HttpRequest.newBuilder(URI.create(d.url())).timeout(Duration.ofMinutes(10)).header("User-Agent", USER_AGENT).GET().build();
                HttpResponse<InputStream> r = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (r.statusCode() != 200 || !r.uri().getHost().endsWith("modrinth.com")) {
                    throw new IOException("the download failed (" + r.statusCode() + ")");
                }
                long total = 0;
                int lastPct = -1;
                try (InputStream in = r.body(); OutputStream out = Files.newOutputStream(part)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_SIZE || total > d.size()) {
                            throw new IOException("the file is larger than announced");
                        }
                        md.update(buf, 0, n);
                        out.write(buf, 0, n);
                        int pct = (int) (total * 100 / d.size());
                        if (pct != lastPct && progress != null) {
                            lastPct = pct;
                            progress.accept(pct);
                        }
                    }
                }
                if (total != d.size() || !HexFormat.of().formatHex(md.digest()).equals(d.sha512())) {
                    throw new IOException("the file does not match its checksum");
                }
                check(kind, part);
                Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
                return d.fileName();
            } catch (IOException | RuntimeException | java.security.NoSuchAlgorithmException e) {
                throw new CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } finally {
                if (part != null) {
                    try {
                        Files.deleteIfExists(part);
                    } catch (IOException ignored) {
                        // A leftover .part file is harmless.
                    }
                }
            }
        });
    }

    /** The bytes of a project icon: only from Modrinth's CDN and no more than 600 KB. */
    public static CompletableFuture<byte[]> icon(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!url.startsWith("https://cdn.modrinth.com/")) {
                    throw new IOException("bad icon address");
                }
                HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("User-Agent", USER_AGENT).GET().build();
                HttpResponse<InputStream> r = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (r.statusCode() != 200 || !r.uri().getHost().endsWith("modrinth.com")) {
                    throw new IOException("icon answered " + r.statusCode());
                }
                try (InputStream in = r.body()) {
                    byte[] data = in.readNBytes(600_001);
                    if (data.length > 600_000) {
                        throw new IOException("icon is too large");
                    }
                    return data;
                }
            } catch (IOException | RuntimeException e) {
                throw new CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
        });
    }

    private static String sha512(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** A resource pack has pack.mcmeta; a shader pack has a shaders folder. */
    private static void check(Kind kind, Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            boolean ok = false;
            var entries = zip.entries();
            int count = 0;
            while (entries.hasMoreElements() && count++ < 200_000) {
                ZipEntry e = entries.nextElement();
                String n = e.getName();
                if (kind == Kind.RESOURCEPACK ? n.equals("pack.mcmeta") : n.startsWith("shaders/")) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                throw new IOException("this is not a " + (kind == Kind.RESOURCEPACK ? "resource pack" : "shader pack"));
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException("this is not a valid zip file");
        }
    }

    public static String reason(Throwable t) {
        Throwable c = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
        String m = c.getMessage();
        if (c instanceof java.net.ConnectException || c instanceof java.net.http.HttpTimeoutException || c instanceof java.nio.channels.UnresolvedAddressException) {
            return "no connection to Modrinth";
        }
        return m == null || m.isBlank() ? c.getClass().getSimpleName() : m;
    }
}
