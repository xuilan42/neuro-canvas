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
- The menu remembers the tab and module you were on. **Interface** can also derive the whole look from one accent colour, apply that accent to every module colour (a preset "Before accent" is saved first), and set the open animation, background and click sounds.
- **Updates:** on start the mod asks GitHub's public API once whether a newer release exists and tells you in chat and in the menu. Turn it off with *Check for updates* in Interface.

## Modules

| Category | Modules |
|---|---|
| **Visuals** | Cosmetics, NameTags, JumpCircles, Particles, Trails, HitParticles, HitBubbles, HandTrails, KillEffects, ScreenPulse, SwingAnimation, ViewModel |
| **Combat** | ESP, TargetESP, HitColor, Arrow, Predictions, CustomHitbox, Teams, FakePlayer |
| **World** | FreeLook, FreeCam, NoRender, Comfort, CropInfo, Zoom (hold **C**), Fullbright, AspectRatio, CustomFog, VolumetricFog, TimeChanger, CustomSky, NorthernLights, ColorGrading, ItemPhysics, BlockOverlay, ToggleSprint, AutoRespawn |
| **HUD** | HUD (watermark and module list), TargetHUD, Crosshair, TotemCounter, DamageFeed, CombatNotifier, AttackIndicator, PerformanceHUD, CombatStats, InfoHUD, EffectsHUD, Cooldowns |
| **Misc** | Interface (themes, fonts, effects) |
| **NeuroAura** | the AI chat (see below) |

Notes on a few modules:

- **CropInfo** shows, for the plant you look at (wheat, carrots, potatoes, beetroots, melon and pumpkin stems, nether wart, cocoa, sweet berries, saplings),
  the growth in percent with a bar and about how long is left. The time is an average based on the game's own random-tick chances (set *Random tick speed*
  to your server's `randomTickSpeed`, default 3) and on the real moisture and neighbours of the crop; it also warns when there is too little light to grow.
- **Teams** marks teammates (same scoreboard team, name colour, tab-list colour, dyed-armour colour, or a manual list)
  so target modules skip them. Commands: `/teams add <name>`, `/teams remove <name>`, `/teams list`, `/teams clear`.
- **FakePlayer** spawns a dummy that exists only on your screen, with health, armour, knockback, a death animation and optional
  instant respawn, for testing combat modules. Commands: `/fake spawn [name]`, `/fake remove`, `/fake heal`.
- **Predictions** (also shows pearls, arrows, tridents and potions already in the air, with the time until they land), **Arrow**, **CustomHitbox** and **ESP** only draw information (**ESP** has three modes: *Box* a 3D box drawn over everything (only the faces and edges that turn towards you, the far ones stay hidden), *2D* a screen rectangle with a health bar, and *Outline* the game's glow outline in your colour; with *Through walls* off they only show what is in your line of sight. every world effect around entities (hit particles, bubbles, kill effects, hit colour, hand trails, predictions, hitboxes) follows one rule: if the camera can see the spot it is drawn over models, behind a wall it is hidden; switch off with *Draw effects over models* in Interface); **FreeCam** detaches the camera; **ToggleSprint** and **AutoRespawn** automate key presses.
  Some servers do not allow such helpers, so check the rules of the server you play on.

**VolumetricFog** (experimental) draws its own fog with a shader after the world is rendered: it reads the depth buffer and blends a haze that is thicker near the
ground and thins out with height, with an optional sun glow. It does not use the game's fog, so it also works with Sodium; it pauses underwater and with Iris shader packs, and
turns itself off (keeping the normal fog) if the shader cannot be built. `/volfog status` and `/volfog retry` show and reset its state.

## Themes

A theme is a palette plus a **style**: corner radius, borders, glow, background blur, an animated shader effect and the switch look.
Built in: Mint, Violet, Ocean, Rose, Light, Mono (classic) and **Glass** (blurred translucent panels), **Terminal** (monospace, CRT scanlines, `[x]` switches),
**Retro** (blocky Minecraft look), **Neon** (glowing outlines and a moving grid), **Paper**, **Aurora** (flowing gradient), **High Contrast**, **Color-blind safe**, and the
packs **Sunset**, **Frost**, **Blood Moon**. Pick one in *Interface*; menu blur and effects are drawn by shaders (switch them off with *Shader effects*).

- **Theme pack:** *Apply theme pack* also sets fog, sky, time, colour grading and the colours of several modules to match (the old look is saved as preset "Before theme pack").
- **Auto theme:** by day/night, or per server (`/theme server` binds the current server to the selected theme, `/theme server clear` removes it).
- **Dynamic accent:** the accent colour follows your health, the time of day or the biome, smoothly.
- **Theme from image:** put a picture at `neurocanvas/theme-image.png` (or use `/theme image <path>`) and the palette is built from its colours.
- **Editor:** the *Editor:* settings in Interface change colours and style live (the menu is the preview); *Save editor theme* writes a file into `neurocanvas/themes/`.
  Theme files are plain JSON (`palette`, `style`, optional `pack`), can be shared with *Copy theme code* / *Import theme code from clipboard*, and reloaded with `/theme reload`.
  Other commands: `/theme list`, `/theme use <name>`.

## Cosmetics (your own 3D model)

A model that hovers behind you like a stand. Built-in: **Phantom**. Your own: put a folder into `neurocanvas/cosmetics/<name>/` with an
`.obj` model and `.png` textures, then `/cosmetics reload` and pick it in the module. An optional `cosmetic.json` can name the files:

```json
{ "model": "model.obj", "texture": "atlas.png", "partTextures": { "eye": "eyes.png" }, "yawOffset": 0, "flipFront": false }
```

Models are optimised when they load, so heavy ones stay cheap: **Triangle budget** (vertex clustering, every part simplified on its own so
small parts keep their shape) and **Texture size** (Original / 1024 / 512 / 256). `/cosmetics info` shows the result. Models live in
`neurocanvas/cosmetics/` (the **Open models folder** button opens it, **Reload model list** rescans it). A model in a T-pose gets a simple two-arm skeleton with elbows:
**Pose** (Relaxed, Ready, Guard, Point, ORA barrage) and, with **Strike on hit**, the arms punch in turns when you hit. Models are not part of this repository;
check the license of what you download. It is only visible on your own screen.

## NameTags (plate over other users)

Shows a "NeuroAura" plate above other players who run the mod on the same server. The mod keeps one connection to the
project's presence server, so joining and leaving show up at once, it works on offline-mode servers, and one nick on two
servers stays two entries. Off by default. It sends only the server address and your nick, and nicks are not verified.
`/neurotag status` shows the connection state. The **NameTag** switch of FakePlayer shows the plate over the dummy, to test its look alone. The server address is set when the mod is built (`registry_url` in `gradle.properties`);
the server itself is not part of this repository.

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
