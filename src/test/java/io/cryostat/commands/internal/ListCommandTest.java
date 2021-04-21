/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 The Cryostat Authors
 * %%
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * #L%
 */
package io.cryostat.commands.internal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import io.cryostat.commands.Command;
import io.cryostat.commands.SerializableCommand;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.tui.ClientWriter;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.TargetConnectionManager.ConnectedTask;
import io.cryostat.net.web.WebServer;

@ExtendWith(MockitoExtension.class)
class ListCommandTest implements ValidatesTargetId {

    ListCommand command;
    @Mock ClientWriter cw;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;
    @Mock WebServer exporter;

    @Override
    public Command commandForValidationTesting() {
        return command;
    }

    @Override
    public List<String> argumentSignature() {
        return List.of(TARGET_ID);
    }

    @BeforeEach
    void setup() {
        command = new ListCommand(cw, targetConnectionManager, exporter);
    }

    @Test
    void shouldBeAvailable() {
        Assertions.assertTrue(command.isAvailable());
    }

    @Test
    void shouldBeNamedList() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("list"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void shouldNotValidateIncorrectArgc(int argc) {
        Exception e =
                assertThrows(
                        FailedValidationException.class, () -> command.validate(new String[argc]));
        String errorMessage = "Expected one argument: hostname:port, ip:port, or JMX service URL";
        verify(cw).println(errorMessage);
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldNotValidateNullArg() {
        Exception e =
                Assertions.assertThrows(
                        FailedValidationException.class,
                        () -> command.validate(new String[] {null}));
        String errorMessage = "One or more arguments were null";
        Mockito.verify(cw).println(errorMessage);
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldHandleNoRecordings() throws Exception {
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());
        command.execute(new String[] {"foo:9091"});
        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("Available recordings:");
        inOrder.verify(cw).println("\tNone");
    }

    @Test
    void shouldPrintRecordingNames() throws Exception {
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        List<IRecordingDescriptor> descriptors =
                Arrays.asList(createDescriptor("foo"), createDescriptor("bar"));
        when(service.getAvailableRecordings()).thenReturn(descriptors);
        command.execute(new String[] {"foo:9091"});
        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("Available recordings:");
        inOrder.verify(cw).println(Mockito.contains("getName\t\tfoo"));
        inOrder.verify(cw).println(Mockito.contains("getName\t\tbar"));
    }

    @Test
    void shouldPrintDownloadURL() throws Exception {
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(connection.getHost()).thenReturn("fooHost");
        when(connection.getPort()).thenReturn(1);
        List<IRecordingDescriptor> descriptors =
                Arrays.asList(createDescriptor("foo"), createDescriptor("bar"));
        when(service.getAvailableRecordings()).thenReturn(descriptors);
        when(exporter.getDownloadURL(Mockito.any(JFRConnection.class), Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return String.format(
                                        "http://example.com:1234/api/v1/targets/%s:%d/recordings/%s",
                                        ((JFRConnection) invocation.getArguments()[0]).getHost(),
                                        ((JFRConnection) invocation.getArguments()[0]).getPort(),
                                        invocation.getArguments()[1]);
                            }
                        });

        command.execute(new String[] {"foo:9091"});
        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("Available recordings:");
        inOrder.verify(cw)
                .println(
                        Mockito.contains(
                                "\tgetDownloadUrl\t\thttp://example.com:1234/api/v1/targets/fooHost:1/recordings/foo"));
        inOrder.verify(cw)
                .println(
                        Mockito.contains(
                                "\tgetDownloadUrl\t\thttp://example.com:1234/api/v1/targets/fooHost:1/recordings/bar"));
    }

    @Test
    void shouldPrintReportURL() throws Exception {
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(connection.getHost()).thenReturn("fooHost");
        when(connection.getPort()).thenReturn(1);
        List<IRecordingDescriptor> descriptors =
                Arrays.asList(createDescriptor("foo"), createDescriptor("bar"));
        when(service.getAvailableRecordings()).thenReturn(descriptors);
        when(exporter.getReportURL(Mockito.any(JFRConnection.class), Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return String.format(
                                        "http://example.com:1234/api/v1/targets/%s:%d/reports/%s",
                                        ((JFRConnection) invocation.getArguments()[0]).getHost(),
                                        ((JFRConnection) invocation.getArguments()[0]).getPort(),
                                        invocation.getArguments()[1]);
                            }
                        });
        command.execute(new String[] {"foo:9091"});
        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("Available recordings:");
        inOrder.verify(cw)
                .println(
                        Mockito.contains(
                                "\tgetReportUrl\t\thttp://example.com:1234/api/v1/targets/fooHost:1/reports/foo"));
        inOrder.verify(cw)
                .println(
                        Mockito.contains(
                                "\tgetReportUrl\t\thttp://example.com:1234/api/v1/targets/fooHost:1/reports/bar"));
    }

    @Test
    void shouldReturnListOutput() throws Exception {
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(connection.getHost()).thenReturn("fooHost");
        when(connection.getPort()).thenReturn(1);
        List<IRecordingDescriptor> descriptors =
                Arrays.asList(createDescriptor("foo"), createDescriptor("bar"));
        when(service.getAvailableRecordings()).thenReturn(descriptors);
        when(exporter.getDownloadURL(Mockito.any(JFRConnection.class), Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return String.format(
                                        "http://example.com:1234/api/v1/targets/%s:%d/recordings/%s",
                                        ((JFRConnection) invocation.getArguments()[0]).getHost(),
                                        ((JFRConnection) invocation.getArguments()[0]).getPort(),
                                        invocation.getArguments()[1]);
                            }
                        });
        when(exporter.getReportURL(Mockito.any(JFRConnection.class), Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return String.format(
                                        "http://example.com:1234/api/v1/targets/%s:%d/reports/%s",
                                        ((JFRConnection) invocation.getArguments()[0]).getHost(),
                                        ((JFRConnection) invocation.getArguments()[0]).getPort(),
                                        invocation.getArguments()[1]);
                            }
                        });

        SerializableCommand.Output<?> out = command.serializableExecute(new String[] {"foo:9091"});
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.ListOutput.class));
        MatcherAssert.assertThat(
                out.getPayload(),
                Matchers.equalTo(
                        Arrays.asList(
                                new HyperlinkedSerializableRecordingDescriptor(
                                        createDescriptor("foo"),
                                        "http://example.com:1234/api/v1/targets/fooHost:1/recordings/foo",
                                        "http://example.com:1234/api/v1/targets/fooHost:1/reports/foo"),
                                new HyperlinkedSerializableRecordingDescriptor(
                                        createDescriptor("bar"),
                                        "http://example.com:1234/api/v1/targets/fooHost:1/recordings/bar",
                                        "http://example.com:1234/api/v1/targets/fooHost:1/reports/bar"))));
    }

    @Test
    void shouldReturnExceptionOutput() throws Exception {
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenThrow(FlightRecorderException.class);

        SerializableCommand.Output<?> out = command.serializableExecute(new String[] {"foo:9091"});
        MatcherAssert.assertThat(
                out, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));
        MatcherAssert.assertThat(out.getPayload(), Matchers.equalTo("FlightRecorderException: "));
    }

    private static IRecordingDescriptor createDescriptor(String name)
            throws QuantityConversionException {
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
