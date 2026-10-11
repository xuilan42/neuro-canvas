# Neuro Canvas

A client-side **Fabric 1.20.1** visual mod: a clean in-game menu with 40+ modules (target ring, hit particles,
fog, sky, crosshair, HUD panels and more), presets, and an **AI chat** that sets them up for you.
Everything runs on the client: it works in singleplayer and on servers, and needs no server-side part.

> Русская версия: [README.ru.md](README.ru.md)

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.20.1 and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Put `neuro-canvas-<version>.jar` into your `mods` folder.
3. Start the game and press **Right Shift** (rebindable in Controls) to open the menu.

Java 17 or newer is required.

## The menu

- **Tabs** by category, a **module list** on the left with on/off switches, and the **settings** of the selected module on the right.
- Every module can have a **key bind** (toggle or hold mode). Search with **Ctrl+F**.
- Colour settings open a full **colour picker** with alpha.
- **Configs** tab: save, load and delete presets, copy a **share code** to the clipboard, import a code, and edit the **HUD layout** by dragging elements.
- **Interface** (Misc tab): 6 themes, 3 bundled fonts (Cyrillic supported), animations.
- The menu remembers the tab and module you were on.

## Modules

| Category | Modules |
|---|---|
| **Visuals** | JumpCircles, Particles, Trails, HitParticles, HitBubbles, HandTrails, KillEffects, ScreenPulse, SwingAnimation, ViewModel |
| **Combat** | TargetESP, HitColor, Arrow, Predictions, CustomHitbox, Teams, FakePlayer |
| **World** | Zoom (hold **C**), Fullbright, AspectRatio, CustomFog, TimeChanger, CustomSky, NorthernLights, ColorGrading, ItemPhysics, BlockOverlay, ToggleSprint, AutoRespawn |
| **HUD** | HUD (watermark and module list), TargetHUD, Crosshair, TotemCounter, DamageFeed, CombatNotifier, AttackIndicator, PerformanceHUD |
| **Misc** | Interface |
| **NeuroAura** | the AI chat (see below) |

Notes on a few modules:

- **Teams** marks teammates (same scoreboard team, name colour, tab-list colour, dyed-armour colour, or a manual list)
  so target modules skip them. Commands: `/teams add <name>`, `/teams remove <name>`, `/teams list`, `/teams clear`.
- **FakePlayer** spawns a dummy that exists only on your screen, with health, armour, knockback, a death animation and optional
  instant respawn, for testing combat modules. Commands: `/fake spawn [name]`, `/fake remove`, `/fake heal`.
- **Predictions**, **Arrow** and **CustomHitbox** only draw information; **ToggleSprint** and **AutoRespawn** automate key presses.
  Some servers do not allow such helpers, so check the rules of the server you play on.

## NeuroAura (AI chat)

Open the **NeuroAura** tab, press **Connection**, and enter:

- the **API address** of any OpenAI-compatible server (OpenAI, OpenRouter, Groq, Ollama, LM Studio, ...),
- an **API key** (not needed for a server on your own computer),
- the **model name**.

Then just write what you want, for example *"red fog and hearts when I hit"* or *"make the crosshair white"*.
The AI answers in a sentence or two and changes the settings of the normal modules, the same ones you see in the menu.
It remembers the last 12 messages, every change is saved as a preset, and **Undo** reverts the newest one.
With **Auto-apply** off it shows the changes first and waits for **Apply**.

**Privacy.** Only what you type in the chat, the recent conversation, and a list of module settings with their current
values are sent, and only to the address you configured. No world data, coordinates or account details are sent.
The key is stored in plain text in `neurocanvas/ai.json` (owner-only permissions on Linux and macOS) and is never part of presets or share codes.

## Files

All files are created inside the game directory in `neurocanvas/`:

| File | Content |
|---|---|
| `modules.json` | enabled modules, settings, key binds, HUD positions |
| `presets/*.json` | saved presets |
| `friends.json` | the manual Teams list |
| `ai.json` | AI connection (address, key, model, Auto-apply) |

## Build

```
./gradlew build
```

The jar appears in `build/libs/`. Requires JDK 17.

## Compatibility

Developed for Minecraft 1.20.1 with Fabric API 0.92.x. It uses mixins for the crosshair, hand rendering, field of view, fog,
sky, time and dropped items, so other mods that change the same things may conflict.

## License

MIT, see [LICENSE](LICENSE). Bundled fonts (Inter, Nunito, Montserrat) are under the SIL Open Font License 1.1, see [licenses](licenses).
This project is not affiliated with Mojang or Microsoft.
