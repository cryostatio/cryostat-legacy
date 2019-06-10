package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.commands.SerializableCommand.ExceptionOutput;
import es.andrewazor.containertest.commands.SerializableCommand.ListOutput;
import es.andrewazor.containertest.commands.SerializableCommand.Output;
import es.andrewazor.containertest.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import es.andrewazor.containertest.net.JMCConnection;
import es.andrewazor.containertest.net.RecordingExporter;
import es.andrewazor.containertest.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class ListCommandTest {

    ListCommand command;
    @Mock
    ClientWriter cw;
    @Mock
    JMCConnection connection;
    @Mock
    IFlightRecorderService service;
    @Mock
    RecordingExporter exporter;

    @BeforeEach
    void setup() {
        command = new ListCommand(cw, exporter);
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedList() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("list"));
    }

    @Test
    void shouldExpectNoArgs() {
        assertTrue(command.validate(new String[0]));
    }

    @Test
    void shouldNotExpectArgs() {
        assertFalse(command.validate(new String[1]));
    }

    @Test
    void shouldHandleNoRecordings() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());
        command.execute(new String[0]);
        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("Available recordings:");
        inOrder.verify(cw).println("\tNone");
    }

    @Test
    void shouldPrintRecordingNames() throws Exception {
        when(connection.getService()).thenReturn(service);
        List<IRecordingDescriptor> descriptors = Arrays.asList(createDescriptor("foo"), createDescriptor("bar"));
        when(service.getAvailableRecordings()).thenReturn(descriptors);
        command.execute(new String[0]);
        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("Available recordings:");
        inOrder.verify(cw).println(Mockito.contains("getName\t\tfoo"));
        inOrder.verify(cw).println(Mockito.contains("getName\t\tbar"));
    }

    @Test
    void shouldReturnListOutput() throws Exception {
        when(connection.getService()).thenReturn(service);
        List<IRecordingDescriptor> descriptors = Arrays.asList(createDescriptor("foo"), createDescriptor("bar"));
        when(service.getAvailableRecordings()).thenReturn(descriptors);
        when(exporter.getDownloadURL(Mockito.anyString())).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                return String.format("http://example.com:1234/%s", invocation.getArguments()[0]);
            }
        });
        when(exporter.getReportURL(Mockito.anyString())).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                return String.format("http://example.com:1234/reports/%s", invocation.getArguments()[0]);
            }
        });

        Output<?> out = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(out, Matchers.instanceOf(ListOutput.class));
        MatcherAssert.assertThat(out.getPayload(),
                Matchers.equalTo(Arrays.asList(
                        new HyperlinkedSerializableRecordingDescriptor(createDescriptor("foo"),
                                "http://example.com:1234/foo", "http://example.com:1234/reports/foo"),
                        new HyperlinkedSerializableRecordingDescriptor(createDescriptor("bar"),
                                "http://example.com:1234/bar", "http://example.com:1234/reports/bar"))));
    }

    @Test
    void shouldReturnExceptionOutput() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenThrow(FlightRecorderException.class);

        Output<?> out = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(out, Matchers.instanceOf(ExceptionOutput.class));
        MatcherAssert.assertThat(out.getPayload(), Matchers.equalTo("FlightRecorderException: "));
    }

    private static IRecordingDescriptor createDescriptor(String name) throws QuantityConversionException {
        IQuantity zeroQuantity = mock(IQuantity.class);
        IRecordingDescriptor descriptor = mock(IRecordingDescriptor.class);
        when(descriptor.getId()).thenReturn(1L);
        when(descriptor.getName()).thenReturn(name);
        when(descriptor.getState()).thenReturn(IRecordingDescriptor.RecordingState.STOPPED);
        when(descriptor.getStartTime()).thenReturn(zeroQuantity);
        when(descriptor.getDuration()).thenReturn(zeroQuantity);
        when(descriptor.isContinuous()).thenReturn(false);
        when(descriptor.getToDisk()).thenReturn(false);
        when(descriptor.getMaxSize()).thenReturn(zeroQuantity);
        when(descriptor.getMaxAge()).thenReturn(zeroQuantity);
        return descriptor;
    }

}