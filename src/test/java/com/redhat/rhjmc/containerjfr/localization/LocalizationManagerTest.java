package com.redhat.rhjmc.containerjfr.localization;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class LocalizationManagerTest {
    LocalizationManager lm;

    @Mock
    Logger logger;

    @BeforeEach
    void setup() {
        lm = new LocalizationManager(logger);
    }

    @Test
    void shouldSuccessfullyInstantiate() {
        assertDoesNotThrow(
            () -> new LocalizationManager(logger)
        );
    }

    @Test
    void shouldSetDefaultLocaleThrowsIfNull() {
        assertThrows(NullPointerException.class, () ->
            lm.setDefaultLocale(null)
        );
    }

    @Test
    void shouldGetSystemDefaultLocale() {
        Locale systemLocale = Locale.getDefault();

        assertEquals(systemLocale, lm.getDefaultLocale());
    }

    @Test
    void shouldGetNewlySetLocale() {
        Locale newLocale = mock(Locale.class);

        lm.setDefaultLocale(newLocale);
        assertEquals(newLocale, lm.getDefaultLocale());
    }

    @Test
    void shouldGetEmptyAvailableLocales() {
        assertTrue(lm.getAvailableLocales().isEmpty());
    }

    @Test
    void shouldGetAvailableLocales() {
        Collection<Locale> locales = new HashSet<>();
        {
            locales.add(Locale.ENGLISH);
            locales.add(Locale.FRENCH);
            locales.add(Locale.CHINESE);
        }

        for (Locale l : locales) {
            lm.putMessage(l, "foo", "bar");
        }

        assertEquals(locales, lm.getAvailableLocales());
    }

    @Test
    void shouldGetAllMessagesForDefaultLocale() {
        Map<String, String> messages = new HashMap<>();
        messages.put("foo", "bar");
        messages.put("foo2", "bar2");
        messages.put("foo3", "bar3");
        for (Map.Entry<String, String> entry : messages.entrySet()) {
            lm.putMessage(entry.getKey(), entry.getValue());
        }

        assertEquals(messages, lm.getAllMessages());
    }

    @Test
    void shouldGetAllMessagesFallbackToDefaultLocale() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("foo", "default1");
        defaults.put("foo2", "default2");
        defaults.put("foo3", "default3");
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            lm.putMessage(Locale.ENGLISH, entry.getKey(), entry.getValue());
        }

        Map<String, String> messages = new HashMap<>();
        messages.put("foo2", "messages2");
        for (Map.Entry<String, String> entry : messages.entrySet()) {
            lm.putMessage(Locale.FRENCH, entry.getKey(), entry.getValue());
        }

        Map<String, String> expected = new HashMap<>();
        expected.put("foo", "default1");
        expected.put("foo2", "messages2");
        expected.put("foo3", "default3");

        lm.setDefaultLocale(Locale.ENGLISH);
        assertEquals(expected, lm.getAllMessages(Locale.FRENCH));
    }

    @Test
    void shouldPutMessageWorkWithDefaultLocale() {
        lm.putMessage("foo", "bar");

        assertEquals("bar", lm.getMessage(lm.getDefaultLocale(), "foo"));
    }

    @Test
    void shouldPutMessageOverwriteOldValues() {
        lm.putMessage("foo", "old value");
        assertEquals("old value", lm.getMessage("foo"));

        lm.putMessage("foo", "new value");
        assertEquals("new value", lm.getMessage("foo"));
    }

    @Test
    void shouldPutMessagesSuccess() {
        Map<String, String> messages = new HashMap<>();
        messages.put("foo", "bar");
        messages.put("foo2", "bar2");
        messages.put("foo3", "bar3");
        lm.putMessages(messages);

        assertEquals(messages, lm.getAllMessages());
    }

    @Test
    void shouldPutMessageFallbackToDefaultLocale() {
        lm.setDefaultLocale(Locale.ENGLISH);
        lm.putMessage("foo", "bar");

        assertEquals("bar", lm.getMessage(Locale.FRENCH, "foo"));

        lm.setDefaultLocale(Locale.FRENCH);
        assertNull(lm.getMessage(Locale.FRENCH, "foo"));
    }

    @Test
    void shouldGetMessageByLanguageTags() {

    }

    @Test
    void shouldMatchLocales() {
        lm.putMessage(Locale.ENGLISH, "foo", "bar");
        lm.putMessage(Locale.US, "foo", "bar");
        lm.putMessage(Locale.CANADA, "foo", "bar");
        lm.putMessage(Locale.FRENCH, "foo", "bar");
        lm.putMessage(Locale.CHINESE, "foo", "bar");

        assertEquals(Locale.CANADA, lm.matchLocale("en-CA,en;q=0.9,zh-CN;q=0.8,zh;q=0.7,ja;q=0.6"));
        assertEquals(Locale.FRENCH, lm.matchLocale("fr-CH, fr;q=0.9, en;q=0.8, de;q=0.7, *;q=0.5"));
        assertEquals(Locale.US, lm.matchLocale("en-US,en;q=0.5"));
        assertEquals(Locale.CHINESE, lm.matchLocale("zh-CN;q=0.8,zh;q=0.7,ja;q=0.6"));
        assertEquals(lm.getDefaultLocale(), lm.matchLocale("de-CH,de;q=0.9"));
    }
}
