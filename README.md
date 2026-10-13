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

- **Tabs** by category, each with a small icon (only the open tab shows its name; hover another one to see it), a **module list** on the left with on/off switches, and the **settings** of the selected module on the right.
- Every module can have a **key bind** (toggle or hold mode). Search with **Ctrl+F**.
- Colour settings open a full **colour picker** with alpha.
- **Configs** tab: save, load and delete presets, copy a **share code** to the clipboard, import a code, and edit the **HUD layout** by dragging elements. Elements snap to the left, centre or right (and top, middle, bottom) of the screen and keep that anchor when the window changes size. The module list can be sorted in Misc > Interface (longest, shortest, A to Z, Z to A, by category, ...).
- **Interface** (Misc tab): themes, 3 bundled fonts (Cyrillic supported) and animations: cards slide in when you change tab, the tab underline glides, hover and switches fade, the settings panel fades in. *Animations* turns them all off.
- The menu remembers the tab and module you were on. **Interface** can also derive the whole look from one accent colour, apply that accent to every module colour (a preset "Before accent" is saved first), and set the open animation, background and click sounds.
- - **Cloud configs:** *Upload: get short code* in the Configs tab (or `/cloudcfg put`) stores your settings on the project's server and copies a short code like `NRNA7VF3`; send it to a friend, who copies it and presses *Download: code from clipboard* (or runs `/cloudcfg get NRNA7VF3`). The code is the only key: anyone who has it can load that config, so it is not a place for secrets (AI keys are never part of a config). Limits: 12 uploads per hour per user, configs are kept for 90 days after they were last used.
- **Updates:** on start the mod asks GitHub's public API once whether a newer release exists and tells you in chat and in the menu. Turn it off with *Check for updates* in Interface.

## Modules

| Category | Modules |
|---|---|
| **Visuals** | NameTags, JumpCircles, Particles, Trails, HitParticles, HitBubbles, HandTrails, KillEffects, ScreenPulse, SwingAnimation, ViewModel |
| **Cosmetics** | Model (your own 3D model), Avatar (animated Blockbench avatars), Shimmer (armour and held items shift through rainbow or gradient colours) |
| **Combat** | ESP, TargetESP, HitColor, Arrow, Predictions, CustomHitbox, Teams, FakePlayer |
| **World** | FreeLook, FreeCam, NoRender, Comfort, CropInfo, Zoom (hold **C**), Fullbright, AspectRatio, CustomFog, VolumetricFog, TimeChanger, WeatherChanger, CustomSky, NorthernLights, ColorGrading, ItemPhysics, BlockOverlay, ToggleSprint, AutoRespawn |
| **HUD** | HUD (watermark and module list), TargetHUD, Crosshair, TotemCounter, DamageFeed, CombatNotifier, AttackIndicator, PerformanceHUD, CombatStats, InfoHUD, EffectsHUD, Cooldowns |
| **Friends** | Friends, PingWheel, NameTags (everything that talks to other users) |
| **Misc** | Interface (themes, fonts, effects) |
| **NeuroAura** | the AI chat (see below) |

Notes on a few modules:

- **TargetESP** has 11 styles (the *Style* setting): Ring, Ghost (glowing orbs with trails), Points, Rhomb (a spinning diamond), Crystals (orbiting 3D gems), Helix (two glowing strands), Pulse (expanding rings), Spiral, Halo (a ring above the head), Pyramid (a cone above the head) and Marker (rotating corner brackets). Colours, size, speed, count and alpha apply to all of them.
- **BlockOverlay** has animated styles (*Style*): Waves (sea-like waves running over the faces), Scan (a bright band sweeping up and down), Ripple (rings spreading from the point you look at), Liquid (a fill that rises and falls with a wavy surface), Chase (light dashes running along the edges), Sparkles (sparks rising from the edges) and Checker (a pulsing checkerboard); *Style speed* sets the pace. It can also shimmer: *Color mode* Rainbow or Gradient (between the outline/fill colour and *Gradient color*), with *Color speed* and *Color flow* (how much the colour differs along the outline).
- **Shimmer** tints armour pieces and held items: *Mode* Rainbow, Gradient (two colours) or Pulse, *Speed*, *Strength*, *Flow between pieces* (the colour runs from the helmet to the boots), *Who* (only you or everyone), and a switch for every armour piece and for held items. It only changes colours you see; other players do not.
- **NorthernLights** is drawn by its own shader: several wavy curtains of thin vertical rays that brighten at the bottom edge and fade upwards (green to blue-violet by default), with soft ends, slow drifting and a smooth fade in at dusk with *Only at night*. *Ray sharpness*, *Curtain height*, distance, width and the two colours are adjustable.
- **CustomSky** also shapes the horizon: *Horizon color* and *Horizon gradient* make the sky, the horizon and the fog fade into each other without a seam, and the orange sunrise/sunset fan is hidden unless *Sunset glow* is on.
- **Particles** are ambient particles around you: they appear at random spots inside *Spawn radius*, drift wherever they like (*Speed*, *Wander*, *Gravity*),
  bounce off blocks (*Bounciness*), and fade out when they get past *Fade-out radius* or too old (*Lifetime*). Shapes are drawn by a shader: glow (firefly), star, sparkle,
  dot, ring, diamond, heart, snowflake, or a mix. Colours: *Gradient* over their life, *Random mix* of the two colours, *Rainbow* or *Single*; plus *Twinkle* and *Glow blending*.
