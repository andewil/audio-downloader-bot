package com.andewil.audiodownloaderbot.audio;

/**
 * Signals that an audio source was found, but the bot could not process it.
 */
public class AudioProcessingException extends RuntimeException {

    /**
     * Creates an exception with a user-safe processing failure description.
     *
     * @param message processing failure description
     */
    public AudioProcessingException(String message) {
        super(message);
    }
}
