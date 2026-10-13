package com.example.neurocanvas.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

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
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        }
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Not a POSIX file system (Windows); nothing to restrict.
        }
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
            return host != null && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1")
                    || host.startsWith("192.168.") || host.endsWith(".local"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean ready() {
        return !baseUrl.isBlank() && !model.isBlank() && (isLocal() || !effectiveKey().isEmpty());
    }
}
