package com.andewil.audiodownloaderbot.audio;

import java.nio.file.Path;

public record DownloadedAudio(Path file, String filename) implements AutoCloseable {

    @Override
    public void close() {
        FileUtils.deleteQuietly(file);
    }
}
