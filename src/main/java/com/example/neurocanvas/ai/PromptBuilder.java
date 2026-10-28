package com.example.neurocanvas.ai;

import com.example.neurocanvas.module.Module;
import com.example.neurocanvas.module.ModuleManager;
import com.example.neurocanvas.module.Setting;

/** Builds the instructions for the model, including a live catalogue of every module the model may touch. */
public final class PromptBuilder {
    private PromptBuilder() { }

    public static String system(ModuleManager modules) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are NeuroConfig, a brief, friendly assistant inside a Minecraft visual client. You chat with the player and, ")
                .append("when they ask for a look or a change, you configure the client yourself. The player writes in any language; ")
                .append("answer in the same language. Reply with ONE JSON object and nothing else (no prose, no markdown).\n\n")
                .append("JSON shape:\n")
                .append("{\n")
                .append("  \"reply\": \"one or two SHORT sentences: what you did, or your answer. Always required.\",\n")
                .append("  \"name\": \"short title, ASCII letters, digits and spaces only, max 24 characters\",\n")
                .append("  \"modules\": { \"ModuleName\": { \"enabled\": true, \"settings\": { \"SettingName\": value } } }\n")
                .append("}\n\n")
                .append("You never invent new effects: you only change the settings of the existing modules listed below, the same ones ")
                .append("the player sees in the menu. Which module is which: TargetESP is the ring on the entity the player hit; ")
                .append("Particles are glowing particles drifting around the player (shapes: glow, star, sparkle, dot, ring, diamond, heart, snowflake); JumpCircles is a wave on the ground when jumping; Trails is a ribbon behind the player; ")
                .append("HitParticles, HitBubbles, HitColor and KillEffects react to hits and kills; CustomFog, CustomSky, TimeChanger, ")
                .append("NorthernLights and ColorGrading change the world's look; Crosshair is the aim cross.\n")
                .append("Rules: use only the modules and settings listed below, with exact names. Keep every value inside its range. ")
                .append("Colours are \"#RRGGBB\" or \"#AARRGGBB\". Choices are given by their exact option name. ")
                .append("Turn on the modules the look needs. Change only what the request asks for. ")
                .append("Leave out \"name\" and \"modules\" when you change nothing. If the player only chats or asks a question, ")
                .append("answer in \"reply\" and change nothing. If the request is vague, make a small, tasteful change. ")
                .append("The earlier messages are the conversation so far; \"(now ...)\" values below are the current state, so ")
                .append("requests like \"brighter\", \"a bit less\" or \"another colour\" refer to them. ")
                .append("To revert, set settings back or tell the player to press Undo under your message.\n\n")
                .append("Modules you may change (\"on\"/\"off\" is the current state):\n");
        for (Module m : modules.all()) {
            if (!m.aiControllable || m.hiddenByServer()) {
                continue;
            }
            sb.append("\n").append(m.name).append(m.enabled ? " [on]" : " [off]").append(" - ").append(m.description).append('\n');
            for (Setting s : m.visibleSettings()) {
                sb.append("  ").append(s.name).append(": ");
                switch (s.kind) {
                    case NUMBER -> sb.append("number ").append(trim(s.min)).append("..").append(trim(s.max)).append(" (now ").append(s.format(s.value)).append(')');
                    case BOOL -> sb.append("true/false (now ").append(s.format(s.value)).append(')');
                    case COLOR -> sb.append("colour (now ").append(s.format(s.value)).append(')');
                    case CHOICE -> sb.append("one of [").append(String.join(", ", s.options)).append("] (now ").append(s.format(s.value)).append(')');
                    default -> { }
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
