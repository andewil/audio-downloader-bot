FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl ffmpeg nodejs python3 \
    && curl -L "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp" -o /usr/local/bin/yt-dlp \
    && chmod +x /usr/local/bin/yt-dlp \
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
ENV YT_DLP_JS_RUNTIME=node:/usr/bin/node
ENV FFMPEG_PATH=/usr/bin/ffmpeg

EXPOSE 8080
EXPOSE 5005

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
