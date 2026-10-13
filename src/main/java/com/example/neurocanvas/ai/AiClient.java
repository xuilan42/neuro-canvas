package com.example.neurocanvas.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Sends one chat request to an OpenAI-compatible server and returns the text of the reply. */
public final class AiClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            // Plain-http servers often drop the HTTP/2 upgrade handshake Java tries by default.
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15)).build();

    public CompletableFuture<String> complete(AiConfig config, String system, String user) {
        return complete(config, system, java.util.Collections.singletonList(new String[] {"user", user}));
    }

    /** @param conversation messages as {role, content}, oldest first; roles are "user" and "assistant" */
    public CompletableFuture<String> complete(AiConfig config, String system, List<String[]> conversation) {
        return send(config, system, conversation, true).thenCompose(first -> {
            if (first.statusCode() == 400 && first.body().contains("response_format")) {
                return send(config, system, conversation, false);
            }
            return CompletableFuture.completedFuture(first);
        }).thenApply(AiClient::extract);
    }

    private CompletableFuture<HttpResponse<String>> send(AiConfig config, String system, List<String[]> conversation,
                                                        boolean jsonMode) {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.model.trim());
        JsonArray messages = new JsonArray();
        messages.add(message("system", system));
        for (String[] m : conversation) {
            messages.add(m.length > 2 && m[2] != null ? messageWithImage(m[0], m[1], m[2]) : message(m[0], m[1]));
        }
        body.add("messages", messages);
        if (jsonMode) {
            JsonObject format = new JsonObject();
            format.addProperty("type", "json_object");
            body.add("response_format", format);
        }

        String base = config.baseUrl.trim();
        String url = base.endsWith("/chat/completions") ? base : base.replaceAll("/+$", "") + "/chat/completions";
        HttpRequest.Builder request;
        try {
            request = HttpRequest.newBuilder(URI.create(url));
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(new AiException("The API address is not a valid URL"));
        }
        request.timeout(Duration.ofSeconds(60)).header("Content-Type", "application/json");
        String key = config.effectiveKey();
        if (!key.isEmpty()) {
            request.header("Authorization", "Bearer " + key);
        }
        request.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        return HTTP.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString()).exceptionallyCompose(e -> {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String detail = cause.getMessage();
            String text = cause instanceof IOException
                    ? "Cannot reach the server" + (detail == null ? " (is it running? check the API address)" : ": " + shorten(detail, 120))
                    : "Request failed: " + cause.getClass().getSimpleName();
            return CompletableFuture.failedFuture(new AiException(text, cause));
        });
    }

    /** A message with a picture, in the format vision models take: a text part and an image part holding a data URL. */
    private static JsonObject messageWithImage(String role, String text, String dataUrl) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        JsonArray parts = new JsonArray();
        JsonObject t = new JsonObject();
        t.addProperty("type", "text");
        t.addProperty("text", text);
        parts.add(t);
        JsonObject img = new JsonObject();
        img.addProperty("type", "image_url");
        JsonObject url = new JsonObject();
        url.addProperty("url", dataUrl);
        img.add("image_url", url);
        parts.add(img);
        m.add("content", parts);
        return m;
    }

    /** Turns a recording (a WAV file) into text with an OpenAI-compatible speech-to-text endpoint. */
    public CompletableFuture<String> transcribe(AiConfig config, byte[] wav) {
        String url = config.voiceUrl.isBlank() ? config.baseUrl.trim().replaceAll("/chat/completions$", "").replaceAll("/+$", "") + "/audio/transcriptions"
                : config.voiceUrl.trim();
        String boundary = "----neurocanvas" + System.nanoTime();
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        try {
            field(body, boundary, "model", config.voiceModel.isBlank() ? "whisper-1" : config.voiceModel.trim());
            if (!config.voiceLanguage.isBlank()) {
                field(body, boundary, "language", config.voiceLanguage.trim());
            }
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            body.write(wav);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            return CompletableFuture.failedFuture(new AiException("Could not prepare the recording"));
        }
        HttpRequest.Builder request;
        try {
            request = HttpRequest.newBuilder(URI.create(url));
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(new AiException("The speech address is not a valid URL"));
        }
        request.timeout(Duration.ofSeconds(60)).header("Content-Type", "multipart/form-data; boundary=" + boundary);
        String key = config.effectiveKey();
        if (!key.isEmpty()) {
            request.header("Authorization", "Bearer " + key);
        }
        request.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        return HTTP.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString()).handle((response, error) -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                throw new AiException("Cannot reach the speech server: " + shorten(String.valueOf(cause.getMessage()), 120), cause);
            }
            int status = response.statusCode();
            if (status == 404) {
                throw new AiException("This server has no speech to text (HTTP 404). Set a server that has /audio/transcriptions, or type instead");
            }
            if (status < 200 || status >= 300) {
                throw new AiException("Speech to text failed (HTTP " + status + "): " + shorten(response.body().replaceAll("\\s+", " "), 140));
            }
            try {
                String text = JsonParser.parseString(response.body()).getAsJsonObject().get("text").getAsString().trim();
                if (text.isEmpty()) {
                    throw new AiException("Nothing was heard");
                }
                return text;
            } catch (AiException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new AiException("Unexpected reply from the speech server", e);
            }
        });
    }

    private static void field(java.io.ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }

    private static String extract(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new AiException("The server rejected the API key (HTTP " + status + ")");
        }
        if (status == 404) {
            throw new AiException("Not found (HTTP 404): check the API address and model name");
        }
        if (status == 429) {
            throw new AiException("Rate limit or no credit left (HTTP 429)");
        }
        if (status < 200 || status >= 300) {
            throw new AiException("The server answered HTTP " + status + ": " + shorten(response.body().replaceAll("\\s+", " "), 160));
        }
        try {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonElement content = root.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content");
            if (content == null || content.isJsonNull() || content.getAsString().isBlank()) {
                throw new AiException("The model returned an empty answer");
            }
            return content.getAsString();
        } catch (AiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AiException("Unexpected reply format from the server", e);
        }
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
