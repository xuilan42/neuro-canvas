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

    /** The folder with your own .lrc files (set by WorldLyrics); a file whose name has the title (and the artist) is used before any search. */
    private static volatile java.nio.file.Path localDir;

    public static void localFolder(java.nio.file.Path dir) {
        localDir = dir;
    }

    /** Letters and digits only, lower case, so "Don't Stop (Remastered)" and "dont stop" compare well. */
    private static String plain(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                b.append(c);
            }
        }
        return b.toString();
    }

    /** How alike two names are, 0..1 (one minus the edit distance over the longer length); a name that contains the other counts as 0.9. */
    static double similarity(String a, String b) {
        String x = plain(a);
        String y = plain(b);
        if (x.isEmpty() || y.isEmpty()) {
            return 0;
        }
        if (x.equals(y)) {
            return 1;
        }
        if (x.contains(y) || y.contains(x)) {
            return 0.9;
        }
        int[] prev = new int[y.length() + 1];
        int[] cur = new int[y.length() + 1];
        for (int j = 0; j <= y.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= x.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= y.length(); j++) {
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + (x.charAt(i - 1) == y.charAt(j - 1) ? 0 : 1));
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return 1.0 - prev[y.length()] / (double) Math.max(x.length(), y.length());
    }

    /** The first artist of "A, B", "A & B", "A feat. B", "A / B": a song is usually filed under the main one. */
    static String firstArtist(String artist) {
        String[] parts = artist.split("(?i)\\s*(,|&|/|;|\\bfeat\\.?|\\bft\\.?|\\bx\\b|\\bwith\\b)\\s*");
        return parts.length == 0 ? artist : parts[0].strip();
    }

    /** "Title - Remastered 2011", "Title (feat. X)", "Title - Live" lose the tail, which databases rarely have. */
    static String bareTitle(String title) {
        String t = title.replaceAll("(?i)\\s*[(\\[]\\s*(feat|ft|with)\\.?[^)\\]]*[)\\]]", "");
        t = t.replaceAll("(?i)\\s+-\\s+(\\d{4}\\s+)?(remaster(ed)?|live|radio edit|single version|mono|stereo|bonus track|acoustic|version)\\b.*$", "");
        return t.strip();
    }

    /** Rows that are credits, not words ("作词 : X", "Lyrics by X"); they carry times in NetEase's files and would be shown as lines. */
    private static final Pattern CREDIT = Pattern.compile("^\\s*(作词|作詞|作曲|編曲|编曲|演唱|歌手|制作人?|製作人?|歌词|歌詞|翻译|翻譯|混音|母带|母帶|录音|錄音|吉他|贝斯|鼓|和声|监制|出品|发行|OP|SP|lyrics? by|music by|written by|composed by|arranged by|produced by|words by)\\s*[:：]", Pattern.CASE_INSENSITIVE);

    private static List<Line> withoutCredits(List<Line> in) {
        List<Line> out = new ArrayList<>(in.size());
        for (Line l : in) {
            if (!CREDIT.matcher(l.text()).find()) {
                out.add(l);
            }
        }
        return out.isEmpty() ? in : out;
    }

    private static Found lookup(NowPlaying.Track t) throws IOException, InterruptedException {
        String[] c = clean(t.artist(), t.title());
        Found local = localFile(c[0], c[1]);
        if (local != null) {
            return local;
        }
        String bare = bareTitle(c[1]);
        String main = firstArtist(c[0]);
        // The most exact question first, then looser ones: the same artist without "feat.", the first artist alone, the title without its tail.
        List<String[]> tries = new ArrayList<>();
        tries.add(new String[] {c[0], c[1]});
        if (!bare.equals(c[1])) {
            tries.add(new String[] {c[0], bare});
        }
        if (!main.equals(c[0]) && !main.isEmpty()) {
            tries.add(new String[] {main, bare});
        }
        // A database that is down or answers with an error must not stop the others: every step is tried, and only when all of them failed with
        // an error (not just found nothing) is the error reported, so that the player card can say the lookup failed and not "no lyrics".
        IOException failure = null;
        for (String[] q : tries) {
            try {
                Found f = lrclibGet(t, q[0], q[1]);
                if (f != null) {
                    return f;
                }
            } catch (IOException e) {
                failure = e;
                break;
            }
        }
        for (String[] q : tries) {
            try {
                Found f = lrclibSearch(t, q[0], q[1]);
                if (f != null) {
                    return f;
                }
            } catch (IOException e) {
                failure = e;
                break;
            }
        }
        // Another database as a last resort: NetEase has far more songs from Asia and Russia than LRCLIB does.
        try {
            Found f = netease(t, main.isEmpty() ? c[0] : main, bare);
            if (f != null) {
                return f;
            }
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            }
        }
        // Both could not be reached: report it. If at least one answered "nothing", that is an honest "not found".
        return null;
    }

    private static Found lrclibGet(NowPlaying.Track t, String artist, String title) throws IOException, InterruptedException {
        if (artist.isEmpty()) {
            return null;
        }
        int seconds = t.duration() > 0 ? (int) Math.round(t.duration()) : 0;
        String url = "https://lrclib.net/api/get?artist_name=" + enc(artist) + "&track_name=" + enc(title) + (seconds > 0 ? "&duration=" + seconds : "")
                + (t.album().isEmpty() ? "" : "&album_name=" + enc(t.album()));
        JsonElement e = get(url);
        return e != null && e.isJsonObject() ? from(e.getAsJsonObject(), t.duration()) : null;
    }

    /**
     * Searches LRCLIB and takes the likeliest result: the title and the artist must resemble ours, a length within two seconds of ours wins, and a
     * result with times beats one without. A result that is clearly another song is never taken, even if it is the only one.
     */
    private static Found lrclibSearch(NowPlaying.Track t, String artist, String title) throws IOException, InterruptedException {
        String q = artist.isEmpty() ? "q=" + enc(title) : "track_name=" + enc(title) + "&artist_name=" + enc(artist);
        JsonElement e = get("https://lrclib.net/api/search?" + q);
        if (e == null || !e.isJsonArray()) {
            return null;
        }
        JsonObject best = null;
        double bestScore = Double.MAX_VALUE;
        JsonArray all = e.getAsJsonArray();
        for (int i = 0; i < Math.min(all.size(), 15); i++) {
            JsonObject o = all.get(i).getAsJsonObject();
            boolean synced = o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull();
            boolean plainText = o.has("plainLyrics") && !o.get("plainLyrics").isJsonNull();
            if (!synced && !plainText) {
                continue;
            }
            double st = similarity(title, str(o, "trackName"));
            double sa = artist.isEmpty() ? 0.7 : similarity(artist, str(o, "artistName"));
            if (st < 0.55 || sa < 0.35) {
                continue;
            }
            double d = o.has("duration") && !o.get("duration").isJsonNull() && t.duration() > 0 ? Math.abs(o.get("duration").getAsDouble() - t.duration()) : 6;
            double score = (1 - st) * 3 + (1 - sa) * 2 + Math.min(d, 30) / 10.0 - (d <= 2 ? 0.5 : 0) + (synced ? 0 : 0.8);
            if (score < bestScore) {
                bestScore = score;
                best = o;
            }
        }
        return best == null ? null : from(best, t.duration());
    }

    private static String str(JsonObject o, String key) {
        JsonElement v = o.get(key);
        return v == null || v.isJsonNull() ? "" : v.getAsString();
    }

    // ---- NetEase (music.163.com): an unofficial but long-standing public web API, also used by waylyrics and Caelestia ----------------------

    private static JsonElement getNetEase(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0").header("Referer", "https://music.163.com/").GET().build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            return null;
        }
        return JsonParser.parseString(r.body());
    }

    private static Found netease(NowPlaying.Track t, String artist, String title) throws IOException, InterruptedException {
        String s = (title + " " + artist).strip();
        JsonElement e = getNetEase("https://music.163.com/api/search/get?type=1&limit=8&s=" + enc(s));
        if (e == null || !e.isJsonObject() || !e.getAsJsonObject().has("result") || !e.getAsJsonObject().get("result").isJsonObject()) {
            return null;
        }
        JsonElement songsEl = e.getAsJsonObject().getAsJsonObject("result").get("songs");
        if (songsEl == null || !songsEl.isJsonArray()) {
            return null;
        }
        long bestId = -1;
        double bestScore = Double.MAX_VALUE;
        for (JsonElement el : songsEl.getAsJsonArray()) {
            JsonObject o = el.getAsJsonObject();
            String name = str(o, "name");
            String by = "";
            if (o.has("artists") && o.get("artists").isJsonArray() && o.getAsJsonArray("artists").size() > 0) {
                by = str(o.getAsJsonArray("artists").get(0).getAsJsonObject(), "name");
            }
            double st = similarity(title, name);
            double sa = artist.isEmpty() ? 0.7 : similarity(artist, by);
            if (st < 0.55 || sa < 0.4) {
                continue;
            }
            double d = o.has("duration") && t.duration() > 0 ? Math.abs(o.get("duration").getAsDouble() / 1000.0 - t.duration()) : 6;
            double score = (1 - st) * 3 + (1 - sa) * 2 + Math.min(d, 30) / 10.0 - (d <= 2 ? 0.5 : 0);
            if (score < bestScore) {
                bestScore = score;
                bestId = o.get("id").getAsLong();
            }
        }
        if (bestId < 0) {
            return null;
        }
        JsonElement l = getNetEase("https://music.163.com/api/song/lyric?lv=1&id=" + bestId);
        if (l == null || !l.isJsonObject() || !l.getAsJsonObject().has("lrc") || !l.getAsJsonObject().get("lrc").isJsonObject()) {
            return null;
        }
        String text = str(l.getAsJsonObject().getAsJsonObject("lrc"), "lyric");
        List<Line> lines = withoutCredits(parseLrc(text));
        return lines.isEmpty() ? null : new Found(lines, true);
    }

    // ---- your own files ------------------------------------------------------------------------------------------------------------------

    /** A .lrc file anywhere in the lyrics folder whose name has the title (and, when it can, the artist): your file always wins over a database. */
    private static Found localFile(String artist, String title) {
        java.nio.file.Path dir = localDir;
        if (dir == null || !java.nio.file.Files.isDirectory(dir)) {
            return null;
        }
        String wantTitle = plain(bareTitle(title));
        String wantArtist = plain(firstArtist(artist));
        if (wantTitle.isEmpty()) {
            return null;
        }
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(dir, 3)) {
            java.nio.file.Path hit = null;
            for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) files::iterator) {
                String name = p.getFileName().toString();
                if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".lrc") || name.toLowerCase(java.util.Locale.ROOT).startsWith("example")) {
                    continue;
                }
                String f = plain(name.substring(0, name.length() - 4));
                if (f.contains(wantTitle) && (wantArtist.isEmpty() || f.contains(wantArtist))) {
                    hit = p;
                    break;
                }
                if (hit == null && f.equals(wantTitle)) {
                    hit = p;
                }
            }
            if (hit == null) {
                return null;
            }
            List<Line> lines = withoutCredits(parseLrc(java.nio.file.Files.readString(hit, StandardCharsets.UTF_8)));
            return lines.isEmpty() ? null : new Found(lines, true);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static Found from(JsonObject o, double duration) {
        JsonElement synced = o.get("syncedLyrics");
        if (synced != null && !synced.isJsonNull() && !synced.getAsString().isBlank()) {
            List<Line> lines = withoutCredits(parseLrc(synced.getAsString()));
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
