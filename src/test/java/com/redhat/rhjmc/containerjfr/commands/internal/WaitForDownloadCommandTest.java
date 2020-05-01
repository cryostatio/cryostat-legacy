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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.TestBase;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.Clock;
import com.redhat.rhjmc.containerjfr.net.web.WebServer;

@ExtendWith(MockitoExtension.class)
class WaitForDownloadCommandTest extends TestBase {

    WaitForDownloadCommand command;
    @Mock WebServer exporter;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;
    @Mock Clock clock;

    @BeforeEach
    void setup() {
        command = new WaitForDownloadCommand(mockClientWriter, clock, exporter);
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedWaitForDownload() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("wait-for-download"));
    }

    @Test
    void shouldBeAvailable() {
        assertTrue(command.isAvailable());
    }

    @Test
    void shouldNotExpectNoArgs() {
        assertFalse(command.validate(new String[0]));
        MatcherAssert.assertThat(stdout(), Matchers.equalTo("Expected one argument\n"));
    }

    @Test
    void shouldNotExpectTwoArgs() {
        assertFalse(command.validate(new String[2]));
        MatcherAssert.assertThat(stdout(), Matchers.equalTo("Expected one argument\n"));
    }

    @Test
    void shouldNotExpectMalformedRecordingNameArg() {
        assertFalse(command.validate(new String[] {"."}));
        MatcherAssert.assertThat(stdout(), Matchers.equalTo(". is an invalid recording name\n"));
    }

    @Test
    void shouldValidateRecordingNameArg() {
        assertTrue(command.validate(new String[] {"foo"}));
        MatcherAssert.assertThat(stdout(), Matchers.emptyString());
    }

    @Test
    void shouldHandleRecordingNotFound() throws Exception {
        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
        verifyZeroInteractions(exporter);

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());

        command.execute(new String[] {"foo"});

        verify(service).getAvailableRecordings();
        MatcherAssert.assertThat(
                stdout(),
                Matchers.equalTo("Recording with name \"foo\" not found in target JVM\n"));

        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(exporter);
    }

    @Test
    void shouldWaitUntilRecordingDownloaded() throws Exception {
        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
        verifyZeroInteractions(exporter);

        IRecordingDescriptor recordingDescriptor = mock(IRecordingDescriptor.class);
        when(recordingDescriptor.getName()).thenReturn("foo");

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings())
                .thenReturn(Collections.singletonList(recordingDescriptor));
        when(exporter.getDownloadCount(Mockito.anyString())).thenReturn(0).thenReturn(1);
        when(exporter.getDownloadURL(Mockito.anyString())).thenReturn("download-url");

        command.execute(new String[] {"foo"});

        verify(service).getAvailableRecordings();

        MatcherAssert.assertThat(
                stdout(),
                Matchers.equalTo("Waiting for download of recording \"foo\" at download-url\n"));

        ArgumentCaptor<String> downloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(exporter, Mockito.times(2)).getDownloadCount(downloadCaptor.capture());
        MatcherAssert.assertThat(downloadCaptor.getValue(), Matchers.equalTo("foo"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(exporter).getDownloadURL(urlCaptor.capture());
        MatcherAssert.assertThat(urlCaptor.getValue(), Matchers.equalTo("foo"));

        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(exporter);
    }
}
