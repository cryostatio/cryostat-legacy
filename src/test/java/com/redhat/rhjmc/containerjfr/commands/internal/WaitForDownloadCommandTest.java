package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;

import com.redhat.rhjmc.containerjfr.TestBase;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.Clock;
import com.redhat.rhjmc.containerjfr.net.RecordingExporter;
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

@ExtendWith(MockitoExtension.class)
class WaitForDownloadCommandTest extends TestBase {

    WaitForDownloadCommand command;
    @Mock
    RecordingExporter exporter;
    @Mock
    JFRConnection connection;
    @Mock IFlightRecorderService service;
    @Mock
    Clock clock;

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
        assertFalse(command.validate(new String[]{ "." }));
        MatcherAssert.assertThat(stdout(), Matchers.equalTo(". is an invalid recording name\n"));
    }

    @Test
    void shouldValidateRecordingNameArg() {
        assertTrue(command.validate(new String[]{ "foo" }));
        MatcherAssert.assertThat(stdout(), Matchers.emptyString());
    }

    @Test
    void shouldHandleRecordingNotFound() throws Exception {
        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
        verifyZeroInteractions(exporter);

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());

        command.execute(new String[]{ "foo" });

        verify(service).getAvailableRecordings();
        MatcherAssert.assertThat(stdout(), Matchers.equalTo("Recording with name \"foo\" not found in target JVM\n"));

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
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(recordingDescriptor));
        when(exporter.getDownloadCount(Mockito.anyString()))
                .thenReturn(0)
                .thenReturn(1);
        when(exporter.getDownloadURL(Mockito.anyString())).thenReturn("download-url");

        command.execute(new String[]{ "foo" });

        verify(service).getAvailableRecordings();

        MatcherAssert.assertThat(stdout(), Matchers.equalTo("Waiting for download of recording \"foo\" at download-url\n"));

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
