package es.andrewazor.containertest.commands.internal;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;

import es.andrewazor.containertest.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class RecordingOptionsCustomizerTest {

    RecordingOptionsCustomizer customizer;
    @Mock
    ClientWriter cw;
    @Mock
    RecordingOptionsBuilder builder;

    @BeforeEach
    void setup() {
        customizer = new RecordingOptionsCustomizer(cw);
    }

    @Test
    void shouldDisableToDiskByDefault() throws QuantityConversionException {
        customizer.apply(builder);
        verify(builder).toDisk(false);
        verifyNoMoreInteractions(builder);
    }

    @Test
    void shouldApplyToDisk() throws QuantityConversionException {
        customizer.toDisk(true);
        customizer.apply(builder);
        verify(builder).toDisk(true);
        verifyNoMoreInteractions(builder);
    }

    @Test
    void shouldApplyMaxAge() throws QuantityConversionException {
        customizer.maxAge(123);
        customizer.apply(builder);
        verifyDefaults();
        verify(builder).maxAge(123);
        verifyNoMoreInteractions(builder);
    }

    @Test
    void shouldMutateAndUndoChangesInternally() throws QuantityConversionException {
        customizer.maxAge(123);
        customizer.maxAge(456);
        customizer.apply(builder);
        verifyDefaults();
        verify(builder).maxAge(456);
        verifyNoMoreInteractions(builder);
    }

    @Test
    void shouldApplyMaxSize() throws QuantityConversionException {
        customizer.maxSize(123);
        customizer.apply(builder);
        verifyDefaults();
        verify(builder).maxSize(123);
        verifyNoMoreInteractions(builder);
    }

    @Test
    void shouldApplyDestinationCompressed() throws QuantityConversionException {
        customizer.destinationCompressed(true);
        customizer.apply(builder);
        verifyDefaults();
        verify(builder).destinationCompressed(true);
        verifyNoMoreInteractions(builder);
    }

    private void verifyDefaults() throws QuantityConversionException {
        verify(builder).toDisk(false);
    }

}