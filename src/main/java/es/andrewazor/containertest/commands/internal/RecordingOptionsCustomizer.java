package es.andrewazor.containertest.commands.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;

import es.andrewazor.containertest.tui.ClientWriter;
import es.andrewazor.containertest.util.CheckedConsumer;

class RecordingOptionsCustomizer {

    private final Map<OptionKey, Consumer<RecordingOptionsBuilder>> customizers;
    private final ClientWriter cw;

    RecordingOptionsCustomizer(ClientWriter cw) {
        this.customizers = new HashMap<>();
        this.cw = cw;

        toDisk(false);
    }

    RecordingOptionsBuilder apply(RecordingOptionsBuilder builder) {
        this.customizers.values().forEach(c -> c.accept(builder));
        return builder;
    }

    void unset(OptionKey key) {
        customizers.remove(key);
    }

    void toDisk(boolean toDisk) {
        customizers.put(OptionKey.TO_DISK, new CustomizerConsumer() {
            @Override
            public void acceptThrows(RecordingOptionsBuilder b) throws Exception {
                b.toDisk(toDisk);
            }
        });
    }

    void maxAge(long seconds) {
        customizers.put(OptionKey.MAX_AGE, new CustomizerConsumer() {
            @Override
            public void acceptThrows(RecordingOptionsBuilder b) throws Exception {
                b.maxAge(seconds);
            }
        });
    }

    void maxSize(long bytes) {
        customizers.put(OptionKey.MAX_SIZE, new CustomizerConsumer() {
            @Override
            public void acceptThrows(RecordingOptionsBuilder b) throws Exception {
                b.maxSize(bytes);
            }
        });
    }

    void destinationCompressed(boolean compressed) {
        customizers.put(OptionKey.DESTINATION_COMPRESSED, new CustomizerConsumer() {
            @Override
            public void acceptThrows(RecordingOptionsBuilder b) throws Exception {
                b.destinationCompressed(compressed);
            }
        });
    }

    enum OptionKey {
        DESTINATION_COMPRESSED("destinationCompressed"),
        MAX_AGE("maxAge"),
        MAX_SIZE("maxSize"),
        TO_DISK("toDisk"),
        ;

        private final String optionName;

        OptionKey(String optionName) {
            this.optionName = optionName;
        }

        static Optional<OptionKey> fromOptionName(String optionName) {
            OptionKey key = null;
            for (OptionKey k : OptionKey.values()) {
                if (k.optionName.equals(optionName)) {
                    key = k;
                }
            }
            return Optional.ofNullable(key);
        }
    }

    private abstract class CustomizerConsumer implements CheckedConsumer<RecordingOptionsBuilder> {
        @Override
        public void handleException(Exception e) {
            cw.println(e);
        }
    }

}