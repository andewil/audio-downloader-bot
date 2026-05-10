package com.andewil.audiodownloaderbot.i18n;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class MessageCatalog {

    public SupportedLanguage resolve(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return SupportedLanguage.ENGLISH;
        }
        return switch (Locale.forLanguageTag(languageCode).getLanguage()) {
            case "de" -> SupportedLanguage.GERMAN;
            case "ru" -> SupportedLanguage.RUSSIAN;
            default -> SupportedLanguage.ENGLISH;
        };
    }

    public String welcome(SupportedLanguage language) {
        return switch (language) {
            case GERMAN -> "Hallo! Ich kann Audio aus Webseiten und YouTube-Links finden, herunterladen, in ein Telegram-kompatibles Format konvertieren und dir als Datei senden.\n\nSende mir einfach einen Link. Wenn ich keine Audiodaten finde, sage ich Bescheid.";
            case RUSSIAN -> "Привет! Я могу найти аудио на странице или в YouTube-ссылке, скачать его, конвертировать в формат, совместимый с Telegram, и отправить файл в этот чат.\n\nПросто пришлите ссылку. Если аудио не найдется, я сообщу об этом.";
            case ENGLISH -> "Hi! I can find audio on a web page or in a YouTube link, download it, convert it to a Telegram-compatible format, and send the file back to this chat.\n\nJust send me a link. If I cannot find audio data, I will let you know.";
        };
    }

    public String sendUrl(SupportedLanguage language) {
        return switch (language) {
            case GERMAN -> "Bitte sende einen Link zu einer Seite, einer Audiodatei oder einem YouTube-Video.";
            case RUSSIAN -> "Пожалуйста, отправьте ссылку на страницу, аудиофайл или YouTube-видео.";
            case ENGLISH -> "Please send a link to a page, an audio file, or a YouTube video.";
        };
    }

    public String processing(SupportedLanguage language) {
        return switch (language) {
            case GERMAN -> "Ich analysiere den Link und suche nach Audio...";
            case RUSSIAN -> "Анализирую ссылку и ищу аудио...";
            case ENGLISH -> "I am analyzing the link and looking for audio...";
        };
    }

    public String notFound(SupportedLanguage language) {
        return switch (language) {
            case GERMAN -> "Ich konnte unter diesem Link keine Audiodaten finden.";
            case RUSSIAN -> "Я не нашел аудио по этой ссылке.";
            case ENGLISH -> "I could not find audio data at this link.";
        };
    }

    public String failed(SupportedLanguage language) {
        return switch (language) {
            case GERMAN -> "Leider konnte ich die Audiodatei nicht verarbeiten. Prüfe bitte den Link oder versuche es später erneut.";
            case RUSSIAN -> "Не удалось обработать аудио. Проверьте ссылку или попробуйте позже.";
            case ENGLISH -> "I could not process the audio. Please check the link or try again later.";
        };
    }

    public String tooLarge(SupportedLanguage language) {
        return switch (language) {
            case GERMAN -> "Die Audiodatei ist zu groß, um sie über Telegram zu senden.";
            case RUSSIAN -> "Аудиофайл слишком большой для отправки через Telegram.";
            case ENGLISH -> "The audio file is too large to send through Telegram.";
        };
    }
}
