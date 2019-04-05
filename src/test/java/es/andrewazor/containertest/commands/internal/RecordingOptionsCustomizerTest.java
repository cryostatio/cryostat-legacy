package es.andrewazor.containertest.commands.internal;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
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
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldApplyToDisk() throws QuantityConversionException {
        customizer.toDisk(true);
        customizer.apply(builder);
        verify(builder).toDisk(true);
        verifyNoMoreInteractions(builder);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldApplyMaxAge() throws QuantityConversionException {
        customizer.maxAge(123);
        customizer.apply(builder);
        verifyDefaults();
        verify(builder).maxAge(123);
        verifyNoMoreInteractions(builder);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldMutateAndUndoChangesInternally() throws QuantityConversionException {
        customizer.maxAge(123);
        customizer.maxAge(456);
        customizer.apply(builder);
        verifyDefaults();
        verify(builder).maxAge(456);
        verifyNoMoreInteractions(builder);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldApplyMaxSize() throws QuantityConversionException {
        customizer.maxSize(123);
        customizer.apply(builder);
        verifyDefaults();
        verify(builder).maxSize(123);
        verifyNoMoreInteractions(builder);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldApplyDestinationCompressed() throws QuantityConversionException {
        customizer.destinationCompressed(true);
        customizer.apply(builder);
        verifyDefaults();
        verify(builder).destinationCompressed(true);
        verifyNoMoreInteractions(builder);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldPrintExceptions() throws QuantityConversionException {
        when(builder.destinationCompressed(ArgumentMatchers.anyBoolean())).thenThrow(NullPointerException.class);
        customizer.destinationCompressed(true);
        customizer.apply(builder);
        verify(cw).println(ArgumentMatchers.any(NullPointerException.class));
        verifyNoMoreInteractions(cw);
    }

    private void verifyDefaults() throws QuantityConversionException {
        verify(builder).toDisk(false);
    }

}