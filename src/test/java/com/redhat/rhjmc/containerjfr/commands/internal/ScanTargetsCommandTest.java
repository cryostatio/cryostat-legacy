/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
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
package com.redhat.rhjmc.containerjfr.commands.internal;

import java.util.List;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.platform.PlatformClient;
import com.redhat.rhjmc.containerjfr.platform.ServiceRef;

@ExtendWith(MockitoExtension.class)
class ScanTargetsCommandTest {

    ScanTargetsCommand command;
    @Mock PlatformClient platformClient;
    @Mock ClientWriter cw;

    @BeforeEach
    void setup() {
        this.command = new ScanTargetsCommand(platformClient, cw);
    }

    @Test
    void shouldBeAvailable() {
        Assertions.assertTrue(command.isAvailable());
    }

    @Test
    void shouldBeNamedScanTargets() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("scan-targets"));
    }

    @Test
    void shouldNotExpectArgs() {
        Exception e =
                Assertions.assertThrows(
                        FailedValidationException.class, () -> command.validate(new String[1]));
        String errorMessage = "No arguments expected";
        Mockito.verify(cw).println(errorMessage);
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldExpectNoArgs() {
        Assertions.assertDoesNotThrow(() -> command.validate(new String[0]));
        Mockito.verifyNoMoreInteractions(cw);
    }

    @Test
    void shouldPrintDiscoveredServices() throws Exception {
        Mockito.when(platformClient.listDiscoverableServices()).thenReturn(getMockServices());
        command.execute(new String[0]);
        InOrder inOrder = Mockito.inOrder(cw);
        inOrder.verify(cw).println("Host A -> service:jmx:jmxmp://aHost");
        inOrder.verify(cw).println("Host B -> service:jmx:jmxmp://bHost");
        inOrder.verify(cw).println("Host C -> service:jmx:jmxmp://cHost");
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void shouldDoNothingIfNoDiscoveredServices() throws Exception {
        Mockito.when(platformClient.listDiscoverableServices()).thenReturn(List.of());
        command.execute(new String[0]);
        Mockito.verifyZeroInteractions(cw);
    }

    @Test
    void shouldThrowIfPlatformClientThrows() throws Exception {
        Mockito.when(platformClient.listDiscoverableServices())
                .thenThrow(NullPointerException.class);
        Assertions.assertThrows(NullPointerException.class, () -> command.execute(new String[0]));
    }

    @Test
    void shouldExecuteAndReturnListOfDiscoveredServices() throws Exception {
        Mockito.when(platformClient.listDiscoverableServices()).thenReturn(getMockServices());
        SerializableCommand.Output output = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(output, Matchers.instanceOf(SerializableCommand.ListOutput.class));
        MatcherAssert.assertThat(
                ((SerializableCommand.ListOutput) output).getPayload(),
                Matchers.equalTo(getMockServices()));
    }

    @Test
    void shouldExecuteAndReturnExceptionOutputIfPlatformClientThrows() throws Exception {
        Mockito.when(platformClient.listDiscoverableServices())
                .thenThrow(NullPointerException.class);
        SerializableCommand.Output output = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(
                output, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));
    }

    List<ServiceRef> getMockServices() {
        ServiceRef mockA;
        ServiceRef mockB;
        ServiceRef mockC;
        try {
            mockA = new ServiceRef("aHost", 0, "Host A");
            mockB = new ServiceRef("bHost", 0, "Host B");
            mockC = new ServiceRef("cHost", 0, "Host C");
        } catch (Exception e) {
            mockA = null;
            mockB = null;
            mockC = null;
        }

        return List.of(mockA, mockB, mockC);
    }
}
