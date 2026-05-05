package com.example.audiodownloaderbot.i18n;

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
            case GERMAN -> "Hallo! Ich kann Audio aus einer Seite finden, herunterladen, in ein Telegram-kompatibles Format konvertieren und dir als Datei senden.\n\nSende mir einfach einen Link. Wenn ich keine Audiodaten finde, sage ich Bescheid.";
            case RUSSIAN -> "Привет! Я могу найти аудио на странице, скачать его, конвертировать в формат, совместимый с Telegram, и отправить файл в этот чат.\n\nПросто пришлите ссылку. Если аудио не найдется, я сообщу об этом.";
            case ENGLISH -> "Hi! I can find audio on a web page, download it, convert it to a Telegram-compatible format, and send the file back to this chat.\n\nJust send me a link. If I cannot find audio data, I will let you know.";
        };
    }

    public String sendUrl(SupportedLanguage language) {
        return switch (language) {
            case GERMAN -> "Bitte sende einen Link zu einer Seite oder Audiodatei.";
            case RUSSIAN -> "Пожалуйста, отправьте ссылку на страницу или аудиофайл.";
            case ENGLISH -> "Please send a link to a page or an audio file.";
        };
    }

    public String processing(SupportedLanguage language) {
        return switch (language) {
            case GERMAN -> "Ich analysiere die Seite und suche nach Audio...";
            case RUSSIAN -> "Анализирую страницу и ищу аудио...";
            case ENGLISH -> "I am analyzing the page and looking for audio...";
        };
    }

    public String notFound(SupportedLanguage language) {
        return switch (language) {
            case GERMAN -> "Ich konnte auf dieser Seite keine Audiodaten finden.";
            case RUSSIAN -> "Я не нашел аудио на этой странице.";
            case ENGLISH -> "I could not find audio data on this page.";
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
