package com.example.neurocanvas.ai;

import java.io.ByteArrayOutputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/** Records the microphone (16 kHz, 16 bit, mono) until stopped, for at most a minute, and returns it as a WAV file. */
public final class VoiceRecorder {
    private static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    private static final int MAX_BYTES = 16000 * 2 * 60;

    private TargetDataLine line;
    private Thread thread;
    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
    private volatile boolean running;
    private long startedAt;

    public boolean recording() {
        return running;
    }

    public int seconds() {
        return running ? (int) ((System.currentTimeMillis() - startedAt) / 1000) : 0;
    }

    /** Opens the microphone. Throws AiException with a readable reason when there is none or it is not allowed. */
    public void start() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                throw new AiException("No microphone found");
            }
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(FORMAT);
            line.start();
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            throw new AiException("The microphone cannot be used: " + e.getMessage(), e);
        }
        pcm.reset();
        running = true;
        startedAt = System.currentTimeMillis();
        thread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            while (running && pcm.size() < MAX_BYTES) {
                int n = line.read(buffer, 0, buffer.length);
                if (n > 0) {
                    pcm.write(buffer, 0, n);
                }
            }
            running = false;
        }, "neurocanvas-voice");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops and returns the recording as a WAV file, or null when nothing was recorded. */
    public byte[] stop() {
        running = false;
        try {
            if (thread != null) {
                thread.join(500);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
        byte[] data = pcm.toByteArray();
        if (data.length < 16000) {
            return null;
        }
        try (AudioInputStream in = new AudioInputStream(new java.io.ByteArrayInputStream(data), FORMAT, data.length / 2);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            AudioSystem.write(in, javax.sound.sampled.AudioFileFormat.Type.WAVE, out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            return null;
        }
    }
}
