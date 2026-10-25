package com.example.neurocanvas.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Brings a world on this computer to the internet through the project's server. The server gives the world a name (xxxx.neuro-connect...), tells
 * this class when somebody connects to that name, and this class then connects the person to the world's own LAN port: one data connection to the
 * server and one to the world for every player, with the bytes copied both ways. Nothing is stored, the game's own login still applies.
 */
public final class WorldTunnel {
    public record Info(String host, long expiresAt, int players) { }

    private static final Logger LOG = LoggerFactory.getLogger("neurocanvas");
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(8)).build();

    private final int lanPort;
    private final Consumer<String> say;
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final Set<WebSocket> sockets2 = ConcurrentHashMap.newKeySet();
    private volatile boolean stopped;
    private volatile WebSocket control;
    private String sub = "";
    private String token = "";
    private String wsBase = "";
    private volatile Info info;
    private volatile Runnable onClosed = () -> { };

    public WorldTunnel(int lanPort, Consumer<String> say) {
        this.lanPort = lanPort;
        this.say = say;
    }

    public Info info() {
        return info;
    }

    public boolean stopped() {
        return stopped;
    }

    public void onClosed(Runnable r) {
        this.onClosed = r;
    }

    private static String base() throws IOException {
        String url = com.example.neurocanvas.module.NameTags.mainUrl();
        if (url.isBlank()) {
            throw new IOException("this build has no server address");
        }
        return url;
    }

    /** Asks the server for a name and opens the control connection. Blocks; call it off the game thread. */
    public Info open(int players) throws IOException, InterruptedException {
        String bearer = AccountClient.bearer();
        if (bearer == null) {
            throw new IOException("sign in first: Misc > Account");
        }
        String base = base();
        JsonObject body = new JsonObject();
        body.addProperty("players", players);
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/v1/tunnel/open")).timeout(Duration.ofSeconds(15))
                .header("content-type", "application/json").header("x-nc", "1").header("Authorization", bearer)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            String msg = "server answered " + r.statusCode();
            try {
                msg = JsonParser.parseString(r.body()).getAsJsonObject().get("error").getAsString();
            } catch (RuntimeException ignored) {
                // The plain status stays.
            }
            throw new IOException(msg);
        }
        JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
        sub = o.get("sub").getAsString();
        token = o.get("token").getAsString();
        wsBase = base.replaceFirst("^http", "ws");
        info = new Info(o.get("host").getAsString(), o.get("expiresAt").getAsLong(), o.get("players").getAsInt());
        connectControl(0);
        return info;
    }

    private String query() {
        return "?sub=" + sub + "&token=" + token;
    }

    private void connectControl(int attempt) {
        StringBuilder text = new StringBuilder();
        HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(wsBase + "/v1/tunnel/ctl" + query()), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket ws) {
                        control = ws;
                        ws.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        text.append(data);
                        if (last) {
                            String msg = text.toString();
                            text.setLength(0);
                            handle(msg);
                        }
                        ws.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
                        lost(attempt);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        lost(attempt);
                    }
                }).exceptionally(e -> {
                    lost(attempt);
                    return null;
                });
    }

    private void lost(int attempt) {
        if (stopped) {
            return;
        }
        if (attempt >= 4) {
            say.accept("The connection to the server was lost; the world is no longer reachable from the internet.");
            close();
            return;
        }
        new Thread(() -> {
            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                return;
            }
            if (!stopped) {
                connectControl(attempt + 1);
            }
        }, "neurocanvas-tunnel-retry").start();
    }

    private void handle(String msg) {
        try {
            JsonObject o = JsonParser.parseString(msg).getAsJsonObject();
            String t = o.get("t").getAsString();
            if (t.equals("conn")) {
                int id = o.get("id").getAsInt();
                Thread th = new Thread(() -> serve(id), "neurocanvas-tunnel-conn");
                th.setDaemon(true);
                th.start();
            } else if (t.equals("closed")) {
                say.accept("The tunnel was closed by the server (" + (o.has("why") ? o.get("why").getAsString() : "no reason") + ").");
                close();
            }
        } catch (RuntimeException e) {
            LOG.debug("tunnel: bad message {}", msg);
        }
    }

    /** One player: a connection to the server for the data and one to the world, copied both ways until either ends. */
    private void serve(int id) {
        Socket local = null;
        WebSocket data = null;
        try {
            local = new Socket();
            local.setTcpNoDelay(true);
            local.connect(new InetSocketAddress("127.0.0.1", lanPort), 3000);
            sockets.add(local);
            Socket sock = local;
            OutputStream out = local.getOutputStream();
            data = HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8))
                    .buildAsync(URI.create(wsBase + "/v1/tunnel/data" + query() + "&conn=" + id), new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket ws) {
                            ws.request(1);
                        }

                        @Override
                        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer buf, boolean last) {
                            try {
                                byte[] bytes = new byte[buf.remaining()];
                                buf.get(bytes);
                                out.write(bytes);
                                out.flush();
                            } catch (IOException e) {
                                closeQuietly(sock);
                            }
                            ws.request(1);
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
                            closeQuietly(sock);
                            return null;
                        }

                        @Override
                        public void onError(WebSocket ws, Throwable error) {
                            closeQuietly(sock);
                        }
                    }).join();
            sockets2.add(data);
            InputStream in = local.getInputStream();
            byte[] chunk = new byte[16384];
            int n;
            while (!stopped && (n = in.read(chunk)) != -1) {
                data.sendBinary(ByteBuffer.wrap(chunk, 0, n), true).join();
            }
        } catch (IOException | RuntimeException e) {
            LOG.debug("tunnel: connection {} ended: {}", id, e.toString());
        } finally {
            closeQuietly(local);
            if (data != null) {
                sockets2.remove(data);
                try {
                    data.sendClose(WebSocket.NORMAL_CLOSURE, "");
                } catch (RuntimeException ignored) {
                    // Already closed.
                }
            }
            if (local != null) {
                sockets.remove(local);
            }
        }
    }

    private static void closeQuietly(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }
    }

    /** Ends the tunnel: tells the server, drops every connection. */
    public void close() {
        if (stopped) {
            return;
        }
        stopped = true;
        try {
            String bearer = AccountClient.bearer();
            if (bearer != null && !sub.isEmpty()) {
                HTTP.sendAsync(HttpRequest.newBuilder(URI.create(base() + "/v1/tunnel")).timeout(Duration.ofSeconds(5))
                        .header("x-nc", "1").header("Authorization", bearer).DELETE().build(), HttpResponse.BodyHandlers.discarding());
            }
        } catch (IOException | RuntimeException ignored) {
            // The server closes it by itself when the time is up.
        }
        WebSocket c = control;
        if (c != null) {
            try {
                c.sendClose(WebSocket.NORMAL_CLOSURE, "");
            } catch (RuntimeException ignored) {
                // Already closed.
            }
        }
        for (Socket s : sockets) {
            closeQuietly(s);
        }
        for (WebSocket w : sockets2) {
            try {
                w.abort();
            } catch (RuntimeException ignored) {
                // Already closed.
            }
        }
        onClosed.run();
    }
}
