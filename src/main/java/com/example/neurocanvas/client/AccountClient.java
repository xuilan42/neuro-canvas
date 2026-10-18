package com.example.neurocanvas.client;

import com.example.neurocanvas.module.NameTags;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import net.minecraft.client.MinecraftClient;

/**
 * The link between this mod and a Neuro Canvas account. The mod never sees a password: it asks the server for a short code, opens
 * the account page in the browser (where the user registers or signs in and confirms the code), and then receives a token that is
 * kept in neurocanvas/account.json. Everything online (the shared items, the friends) is done as that account.
 */
public final class AccountClient {
    public enum State { SIGNED_OUT, LINKING, SIGNED_IN }

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(8)).build();
    private static volatile State state = State.SIGNED_OUT;
    private static volatile String token = "";
    private static volatile String name = "";
    private static volatile boolean verified;
    private static volatile String linkCode = "";
    private static volatile String note = "";
    private static volatile int generation;
    private static Path file;

    private AccountClient() { }

    public static void init(Path dir) {
        file = dir.resolve("account.json");
        try {
            if (Files.exists(file)) {
                JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                token = o.has("token") ? o.get("token").getAsString() : "";
                name = o.has("name") ? o.get("name").getAsString() : "";
                verified = o.has("verified") && o.get("verified").getAsBoolean();
            }
        } catch (IOException | RuntimeException ignored) {
            token = "";
        }
        if (!token.matches("[0-9a-f]{64}")) {
            token = "";
        }
        state = token.isEmpty() ? State.SIGNED_OUT : State.SIGNED_IN;
        if (!token.isEmpty()) {
            Thread t = new Thread(AccountClient::refresh, "neurocanvas-account");
            t.setDaemon(true);
            t.start();
        }
    }

    public static State state() { return state; }
    public static boolean signedIn() { return state == State.SIGNED_IN && !token.isEmpty(); }
    public static String name() { return name; }
    public static boolean verified() { return verified; }
    public static String linkCode() { return linkCode; }
    public static String note() { return note; }

    /** The Authorization header value for requests made as the account, or null when nobody is signed in. */
    public static String bearer() {
        return signedIn() ? "Bearer " + token : null;
    }

    private static String base() throws IOException {
        String url = NameTags.mainUrl();
        if (url.isBlank()) {
            throw new IOException("this build has no server address");
        }
        return url;
    }

    private static void save() {
        try {
            if (file == null) {
                return;
            }
            if (token.isEmpty()) {
                Files.deleteIfExists(file);
                return;
            }
            JsonObject o = new JsonObject();
            o.addProperty("token", token);
            o.addProperty("name", name);
            o.addProperty("verified", verified);
            Files.createDirectories(file.getParent());
            Files.writeString(file, o.toString(), StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException | IOException ignored) {
                // Not a POSIX file system.
            }
        } catch (IOException ignored) {
            // The account stays linked for this session even when it cannot be remembered.
        }
    }

    private static JsonObject post(String path, JsonObject body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + path)).timeout(Duration.ofSeconds(15))
                .header("content-type", "application/json").header("x-nc", "1")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject o;
        try {
            o = JsonParser.parseString(r.body()).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("server answered " + r.statusCode());
        }
        if (r.statusCode() != 200) {
            throw new IOException(o.has("error") ? o.get("error").getAsString() : "server answered " + r.statusCode());
        }
        return o;
    }

    /** Asks the server who the token belongs to; a token that was refused is forgotten. */
    public static void refresh() {
        String t = token;
        if (t.isEmpty()) {
            return;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/v1/acc/me")).timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + t).GET().build();
            HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 401) {
                forget("Your session ended, sign in again");
            } else if (r.statusCode() == 200) {
                JsonObject u = JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonObject("user");
                name = u.get("name").getAsString();
                verified = u.get("verified").getAsBoolean();
                save();
            }
        } catch (IOException | RuntimeException ignored) {
            // Offline: the saved account is kept and tried again next time.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void forget(String why) {
        token = "";
        name = "";
        verified = false;
        linkCode = "";
        note = why;
        state = State.SIGNED_OUT;
        save();
    }

    /** The server refused the token (for example the session was ended on the site). */
    public static void invalidate() {
        if (signedIn()) {
            forget("Your session ended, sign in again");
        }
    }

    /** Starts linking: asks for a code, opens the page in the browser and waits (up to ten minutes) until the code is confirmed there. */
    public static void startLink() {
        if (state == State.LINKING) {
            return;
        }
        int gen = ++generation;
        state = State.LINKING;
        linkCode = "";
        note = "";
        Thread t = new Thread(() -> link(gen), "neurocanvas-link");
        t.setDaemon(true);
        t.start();
    }

    private static void link(int gen) {
        try {
            JsonObject start = post("/v1/acc/device/start", new JsonObject());
            String poll = start.get("poll").getAsString();
            linkCode = start.get("code").getAsString();
            String url = start.get("url").getAsString();
            // Only a web address is opened (never a file or another scheme); plain http only for a registry on this computer.
            boolean local = base().startsWith("http://127.0.0.1") || base().startsWith("http://localhost");
            if (!url.startsWith("https://") && !(local && url.startsWith(base()))) {
                throw new IOException("the server gave an unexpected address");
            }
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                mc.execute(() -> net.minecraft.util.Util.getOperatingSystem().open(URI.create(url)));
            }
            long end = System.currentTimeMillis() + 10 * 60_000;
            while (gen == generation && System.currentTimeMillis() < end) {
                Thread.sleep(2000);
                if (gen != generation) {
                    return;
                }
                JsonObject b = new JsonObject();
                b.addProperty("poll", poll);
                JsonObject r = post("/v1/acc/device/poll", b);
                String status = r.get("status").getAsString();
                if (status.equals("ok")) {
                    token = r.get("token").getAsString();
                    JsonObject u = r.getAsJsonObject("user");
                    name = u.get("name").getAsString();
                    verified = u.get("verified").getAsBoolean();
                    linkCode = "";
                    note = "";
                    state = State.SIGNED_IN;
                    save();
                    return;
                }
                if (status.equals("expired")) {
                    break;
                }
            }
            if (gen == generation) {
                linkCode = "";
                note = "The code expired, try again";
                state = State.SIGNED_OUT;
            }
        } catch (IOException | RuntimeException e) {
            if (gen == generation) {
                linkCode = "";
                note = "Could not reach the server";
                state = State.SIGNED_OUT;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void cancelLink() {
        if (state == State.LINKING) {
            generation++;
            linkCode = "";
            state = State.SIGNED_OUT;
        }
    }

    /** Ends the session on the server (best effort) and forgets the token here. */
    public static void signOut() {
        String t = token;
        generation++;
        forget("");
        if (!t.isEmpty()) {
            Thread th = new Thread(() -> {
                try {
                    HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/v1/acc/logout")).timeout(Duration.ofSeconds(10))
                            .header("Authorization", "Bearer " + t).header("x-nc", "1").header("content-type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
                    HTTP.send(req, HttpResponse.BodyHandlers.discarding());
                } catch (IOException | RuntimeException ignored) {
                    // The token is gone from this computer either way.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "neurocanvas-logout");
            th.setDaemon(true);
            th.start();
        }
    }

    /** A request made as the account (JSON in, JSON out). An error message from the server becomes the exception message. */
    public static java.util.concurrent.CompletableFuture<JsonObject> call(String method, String path, JsonObject body) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                String bearer = bearer();
                if (bearer == null) {
                    throw new IOException("sign in first");
                }
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base() + path)).timeout(Duration.ofSeconds(15))
                        .header("Authorization", bearer).header("x-nc", "1");
                HttpRequest req = switch (method) {
                    case "GET" -> b.GET().build();
                    case "DELETE" -> b.DELETE().build();
                    default -> b.header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body.toString())).build();
                };
                HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 401) {
                    invalidate();
                    throw new IOException("sign in again");
                }
                JsonObject o;
                try {
                    o = JsonParser.parseString(r.body()).getAsJsonObject();
                } catch (RuntimeException e) {
                    throw new IOException("server answered " + r.statusCode());
                }
                if (r.statusCode() != 200) {
                    throw new IOException(o.has("error") ? o.get("error").getAsString() : "server answered " + r.statusCode());
                }
                return o;
            } catch (IOException | RuntimeException e) {
                throw new java.util.concurrent.CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CompletionException(e);
            }
        });
    }

    public static String siteUrl() throws IOException {
        return base() + "/account.html";
    }
}
