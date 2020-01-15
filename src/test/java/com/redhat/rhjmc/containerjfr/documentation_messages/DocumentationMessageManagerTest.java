package com.redhat.rhjmc.containerjfr.documentation_messages;

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
public class DocumentationMessageManagerTest {
    DocumentationMessageManager dmm;

    @Mock
    Logger logger;

    @BeforeEach
    void setup() {
        dmm = new DocumentationMessageManager(logger);
    }

    @Test
    void shouldSuccessfullyInstantiate() {
        assertDoesNotThrow(
            () -> new DocumentationMessageManager(logger)
        );
    }

    @Test
    void shouldSetDefaultLocaleThrowsIfNull() {
        assertThrows(NullPointerException.class, () ->
            dmm.setDefaultLocale(null)
        );
    }

    @Test
    void shouldGetSystemDefaultLocale() {
        Locale systemLocale = Locale.getDefault();

        assertEquals(systemLocale, dmm.getDefaultLocale());
    }

    @Test
    void shouldGetNewlySetLocale() {
        Locale newLocale = mock(Locale.class);

        dmm.setDefaultLocale(newLocale);
        assertEquals(newLocale, dmm.getDefaultLocale());
    }

    @Test
    void shouldGetEmptyAvailableLocales() {
        assertTrue(dmm.getAvailableLocales().isEmpty());
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
            dmm.putMessage(l, "foo", "bar");
        }

        assertEquals(locales, dmm.getAvailableLocales());
    }

    @Test
    void shouldGetAllMessagesForDefaultLocale() {
        Map<String, String> messages = new HashMap<>();
        messages.put("foo", "bar");
        messages.put("foo2", "bar2");
        messages.put("foo3", "bar3");
        for (Map.Entry<String, String> entry : messages.entrySet()) {
            dmm.putMessage(entry.getKey(), entry.getValue());
        }

        assertEquals(messages, dmm.getAllMessages());
    }

    @Test
    void shouldGetAllMessagesFallbackToDefaultLocale() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("foo", "default1");
        defaults.put("foo2", "default2");
        defaults.put("foo3", "default3");
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            dmm.putMessage(Locale.ENGLISH, entry.getKey(), entry.getValue());
        }

        Map<String, String> messages = new HashMap<>();
        messages.put("foo2", "messages2");
        for (Map.Entry<String, String> entry : messages.entrySet()) {
            dmm.putMessage(Locale.FRENCH, entry.getKey(), entry.getValue());
        }

        Map<String, String> expected = new HashMap<>();
        expected.put("foo", "default1");
        expected.put("foo2", "messages2");
        expected.put("foo3", "default3");

        dmm.setDefaultLocale(Locale.ENGLISH);
        assertEquals(expected, dmm.getAllMessages(Locale.FRENCH));
    }

    @Test
    void shouldPutMessageWorkWithDefaultLocale() {
        dmm.putMessage("foo", "bar");

        assertEquals("bar", dmm.getMessage(dmm.getDefaultLocale(), "foo"));
    }

    @Test
    void shouldPutMessageOverwriteOldValues() {
        dmm.putMessage("foo", "old value");
        assertEquals("old value", dmm.getMessage("foo"));

        dmm.putMessage("foo", "new value");
        assertEquals("new value", dmm.getMessage("foo"));
    }

    @Test
    void shouldPutMessagesSuccess() {
        Map<String, String> messages = new HashMap<>();
        messages.put("foo", "bar");
        messages.put("foo2", "bar2");
        messages.put("foo3", "bar3");
        dmm.putMessages(messages);

        assertEquals(messages, dmm.getAllMessages());
    }

    @Test
    void shouldPutMessageFallbackToDefaultLocale() {
        dmm.setDefaultLocale(Locale.ENGLISH);
        dmm.putMessage("foo", "bar");

        assertEquals("bar", dmm.getMessage(Locale.FRENCH, "foo"));

        dmm.setDefaultLocale(Locale.FRENCH);
        assertNull(dmm.getMessage(Locale.FRENCH, "foo"));
    }

    @Test
    void shouldGetMessageByLanguageTags() {
        
    }

    @Test
    void shouldMatchLocales() {
        dmm.putMessage(Locale.ENGLISH, "foo", "bar");
        dmm.putMessage(Locale.US, "foo", "bar");
        dmm.putMessage(Locale.CANADA, "foo", "bar");
        dmm.putMessage(Locale.FRENCH, "foo", "bar");
        dmm.putMessage(Locale.CHINESE, "foo", "bar");

        assertEquals(Locale.CANADA, dmm.matchLocale("en-CA,en;q=0.9,zh-CN;q=0.8,zh;q=0.7,ja;q=0.6"));
        assertEquals(Locale.FRENCH, dmm.matchLocale("fr-CH, fr;q=0.9, en;q=0.8, de;q=0.7, *;q=0.5"));
        assertEquals(Locale.US, dmm.matchLocale("en-US,en;q=0.5"));
        assertEquals(Locale.CHINESE, dmm.matchLocale("zh-CN;q=0.8,zh;q=0.7,ja;q=0.6"));
        assertEquals(dmm.getDefaultLocale(), dmm.matchLocale("de-CH,de;q=0.9"));
    }
}