- **Shaders:** the mod builds its shader programs (menu blur and effects, volumetric fog, particle shapes) once and reuses them. *Precompile shaders* in Interface (or `/shaders`) compiles
  all of them in advance, which avoids a short freeze the first time an effect appears, and prints a report; `/shaders recompile` rebuilds them. If one cannot be built, the report shows why.
  Particles then fall back to plain squares instead of disappearing. *Precompile shaders on join* does it automatically.
- **Shader visuals:** the world visuals are drawn by the mod's own shader: lines (hitboxes, predictions, kill bolts, block outline) are glowing soft-edged beams of constant screen width,
  boxes have a glowing rim on every face, the target ring, jump ripples and trails are drawn as smooth glowing shapes, hand trails and hit colour use it too. *Shader visuals* and
  *Visual glow* in Interface switch it off or tune the glow; if the shader cannot be built the old basic shapes are used instead.
- **CropInfo** shows, for the plant you look at (wheat, carrots, potatoes, beetroots, melon and pumpkin stems, nether wart, cocoa, sweet berries, saplings),
  the growth in percent with a bar and about how long is left. The time is an average based on the game's own random-tick chances (set *Random tick speed*
  to your server's `randomTickSpeed`, default 3) and on the real moisture and neighbours of the crop; it also warns when there is too little light to grow.
- **Teams** marks teammates (same scoreboard team, name colour, tab-list colour, dyed-armour colour, or a manual list)
  so target modules skip them. Commands: `/teams add <name>`, `/teams remove <name>`, `/teams list`, `/teams clear`.
- **FakePlayer** spawns a dummy that exists only on your screen, with health, armour, knockback, a death animation and optional
  instant respawn, for testing combat modules. Commands: `/fake spawn [name]`, `/fake remove`, `/fake heal`.
