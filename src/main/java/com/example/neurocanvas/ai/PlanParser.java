package com.example.neurocanvas.ai;

import com.example.neurocanvas.module.Module;
import com.example.neurocanvas.module.ModuleManager;
import com.example.neurocanvas.module.Setting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;

/**
 * Turns the model's text into an {@link AiPlan}. The answer is untrusted: only known modules and settings
 * are accepted, values are clamped, and anything else is skipped with a note.
 */
public final class PlanParser {
    private static final int MAX_SETTINGS = 40;

    private PlanParser() { }

    public static AiPlan parse(String content, ModuleManager modules) {
        AiPlan plan = new AiPlan();
        JsonObject root = extractJson(content);
        if (root == null) {
            // No usable JSON: show plain answers as they are, but never dump a half-broken JSON blob into the chat.
            plan.reply = content.contains("{")
                    ? "I could not format my answer properly. Please ask again."
                    : shorten(content.trim(), 400);
            return plan;
        }
        if (root.has("reply") && root.get("reply").isJsonPrimitive()) {
            plan.reply = shorten(root.get("reply").getAsString().trim(), 400);
        }
        if (root.has("name") && root.get("name").isJsonPrimitive()) {
            String name = ModuleManager.cleanName(root.get("name").getAsString());
            plan.name = name.isEmpty() ? "AI" : name;
        }
        if (root.has("modules") && root.get("modules").isJsonObject()) {
            readModules(root.getAsJsonObject("modules"), modules, plan);
        }
        return plan;
    }

    /**
     * Models sometimes wrap the JSON in prose, repeat it, or correct themselves. Find every top-level
     * {...} block and return the last one that parses and looks like a plan.
     */
    static JsonObject extractJson(String content) {
        java.util.List<int[]> spans = new java.util.ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = depth > 0;
            } else if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}' && depth > 0 && --depth == 0) {
                spans.add(new int[] {start, i});
            }
        }
        for (int i = spans.size() - 1; i >= 0; i--) {
            try {
                JsonObject o = JsonParser.parseString(content.substring(spans.get(i)[0], spans.get(i)[1] + 1)).getAsJsonObject();
                if (o.has("reply") || o.has("modules") || o.has("effects")) {
                    return o;
                }
            } catch (RuntimeException ignored) {
                // Not valid JSON; try the previous block.
            }
        }
        return null;
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static void readModules(JsonObject object, ModuleManager modules, AiPlan plan) {
        int settingCount = 0;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            Module module = find(modules, entry.getKey());
            if (module == null) {
                plan.warnings.add("unknown module " + entry.getKey());
                continue;
            }
            if (!module.aiControllable) {
                plan.warnings.add(module.name + " cannot be changed by AI");
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject body = entry.getValue().getAsJsonObject();
            AiPlan.ModuleChange change = new AiPlan.ModuleChange(module);
            if (body.has("enabled") && body.get("enabled").isJsonPrimitive()) {
                boolean want = body.get("enabled").getAsBoolean();
                if (want != module.enabled) {
                    change.enable = want;
                }
            }
            if (body.has("settings") && body.get("settings").isJsonObject()) {
                for (Map.Entry<String, JsonElement> s : body.getAsJsonObject("settings").entrySet()) {
                    Setting setting = module.setting(s.getKey());
                    if (setting == null) {
                        plan.warnings.add(module.name + " has no setting " + s.getKey());
                        continue;
                    }
                    Double parsed = setting.parse(s.getValue());
                    if (parsed == null) {
                        plan.warnings.add(module.name + "." + setting.name + " got an invalid value");
                        continue;
                    }
                    if (++settingCount > MAX_SETTINGS) {
                        plan.warnings.add("too many setting changes");
                        break;
                    }
                    double clamped = Math.max(setting.min, Math.min(setting.max, parsed));
                    if (Math.abs(clamped - setting.value) > 1e-9) {
                        change.settings.add(new AiPlan.SettingChange(setting, setting.value, clamped));
                    }
                }
            }
            if (change.enable != null || !change.settings.isEmpty()) {
                plan.changes.add(change);
            }
        }
    }

    private static Module find(ModuleManager modules, String name) {
        String wanted = name.replace(" ", "").toLowerCase();
        for (Module m : modules.all()) {
            if (m.name.replace(" ", "").toLowerCase().equals(wanted)) {
                return m;
            }
        }
        return null;
    }
}
