package com.example.neurocanvas.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Ways to ask the operating system what the media players on it are playing, one per system, using only what the system already has:
 * Windows asks the system media controls (the same ones the volume pop-up shows) through the PowerShell that comes with Windows, macOS asks
 * the Spotify and Music apps through AppleScript (and nowplaying-cli when it is installed), Linux asks every player that speaks MPRIS through
 * playerctl, or through busctl when playerctl is not installed. Browsers on any system are covered by the browser script instead.
 */
final class PlayerBackends {
    private static final String COLUMNS = "status\tartist\ttitle\talbum\tposition\tduration\tart\tplayer";

    private PlayerBackends() { }

    static void run(Consumer<NowPlaying.Track> sink) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                windows(sink);
            } else if (os.contains("mac") || os.contains("darwin")) {
                mac(sink);
            } else {
                linux(sink);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private record Result(int exit, String out) { }

    /** Runs a command, waits at most a few seconds for it, and returns what it printed; throws IOException when the program does not exist. */
    private static Result exec(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
        p.getOutputStream().close();
        byte[] out = p.getInputStream().readAllBytes();
        if (!p.waitFor(4, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return new Result(-1, "");
        }
        return new Result(p.exitValue(), new String(out, StandardCharsets.UTF_8));
    }

    private static NowPlaying.Track best(NowPlaying.Track a, NowPlaying.Track b) {
        return a == null || b != null && b.playing() && !a.playing() ? b : a;
    }

    // ---- Linux ---------------------------------------------------------------------------------------------------

    private static void linux(Consumer<NowPlaying.Track> sink) throws InterruptedException {
        boolean ctl = true;
        boolean bus = true;
        String error = "";
        while (ctl || bus) {
            NowPlaying.Track best = null;
            String via = "";
            if (ctl) {
                try {
                    Result r = exec("playerctl", "-a", "metadata", "--format",
                            "{{status}}\t{{artist}}\t{{title}}\t{{album}}\t{{position}}\t{{mpris:length}}\t{{mpris:artUrl}}\t{{playerName}}");
                    for (String line : r.out().split("\n")) {
                        best = best(best, NowPlaying.parseFields(line, 1_000_000.0));
                    }
                    via = "playerctl";
                } catch (IOException missing) {
                    ctl = false;
                }
            }
            // playerctl can be installed and still tell nothing (an odd player, an old version): busctl asks the same players directly.
            if (best == null && bus) {
                try {
                    best = busBest();
                    via = "busctl";
                } catch (IOException missing) {
                    bus = false;
                    error = String.valueOf(missing.getMessage());
                } catch (RuntimeException odd) {
                    error = "unexpected answer: " + odd;
                }
            }
            if (best != null) {
                NowPlaying.backend(via + " (MPRIS)");
                sink.accept(best);
            } else {
                NowPlaying.backend("MPRIS: no player is playing" + (ctl ? ", playerctl" : "") + (bus ? ", busctl" : ""), error);
            }
            Thread.sleep(1000);
        }
        NowPlaying.backend("none (install playerctl, or use the browser script)", error);
    }

    /** Asks the MPRIS players on the session bus directly: the one that plays, else the first one. */
    private static NowPlaying.Track busBest() throws IOException, InterruptedException {
        NowPlaying.Track first = null;
        for (String n : busNames()) {
            try {
                NowPlaying.Track t = busTrack(n);
                if (t != null && t.playing()) {
                    return t;
                }
                first = first == null ? t : first;
            } catch (RuntimeException oneBroken) {
                // A player that does not answer properly must not hide the others.
            }
        }
        return first;
    }

    private static List<String> busNames() throws IOException, InterruptedException {
        List<String> out = new ArrayList<>();
        for (String line : exec("busctl", "--user", "--no-legend", "list").out().split("\n")) {
            String name = line.strip().split("\\s+")[0];
            // playerctld is a helper that forwards to the real players; it is not a player itself.
            if (name.startsWith("org.mpris.MediaPlayer2.") && !name.equals("org.mpris.MediaPlayer2.playerctld")) {
                out.add(name);
            }
        }
        return out;
    }

    /** One property of the player interface of an MPRIS player; busctl prints it as {"type":"...","data":...}. */
    private static JsonElement busGet(String name, String property) throws IOException, InterruptedException {
        Result r = exec("busctl", "--user", "--json=short", "get-property", name, "/org/mpris/MediaPlayer2", "org.mpris.MediaPlayer2.Player", property);
        if (r.out().isBlank()) {
            throw new IllegalStateException(name + " gave no answer");
        }
        JsonObject o = JsonParser.parseString(r.out()).getAsJsonObject();
        return o.get("data");
    }

    private static String variantString(JsonObject metadata, String key) {
        JsonElement v = metadata.get(key);
        if (v == null || !v.isJsonObject()) {
            return "";
        }
        JsonElement d = v.getAsJsonObject().get("data");
        if (d == null || d.isJsonNull()) {
            return "";
        }
        if (d.isJsonArray()) {
            JsonArray a = d.getAsJsonArray();
            List<String> parts = new ArrayList<>();
            for (JsonElement e : a) {
                parts.add(e.getAsString());
            }
            return String.join(", ", parts);
        }
        return d.getAsString();
    }

    private static NowPlaying.Track busTrack(String name) throws IOException, InterruptedException {
        JsonObject md = busGet(name, "Metadata").getAsJsonObject();
        String title = variantString(md, "xesam:title");
        if (title.isBlank()) {
            return null;
        }
        String status = busGet(name, "PlaybackStatus").getAsString();
        double position = busGet(name, "Position").getAsDouble() / 1_000_000.0;
        String length = variantString(md, "mpris:length");
        double duration = length.isEmpty() ? -1 : Double.parseDouble(length) / 1_000_000.0;
        return new NowPlaying.Track(title, variantString(md, "xesam:artist"), variantString(md, "xesam:album"), variantString(md, "mpris:artUrl"),
                Math.max(0, position), duration, status.equalsIgnoreCase("Playing"), name.substring("org.mpris.MediaPlayer2.".length()));
    }

    // ---- Windows -------------------------------------------------------------------------------------------------

    /**
     * One PowerShell stays running and prints a line a second. It asks the system media controls (Windows 10 1809 and later) for the sessions
     * (every browser tab or app that shows up in the volume pop-up) and reports the one that plays. It ends by itself when the game does.
     */
    private static String windowsScript() {
        return """
                $ErrorActionPreference = 'SilentlyContinue'
                [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
                Add-Type -AssemblyName System.Runtime.WindowsRuntime
                $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
                function Await($op, $type) {
                    $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
                    $t.Wait(-1) | Out-Null
                    $t.Result
                }
                [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
                [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties,Windows.Media.Control,ContentType=WindowsRuntime]
                $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
                while ($true) {
                    if (-not (Get-Process -Id %d)) { break }
                    try {
                        $best = $null
                        foreach ($s in $mgr.GetSessions()) {
                            if ($s.GetPlaybackInfo().PlaybackStatus -eq 4) { $best = $s; break }
                            if ($best -eq $null) { $best = $s }
                        }
                        if ($best -ne $null) {
                            $p = Await ($best.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                            $tl = $best.GetTimelineProperties()
                            $playing = ($best.GetPlaybackInfo().PlaybackStatus -eq 4)
                            $pos = $tl.Position.TotalSeconds
                            if ($playing) { $pos += ([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime).TotalSeconds }
                            $dur = $tl.EndTime.TotalSeconds - $tl.StartTime.TotalSeconds
                            $status = 'Paused'
                            if ($playing) { $status = 'Playing' }
                            $line = @($status, $p.Artist, $p.Title, $p.AlbumTitle, [math]::Round($pos, 2).ToString([Globalization.CultureInfo]::InvariantCulture), [math]::Round($dur, 2).ToString([Globalization.CultureInfo]::InvariantCulture), '', $best.SourceAppUserModelId) -join "`t"
                            [Console]::Out.WriteLine($line)
                            [Console]::Out.Flush()
                        }
                    } catch { }
                    Start-Sleep -Milliseconds 1000
                }
                """.formatted(ProcessHandle.current().pid());
    }

    private static void windows(Consumer<NowPlaying.Track> sink) throws InterruptedException {
        NowPlaying.backend("Windows media controls (PowerShell)");
        try {
            String encoded = Base64.getEncoder().encodeToString(windowsScript().getBytes(StandardCharsets.UTF_16LE));
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass",
                    "-EncodedCommand", encoded).redirectErrorStream(false).start();
            p.getOutputStream().close();
            Runtime.getRuntime().addShutdownHook(new Thread(p::destroyForcibly));
            try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    NowPlaying.Track t = NowPlaying.parseFields(line, 1.0);
                    if (t != null) {
                        sink.accept(t);
                    }
                }
            }
            NowPlaying.backend("none (PowerShell ended; use the browser script)", "powershell exit " + p.waitFor());
        } catch (IOException e) {
            NowPlaying.backend("none (no PowerShell; use the browser script)", e.getMessage());
        }
    }

    // ---- macOS ---------------------------------------------------------------------------------------------------

    private static String spotifyScript() {
        return """
                tell application "Spotify"
                    if player state is stopped then return ""
                    set st to player state as string
                    return st & tab & (artist of current track) & tab & (name of current track) & tab & (album of current track) & tab & (player position as string) & tab & (((duration of current track) / 1000) as string) & tab & (artwork url of current track) & tab & "Spotify"
                end tell
                """;
    }

    private static String musicScript() {
        return """
                tell application "Music"
                    if player state is stopped then return ""
                    set st to player state as string
                    return st & tab & (artist of current track) & tab & (name of current track) & tab & (album of current track) & tab & (player position as string) & tab & ((duration of current track) as string) & tab & "" & tab & "Music"
                end tell
                """;
    }

    private static void mac(Consumer<NowPlaying.Track> sink) throws InterruptedException {
        NowPlaying.backend("Spotify / Music apps (AppleScript)");
        boolean cli = true;
        try {
            while (true) {
                NowPlaying.Track best = null;
                for (String app : new String[] {"Spotify", "Music"}) {
                    // Only ask an app that is running: asking a closed one would start it.
                    if (exec("pgrep", "-x", app).exit() == 0) {
                        Result r = exec("osascript", "-e", app.equals("Spotify") ? spotifyScript() : musicScript());
                        best = best(best, NowPlaying.parseFields(r.out().strip(), 1.0));
                    }
                }
                if (best == null && cli) {
                    try {
                        best = nowPlayingCli();
                    } catch (IOException e) {
                        cli = false;
                    }
                }
                if (best != null) {
                    sink.accept(best);
                }
                Thread.sleep(1000);
            }
        } catch (IOException e) {
            NowPlaying.backend("none (use the browser script)", e.getMessage());
        }
    }

    /** nowplaying-cli (brew install nowplaying-cli) reads whatever macOS shows as playing, browsers included; one value per line. */
    private static NowPlaying.Track nowPlayingCli() throws IOException, InterruptedException {
        Result r = exec("nowplaying-cli", "get", "title", "artist", "album", "duration", "elapsedTime", "playbackRate");
        String[] f = r.out().split("\n", -1);
        if (f.length < 6 || f[0].isBlank() || f[0].strip().equals("null") || f[0].strip().equals("(null)")) {
            return null;
        }
        double rate = f[5].strip().equals("null") || f[5].strip().equals("(null)") ? 0 : NowPlaying.number(f[5], 1.0);
        return NowPlaying.parseFields((rate > 0 ? "Playing" : "Paused") + "\t" + nullToEmpty(f[1]) + "\t" + f[0].strip() + "\t" + nullToEmpty(f[2]) + "\t"
                + nullToEmpty(f[4]) + "\t" + nullToEmpty(f[3]) + "\t\tsystem", 1.0);
    }

    private static String nullToEmpty(String s) {
        String t = s.strip();
        return t.equals("null") || t.equals("(null)") ? "" : t;
    }

    static String columns() {
        return COLUMNS;
    }
}
