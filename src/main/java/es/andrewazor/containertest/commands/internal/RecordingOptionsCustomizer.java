package es.andrewazor.containertest.commands.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;

import es.andrewazor.containertest.tui.ClientWriter;
import es.andrewazor.containertest.util.CheckedConsumer;

class RecordingOptionsCustomizer implements Function<RecordingOptionsBuilder, RecordingOptionsBuilder> {

    private final Map<OptionKey, CustomizerConsumer> customizers;
    private final ClientWriter cw;

    RecordingOptionsCustomizer(ClientWriter cw) {
        this.customizers = new HashMap<>();
        this.cw = cw;

        set(OptionKey.TO_DISK, Boolean.toString(false));
    }

    @Override
    public RecordingOptionsBuilder apply(RecordingOptionsBuilder builder) {
        this.customizers.values().forEach(c -> c.accept(builder));
        return builder;
    }

    void set(OptionKey key, String value) {
        CustomizerConsumer consumer = key.mapper.apply(value);
        consumer.setClientWriter(cw);
        customizers.put(key, consumer);
    }

    void unset(OptionKey key) {
        customizers.remove(key);
    }

    enum OptionKey {
        MAX_AGE("maxAge", v -> new CustomizerConsumer() {
            @Override
            public void acceptThrows(RecordingOptionsBuilder t) throws Exception {
                t.maxAge(Long.parseLong(v));
            }
        }),
        MAX_SIZE("maxSize", v -> new CustomizerConsumer() {
            @Override
            public void acceptThrows(RecordingOptionsBuilder t) throws Exception {
                t.maxSize(Long.parseLong(v));
            }
        }),
        TO_DISK("toDisk", v -> new CustomizerConsumer() {
            @Override
            public void acceptThrows(RecordingOptionsBuilder t) throws Exception {
                t.toDisk(Boolean.parseBoolean(v));
            }
        }),
        ;

        private final String name;
        private final Function<String, CustomizerConsumer> mapper;

        OptionKey(String name, Function<String, CustomizerConsumer> mapper) {
            this.name = name;
            this.mapper = mapper;
        }

        static Optional<OptionKey> fromOptionName(String optionName) {
            OptionKey key = null;
            for (OptionKey k : OptionKey.values()) {
                if (k.name.equals(optionName)) {
                    key = k;
                }
            }
            return Optional.ofNullable(key);
        }
    }

    private static abstract class CustomizerConsumer implements CheckedConsumer<RecordingOptionsBuilder> {
        private Optional<ClientWriter> cw = Optional.empty();

        void setClientWriter(ClientWriter cw) {
            this.cw = Optional.of(cw);
        }

        @Override
        public void handleException(Exception e) {
            cw.ifPresent(w -> w.println(e));
        }
    }

}