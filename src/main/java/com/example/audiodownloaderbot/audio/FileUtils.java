package com.example.audiodownloaderbot.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class FileUtils {

    private FileUtils() {
    }

    static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Temporary files are best-effort cleanup only.
        }
    }
}
