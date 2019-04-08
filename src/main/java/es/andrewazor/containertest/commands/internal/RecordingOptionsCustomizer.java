package es.andrewazor.containertest.commands.internal;

import java.util.HashMap;
import java.util.Map;
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

    void destinationFile(String file) {
        customizers.put(OptionKey.DESTINATION_FILE, new CustomizerConsumer() {
            @Override
            public void acceptThrows(RecordingOptionsBuilder b) throws Exception {
                b.destinationFile(file);
            }
        });
    }

    private enum OptionKey {
        DESTINATION_COMPRESSED,
        DESTINATION_FILE,
        MAX_AGE,
        MAX_SIZE,
        TO_DISK,
        ;
    }

    private abstract class CustomizerConsumer implements CheckedConsumer<RecordingOptionsBuilder> {
        @Override
        public void handleException(Exception e) {
            cw.println(e);
        }
    }

}