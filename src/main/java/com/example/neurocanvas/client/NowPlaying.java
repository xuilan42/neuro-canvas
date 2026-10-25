package com.example.neurocanvas.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What is playing right now, learned from two places: a small script in the browser (Spotify, YouTube Music, SoundCloud... anything that tells
 * the browser what it plays) that posts to a port that only this computer can reach, and on Linux the system's media players through playerctl.
 * Nothing leaves the computer here; only the track's own name and artist are later used to look the lyrics up.
 */
public final class NowPlaying {
    public static final int PORT = 47821;
    private static final Logger LOG = LoggerFactory.getLogger("neurocanvas");
    private static final long FRESH_MS = 5_000;

    public record Track(String title, String artist, String album, String art, double position, double duration, boolean playing, String source) {
        public String key() {
            return (artist + "|" + title).toLowerCase();
        }
    }

    private static volatile Track track;
    private static volatile long stamp;
    private static volatile long browserStamp;
    private static boolean started;

    private NowPlaying() { }

    /** Starts listening (once). Called when something asks for the music for the first time. */
    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        Thread server = new Thread(NowPlaying::serve, "neurocanvas-nowplaying-http");
        server.setDaemon(true);
        server.start();
        Thread poll = new Thread(NowPlaying::pollPlayers, "neurocanvas-nowplaying-system");
        poll.setDaemon(true);
        poll.start();
    }

    /** The current track, or null when nothing has reported for a few seconds. */
    public static Track current() {
        Track t = track;
        return t != null && System.currentTimeMillis() - stamp < FRESH_MS ? t : null;
    }

    /** Where the track is now, in seconds: the last reported position plus the time since, while it plays. */
    public static double position() {
        Track t = track;
        if (t == null) {
            return 0;
        }
        double p = t.position();
        if (t.playing()) {
            p += (System.currentTimeMillis() - stamp) / 1000.0;
        }
        return p;
    }

    static void update(Track t, boolean fromBrowser) {
        long now = System.currentTimeMillis();
        if (!fromBrowser && now - browserStamp < FRESH_MS) {
            return;
        }
        if (fromBrowser) {
            browserStamp = now;
        }
        if (t.position() < 0) {
            // The player does not say where it is in the track: count the time while it plays, from the start of the track.
            Track old = track;
            double estimate = old != null && old.key().equals(t.key()) && now - stamp < FRESH_MS ? position() : 0;
            t = new Track(t.title(), t.artist(), t.album(), t.art(), estimate, t.duration(), t.playing(), t.source());
        }
        track = t;
        stamp = now;
    }

    // ---- the browser side: a tiny HTTP server on the loopback address ----------------------------------------

    private static void serve() {
        try (ServerSocket socket = new ServerSocket(PORT, 8, InetAddress.getLoopbackAddress())) {
            while (true) {
                try (Socket c = socket.accept()) {
                    c.setSoTimeout(2000);
                    handle(c);
                } catch (IOException | RuntimeException ignored) {
                    // A broken request only ends that request.
                }
            }
        } catch (IOException e) {
            LOG.warn("Now playing: cannot listen on port {} ({}); the browser script will not work", PORT, e.getMessage());
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int ch;
        while ((ch = in.read()) != -1 && ch != '\n') {
            if (b.size() > 8192) {
                throw new IOException("line too long");
            }
            if (ch != '\r') {
                b.write(ch);
            }
        }
        return b.toString(StandardCharsets.ISO_8859_1);
    }

    private static void handle(Socket c) throws IOException {
        InputStream in = c.getInputStream();
        String request = readLine(in);
        int length = 0;
        String host = "";
        boolean marked = false;
        boolean fromPage = false;
        for (int count = 0; ; ) {
            String h = readLine(in);
            if (h.isEmpty()) {
                break;
            }
            if (++count > 64) {
                throw new IOException("too many headers");
            }
            String lower = h.toLowerCase();
            if (lower.startsWith("content-length:")) {
                try {
                    length = Integer.parseInt(h.substring(15).strip());
                } catch (NumberFormatException e) {
                    respond(c, 400, "{}");
                    return;
                }
            } else if (lower.startsWith("host:")) {
                host = h.substring(5).strip();
            } else if (lower.startsWith("x-nc:")) {
                marked = true;
            } else if (lower.startsWith("origin:") || lower.startsWith("sec-fetch-site:") && !lower.substring(15).strip().equals("none")) {
                fromPage = true;
            }
        }
        // A page on the internet must not be able to reach this through a made-up host name (DNS rebinding).
        boolean hostOk = host.equals("127.0.0.1:" + PORT) || host.equals("localhost:" + PORT) || host.equals("127.0.0.1") || host.equals("localhost");
        String method = request.split(" ")[0];
        if (!hostOk) {
            respond(c, 403, "{}");
            return;
        }
        // A web page in the browser must not be able to feed this: its requests carry an Origin (or a Sec-Fetch-Site) and cannot add the X-NC header
        // without a preflight, which is never answered with permission.
        if (fromPage && !marked || method.equals("OPTIONS")) {
            respond(c, 403, "{}");
            return;
        }
        if (method.equals("POST") && length > 0 && length <= 16384) {
            byte[] body = in.readNBytes(length);
            try {
                Track t = parse(new String(body, StandardCharsets.UTF_8));
                if (t != null && t.art().regionMatches(true, 0, "file:", 0, 5)) {
                    // The browser has no business pointing at files on this computer.
                    t = new Track(t.title(), t.artist(), t.album(), "", t.position(), t.duration(), t.playing(), t.source());
                }
                if (t != null) {
                    update(t, true);
                }
                respond(c, 200, "{\"ok\":true}");
            } catch (RuntimeException e) {
                respond(c, 400, "{\"ok\":false}");
            }
            return;
        }
        respond(c, 404, "{}");
    }

    private static void respond(Socket c, int status, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + status + (status == 200 ? " OK" : status == 204 ? " No Content" : " Error") + "\r\n"
                + "Content-Type: application/json\r\nContent-Length: " + data.length
                + "\r\nConnection: close\r\n\r\n";
        c.getOutputStream().write(head.getBytes(StandardCharsets.ISO_8859_1));
        c.getOutputStream().write(data);
        c.getOutputStream().flush();
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString().strip();
    }

    private static double num(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? -1 : e.getAsDouble();
    }

    /** Reads what the browser script sends; null when it has no title. Fields are clipped so nothing odd gets far. */
    static Track parse(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        String title = clip(str(o, "title"), 200);
        if (title.isEmpty()) {
            return null;
        }
        JsonElement playing = o.get("playing");
        return new Track(title, clip(str(o, "artist"), 120), clip(str(o, "album"), 120), clip(str(o, "art"), 600), num(o, "position"),
                num(o, "duration"), playing == null || playing.isJsonNull() || playing.getAsBoolean(), clip(str(o, "source"), 60));
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ---- the system side: what the media players of the operating system say (see PlayerBackends) ------------------

    private static volatile String backendName = "not started";
    private static volatile String backendError = "";

    static void backend(String name) {
        backendName = name;
        backendError = "";
    }

    static void backend(String name, String error) {
        backendName = name;
        backendError = error == null ? "" : error;
    }

    /** One line for the player of the system: which way of asking is used and what went wrong, if anything. */
    public static String describe() {
        long browserAge = browserStamp == 0 ? -1 : (System.currentTimeMillis() - browserStamp) / 1000;
        Track t = current();
        return "system: " + System.getProperty("os.name") + ", system player: " + backendName + (backendError.isEmpty() ? "" : " (" + backendError + ")")
                + ", browser script: " + (browserAge < 0 ? "never heard" : browserAge + " s ago")
                + ", now: " + (t == null ? "nothing" : t.source() + (t.playing() ? " playing" : " paused"));
    }

    static double number(String s, double divisor) {
        try {
            return Double.parseDouble(s.strip().replace(',', '.')) / divisor;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * One line from a system player: status, artist, title, album, position, length, picture, player, separated by tabs. The two times are in
     * units of {@code timeDivisor} per second (a million for playerctl, 1 for the Windows and macOS scripts); a missing value is -1.
     */
    static Track parseFields(String line, double timeDivisor) {
        String[] f = line.split("\t", -1);
        if (f.length < 8 || f[2].isBlank()) {
            return null;
        }
        return new Track(clip(f[2].strip(), 200), clip(f[1].strip(), 120), clip(f[3].strip(), 120), clip(f[6].strip(), 600),
                number(f[4], timeDivisor), number(f[5], timeDivisor), f[0].strip().equalsIgnoreCase("Playing"), f[7].strip());
    }

    static void pollPlayers() {
        PlayerBackends.run(t -> update(t, false));
    }
}
