package es.andrewazor.containertest.commands.internal;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;

import es.andrewazor.containertest.tui.ClientWriter;

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
        customizers.put(OptionKey.TO_DISK, b -> {
            try {
                b.toDisk(toDisk);
            } catch (QuantityConversionException e) {
                // TODO add a printException method to ClientWriter for this
                cw.println(ExceptionUtils.getStackTrace(e));
            }
        });
    }

    void maxAge(long seconds) {
        customizers.put(OptionKey.MAX_AGE, b -> {
            try {
                b.maxAge(seconds);
            } catch (QuantityConversionException e) {
                cw.println(ExceptionUtils.getStackTrace(e));
            }
        });
    }

    void maxSize(long bytes) {
        customizers.put(OptionKey.MAX_SIZE, b -> {
            try {
                b.maxSize(bytes);
            } catch (QuantityConversionException e) {
                cw.println(ExceptionUtils.getStackTrace(e));
            }
        });
    }

    void destinationCompressed(boolean compressed) {
        customizers.put(OptionKey.DESTINATION_COMPRESSED, b -> {
            try {
                b.destinationCompressed(compressed);
            } catch (QuantityConversionException e) {
                cw.println(ExceptionUtils.getStackTrace(e));
            }
        });
    }

    private enum OptionKey {
        DESTINATION_COMPRESSED,
        MAX_AGE,
        MAX_SIZE,
        TO_DISK,
        ;
    }

}