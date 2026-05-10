# Audio Downloader Bot

Telegram bot written with Java, Spring Boot, Maven and rubenlagus TelegramBots.

## Requirements

- Java 17+
- Maven 3.9+
- Telegram bot token from BotFather
- `yt-dlp` and `ffmpeg` available in PATH for YouTube links
- Optional but recommended for other sites: `yt-dlp` and `ffmpeg` available in PATH

YouTube links are handled by `yt-dlp`: the bot downloads the best available audio stream, extracts it to MP3 with `ffmpeg`, validates the configured file-size limit, and sends the MP3 to Telegram.

Recent YouTube extraction may require a JavaScript runtime. In WSL/Ubuntu, install `nodejs` and run the bot with:

```powershell
$env:YT_DLP_PATH="/home/andewil/.local/bin/yt-dlp"
$env:FFMPEG_PATH="/usr/bin/ffmpeg"
$env:YT_DLP_JS_RUNTIME="node:/usr/bin/node"
```

`yt-dlp` also gives the bot broad page support for non-YouTube sites. Without it, the bot can still download direct audio URLs and common `<audio>`, `<source>`, `og:audio` links from HTML pages.

## Run

```powershell
$env:TELEGRAM_BOT_USERNAME="your_bot_username"
$env:TELEGRAM_BOT_TOKEN="123456:token"
mvn spring-boot:run
```

## Configuration

All settings can be provided as environment variables:

- `TELEGRAM_BOT_USERNAME`
- `TELEGRAM_BOT_TOKEN`
- `BOT_DOWNLOAD_DIR`
- `BOT_REQUEST_TIMEOUT`
- `BOT_PROCESSING_TIMEOUT`
- `BOT_MAX_FILE_SIZE`
- `YT_DLP_PATH`
- `YT_DLP_JS_RUNTIME`
- `FFMPEG_PATH`