- **WeatherChanger** shows clear sky, rain, a thunderstorm or a custom rain/thunder strength on your screen only (the server's weather does not change; snow instead of rain still depends on the biome).
- **Predictions** have looks to pick (every list ends with *None*, so you can keep only the landing marker or only the path): *Path style* Line, Dashed, Dots, Arrows (moving chevrons), Glow trail, Rainbow or Double, and *Landing style* Cross, Ring, Target, Diamond, Pulse or Pin; *Animation speed* sets the motion; the landing mark lies on the surface it hits (flat on the floor, upright on a wall). **Predictions** (also shows pearls, arrows, tridents and potions already in the air, with the time until they land), **Arrow**, **CustomHitbox** and **ESP** only draw information (**ESP** has three modes: *Box* a 3D box drawn over everything (only the faces and edges that turn towards you, the far ones stay hidden), *2D* a screen rectangle with a health bar, and *Outline* the game's glow outline in your colour; with *Through walls* off they only show what is in your line of sight. every world effect around entities (hit particles, bubbles, kill effects, hit colour, hand trails, predictions, hitboxes) follows one rule: if the camera can see the spot it is drawn over models, behind a wall it is hidden; switch off with *Draw effects over models* in Interface); **FreeCam** detaches the camera; **ToggleSprint** and **AutoRespawn** automate key presses.
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

## Model (your own 3D model, Cosmetics tab)

The **Model** module is a model that hovers behind you like a stand. Built-in: **Phantom**. Your own: put a folder into `neurocanvas/cosmetics/<name>/` with an
`.obj` model and `.png` textures, then `/cosmetics reload` and pick it in the module. An optional `cosmetic.json` can name the files:

```json
{ "model": "model.obj", "texture": "atlas.png", "partTextures": { "eye": "eyes.png" }, "yawOffset": 0, "flipFront": false }
```

Models are optimised when they load, so heavy ones stay cheap: **Triangle budget** (vertex clustering, every part simplified on its own so
small parts keep their shape) and **Texture size** (Original / 1024 / 512 / 256). `/cosmetics info` shows the result. Models live in
`neurocanvas/cosmetics/` (the **Open models folder** button opens it, **Reload model list** rescans it). A model in a T-pose gets a simple two-arm skeleton with elbows:
**Pose** (Relaxed, Ready, Guard, Point, ORA barrage) and, with **Strike on hit**, the arms punch in turns when you hit. Models are not part of this repository;
check the license of what you download. It is only visible on your own screen.

## Friends (the online tab)

Everything that connects you with other Neuro Canvas users lives in the **Friends** tab: **NameTags** (the plate over other users), **PingWheel** and the **Friends** list.
In the Friends module you set *Share with* (everyone, friends only or nobody), *Accept pings from* (everyone or friends only), and whether to show *when I am in the menu*
and *who is in the menu*. Below the switches there are two lists: **Online on this server** (tap the star to make someone a friend) and **My friends** (the green dot means online,
**x** removes); you can also type a name and press **Add**. The list is saved in `neurocanvas/online-friends.json`.

With *Friends only* your messages are addressed to your friends by name on the registry server, so nobody else receives them at all. While you have the Neuro Canvas menu open,
other users (who accept you) see "· in menu" on your plate. Everything here needs **NameTags** on, because it uses the same connection.

## PingWheel (marks like in co-op games)

Hold **V** by default (change it in the PingWheel module: the *Keybind* row, any key such as **G**): a wheel opens, move the cursor to a mark and let go of the key (or click). The mark appears on the block or entity you were
aiming at, up to 256 blocks away: a ring and a beam in the world, plus an icon on the screen with the distance and the name (it sticks to the screen edge when it is
off to the side). Outer ring: **Attention (!)**, **Question (?)**, **Go here**, **Danger**, **Look**, **Loot**, **Enemy**, **Help**. Inner ring: **Clear my pings**,
**Countdown** (a big 3-2-1-GO for everyone), **Wave** and **Dance** (play the animations `wave`/`emote_wave` and `dance`/`emote_dance` of your Avatar, if it has them).

- Other Neuro Canvas users on the same server see your pings and countdowns (and you see theirs): the messages go through the project's registry server, the same connection NameTags uses,
  so **NameTags must be on**. Only the spot you ping and your name are sent, never your position. Switches: *Share with other users*, *Show other users' pings*.
- Each user can have 3 pings at a time; they fade after *Lifetime*. The server limits how fast pings can be sent.

## Avatar (animated Blockbench avatars)

A small avatar system in the spirit of Figura, built into the mod. Make a model in **Blockbench** (cubes, bones, a texture and animations),
save it as `.bbmodel` with the texture embedded, and put it into `neurocanvas/avatars/<name>/model.bbmodel`. Press **Reload avatar list** in the Avatar module.
An example avatar (`Example`) is created the first time.

- **Mode:** *On me* puts the avatar where you are, facing your body direction, and can hide your own body (*Hide my own body*); *Stand* makes it follow you at a distance.
- **Animations play by their names:** `idle`, `walk`, `sprint`, `sneak`, `jump`, `fall` follow what you do; `attack` plays when you swing or hit, `hurt` when you take damage.
- **Keys:** an optional `avatar.json` next to the model binds keys to animations: `{ "model": "model.bbmodel", "scale": 1.0, "yaw": 0, "keys": { "G": "wave" } }`.
  An animation set to *once* plays once per press, *hold* plays while the key is held, *loop* toggles on and off.
- Supported: cubes with box UV or per-face UV, bone hierarchy with pivots and rotations, position, rotation and scale keyframes (linear or step). Not supported yet: Molang expressions in keyframes, mesh elements, Lua scripts.
- It is only drawn on your screen, other players do not see it. Models are not part of this repository.

## NameTags (plate over other users)

Shows a "NeuroAura" plate above other players who run the mod on the same server. The mod keeps one connection to the
project's presence server, so joining and leaving show up at once, it works on offline-mode servers, and one nick on two
servers stays two entries. Off by default. It sends only the server address and your nick, and nicks are not verified.
`/neurotag status` shows the connection state. If you and a friend join the same server through different addresses (a domain and an IP), or want to meet on different servers, both run `/neurotag room <secret-name>`: everyone with the same room name sees each other's plates, pings and status, whatever server they are on (`/neurotag room off` goes back to matching by address). Treat the room name like a password; pings are marks at coordinates, so they only make sense when you are in the same world. The **NameTag** switch of FakePlayer shows the plate over the dummy, to test its look alone. The server address is set when the mod is built (`registry_url` in `gradle.properties`, a comma separated list: the next address is only used while the first is unreachable);
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

Developed for Minecraft 1.20.1 with Fabric API 0.92.x and tested with Sodium, Indium and Lithium. Shader packs (Iris) are not tested: VolumetricFog pauses itself with them. It uses mixins for the crosshair, hand rendering, field of view, fog,
sky, time and dropped items, so other mods that change the same things may conflict.

## License

GNU GPL v3, see [LICENSE](LICENSE). Copyright (c) 2026 xuilan42. You may use, modify and share the code, but every copy or derived work must keep the copyright notice, state your changes, and be released under the same GPLv3 with its source code open. Taking the code into a closed or re-licensed project is not allowed. Bundled fonts (Inter, Nunito, Montserrat) are under the SIL Open Font License 1.1, see [licenses](licenses).
This project is not affiliated with Mojang or Microsoft.
