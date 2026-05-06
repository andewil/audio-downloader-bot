package com.andewil.audiodownloaderbot.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.andewil.audiodownloaderbot.config.BotProperties;
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

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AudioDownloadService {

    private static final Set<String> TELEGRAM_AUDIO_EXTENSIONS = Set.of(".mp3", ".m4a");
    private static final List<String> AUDIO_EXTENSIONS = List.of(".mp3", ".m4a", ".aac", ".ogg", ".oga", ".opus", ".wav", ".flac", ".webm");

    private final BotProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AudioDownloadService(BotProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public DownloadedAudio download(String rawUrl) {
        log.info("Downloading audio from {}", rawUrl);
        URI url = parseUrl(rawUrl);
        createDownloadDir();

        Optional<DownloadedAudio> viaYtDlp = downloadWithYtDlp(url);
        if (viaYtDlp.isPresent()) {
            log.info("Downloaded audio via yt-dlp from {}", url);
            return viaYtDlp.get();
        }

        Path downloaded = null;
        Path converted = null;
        try {
            URI audioUrl = findAudioUrl(url);
            downloaded = downloadFile(audioUrl, url);
            validateFileSize(downloaded);

            if (isTelegramCompatible(downloaded)) {
                log.info("Downloaded audio is compatible with Telegram from {}", url);
                return new DownloadedAudio(downloaded, safeFilename(downloaded));
            }

            converted = convertToMp3(downloaded);
            validateFileSize(converted);
            FileUtils.deleteQuietly(downloaded);

            log.info("Converted audio to MP3 from {}", url);
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
                    .header("User-Agent", userAgent())
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
            Optional<URI> staticCandidate = document.select("audio[src], audio source[src], source[type^=audio][src], a[href], meta[property=og:audio], meta[property=og:audio:url]")
                    .stream()
                    .map(this::audioCandidate)
                    .flatMap(Optional::stream)
                    .filter(candidate -> looksLikeAudio(candidate) || isAudioResource(candidate, pageUrl))
                    .findFirst();
            if (staticCandidate.isPresent()) {
                return staticCandidate.get();
            }

            return findAjaxPlayerAudioUrl(document, pageUrl)
                    .orElseThrow(() -> new AudioNotFoundException("Audio was not found"));
        } catch (IOException e) {
            throw new AudioNotFoundException("Unable to read page");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AudioNotFoundException("Interrupted while reading page");
        }
    }

    private Optional<URI> findAjaxPlayerAudioUrl(Document document, URI pageUrl) {
        return document.select("[data-play-id]")
                .stream()
                .map(element -> element.attr("data-play-id"))
                .filter(id -> !id.isBlank())
                .distinct()
                .map(id -> ajaxPlayerUrl(pageUrl, id))
                .flatMap(Optional::stream)
                .filter(candidate -> looksLikeAudio(candidate) || isAudioResource(candidate, pageUrl))
                .findFirst();
    }

    private Optional<URI> ajaxPlayerUrl(URI pageUrl, String id) {
        try {
            URI endpoint = pageUrl.resolve("/ajax.php?side=front&mod=playing&id=" + id + "&act=one");
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(properties.getRequestTimeout())
                    .GET()
                    .header("User-Agent", userAgent())
                    .header("Referer", pageUrl.toString())
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode first = root.isArray() && !root.isEmpty() ? root.get(0) : root;
            JsonNode url = first == null ? null : first.get("url");
            if (url == null || !url.isTextual() || url.asText().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(pageUrl.resolve(url.asText()));
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
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

    private Path downloadFile(URI audioUrl, URI referer) {
        Path target = null;
        try {
            HttpRequest request = HttpRequest.newBuilder(audioUrl)
                    .timeout(properties.getProcessingTimeout())
                    .GET()
                    .header("User-Agent", userAgent())
                    .header("Referer", referer.toString())
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new AudioNotFoundException("Audio file is unavailable");
            }

            String contentType = response.headers().firstValue("content-type").orElse("");
            String extension = extensionFromUri(audioUrl)
                    .or(() -> extensionFromContentType(contentType))
                    .orElse(".audio");
            target = properties.getDownloadDir().resolve(UUID.randomUUID() + extension);
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

    private Optional<String> extensionFromContentType(String contentType) {
        String lower = contentType == null ? "" : contentType.toLowerCase();
        if (lower.startsWith("audio/mpeg") || lower.startsWith("audio/mp3")) {
            return Optional.of(".mp3");
        }
        if (lower.startsWith("audio/mp4") || lower.startsWith("audio/x-m4a")) {
            return Optional.of(".m4a");
        }
        if (lower.startsWith("audio/aac")) {
            return Optional.of(".aac");
        }
        if (lower.startsWith("audio/ogg")) {
            return Optional.of(".ogg");
        }
        if (lower.startsWith("audio/wav") || lower.startsWith("audio/x-wav")) {
            return Optional.of(".wav");
        }
        if (lower.startsWith("audio/flac") || lower.startsWith("audio/x-flac")) {
            return Optional.of(".flac");
        }
        if (lower.startsWith("audio/webm")) {
            return Optional.of(".webm");
        }
        return Optional.empty();
    }

    private boolean isAudioResource(URI uri, URI referer) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(properties.getRequestTimeout())
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", userAgent())
                    .header("Referer", referer.toString())
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 400 && response.headers()
                    .firstValue("content-type")
                    .map(contentType -> contentType.toLowerCase().startsWith("audio/"))
                    .orElse(false);
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private String userAgent() {
        return "Mozilla/5.0 (compatible; audio-downloader-bot/1.0)";
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
