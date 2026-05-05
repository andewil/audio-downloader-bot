# Audio Downloader Bot

Telegram bot written with Java, Spring Boot, Maven and rubenlagus TelegramBots.

## Requirements

- Java 17+
- Maven 3.9+
- Telegram bot token from BotFather
- Optional but recommended: `yt-dlp` and `ffmpeg` available in PATH

`yt-dlp` gives the bot broad page support. Without it, the bot can still download direct audio URLs and common `<audio>`, `<source>`, `og:audio` links from HTML pages.

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
- `FFMPEG_PATH`
