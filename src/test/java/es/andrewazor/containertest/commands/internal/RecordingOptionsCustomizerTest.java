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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;

import es.andrewazor.containertest.commands.internal.RecordingOptionsCustomizer.OptionKey;
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
    void shouldApplyToDisk() throws QuantityConversionException {
        customizer.set(OptionKey.TO_DISK, "true");
        customizer.apply(builder);
        verify(builder).toDisk(true);
        verifyNoMoreInteractions(builder);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldApplyMaxAge() throws QuantityConversionException {
        customizer.set(OptionKey.MAX_AGE, "123");
        customizer.apply(builder);
        verify(builder).maxAge(123);
        verifyNoMoreInteractions(builder);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldMutateAndUndoChangesInternally() throws QuantityConversionException {
        customizer.set(OptionKey.MAX_AGE, "123");
        customizer.set(OptionKey.MAX_AGE, "456");
        customizer.apply(builder);
        verify(builder).maxAge(456);
        verifyNoMoreInteractions(builder);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldApplyMaxSize() throws QuantityConversionException {
        customizer.set(OptionKey.MAX_SIZE, "123");
        customizer.apply(builder);
        verify(builder).maxSize(123);
        verifyNoMoreInteractions(builder);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldUnset() throws QuantityConversionException {
        customizer.set(OptionKey.MAX_SIZE, "123");
        customizer.unset(OptionKey.MAX_SIZE);
        customizer.apply(builder);
        verifyNoMoreInteractions(builder);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldPrintExceptions() throws QuantityConversionException {
        when(builder.maxSize(Mockito.anyLong())).thenThrow(UnsupportedOperationException.class);
        customizer.set(OptionKey.MAX_SIZE, "123");
        customizer.apply(builder);
        verify(cw).println(ArgumentMatchers.any(UnsupportedOperationException.class));
        verifyNoMoreInteractions(builder);
        verifyNoMoreInteractions(cw);
    }

}