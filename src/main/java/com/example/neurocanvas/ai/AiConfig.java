package com.example.neurocanvas.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Connection settings for an OpenAI-compatible chat API. Kept in its own file so the key never ends up
 * in presets or share codes.
 */
public final class AiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String baseUrl = "https://api.openai.com/v1";
    public String apiKey = "";
    public String model = "gpt-4o-mini";
    /** Speech to text (an OpenAI-compatible /audio/transcriptions endpoint): the model, an optional full address and an optional language code. */
    public String voiceModel = "whisper-1";
    public String voiceUrl = "";
    public String voiceLanguage = "";
    /** In the chat, apply what the AI suggests right away (an Undo button is always shown). */
    public boolean autoApply = true;

    private transient Path file;

    public static AiConfig load(Path file) {
        AiConfig config = new AiConfig();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                AiConfig loaded = GSON.fromJson(reader, AiConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (IOException | RuntimeException ignored) {
                // A broken file falls back to defaults and is rewritten on the next save.
            }
        }
        config.file = file;
        if (config.baseUrl == null) config.baseUrl = "";
        if (config.apiKey == null) config.apiKey = "";
        if (config.model == null) config.model = "";
        if (config.voiceModel == null) config.voiceModel = "whisper-1";
        if (config.voiceUrl == null) config.voiceUrl = "";
        if (config.voiceLanguage == null) config.voiceLanguage = "";
        return config;
    }

    public void save() throws IOException {
        com.example.neurocanvas.client.SecretFiles.write(file, GSON.toJson(this));
    }

    /** The key from the file, or from the NEUROCANVAS_API_KEY environment variable when the file has none. */
    public String effectiveKey() {
        if (!apiKey.isBlank()) {
            return apiKey.trim();
        }
        String env = System.getenv("NEUROCANVAS_API_KEY");
        return env == null ? "" : env.trim();
    }

    /** Servers on your own machine (Ollama, LM Studio) do not need a key. */
    public boolean isLocal() {
        try {
            String host = URI.create(baseUrl.trim()).getHost();
            return isLocalHost(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static boolean isLocalHost(String host) {
        if (host == null) {
            return false;
        }
        if (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1") || host.equals("[::1]")
                || host.startsWith("192.168.") || host.startsWith("10.") || host.endsWith(".local")) {
            return true;
        }
        if (host.startsWith("172.")) {
            String[] parts = host.split("\\.");
            try {
                int second = parts.length > 1 ? Integer.parseInt(parts[1]) : -1;
                return second >= 16 && second <= 31;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    /** True when sending a key to this address would put it on the internet in the clear (http to a host that is not on this machine or network). */
    public static boolean sendsKeyInTheClear(String url) {
        try {
            URI u = URI.create(url.trim());
            return "http".equalsIgnoreCase(u.getScheme()) && !isLocalHost(u.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean ready() {
        return !baseUrl.isBlank() && !model.isBlank() && (isLocal() || !effectiveKey().isEmpty());
    }
}
