package com.andewil.audiodownloaderbot.audio;

public class AudioNotFoundException extends RuntimeException {

    public AudioNotFoundException(String message) {
        super(message);
    }
}
