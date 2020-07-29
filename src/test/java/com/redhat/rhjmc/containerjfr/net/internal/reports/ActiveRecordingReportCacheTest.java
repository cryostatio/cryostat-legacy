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
package com.redhat.rhjmc.containerjfr.net.internal.reports;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.reports.ReportGenerator;
import com.redhat.rhjmc.containerjfr.net.ConnectionDescriptor;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.internal.reports.ReportService.RecordingNotFoundException;

@ExtendWith(MockitoExtension.class)
class ActiveRecordingReportCacheTest {

    ActiveRecordingReportCache cache;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock ReportGenerator reportGenerator;
    @Mock ReentrantLock lock;
    @Mock Logger logger;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;

    @BeforeEach
    void setup() {
        this.cache =
                new ActiveRecordingReportCache(
                        targetConnectionManager, reportGenerator, lock, logger);
    }

    @Test
    void shouldReturnFalseWhenDeletingNonExistentReport() {
        Assertions.assertFalse(cache.delete(new ConnectionDescriptor("foo"), "bar"));
    }

    @Test
    void shouldReturnTrueWhenDeletingReport() throws Exception {
        Mockito.when(targetConnectionManager.connect(Mockito.any(ConnectionDescriptor.class)))
                .thenReturn(connection);
        Mockito.when(connection.getService()).thenReturn(service);

        String recordingName = "bar";
        IRecordingDescriptor recording = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(recording.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(recording));

        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(service.openStream(Mockito.any(), Mockito.anyBoolean())).thenReturn(stream);

        Mockito.when(reportGenerator.generateReport(Mockito.any()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock args) throws Throwable {
                                return "Generated Report";
                            }
                        });

        String targetId = "foo";

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        cache.get(connectionDescriptor, recordingName);
        Assertions.assertTrue(cache.delete(connectionDescriptor, recordingName));
    }

    @Test
    void shouldReturnGeneratedReportResult() throws Exception {
        Mockito.when(targetConnectionManager.connect(Mockito.any(ConnectionDescriptor.class)))
                .thenReturn(connection);
        Mockito.when(connection.getService()).thenReturn(service);

        String recordingName = "bar";
        IRecordingDescriptor recording = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(recording.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(recording));

        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(service.openStream(Mockito.any(), Mockito.anyBoolean())).thenReturn(stream);

        Mockito.when(reportGenerator.generateReport(Mockito.any()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock args) throws Throwable {
                                return "Generated Report";
                            }
                        });

        String targetId = "foo";

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        String report = cache.get(connectionDescriptor, recordingName);
        MatcherAssert.assertThat(report, Matchers.equalTo("Generated Report"));

        InOrder inOrder =
                Mockito.inOrder(
                        connection,
                        service,
                        targetConnectionManager,
                        reportGenerator,
                        lock,
                        stream);
        inOrder.verify(lock).lock();

        inOrder.verify(targetConnectionManager).connect(Mockito.any(ConnectionDescriptor.class));

        inOrder.verify(connection).getService();
        inOrder.verify(service).openStream(Mockito.eq(recording), Mockito.eq(false));

        inOrder.verify(reportGenerator).generateReport(Mockito.eq(stream));

        inOrder.verify(stream).close();
        inOrder.verify(connection).close();

        inOrder.verify(lock).unlock();
    }

    @Test
    void shouldReturnCachedReportResultOnSecondRequest() throws Exception {
        Mockito.when(targetConnectionManager.connect(Mockito.any(ConnectionDescriptor.class)))
                .thenReturn(connection);
        Mockito.when(connection.getService()).thenReturn(service);

        String recordingName = "bar";
        IRecordingDescriptor recording = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(recording.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(recording));

        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(service.openStream(Mockito.any(), Mockito.anyBoolean())).thenReturn(stream);

        Mockito.when(reportGenerator.generateReport(Mockito.any()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock args) throws Throwable {
                                return "Generated Report";
                            }
                        });

        String targetId = "foo";

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        String report1 = cache.get(connectionDescriptor, recordingName);
        MatcherAssert.assertThat(report1, Matchers.equalTo("Generated Report"));
        String report2 = cache.get(connectionDescriptor, recordingName);
        MatcherAssert.assertThat(report2, Matchers.equalTo(report1));

        InOrder inOrder =
                Mockito.inOrder(
                        connection,
                        service,
                        targetConnectionManager,
                        reportGenerator,
                        lock,
                        stream);
        inOrder.verify(lock, Mockito.times(1)).lock();

        inOrder.verify(targetConnectionManager, Mockito.times(1))
                .connect(Mockito.any(ConnectionDescriptor.class));

        inOrder.verify(connection, Mockito.times(1)).getService();
        inOrder.verify(service, Mockito.times(1))
                .openStream(Mockito.eq(recording), Mockito.eq(false));

        inOrder.verify(reportGenerator, Mockito.times(1)).generateReport(Mockito.eq(stream));

        inOrder.verify(stream, Mockito.times(1)).close();
        inOrder.verify(connection, Mockito.times(1)).close();

        inOrder.verify(lock, Mockito.times(1)).unlock();
    }

    @Test
    void shouldThrowExceptionIfRecordingNotFound() throws Exception {
        Mockito.when(targetConnectionManager.connect(Mockito.any(ConnectionDescriptor.class)))
                .thenReturn(connection);
        Mockito.when(connection.getService()).thenReturn(service);

        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of());

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("foo");
        Assertions.assertThrows(
                RecordingNotFoundException.class, () -> cache.get(connectionDescriptor, "bar"));
    }

    @Test
    void shouldThrowExceptionIfServiceThrows() throws Exception {
        Mockito.when(targetConnectionManager.connect(Mockito.any(ConnectionDescriptor.class)))
                .thenReturn(connection);
        Mockito.when(connection.getService()).thenReturn(service);

        String recordingName = "bar";
        IRecordingDescriptor recording = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(recording.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(recording));

        Mockito.when(service.openStream(Mockito.any(), Mockito.anyBoolean()))
                .thenThrow(FlightRecorderException.class);

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("foo");
        Assertions.assertThrows(
                RecordingNotFoundException.class, () -> cache.get(connectionDescriptor, "bar"));
    }
}
