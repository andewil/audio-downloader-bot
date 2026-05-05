package com.example.audiodownloaderbot.audio;

import com.example.audiodownloaderbot.config.BotProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

@Service
public class AudioDownloadService {

    private static final Set<String> TELEGRAM_AUDIO_EXTENSIONS = Set.of(".mp3", ".m4a");
    private static final List<String> AUDIO_EXTENSIONS = List.of(".mp3", ".m4a", ".aac", ".ogg", ".oga", ".opus", ".wav", ".flac", ".webm");

    private final BotProperties properties;
    private final HttpClient httpClient;

    public AudioDownloadService(BotProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public DownloadedAudio download(String rawUrl) {
        URI url = parseUrl(rawUrl);
        createDownloadDir();

        Optional<DownloadedAudio> viaYtDlp = downloadWithYtDlp(url);
        if (viaYtDlp.isPresent()) {
            return viaYtDlp.get();
        }

        Path downloaded = null;
        Path converted = null;
        try {
            URI audioUrl = findAudioUrl(url);
            downloaded = downloadFile(audioUrl, extensionFromUri(audioUrl).orElse(".audio"));
            validateFileSize(downloaded);

            if (isTelegramCompatible(downloaded)) {
                return new DownloadedAudio(downloaded, safeFilename(downloaded));
            }

            converted = convertToMp3(downloaded);
            validateFileSize(converted);
            FileUtils.deleteQuietly(downloaded);
            return new DownloadedAudio(converted, safeFilename(converted));
        } catch (RuntimeException e) {
            FileUtils.deleteQuietly(downloaded);
            FileUtils.deleteQuietly(converted);
            throw e;
        }
    }

    private URI parseUrl(String rawUrl) {
        try {
            URI uri = new URI(rawUrl.trim());
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new AudioNotFoundException("Only http and https links are supported");
            }
            return uri;
        } catch (URISyntaxException | NullPointerException e) {
            throw new AudioNotFoundException("Invalid URL");
        }
    }

    private Optional<DownloadedAudio> downloadWithYtDlp(URI url) {
        Path sessionDir = properties.getDownloadDir().resolve("yt-dlp-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID());
        try {
            Files.createDirectories(sessionDir);
            ProcessBuilder builder = new ProcessBuilder(
                    properties.getYtDlpPath(),
                    "--no-playlist",
                    "-x",
                    "--audio-format", "mp3",
                    "--audio-quality", "0",
                    "-o", sessionDir.resolve("%(title).180B.%(ext)s").toString(),
                    url.toString()
            );
            builder.redirectErrorStream(true);
            Process process = builder.start();
            boolean finished = process.waitFor(properties.getProcessingTimeout().toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                return Optional.empty();
            }

            Optional<Path> result = Files.list(sessionDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".mp3"))
                    .max(Comparator.comparingLong(this::sizeOrZero));
            if (result.isEmpty()) {
                return Optional.empty();
            }

            Path audio = properties.getDownloadDir().resolve(UUID.randomUUID() + ".mp3");
            Files.move(result.get(), audio);
            validateFileSize(audio);
            return Optional.of(new DownloadedAudio(audio, safeFilename(audio)));
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        } finally {
            deleteDirectoryQuietly(sessionDir);
        }
    }

    private URI findAudioUrl(URI pageUrl) {
        if (looksLikeAudio(pageUrl)) {
            return pageUrl;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(pageUrl)
                    .timeout(properties.getRequestTimeout())
                    .GET()
                    .header("User-Agent", "audio-downloader-bot/1.0")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new AudioNotFoundException("Page is unavailable");
            }
            String contentType = response.headers().firstValue("content-type").orElse("");
            if (contentType.toLowerCase().startsWith("audio/")) {
                return pageUrl;
            }

            Document document = Jsoup.parse(response.body(), pageUrl.toString());
            return document.select("audio[src], audio source[src], source[type^=audio][src], a[href], meta[property=og:audio], meta[property=og:audio:url]")
                    .stream()
                    .map(this::audioCandidate)
                    .flatMap(Optional::stream)
                    .filter(this::looksLikeAudio)
                    .findFirst()
                    .orElseThrow(() -> new AudioNotFoundException("Audio was not found"));
        } catch (IOException e) {
            throw new AudioNotFoundException("Unable to read page");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AudioNotFoundException("Interrupted while reading page");
        }
    }

    private Optional<URI> audioCandidate(Element element) {
        String value = element.hasAttr("src") ? element.absUrl("src") : element.absUrl("href");
        if (value.isBlank()) {
            value = element.absUrl("content");
        }
        if (value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new URI(value));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    private Path downloadFile(URI audioUrl, String extension) {
        Path target = properties.getDownloadDir().resolve(UUID.randomUUID() + extension);
        try {
            HttpRequest request = HttpRequest.newBuilder(audioUrl)
                    .timeout(properties.getProcessingTimeout())
                    .GET()
                    .header("User-Agent", "audio-downloader-bot/1.0")
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new AudioNotFoundException("Audio file is unavailable");
            }
            try (InputStream body = response.body()) {
                Files.copy(body, target);
            }
            return target;
        } catch (IOException e) {
            FileUtils.deleteQuietly(target);
            throw new AudioNotFoundException("Unable to download audio");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FileUtils.deleteQuietly(target);
            throw new AudioNotFoundException("Interrupted while downloading audio");
        }
    }

    private Path convertToMp3(Path source) {
        Path target = properties.getDownloadDir().resolve(UUID.randomUUID() + ".mp3");
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    properties.getFfmpegPath(),
                    "-y",
                    "-i", source.toString(),
                    "-vn",
                    "-codec:a", "libmp3lame",
                    "-b:a", "192k",
                    target.toString()
            );
            builder.redirectErrorStream(true);
            Process process = builder.start();
            boolean finished = process.waitFor(properties.getProcessingTimeout().toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new AudioNotFoundException("Conversion timed out");
            }
            if (process.exitValue() != 0 || !Files.exists(target)) {
                throw new AudioNotFoundException("Conversion failed");
            }
            return target;
        } catch (IOException e) {
            FileUtils.deleteQuietly(target);
            throw new AudioNotFoundException("ffmpeg is required to convert this file");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FileUtils.deleteQuietly(target);
            throw new AudioNotFoundException("Interrupted while converting audio");
        }
    }

    private boolean looksLikeAudio(URI uri) {
        String lower = uri.getPath() == null ? "" : uri.getPath().toLowerCase();
        return AUDIO_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private boolean isTelegramCompatible(Path path) {
        String lower = path.getFileName().toString().toLowerCase();
        return TELEGRAM_AUDIO_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private Optional<String> extensionFromUri(URI uri) {
        String path = uri.getPath();
        if (path == null) {
            return Optional.empty();
        }
        String lower = path.toLowerCase();
        return AUDIO_EXTENSIONS.stream().filter(lower::endsWith).findFirst();
    }

    private String safeFilename(Path path) {
        String name = path.getFileName().toString();
        return name.length() > 64 ? name.substring(0, 60) + ".mp3" : name;
    }

    private void validateFileSize(Path file) {
        try {
            if (Files.size(file) > properties.getMaxFileSize()) {
                throw new AudioTooLargeException("Audio file is too large");
            }
        } catch (IOException e) {
            throw new AudioNotFoundException("Unable to read downloaded file");
        }
    }

    private long sizeOrZero(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private void createDownloadDir() {
        try {
            Files.createDirectories(properties.getDownloadDir());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create download directory", e);
        }
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(FileUtils::deleteQuietly);
        } catch (IOException ignored) {
            // Temporary directories are best-effort cleanup only.
        }
    }
}
