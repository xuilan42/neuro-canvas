package com.example.neurocanvas.client;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.example.neurocanvas.ai.AiConfig;
import com.example.neurocanvas.gui.ClickGui;
import com.example.neurocanvas.module.HandTrails;
import com.example.neurocanvas.module.HitBubbles;
import com.example.neurocanvas.module.HitColor;
import com.example.neurocanvas.module.HitParticles;
import com.example.neurocanvas.module.Arrow;
import com.example.neurocanvas.module.AspectRatio;
import com.example.neurocanvas.module.AttackIndicator;
import com.example.neurocanvas.module.AutoRespawn;
import com.example.neurocanvas.module.BlockOverlay;
import com.example.neurocanvas.module.ColorGrading;
import com.example.neurocanvas.module.CombatNotifier;
import com.example.neurocanvas.module.CombatStats;
import com.example.neurocanvas.module.Cooldowns;
import com.example.neurocanvas.module.Comfort;
import com.example.neurocanvas.module.Cosmetics;
import com.example.neurocanvas.module.CropInfo;
import com.example.neurocanvas.module.Crosshair;
import com.example.neurocanvas.module.CustomFog;
import com.example.neurocanvas.module.CustomHitbox;
import com.example.neurocanvas.module.CustomSky;
import com.example.neurocanvas.module.DamageFeed;
import com.example.neurocanvas.module.EffectsHud;
import com.example.neurocanvas.module.Esp;
import com.example.neurocanvas.module.FakePlayer;
import com.example.neurocanvas.module.FreeCam;
import com.example.neurocanvas.module.FreeLook;
import com.example.neurocanvas.module.Fullbright;
import com.example.neurocanvas.module.Hud;
import com.example.neurocanvas.module.InfoHud;
import com.example.neurocanvas.module.Interface;
import com.example.neurocanvas.module.ItemPhysics;
import com.example.neurocanvas.module.KillEffects;
import com.example.neurocanvas.module.NorthernLights;
import com.example.neurocanvas.module.NoRender;
import com.example.neurocanvas.module.PerformanceHud;
import com.example.neurocanvas.module.Predictions;
import com.example.neurocanvas.module.JumpCircles;
import com.example.neurocanvas.module.Module;
import com.example.neurocanvas.module.ModuleManager;
import com.example.neurocanvas.module.NameTags;
import com.example.neurocanvas.module.NeuroAura;
import com.example.neurocanvas.module.Particles;
import com.example.neurocanvas.module.PingWheel;
import com.example.neurocanvas.module.RenderEnv;
import com.example.neurocanvas.module.ScreenPulse;
import com.example.neurocanvas.module.SwingAnimation;
import com.example.neurocanvas.module.Teams;
import com.example.neurocanvas.module.TargetEsp;
import com.example.neurocanvas.module.TargetHud;
import com.example.neurocanvas.module.TimeChanger;
import com.example.neurocanvas.module.TotemCounter;
import com.example.neurocanvas.module.ToggleSprint;
import com.example.neurocanvas.module.Trails;
import com.example.neurocanvas.module.ViewModel;
import com.example.neurocanvas.module.VolumetricFog;
import com.example.neurocanvas.module.Zoom;
import com.example.neurocanvas.module.WorldState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fully client-side: works in singleplayer and on any server, no permissions or server mod needed. */
public class NeuroCanvasClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("neurocanvas");
    private static ModuleManager modules;

    private static final WorldState state = new WorldState();
    private KeyBinding openGui;
    private boolean pingKeyDown;

    public static WorldState state() {
        return state;
    }

    public static ModuleManager modules() {
        return modules;
    }

    @Override
    public void onInitializeClient() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("neurocanvas");
        modules = new ModuleManager(dir.resolve("modules.json"));
        modules.register(new TargetEsp());
        modules.register(new JumpCircles());
        modules.register(new Particles(dir.resolve("particles")));
        modules.register(new Trails());
        modules.register(new HitParticles());
        modules.register(new HitBubbles());
        modules.register(new HitColor());
        modules.register(new Crosshair());
        modules.register(new NeuroAura(AiConfig.load(dir.resolve("ai.json"))));
        modules.register(new PerformanceHud());
        modules.register(new CombatStats());
        modules.register(new InfoHud());
        modules.register(new EffectsHud());
        modules.register(new Cooldowns());
        modules.register(new ToggleSprint());
        modules.register(new AutoRespawn());
        modules.register(new ColorGrading());
        modules.register(new ScreenPulse());
        modules.register(new CustomSky());
        modules.register(new NorthernLights());
        modules.register(new ItemPhysics());
        modules.register(new BlockOverlay());
        modules.register(new HandTrails());
        modules.register(new KillEffects());
        modules.register(new Predictions());
        modules.register(new CustomHitbox());
        FakePlayer fakePlayer = new FakePlayer();
        modules.register(fakePlayer);
        Cosmetics cosmetics = new Cosmetics(dir.resolve("cosmetics"));
        modules.register(cosmetics);
        modules.register(new com.example.neurocanvas.module.Shimmer());
        modules.register(new com.example.neurocanvas.module.Friends(dir.resolve("online-friends.json")));
        modules.register(new PingWheel());
        modules.register(new com.example.neurocanvas.module.Showcase());
        modules.register(new com.example.neurocanvas.module.WeatherChanger());
        modules.register(new com.example.neurocanvas.module.Avatars(dir.resolve("avatars")));
        Path wardrobe = dir.resolve("wardrobe");
        SharedStore.init(dir.resolve("shared.json"));
        modules.register(new com.example.neurocanvas.module.Capes(wardrobe));
        modules.register(new com.example.neurocanvas.module.Wings(wardrobe));
        modules.register(new com.example.neurocanvas.module.Hats(wardrobe));
        modules.register(new com.example.neurocanvas.module.Outfits(wardrobe));
        modules.register(new com.example.neurocanvas.module.Pets(wardrobe));
        modules.register(new com.example.neurocanvas.module.Emotes(wardrobe));
        modules.register(new com.example.neurocanvas.module.Graffiti(wardrobe));
        modules.register(new com.example.neurocanvas.module.Market(wardrobe));
        modules.register(new VolumetricFog());
        modules.register(new CropInfo());
        modules.register(new FreeLook());
        modules.register(new FreeCam());
        modules.register(new NoRender());
        modules.register(new Comfort());
        modules.register(new Esp());
        NameTags nameTags = new NameTags();
        modules.register(nameTags);
        Teams teams = new Teams(dir.resolve("friends.json"));
        modules.register(teams);
        modules.register(new DamageFeed());
        modules.register(new CombatNotifier());
        modules.register(new AttackIndicator());
        modules.register(new Arrow());
        modules.register(new Zoom());
        modules.register(new Fullbright());
        modules.register(new AspectRatio());
        modules.register(new TotemCounter());
        modules.register(new CustomFog());
        modules.register(new TimeChanger());
        modules.register(new SwingAnimation());
        modules.register(new ViewModel());
        modules.register(new Hud());
        modules.register(new TargetHud());
        Interface ui = new Interface(dir.resolve("themes"), dir.resolve("theme-servers.json"));
        modules.register(ui);
        try {
            modules.load();
            if (Interface.checkUpdates()) {
                FabricLoader.getInstance().getModContainer("neurocanvas")
                        .ifPresent(mod -> Updater.check(mod.getMetadata().getVersion().getFriendlyString()));
            }
        } catch (IOException e) {
            LOGGER.error("Cannot load config", e);
        }

        openGui = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.neurocanvas.gui", GLFW.GLFW_KEY_RIGHT_SHIFT, "key.categories.misc"));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            state.tick(mc);
            pollKeybinds(mc);
            if (mc.player != null && Updater.takeNotice()) {
                mc.player.sendMessage(net.minecraft.text.Text.literal("[Neuro Canvas] Update " + Updater.latest()
                        + " is available. Click here to open the download page.").styled(style -> style
                        .withClickEvent(new net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.OPEN_URL, Updater.url()))
                        .withUnderline(true)), false);
            }
            while (openGui.wasPressed()) {
                mc.setScreen(new ClickGui());
            }
            PingWheel pings = modules.get(PingWheel.class);
            boolean pingDown = pings.key >= 0 && mc.getWindow() != null && InputUtil.isKeyPressed(mc.getWindow().getHandle(), pings.key);
            if (pingDown && !pingKeyDown && pings.enabled && mc.player != null && mc.currentScreen == null) {
                mc.setScreen(new com.example.neurocanvas.gui.PingWheelScreen(pings, PingWheel.capture(mc), pings.key));
            }
            pingKeyDown = pingDown;
            if (mc.player != null) {
                for (Module m : modules.all()) {
                    m.tickAlways(mc);
                    if (m.enabled) {
                        m.tick(mc);
                    }
                }
            }
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient) {
                state.onAttack(entity);
            }
            return ActionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient && fakePlayer.owns(entity)) {
                fakePlayer.onAttack(player);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                world.isClient && fakePlayer.owns(entity) ? ActionResult.FAIL : ActionResult.PASS);
        WorldRenderEvents.LAST.register(this::renderWorld);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(literal("fake")
                .then(literal("spawn")
                        .executes(ctx -> {
                            fakePlayer.enabled = true;
                            fakePlayer.respawnHere();
                            return 1;
                        })
                        .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                            fakePlayer.setName(StringArgumentType.getString(ctx, "name"));
                            fakePlayer.enabled = true;
                            fakePlayer.respawnHere();
                            return 1;
                        })))
                .then(literal("remove").executes(ctx -> {
                    fakePlayer.enabled = false;
                    return 1;
                }))
                .then(literal("heal").executes(ctx -> {
                    fakePlayer.healFully();
                    return 1;
                }))));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(literal("shaders")
                .executes(ctx -> {
                    for (String line : com.example.neurocanvas.fx.Shaders.compileAll(false)) {
                        say("[Shaders] " + line);
                    }
                    return 1;
                })
                .then(literal("recompile").executes(ctx -> {
                    for (String line : com.example.neurocanvas.fx.Shaders.compileAll(true)) {
                        say("[Shaders] " + line);
                    }
                    return 1;
                }))));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(literal("theme")
                .then(literal("list").executes(ctx -> {
                    StringBuilder names = new StringBuilder();
                    for (com.example.neurocanvas.gui.ThemeDef d : com.example.neurocanvas.gui.Themes.all()) {
                        names.append(names.length() == 0 ? "" : ", ").append(d.name());
                    }
                    say("[Theme] " + names);
                    return 1;
                }))
                .then(literal("use").then(argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                    ui.useThemeByName(StringArgumentType.getString(ctx, "name"));
                    say("[Theme] selected " + Interface.baseThemeName());
                    return 1;
                })))
                .then(literal("reload").executes(ctx -> {
                    ui.reloadFromCommand();
                    say("[Theme] themes reloaded");
                    return 1;
                }))
                .then(literal("image").then(argument("path", StringArgumentType.greedyString()).executes(ctx -> {
                    say("[Theme] " + ui.themeFromImage(java.nio.file.Path.of(StringArgumentType.getString(ctx, "path").replace("\"", ""))));
                    return 1;
                })))
                .then(literal("server")
                        .executes(ctx -> {
                            String key = com.example.neurocanvas.module.NameTags.serverAddress(MinecraftClient.getInstance());
                            if (key == null) {
                                say("[Theme] join a multiplayer server first");
                                return 0;
                            }
                            try {
                                com.example.neurocanvas.gui.Themes.bindServer(key, Interface.baseThemeName());
                                say("[Theme] " + key + " now uses \"" + Interface.baseThemeName() + "\" when Auto theme is By server");
                            } catch (java.io.IOException e) {
                                say("[Theme] could not save: " + e.getMessage());
                            }
                            return 1;
                        })
                        .then(literal("clear").executes(ctx -> {
                            String key = com.example.neurocanvas.module.NameTags.serverAddress(MinecraftClient.getInstance());
                            try {
                                if (key != null) {
                                    com.example.neurocanvas.gui.Themes.bindServer(key, null);
                                }
                                say("[Theme] server theme cleared");
                            } catch (java.io.IOException e) {
                                say("[Theme] could not save: " + e.getMessage());
                            }
                            return 1;
                        })))));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(literal("volfog")
                .then(literal("status").executes(ctx -> {
                    say("[VolumetricFog] " + modules.get(VolumetricFog.class).status());
                    return 1;
                }))
                .then(literal("retry").executes(ctx -> {
                    modules.get(VolumetricFog.class).retry();
                    say("[VolumetricFog] trying again");
                    return 1;
                }))));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(literal("cosmetics")
                .then(literal("reload").executes(ctx -> {
                    cosmetics.rescan();
                    say("[Cosmetics] model list reloaded");
                    return 1;
                }))
                .then(literal("info").executes(ctx -> {
                    say("[Cosmetics] " + cosmetics.info());
                    return 1;
                }))));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(literal("neurotag")
                .then(literal("status").executes(ctx -> {
                    say("[NameTags] " + (nameTags.enabled ? "on" : "off") + ", " + nameTags.status()
                            + (nameTags.room().isEmpty() ? "" : ", room \"" + nameTags.room() + "\""));
                    return 1;
                }))
                .then(literal("room").then(literal("off").executes(ctx -> {
                    nameTags.setRoom("");
                    say("[NameTags] back to matching by server address");
                    return 1;
                })).then(argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    say(nameTags.setRoom(name) ? "[NameTags] room \"" + name.toLowerCase() + "\": everyone who types the same room name sees each other, whatever server they are on"
                            : "[NameTags] a room name is 3-40 letters, digits, _ or -");
                    return 1;
                })))));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(literal("cloudcfg")
                .then(literal("put").executes(ctx -> {
                    say("[Cloud] uploading...");
                    CloudConfigs.upload(modules.exportCode()).whenComplete((code, error) -> MinecraftClient.getInstance().execute(() -> {
                        if (error != null) {
                            say("[Cloud] upload failed: " + CloudConfigs.reason(error));
                        } else {
                            MinecraftClient.getInstance().keyboard.setClipboard(code);
                            say("[Cloud] short code " + code + " (copied). Send it to a friend, they run /cloudcfg get " + code);
                        }
                    }));
                    return 1;
                }))
                .then(literal("get").then(argument("code", StringArgumentType.word()).executes(ctx -> {
                    String code = StringArgumentType.getString(ctx, "code");
                    CloudConfigs.download(code).whenComplete((data, error) -> MinecraftClient.getInstance().execute(() -> {
                        if (error != null) {
                            say("[Cloud] " + CloudConfigs.reason(error));
                            return;
                        }
                        try {
                            modules.importCode(data);
                            say("[Cloud] loaded the config " + code.toUpperCase());
                        } catch (IOException e) {
                            say("[Cloud] import failed: " + e.getMessage());
                        }
                    }));
                    return 1;
                })))));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(literal("teams")
                .then(literal("add").then(argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    say(teams.addFriend(name) ? "Added " + name + " to your team list" : name + " is already on the list");
                    return 1;
                })))
                .then(literal("remove").then(argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    say(teams.removeFriend(name) ? "Removed " + name : name + " is not on the list");
                    return 1;
                })))
                .then(literal("list").executes(ctx -> {
                    say(teams.friends().isEmpty() ? "Team list is empty" : "Team list: " + String.join(", ", teams.friends()));
                    return 1;
                }))
                .then(literal("clear").executes(ctx -> {
                    teams.clearFriends();
                    say("Team list cleared");
                    return 1;
                }))));
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            for (Module m : modules.all()) {
                if (m.enabled) {
                    m.renderHud(ctx, mc);
                }
            }
        });
    }

    private static void say(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal("[Teams] " + message), false);
        }
    }

    private void pollKeybinds(MinecraftClient mc) {
        if (mc.currentScreen != null || mc.getWindow() == null) {
            return;
        }
        long window = mc.getWindow().getHandle();
        for (Module m : modules.all()) {
            if (m.key < 0 || m.customKey) {
                continue;
            }
            boolean down = InputUtil.isKeyPressed(window, m.key);
            if (m.hold) {
                m.enabled = down;
            } else if (down && !m.keyDown) {
                m.enabled = !m.enabled;
            }
            m.keyDown = down;
        }
    }

    private void renderWorld(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return;
        }
        modules.get(VolumetricFog.class).pass(ctx);
        Vec3d cam = ctx.camera().getPos();
        MatrixStack matrices = ctx.matrixStack();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        RenderEnv env = new RenderEnv(matrices.peek().getPositionMatrix(), ctx.tickDelta(),
                (System.currentTimeMillis() % 1_000_000L) / 1000.0, ctx.camera().getYaw(), ctx.camera().getPitch(), mc, state);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        for (Module m : modules.all()) {
            if (m.enabled) {
                // Every module starts from the same known state, whatever the previous one left behind.
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableCull();
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(true);
                m.renderWorld(env);
            }
        }
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        matrices.pop();
    }
}
