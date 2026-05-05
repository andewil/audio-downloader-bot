package com.example.audiodownloaderbot.audio;

public class AudioTooLargeException extends RuntimeException {

    public AudioTooLargeException(String message) {
        super(message);
    }
}
