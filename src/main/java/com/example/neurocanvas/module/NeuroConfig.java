package com.example.neurocanvas.module;

import com.example.neurocanvas.ai.AiClient;
import com.example.neurocanvas.ai.AiConfig;
import com.example.neurocanvas.ai.AiException;
import com.example.neurocanvas.ai.AiPlan;
import com.example.neurocanvas.ai.PlanParser;
import com.example.neurocanvas.ai.PromptBuilder;
import com.example.neurocanvas.client.NeuroCanvasClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The AI module, used as a chat. You write what you want, the model answers briefly and proposes changes
 * (changes to the settings of other modules). Changes are applied at once or on confirmation, saved as a preset,
 * and the newest one can always be undone.
 */
public final class NeuroConfig extends Module {
    public enum Status { NONE, PENDING, APPLIED, UNDONE, DISMISSED }

    /** One line of the chat. AI messages may carry a plan with its own apply/undo state. */
    public static final class ChatMessage {
        public final boolean fromUser;
        public final String text;
        public final boolean error;
        public String prompt = "";
        public AiPlan plan;
        public Status status = Status.NONE;
        public String preset = "";
        private Map<String, Map<String, Double>> before;

        ChatMessage(boolean fromUser, String text, boolean error) {
            this.fromUser = fromUser;
            this.text = text;
            this.error = error;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("neurocanvas");
    private static final int MEMORY = 12;

    private final AiConfig config;
    private final AiClient client = new AiClient();
    private final List<ChatMessage> chat = new ArrayList<>();
    private boolean working;
    private String attachedUrl;
    private String attachedName = "";
    private final com.example.neurocanvas.ai.VoiceRecorder recorder = new com.example.neurocanvas.ai.VoiceRecorder();
    private boolean transcribing;

    public NeuroConfig(AiConfig config) {
        super("NeuroConfig", Category.AI, "Chat with an AI that answers briefly and configures the client for you", true);
        this.config = config;
        aiControllable = false;
    }

    public AiConfig config() {
        return config;
    }

    public List<ChatMessage> chat() {
        return chat;
    }

    public boolean working() {
        return working;
    }

    // ---- picture and voice ------------------------------------------------------------------

    public String attachedName() {
        return attachedUrl == null ? "" : attachedName;
    }

    public boolean attached() {
        return attachedUrl != null;
    }

    public void clearAttachment() {
        attachedUrl = null;
        attachedName = "";
    }

    private void fail(String message) {
        chat.add(new ChatMessage(false, message, true));
    }

    /** Attaches a picture to the next message. The model must understand images (a vision model). */
    public void attach(java.nio.file.Path file) {
        try {
            attachedUrl = com.example.neurocanvas.ai.ImagePrep.dataUrl(file);
            attachedName = file.getFileName().toString();
        } catch (java.io.IOException e) {
            fail("Could not use the picture: " + e.getMessage());
        }
    }

    /** The Attach button: a picture path on the clipboard first, otherwise the newest screenshot. */
    public void attachFromClipboardOrScreenshot() {
        try {
            String clip = MinecraftClient.getInstance().keyboard.getClipboard().trim().replaceAll("^\"|\"$", "");
            if (!clip.isEmpty() && clip.length() < 400) {
                java.nio.file.Path p = java.nio.file.Path.of(clip);
                if (java.nio.file.Files.isRegularFile(p)) {
                    attach(p);
                    return;
                }
            }
        } catch (RuntimeException ignored) {
            // The clipboard is not a path.
        }
        java.nio.file.Path shot = com.example.neurocanvas.ai.ImagePrep.latestScreenshot();
        if (shot == null) {
            fail("No picture: copy the path of a file, drop a file on the window, or take a screenshot with F2");
            return;
        }
        attach(shot);
    }

    public boolean recording() {
        return recorder.recording();
    }

    public int recordingSeconds() {
        return recorder.seconds();
    }

    public boolean transcribing() {
        return transcribing;
    }

    /** The Mic button: the first press starts recording, the second stops it and turns the speech into text for the input box. */
    public void toggleVoice(java.util.function.Consumer<String> onText) {
        if (transcribing) {
            return;
        }
        if (!recorder.recording()) {
            try {
                recorder.start();
            } catch (com.example.neurocanvas.ai.AiException e) {
                fail(e.getMessage());
            }
            return;
        }
        byte[] wav = recorder.stop();
        if (wav == null) {
            fail("The recording was too short");
            return;
        }
        if (!config.ready()) {
            fail("Not configured yet. Open Connection and set the API address, key and model.");
            return;
        }
        transcribing = true;
        client.transcribe(config, wav).whenComplete((text, throwable) -> MinecraftClient.getInstance().execute(() -> {
            transcribing = false;
            if (throwable != null) {
                Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                fail(cause instanceof com.example.neurocanvas.ai.AiException ai ? ai.getMessage() : "Speech to text failed: " + cause.getMessage());
                return;
            }
            onText.accept(text);
        }));
    }

    public void clearChat() {
        chat.clear();
    }

    // ---- the chat flow --------------------------------------------------------------------

    /** The last messages as the model should see them; AI turns note whether their changes were applied. */
    private List<String[]> conversation() {
        List<ChatMessage> tail = new ArrayList<>();
        for (ChatMessage m : chat) {
            if (!m.error) {
                tail.add(m);
            }
        }
        tail = tail.subList(Math.max(0, tail.size() - MEMORY), tail.size());
        List<String[]> out = new ArrayList<>();
        for (ChatMessage m : tail) {
            if (out.isEmpty() && !m.fromUser) {
                continue;
            }
            String content = m.text;
            if (!m.fromUser && m.plan != null) {
                content += switch (m.status) {
                    case APPLIED -> " [changes applied]";
                    case PENDING -> " [changes proposed, not applied]";
                    case UNDONE -> " [changes were undone by the player]";
                    case DISMISSED -> " [changes were dismissed by the player]";
                    default -> "";
                };
            }
            out.add(new String[] {m.fromUser ? "user" : "assistant", content});
        }
        return out;
    }

    public void send(String text) {
        String t = text.trim();
        if (t.isEmpty() || working) {
            return;
        }
        String image = attachedUrl;
        chat.add(new ChatMessage(true, image == null ? t : t + "  [picture: " + attachedName + "]", false));
        attachedUrl = null;
        attachedName = "";
        if (!config.ready()) {
            chat.add(new ChatMessage(false, "Not configured yet. Open Connection and set the API address, key and model.", true));
            return;
        }
        working = true;
        String system = PromptBuilder.system(NeuroCanvasClient.modules());
        List<String[]> conv = conversation();
        if (image != null && !conv.isEmpty()) {
            String[] last = conv.get(conv.size() - 1);
            conv.set(conv.size() - 1, new String[] {last[0], t + "\n(The player attached a picture: use its colours, mood and style where it helps.)", image});
        }
        client.complete(config, system, conv).whenComplete((reply, throwable) ->
                MinecraftClient.getInstance().execute(() -> {
                    working = false;
                    try {
                        if (throwable != null) {
                            Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                            throw cause instanceof AiException ai ? ai : new AiException("Request failed: " + cause.getMessage(), cause);
                        }
                        AiPlan plan = PlanParser.parse(reply, NeuroCanvasClient.modules());
                        String say = plan.reply.isBlank() ? (plan.isEmpty() ? "..." : "Done.") : plan.reply;
                        ChatMessage m = new ChatMessage(false, say, false);
                        m.prompt = t;
                        chat.add(m);
                        if (!plan.isEmpty()) {
                            m.plan = plan;
                            m.status = Status.PENDING;
                            if (config.autoApply) {
                                apply(m);
                            }
                        }
                    } catch (AiException e) {
                        LOGGER.warn("NeuroConfig request failed: {}", e.getMessage());
                        chat.add(new ChatMessage(false, e.getMessage(), true));
                    }
                }));
    }

    /** Applies a proposed plan, remembers how to undo it, and saves the new state as a preset. */
    public void apply(ChatMessage m) {
        if (m.status != Status.PENDING || m.plan == null) {
            return;
        }
        ModuleManager modules = NeuroCanvasClient.modules();
        m.before = modules.snapshot();
        try {
            for (AiPlan.ModuleChange change : m.plan.changes) {
                if (change.enable != null) {
                    change.module.enabled = change.enable;
                }
                for (AiPlan.SettingChange s : change.settings) {
                    s.setting().set(s.newValue());
                }
            }
            modules.save();
            m.preset = uniquePreset(modules, m.plan.name);
            modules.savePreset(m.preset);
            m.status = Status.APPLIED;
        } catch (IOException e) {
            LOGGER.error("Could not apply the AI plan", e);
            chat.add(new ChatMessage(false, "Applied, but saving failed: " + e.getMessage(), true));
            m.status = Status.APPLIED;
        }
    }

    public void dismiss(ChatMessage m) {
        if (m.status == Status.PENDING) {
            m.status = Status.DISMISSED;
        }
    }

    private ChatMessage lastApplied() {
        for (int i = chat.size() - 1; i >= 0; i--) {
            if (chat.get(i).status == Status.APPLIED) {
                return chat.get(i);
            }
        }
        return null;
    }

    /** Only the newest applied change can be undone, so earlier ones are never rolled back out of order. */
    public boolean canUndo(ChatMessage m) {
        return m.status == Status.APPLIED && m == lastApplied();
    }

    public void undo(ChatMessage m) {
        if (!canUndo(m) || m.before == null) {
            return;
        }
        ModuleManager modules = NeuroCanvasClient.modules();
        try {
            modules.apply(m.before);
            modules.save();
            if (!m.preset.isEmpty()) {
                modules.deletePreset(m.preset);
            }
        } catch (IOException e) {
            LOGGER.error("Could not undo the AI plan", e);
        }
        m.status = Status.UNDONE;
    }

    private static String uniquePreset(ModuleManager modules, String wanted) {
        String base = ModuleManager.cleanName(wanted);
        if (base.isEmpty()) {
            base = "AI";
        }
        List<String> existing = modules.presets();
        String name = base;
        for (int n = 2; existing.contains(name); n++) {
            name = base + " " + n;
        }
        return name;
    }
}
