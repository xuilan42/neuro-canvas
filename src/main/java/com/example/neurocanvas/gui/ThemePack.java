package com.example.neurocanvas.gui;

import com.example.neurocanvas.module.Module;
import com.example.neurocanvas.module.ModuleManager;
import com.example.neurocanvas.module.Setting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;

/** Applies a theme's pack: module on/off states and settings that go with the look (fog, sky, colours, ...). */
public final class ThemePack {
    private ThemePack() { }

    /** @return how many settings and switches were changed */
    public static int apply(ModuleManager modules, JsonObject pack) {
        int changed = 0;
        for (Map.Entry<String, JsonElement> entry : pack.entrySet()) {
            Module module = null;
            for (Module m : modules.all()) {
                if (m.name.equalsIgnoreCase(entry.getKey())) {
                    module = m;
                }
            }
            if (module == null || !entry.getValue().isJsonObject() || !module.aiControllable) {
                continue;
            }
            JsonObject body = entry.getValue().getAsJsonObject();
            if (body.has("enabled") && body.get("enabled").isJsonPrimitive()) {
                module.enabled = body.get("enabled").getAsBoolean();
                changed++;
            }
            if (body.has("settings") && body.get("settings").isJsonObject()) {
                for (Map.Entry<String, JsonElement> s : body.getAsJsonObject("settings").entrySet()) {
                    Setting setting = module.setting(s.getKey());
                    Double value = setting == null ? null : setting.parse(s.getValue());
                    if (value != null) {
                        setting.set(value);
                        changed++;
                    }
                }
            }
        }
        return changed;
    }
}
