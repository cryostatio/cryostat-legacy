package com.redhat.rhjmc.containerjfr.localization;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class MessageLoader {
    private Map<Locale, URL> dictionaryFiles = new HashMap<>();

    public MessageLoader(Class<?> context, String basename, String... variants) throws IOException {
        String path = basename + ".properties";
        URL url = context.getResource(path);
        if (url == null) {
            throw new IOException(context.getPackageName().replace('.', '/') + path + " does not exists");
        }
        dictionaryFiles.put(Locale.ENGLISH, url);

        for (String v : variants) {
            path = basename + '_' + v + ".properties";
            url = context.getResource(path);
            if (url == null) {
                throw new IOException(context.getPackageName().replace('.', '/') + path + " does not exists");
            }
        }
    }

    public void loadInto(LocalizationManager lm) throws IOException {
        for (Map.Entry<Locale, URL> entry : dictionaryFiles.entrySet()) {
            InputStream is = entry.getValue().openStream();

            Properties dictionary = new Properties();
            dictionary.load(is);
            is.close();

            lm.putMessages(entry.getKey(), dictionary.entrySet().stream().collect(
                Collectors.toMap(
                    e -> e.getKey().toString(),
                    e -> e.getValue().toString()
                )
            ));
        }
    }
}
