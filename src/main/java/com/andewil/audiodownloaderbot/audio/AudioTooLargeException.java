package com.andewil.audiodownloaderbot.audio;

public class AudioTooLargeException extends RuntimeException {

    public AudioTooLargeException(String message) {
        super(message);
    }
}
