FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl ffmpeg python3 unzip \
    && curl -L "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp" -o /usr/local/bin/yt-dlp \
    && chmod +x /usr/local/bin/yt-dlp \
    && curl -L "https://github.com/denoland/deno/releases/latest/download/deno-x86_64-unknown-linux-gnu.zip" -o /tmp/deno.zip \
    && unzip /tmp/deno.zip -d /usr/local/bin \
    && chmod +x /usr/local/bin/deno \
    && rm -f /tmp/deno.zip \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN useradd --system --create-home --home-dir /app appuser \
    && mkdir -p /app/downloads \
    && chown -R appuser:appuser /app

COPY --from=build /workspace/target/*.jar /app/app.jar

USER appuser

ENV BOT_DOWNLOAD_DIR=/app/downloads
ENV YT_DLP_PATH=/usr/local/bin/yt-dlp
ENV YT_DLP_JS_RUNTIME=deno:/usr/local/bin/deno
ENV FFMPEG_PATH=/usr/bin/ffmpeg

EXPOSE 8080
EXPOSE 5005

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
