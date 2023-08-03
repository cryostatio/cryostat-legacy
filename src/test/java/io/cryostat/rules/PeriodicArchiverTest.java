/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.rules;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.platform.ServiceRef;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PeriodicArchiverTest {

    PeriodicArchiver archiver;
    String jmxUrl = "service:jmx:rmi://localhost:9091/jndi/rmi://fooHost:9091/jmxrmi";
    ServiceRef serviceRef;
    @Mock CredentialsManager credentialsManager;
    Rule rule;
    @Mock RecordingArchiveHelper recordingArchiveHelper;
    AtomicInteger failureCounter;
    @Mock Logger logger;
    @Mock Queue<String> previousRecordings;

    @BeforeEach
    void setup() throws Exception {
        this.serviceRef = new ServiceRef("id1", new URI(jmxUrl), "com.example.App");
        this.failureCounter = new AtomicInteger();
        this.rule =
                new Rule.Builder()
                        .name("Test Rule")
                        .description("Automated unit test rule")
                        .matchExpression("target.alias=='com.example.App'")
                        .eventSpecifier("template=Continuous")
                        .maxAgeSeconds(30)
                        .maxSizeBytes(1234)
                        .preservedArchives(2)
                        .archivalPeriodSeconds(67)
                        .build();
        this.archiver =
                new PeriodicArchiver(
                        serviceRef,
                        credentialsManager,
                        rule,
                        recordingArchiveHelper,
                        p -> {
                            failureCounter.incrementAndGet();
                            return null;
                        },
                        logger);
    }

    @Test
    void testPerformArchival() throws Exception {
        CompletableFuture<List<ArchivedRecordingInfo>> listFuture = new CompletableFuture<>();
        listFuture.complete(new ArrayList<>());
        Mockito.when(recordingArchiveHelper.getRecordings(jmxUrl)).thenReturn(listFuture);

        CompletableFuture<ArchivedRecordingInfo> infoFuture = new CompletableFuture<>();
        infoFuture.complete(Mockito.mock(ArchivedRecordingInfo.class));
        Mockito.when(recordingArchiveHelper.saveRecording(Mockito.any(), Mockito.anyString()))
                .thenReturn(infoFuture);

        archiver.run();

        Mockito.verify(credentialsManager).getCredentials(serviceRef);
        Mockito.verify(recordingArchiveHelper).saveRecording(Mockito.any(), Mockito.anyString());
    }

    @Test
    void testNotifyOnConnectionFailure() throws Exception {
        CompletableFuture<List<ArchivedRecordingInfo>> listFuture = new CompletableFuture<>();
        listFuture.complete(new ArrayList<>());
        Mockito.when(recordingArchiveHelper.getRecordings(jmxUrl)).thenReturn(listFuture);

        Mockito.doThrow(SecurityException.class)
                .when(recordingArchiveHelper)
                .saveRecording(Mockito.any(), Mockito.any());
        MatcherAssert.assertThat(failureCounter.intValue(), Matchers.equalTo(0));

        archiver.run();

        MatcherAssert.assertThat(failureCounter.intValue(), Matchers.equalTo(1));
    }

    @Test
    void testPruneArchive() throws Exception {
        CompletableFuture<List<ArchivedRecordingInfo>> listFuture = new CompletableFuture<>();
        listFuture.complete(new ArrayList<>());
        Mockito.when(recordingArchiveHelper.getRecordings(jmxUrl)).thenReturn(listFuture);

        CompletableFuture<ArchivedRecordingInfo> saveFuture = new CompletableFuture<>();
        ArchivedRecordingInfo info = Mockito.mock(ArchivedRecordingInfo.class);
        saveFuture.complete(info);
        Mockito.when(info.getName()).thenReturn("someRecording");
        Mockito.when(recordingArchiveHelper.saveRecording(Mockito.any(), Mockito.anyString()))
                .thenReturn(saveFuture);

        CompletableFuture<ArchivedRecordingInfo> deleteFuture = new CompletableFuture<>();
        deleteFuture.complete(Mockito.mock(ArchivedRecordingInfo.class));
        Mockito.when(
                        recordingArchiveHelper.deleteRecording(
                                Mockito.anyString(), Mockito.anyString()))
                .thenReturn(deleteFuture);

        // get the archiver into a state where it has reached its limit of preserved recordings
        for (int i = 0; i < rule.getPreservedArchives(); i++) {
            archiver.run();
        }

        archiver.run();

        Mockito.verify(credentialsManager, Mockito.times(3)).getCredentials(serviceRef);
        Mockito.verify(recordingArchiveHelper, Mockito.times(3))
                .saveRecording(Mockito.any(), Mockito.anyString());
        Mockito.verify(recordingArchiveHelper, Mockito.times(1))
                .deleteRecording(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void testArchiveScanning() throws Exception {
        // populate the archive with various recordings, two of which are for  the current target
        // (based on the encoded serviceUri), with only one of those two having a recording name
        // that matches the Rule in question
        String matchingFileName =
                String.format("targetFoo_%s_20200903T202547Z.jfr", rule.getRecordingName());
        CompletableFuture<List<ArchivedRecordingInfo>> listFuture = new CompletableFuture<>();
        listFuture.complete(
                List.of(
                        new ArchivedRecordingInfo(
                                jmxUrl,
                                "targetFoo_recordingFoo_20210101T202547Z.jfr",
                                "/some/path/download/recordingFoo",
                                "/some/path/archive/recordingFoo",
                                new Metadata(),
                                0,
                                0),
                        new ArchivedRecordingInfo(
                                "aJmxUrl",
                                "targetA_recordingA_20190801T202547Z.jfr",
                                "/some/path/download/recordingA",
                                "/some/path/archive/recordingA",
                                new Metadata(),
                                0,
                                0),
                        new ArchivedRecordingInfo(
                                "bJmxUrl",
                                "target123_123recording_20211107T202547Z.jfr",
                                "/some/path/download/123recording",
                                "/some/path/archive/123recording",
                                new Metadata(),
                                0,
                                0),
                        new ArchivedRecordingInfo(
                                jmxUrl,
                                matchingFileName,
                                String.format("/some/path/download/%s", rule.getRecordingName()),
                                String.format("/some/path/archive/%s", rule.getRecordingName()),
                                new Metadata(),
                                0,
                                0)));
        Mockito.when(recordingArchiveHelper.getRecordings(jmxUrl)).thenReturn(listFuture);

        CompletableFuture<ArchivedRecordingInfo> saveFuture = new CompletableFuture<>();
        ArchivedRecordingInfo newlySavedRecording =
                new ArchivedRecordingInfo(
                        jmxUrl,
                        "someRecording.jfr",
                        "/some/path/download/someRecording.jfr",
                        "/some/path/archive/someRecording.jfr",
                        new Metadata(),
                        0,
                        0);
        saveFuture.complete(newlySavedRecording);
        Mockito.when(recordingArchiveHelper.saveRecording(Mockito.any(), Mockito.anyString()))
                .thenReturn(saveFuture);

        archiver.run();

        // if the archived recordings were scanned properly the first entry should be the matching
        // file name above, followed by the newly saved "someRecording.jfr"
        Queue<String> previousRecordings = archiver.getPreviousRecordings();
        Assertions.assertEquals(matchingFileName, previousRecordings.remove());
        Assertions.assertEquals(newlySavedRecording.getName(), previousRecordings.remove());

        Mockito.verify(credentialsManager).getCredentials(serviceRef);
        Mockito.verify(recordingArchiveHelper).saveRecording(Mockito.any(), Mockito.anyString());
    }
}
