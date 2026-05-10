package com.andewil.audiodownloaderbot.bot;

import com.andewil.audiodownloaderbot.audio.AudioDownloadService;
import com.andewil.audiodownloaderbot.audio.AudioNotFoundException;
import com.andewil.audiodownloaderbot.audio.AudioProcessingException;
import com.andewil.audiodownloaderbot.audio.AudioTooLargeException;
import com.andewil.audiodownloaderbot.audio.DownloadedAudio;
import com.andewil.audiodownloaderbot.config.BotProperties;
import com.andewil.audiodownloaderbot.i18n.MessageCatalog;
import com.andewil.audiodownloaderbot.i18n.SupportedLanguage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
@Slf4j
public class AudioDownloaderBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final BotProperties properties;
    private final MessageCatalog messages;
    private final AudioDownloadService audioDownloadService;
    private final TelegramClient telegramClient;
    private final ExecutorService workers = Executors.newCachedThreadPool();

    public AudioDownloaderBot(BotProperties properties, MessageCatalog messages, AudioDownloadService audioDownloadService) {
        this.properties = properties;
        this.messages = messages;
        this.audioDownloadService = audioDownloadService;
        this.telegramClient = new OkHttpTelegramClient(properties.getToken());

        if (properties.getToken() == null) {
            throw new IllegalArgumentException("Bot token cannot be null");
        }
    }

    @Override
    public String getBotToken() {
        return properties.getToken();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        log.info("Received update: {}", update);

        Message message = update.getMessage();
        Long chatId = message.getChatId();
        SupportedLanguage language = messages.resolve(message.getFrom() == null ? null : message.getFrom().getLanguageCode());
        String text = message.getText().trim();

        if ("/start".equalsIgnoreCase(text) || "/help".equalsIgnoreCase(text)) {
            sendText(chatId, messages.welcome(language));
            return;
        }

        if (!isUrl(text)) {
            sendText(chatId, messages.sendUrl(language));
            return;
        }

        sendText(chatId, messages.processing(language));
        workers.submit(() -> processAudio(chatId, text, language));
    }

    private void processAudio(Long chatId, String url, SupportedLanguage language) {
        try (DownloadedAudio audio = audioDownloadService.download(url)) {
            SendAudio sendAudio = SendAudio.builder()
                    .chatId(chatId.toString())
                    .audio(new InputFile(audio.file().toFile(), audio.filename()))
                    .title("Audio from " + url)
                    .build();
            telegramClient.execute(sendAudio);
            log.info("Audio sent to chatId: {}", chatId);
        } catch (AudioTooLargeException e) {
            sendText(chatId, messages.tooLarge(language));
        } catch (AudioNotFoundException e) {
            sendText(chatId, messages.notFound(language));
        } catch (AudioProcessingException e) {
            log.warn("Audio source was found but processing failed", e);
            sendText(chatId, messages.failed(language));
        } catch (TelegramApiException e) {
            log.warn("Telegram API failed while sending audio", e);
            sendText(chatId, messages.failed(language));
        } catch (RuntimeException e) {
            log.warn("Audio processing failed", e);
            sendText(chatId, messages.failed(language));
        }
    }

    private void sendText(Long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Telegram API failed while sending message", e);
        }
    }

    private boolean isUrl(String text) {
        return text.startsWith("http://") || text.startsWith("https://");
    }
}
