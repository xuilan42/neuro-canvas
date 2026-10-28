package com.example.neurocanvas.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The packets of HolyWorld's In-Game API (Feature Control, channel liteapi:feature-control), written from its public documentation
 * (wiki.holyworld.me): the client lists its features, the server answers with the ones that are blocked. No game classes here, so it can be tested alone.
 */
final class HolyApiProtocol {
    /** The stable name the server keeps personal blocks under; it must not change between versions. */
    static final String CLIENT_ID = "neurocanvas";
    static final int MAX_PACKET = 65536;
    private static final int MAX_BLOCKED = 256;
    private static final int MAX_NAME = 64;

    /** {@code ok} false means the server answered with an error (BAD_REQUEST, RATE_LIMITED, ...). */
    record Result(boolean ok, Set<String> blocklist, String error) { }

    private HolyApiProtocol() { }

    static String request(String id, List<String> features) {
        JsonArray list = new JsonArray();
        for (String f : features) {
            list.add(f);
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("client", CLIENT_ID);
        payload.add("features", list);
        JsonObject root = new JsonObject();
        root.addProperty("id", id);
        root.addProperty("method", "checkFeatures");
        root.add("payload", payload);
        return root.toString();
    }

    /** Reads an answer; null when it is not an answer to {@code expectedId} or is not valid. Sizes are limited, whatever the server sends. */
    static Result parse(String json, String expectedId) {
        if (json == null || json.length() > MAX_PACKET) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonElement id = root.get("id");
            if (id == null || id.isJsonNull() || !id.getAsString().equals(expectedId)) {
                return null;
            }
            JsonElement ok = root.get("ok");
            if (ok == null || ok.isJsonNull()) {
                return null;
            }
            if (!ok.getAsBoolean()) {
                JsonElement error = root.get("error");
                String text = error == null || error.isJsonNull() ? "unknown error" : error.getAsString();
                return new Result(false, Set.of(), text.length() > 40 ? text.substring(0, 40) : text);
            }
            JsonObject payload = root.getAsJsonObject("payload");
            JsonArray array = payload == null ? null : payload.getAsJsonArray("blocklist");
            if (array == null) {
                return null;
            }
            Set<String> out = new HashSet<>();
            for (JsonElement e : array) {
                if (out.size() >= MAX_BLOCKED) {
                    break;
                }
                String name = e.getAsString();
                if (!name.isBlank() && name.length() <= MAX_NAME) {
                    out.add(name.toLowerCase(Locale.ROOT));
                }
            }
            return new Result(true, Set.copyOf(out), "");
        } catch (RuntimeException e) {
            return null;
        }
    }
}
