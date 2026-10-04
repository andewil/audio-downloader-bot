# Audio Downloader Bot

Telegram bot written with Java, Spring Boot, Maven and rubenlagus TelegramBots.

## Requirements

- Java 21+
- Maven 3.9+
- Telegram bot token from BotFather
- `yt-dlp` and `ffmpeg` available in PATH for YouTube links
- Optional but recommended for other sites: `yt-dlp` and `ffmpeg` available in PATH

YouTube links are handled by `yt-dlp`: the bot downloads the best available audio stream, extracts it to MP3 with `ffmpeg`, validates the configured file-size limit, and sends the MP3 to Telegram.

Recent YouTube extraction may require a JavaScript runtime. The Docker image includes `deno` for this. In WSL/Ubuntu you can use `nodejs` and run the bot with:

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

## Docker

The image is built in two stages (Maven + Temurin 21 JDK, then Temurin 21 JRE) and bundles `yt-dlp`, `ffmpeg` and `deno`:

```powershell
docker build -t audio-downloader-bot .
docker run -d --name audio-downloader-bot `
  -e TELEGRAM_BOT_USERNAME="your_bot_username" `
  -e TELEGRAM_BOT_TOKEN="123456:token" `
  audio-downloader-bot
```

## CI/CD

GitHub Actions workflow `.github/workflows/build-deploy.yml`:

- `test` — runs `mvn -B verify` on Java 21 for pushes and pull requests to `main`.
- `build` — on `main` only: sets the project version to `1.0.<run number>`, builds the Docker image and pushes tags `1.0` and `1.0.<run number>`.
- `deploy` — connects to the server over SSH, pulls `1.0.<run number>` and recreates the `audio-downloader-bot` container.

Required repository settings (Settings → Secrets and variables → Actions):

| Kind | Name | Description |
|---|---|---|
| Variable | `IMAGE_NAME` | Full image name including registry host, e.g. `registry.example.com/audio-downloader-bot` |
| Variable | `SSH_SERVER` | Deployment host |
| Variable | `SSH_PORT` | SSH port (defaults to 22) |
| Variable | `TELEGRAM_BOT_USERNAME` | Bot username |
| Secret | `REGISTRY_USERNAME`, `REGISTRY_PASSWORD` | Docker registry credentials |
| Secret | `SSH_USER` | SSH user on the deployment host |
| Secret | `SSH_KEY` | SSH private key |
| Secret | `TELEGRAM_BOT_TOKEN` | Bot token |

The deployment host must be able to pull from the registry without an explicit `docker login` in the workflow.

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
