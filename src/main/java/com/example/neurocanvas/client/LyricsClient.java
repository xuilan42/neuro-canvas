package com.example.neurocanvas.client;

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
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the lyrics of the track that plays, in LRCLIB (lrclib.net, an open lyrics database made for exactly this: players that show the line
 * being sung). Only the artist, the title and the length of the track are sent. Nothing is stored on disk; the answer is kept in memory for the
 * session, and a track that was not found is not asked for again for a minute.
 */
public final class LyricsClient {
    public record Line(double time, String text) { }

    /** {@code synced} is true when the lines carry their own times; otherwise they are spread evenly over the length of the track. */
    public record Found(List<Line> lines, boolean synced) { }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final String AGENT = "NeuroCanvas (https://github.com/xuilan42/neuro-canvas)";
    private static final Pattern STAMP = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)]");
    private static final Pattern NOISE = Pattern.compile("(?i)\\s*[(\\[][^)\\]]*(official|video|audio|lyric|visuali[sz]er|remaster|hd|4k|mv|clip)[^)\\]]*[)\\]]");
    private static final Map<String, Found> CACHE = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Found> eldest) {
            return size() > 40;
        }
    };
    private static final Map<String, Long> MISSED = new java.util.HashMap<>();

    private LyricsClient() { }

    /** Looks the lyrics up in the background; completes with null when there are none. */
    public static CompletableFuture<Found> find(NowPlaying.Track t) {
        String key = t.key();
        synchronized (CACHE) {
            Found hit = CACHE.get(key);
            if (hit != null) {
                return CompletableFuture.completedFuture(hit);
            }
            Long missed = MISSED.get(key);
            if (missed != null && System.currentTimeMillis() - missed < 60_000) {
                return CompletableFuture.completedFuture(null);
            }
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                Found f = lookup(t);
                synchronized (CACHE) {
                    if (f != null) {
                        CACHE.put(key, f);
                    } else {
                        MISSED.put(key, System.currentTimeMillis());
                    }
                }
                return f;
            } catch (IOException | RuntimeException e) {
                throw new CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
        });
    }

    /** "Artist - Title (Official Video)" style names from video sites become a plain artist and title. */
    static String[] clean(String artist, String title) {
        String t = NOISE.matcher(title).replaceAll("").strip();
        String a = artist.replaceAll("(?i)\\s*-\\s*topic$", "").replaceAll("(?i)vevo$", "").strip();
        if (a.isEmpty()) {
            int dash = t.indexOf(" - ");
            if (dash > 0) {
                a = t.substring(0, dash).strip();
                t = t.substring(dash + 3).strip();
            }
        } else if (t.toLowerCase().startsWith(a.toLowerCase() + " - ")) {
            t = t.substring(a.length() + 3).strip();
        }
        return new String[] {a, t};
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static JsonElement get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("User-Agent", AGENT).GET().build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 404) {
            return null;
        }
        if (r.statusCode() != 200) {
            throw new IOException("lyrics service answered " + r.statusCode());
        }
        return JsonParser.parseString(r.body());
    }

    private static Found lookup(NowPlaying.Track t) throws IOException, InterruptedException {
        String[] c = clean(t.artist(), t.title());
        int seconds = t.duration() > 0 ? (int) Math.round(t.duration()) : 0;
        if (!c[0].isEmpty()) {
            String url = "https://lrclib.net/api/get?artist_name=" + enc(c[0]) + "&track_name=" + enc(c[1]) + (seconds > 0 ? "&duration=" + seconds : "")
                    + (t.album().isEmpty() ? "" : "&album_name=" + enc(t.album()));
            JsonElement e = get(url);
            Found f = e != null && e.isJsonObject() ? from(e.getAsJsonObject(), t.duration()) : null;
            if (f != null) {
                return f;
            }
        }
        String q = c[0].isEmpty() ? "q=" + enc(c[1]) : "track_name=" + enc(c[1]) + "&artist_name=" + enc(c[0]);
        JsonElement e = get("https://lrclib.net/api/search?" + q);
        if (e == null || !e.isJsonArray()) {
            return null;
        }
        JsonObject best = null;
        double bestScore = Double.MAX_VALUE;
        JsonArray all = e.getAsJsonArray();
        for (int i = 0; i < Math.min(all.size(), 12); i++) {
            JsonObject o = all.get(i).getAsJsonObject();
            boolean synced = o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull();
            boolean plain = o.has("plainLyrics") && !o.get("plainLyrics").isJsonNull();
            if (!synced && !plain) {
                continue;
            }
            double d = o.has("duration") && !o.get("duration").isJsonNull() && t.duration() > 0 ? Math.abs(o.get("duration").getAsDouble() - t.duration()) : 5;
            double score = d + (synced ? 0 : 20);
            if (score < bestScore) {
                bestScore = score;
                best = o;
            }
        }
        return best == null ? null : from(best, t.duration());
    }

    private static Found from(JsonObject o, double duration) {
        JsonElement synced = o.get("syncedLyrics");
        if (synced != null && !synced.isJsonNull() && !synced.getAsString().isBlank()) {
            List<Line> lines = parseLrc(synced.getAsString());
            if (!lines.isEmpty()) {
                return new Found(lines, true);
            }
        }
        JsonElement plain = o.get("plainLyrics");
        if (plain == null || plain.isJsonNull() || plain.getAsString().isBlank()) {
            return null;
        }
        List<String> rows = new ArrayList<>();
        for (String r : plain.getAsString().split("\n")) {
            if (!r.isBlank()) {
                rows.add(r.strip());
            }
        }
        // Without times the best guess is an even spread over the length of the track.
        double span = duration > 0 ? duration * 0.92 : rows.size() * 3.5;
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            lines.add(new Line(span * i / Math.max(1, rows.size()), rows.get(i)));
        }
        return lines.isEmpty() ? null : new Found(lines, false);
    }

    /** Reads an LRC text: one or more [mm:ss.xx] stamps in front of each line. */
    static List<Line> parseLrc(String lrc) {
        List<Line> out = new ArrayList<>();
        for (String row : lrc.split("\n")) {
            Matcher m = STAMP.matcher(row);
            List<Double> stamps = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                stamps.add(Integer.parseInt(m.group(1)) * 60 + Double.parseDouble(m.group(2)));
                end = m.end();
            }
            String text = row.substring(end).strip();
            if (!stamps.isEmpty() && !text.isEmpty()) {
                for (double s : stamps) {
                    out.add(new Line(s, text.length() > 90 ? text.substring(0, 90) : text));
                }
            }
        }
        out.sort(java.util.Comparator.comparingDouble(Line::time));
        return out;
    }
}
