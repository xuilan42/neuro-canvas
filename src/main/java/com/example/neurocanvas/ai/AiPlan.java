package com.example.neurocanvas.ai;

import com.example.neurocanvas.module.Module;
import com.example.neurocanvas.module.Setting;
import java.util.ArrayList;
import java.util.List;

/** What the model wants to change, already checked against the real modules and their limits. */
public final class AiPlan {
    public record SettingChange(Setting setting, double oldValue, double newValue) { }

    public static final class ModuleChange {
        public final Module module;
        public Boolean enable;
        public final List<SettingChange> settings = new ArrayList<>();

        public ModuleChange(Module module) {
            this.module = module;
        }
    }

    /** One line of the preview: kind 0 = module heading, 1 = setting, 2 = effect, 3 = warning. */
    public record Line(String text, int kind) { }

    public String name = "AI";
    /** The short chat answer that came with the plan. */
    public String reply = "";
    public final List<ModuleChange> changes = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    public List<Line> describe() {
        List<Line> lines = new ArrayList<>();
        for (ModuleChange c : changes) {
            String head = c.module.name;
            if (c.enable != null) {
                head += c.enable ? "   turn on" : "   turn off";
            }
            lines.add(new Line(head, 0));
            for (SettingChange s : c.settings) {
                lines.add(new Line("   " + s.setting().name + ":  " + s.setting().format(s.oldValue()) + "  >  "
                        + s.setting().format(s.newValue()), 1));
            }
        }
        for (String w : warnings) {
            lines.add(new Line("skipped: " + w, 3));
        }
        return lines;
    }
}
