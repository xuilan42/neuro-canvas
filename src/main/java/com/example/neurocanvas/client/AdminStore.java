package com.example.neurocanvas.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The admin key of the project's server, kept in neurocanvas/admin.key on this computer only. Without it nothing here is shown. */
public final class AdminStore {
    private static Path file;
    private static String key = "";

    private AdminStore() { }

    public static void init(Path path) {
        file = path;
        try {
            key = Files.exists(path) ? Files.readString(path).trim() : "";
        } catch (IOException e) {
            key = "";
        }
    }

    public static boolean has() {
        return !key.isEmpty();
    }

    public static String key() {
        return key;
    }

    /** Stores a key, or forgets it when empty. Returns false when the key has an unusual shape. */
    public static boolean set(String value) {
        String k = value == null ? "" : value.trim();
        if (!k.isEmpty() && !k.matches("[A-Za-z0-9_-]{16,128}")) {
            return false;
        }
        key = k;
        try {
            if (k.isEmpty()) {
                Files.deleteIfExists(file);
            } else {
                Files.createDirectories(file.getParent());
                Files.writeString(file, k);
                try {
                    Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
                } catch (UnsupportedOperationException | IOException ignored) {
                    // Not a POSIX file system.
                }
            }
        } catch (IOException ignored) {
            // The key still works until the game is closed.
        }
        return true;
    }
}
