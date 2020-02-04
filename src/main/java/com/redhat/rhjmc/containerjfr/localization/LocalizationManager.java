package com.redhat.rhjmc.containerjfr.localization;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.redhat.rhjmc.containerjfr.core.log.Logger;

public class LocalizationManager {
    private Logger logger;

    private Locale defaultLocale = Locale.getDefault();
    private Map<Locale, Map<String, String>> messages = new HashMap<>();

    LocalizationManager(Logger logger) {
        this.logger = logger;
    }

    public void setDefaultLocale(Locale locale) {
        Objects.requireNonNull(locale, "locale must not be null");
        defaultLocale = locale;
    }

    public Locale getDefaultLocale() {
        return defaultLocale;
    }

    public Collection<Locale> getAvailableLocales() {
        return messages.keySet();
    }

    /**
     * Get all messages available for default locale
     *
     * @return a map of message key-value pairs
     */
    public Map<String, String> getAllMessages() {
        return getAllMessages(defaultLocale);
    }

    /**
     * Get all messages available
     *
     * <p>If there are messages available for default locale but not for the specific locale,
     * messages for the default locale will be used.
     *
     * @param locale the locale of the result messages
     * @return a map of message key-value pairs
     */
    public Map<String, String> getAllMessages(Locale locale) {
        Map<String, String> defaultMessages = messages.getOrDefault(defaultLocale, Map.of());
        if (locale == defaultLocale) {
            return new HashMap<>(defaultMessages);
        }

        Map<String, String> ret = new HashMap<>(messages.getOrDefault(locale, Map.of()));
        for (Map.Entry<String, String> entry : defaultMessages.entrySet()) {
            ret.merge(
                    entry.getKey(),
                    entry.getValue(),
                    (oldValue, newValue) -> {
                        logger.warn(
                                String.format(
                                        "missing message for key %s in locale %s",
                                        entry.getKey(), locale));
                        return oldValue;
                    });
        }

        return ret;
    }

    /**
     * Add or overwrite a message of the given key for the default locale
     *
     * @param key the unique message key
     * @param message the message content
     */
    public void putMessage(String key, String message) {
        putMessage(defaultLocale, key, message);
    }

    /**
     * Add or overwrite a message of the given key
     *
     * @param locale the locale of the message being added
     * @param key the unique message key
     * @param message the message content
     */
    public void putMessage(Locale locale, String key, String message) {
        messages.putIfAbsent(locale, new HashMap<>());
        messages.get(locale).put(key, message);
    }

    /**
     * Add or overwrite one or more messages for the default locale in a batch. Useful when loading
     * from a .properties file
     *
     * @param messages a map of message key-value pairs
     */
    public void putMessages(Map<String, String> messages) {
        putMessages(defaultLocale, messages);
    }

    /**
     * Add or overwrite one or more messages in a batch. Useful when loading from a .properties file
     *
     * @param locale the locale of the messages being added
     * @param messages a map of message key-value pairs
     */
    public void putMessages(Locale locale, Map<String, String> messages) {
        this.messages.putIfAbsent(locale, new HashMap<>());
        Map<String, String> base = this.messages.get(locale);

        for (Map.Entry<String, String> entry : messages.entrySet()) {
            base.merge(entry.getKey(), entry.getValue(), (oldValue, newValue) -> newValue);
        }
    }

    /**
     * Get the message of a given key in default locale
     *
     * @param key key for the message
     * @return the message content
     */
    public String getMessage(String key) {
        return getMessage(defaultLocale, key);
    }

    /**
     * Get the message of a given key
     *
     * @param locale the locale of the result message
     * @param key key for the message
     * @return the message content
     */
    public String getMessage(Locale locale, String key) {
        Objects.requireNonNull(locale);
        Objects.requireNonNull(key);

        Map<String, String> dictionary = messages.get(locale);
        if (dictionary == null && locale != defaultLocale) {
            logger.warn(String.format("missing message for key %s in locale %s", key, locale));
            dictionary = messages.get(defaultLocale);
        }
        if (dictionary == null) {
            return null;
        }

        return dictionary.get(key);
    }

    /**
     * Get the message of a given key, respecting a list of accepts language tags with priorities
     *
     * <p>If none of the acceptable language is available, the default local will be used.
     *
     * @param languageTags a list of acceptable languages eg. the HTTP Accept-Languages header value
     * @param key key for the message
     * @return the message content
     */
    public String getMessage(String languageTags, String key) {
        Objects.requireNonNull(languageTags);

        return getMessage(matchLocale(languageTags), key);
    }

    public Locale matchLocale(String languageTags) {
        Locale locale =
                Locale.lookup(Locale.LanguageRange.parse(languageTags), getAvailableLocales());
        if (locale == null) {
            logger.warn("no matching locale found for accepted languages: " + languageTags);
            locale = defaultLocale;
        }

        return locale;
    }
}
