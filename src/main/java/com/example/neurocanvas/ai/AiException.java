package com.example.neurocanvas.ai;

/** A problem talking to the model or understanding its answer, with a message safe to show the player. */
public final class AiException extends RuntimeException {
    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
