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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.FakeScheduledExecutorService;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.configuration.CredentialsManager.CredentialsEvent;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.net.CryostatFlightRecorderService;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.TargetDiscoveryEvent;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;
import io.cryostat.recordings.RecordingTargetHelper;
import io.cryostat.recordings.RecordingTargetHelper.ReplacementPolicy;
import io.cryostat.util.events.Event;
import io.cryostat.util.events.EventListener;

import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class RuleProcessorTest {

    RuleProcessor processor;
    FakeScheduledExecutorService executor;
    @Mock PlatformClient platformClient;
    @Mock RuleRegistry registry;
    @Mock CredentialsManager credentialsManager;
    @Mock RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock RecordingArchiveHelper recordingArchiveHelper;
    @Mock RecordingTargetHelper recordingTargetHelper;
    @Mock RecordingMetadataManager metadataManager;
    @Mock PeriodicArchiverFactory periodicArchiverFactory;
    @Mock Logger logger;

    @Mock JFRConnection connection;
    @Mock CryostatFlightRecorderService service;

    @BeforeEach
    void setup() {
        this.executor = Mockito.spy(new FakeScheduledExecutorService());
        this.processor =
                new RuleProcessor(
                        executor,
                        platformClient,
                        registry,
                        credentialsManager,
                        recordingOptionsBuilderFactory,
                        targetConnectionManager,
                        recordingArchiveHelper,
                        recordingTargetHelper,
                        metadataManager,
                        periodicArchiverFactory,
                        logger);
    }

    @Test
    void testStartAddsProcessorAsDiscoveryListener() {
        Mockito.verifyNoInteractions(platformClient);

        processor.start();

        Mockito.verify(platformClient).addTargetDiscoveryListener(processor);
    }

    @Test
    void testStopRemovesProcessorAsDiscoveryListener() {
        Mockito.verifyNoInteractions(platformClient);

        processor.stop();

        Mockito.verify(platformClient).removeTargetDiscoveryListener(processor);
    }

    @Test
    void testSuccessfulRuleActivationWithCredentials() throws Exception {
        RecordingOptionsBuilder recordingOptionsBuilder =
                Mockito.mock(RecordingOptionsBuilder.class);
        Mockito.when(recordingOptionsBuilder.name(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.toDisk(Mockito.anyBoolean()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.maxAge(Mockito.anyLong()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.maxSize(Mockito.anyLong()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilderFactory.create(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        IConstrainedMap<String> recordingOptions = Mockito.mock(IConstrainedMap.class);
        Mockito.when(recordingOptionsBuilder.build()).thenReturn(recordingOptions);

        Mockito.when(
                        targetConnectionManager.executeConnectedTaskAsync(
                                Mockito.any(), Mockito.any()))
                .thenAnswer(
                        arg0 ->
                                CompletableFuture.completedFuture(
                                        ((TargetConnectionManager.ConnectedTask<Object>)
                                                        arg0.getArgument(1))
                                                .execute(connection)));

        Mockito.when(connection.getService()).thenReturn(service);

        String jmxUrl = "service:jmx:rmi://localhost:9091/jndi/rmi://fooHost:9091/jmxrmi";
        ServiceRef serviceRef = new ServiceRef("id", new URI(jmxUrl), "com.example.App");

        Credentials credentials = new Credentials("foouser", "barpassword");
        Mockito.when(credentialsManager.getCredentials(serviceRef)).thenReturn(credentials);

        TargetDiscoveryEvent tde = new TargetDiscoveryEvent(EventKind.FOUND, serviceRef);

        Rule rule =
                new Rule.Builder()
                        .name("Test Rule")
                        .description("Automated unit test rule")
                        .matchExpression("target.alias == 'com.example.App'")
                        .eventSpecifier("template=Continuous")
                        .maxAgeSeconds(30)
                        .maxSizeBytes(1234)
                        .preservedArchives(5)
                        .archivalPeriodSeconds(67)
                        .build();

        Mockito.when(registry.getRules(serviceRef)).thenReturn(Set.of(rule));

        IRecordingDescriptor autoRule = Mockito.mock(IRecordingDescriptor.class);

        Mockito.when(recordingTargetHelper.getDescriptorByName(Mockito.any(), Mockito.any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(autoRule));

        Mockito.when(
                        recordingTargetHelper.startRecording(
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.anyBoolean()))
                .thenReturn(autoRule);

        Metadata metadata = new Metadata(Map.of());

        Mockito.when(metadataManager.getMetadata(Mockito.any(), Mockito.any()))
                .thenReturn(metadata);

        PeriodicArchiver periodicArchiver = Mockito.mock(PeriodicArchiver.class);
        Mockito.when(
                        periodicArchiverFactory.create(
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any()))
                .thenReturn(periodicArchiver);

        processor.accept(tde);

        Mockito.verify(recordingOptionsBuilder).name("auto_Test_Rule");
        Mockito.verify(recordingOptionsBuilder).maxAge(30);
        Mockito.verify(recordingOptionsBuilder).maxSize(1234);

        ArgumentCaptor<ReplacementPolicy> replaceCaptor =
                ArgumentCaptor.forClass(ReplacementPolicy.class);

        ArgumentCaptor<ConnectionDescriptor> connectionDescriptorCaptor =
                ArgumentCaptor.forClass(ConnectionDescriptor.class);

        ArgumentCaptor<IConstrainedMap<String>> recordingOptionsCaptor =
                ArgumentCaptor.forClass(IConstrainedMap.class);

        ArgumentCaptor<String> templateNameCaptor = ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<TemplateType> templateTypeCaptor =
                ArgumentCaptor.forClass(TemplateType.class);

        ArgumentCaptor<Metadata> metadataCaptor = ArgumentCaptor.forClass(Metadata.class);

        ArgumentCaptor<Boolean> archiveOnStopCaptor = ArgumentCaptor.forClass(Boolean.class);

        Mockito.verify(recordingTargetHelper)
                .startRecording(
                        replaceCaptor.capture(),
                        connectionDescriptorCaptor.capture(),
                        recordingOptionsCaptor.capture(),
                        templateNameCaptor.capture(),
                        templateTypeCaptor.capture(),
                        metadataCaptor.capture(),
                        archiveOnStopCaptor.capture());

        MatcherAssert.assertThat(
                replaceCaptor.getValue(), Matchers.equalTo(ReplacementPolicy.STOPPED));

        ConnectionDescriptor connectionDescriptor = connectionDescriptorCaptor.getValue();
        MatcherAssert.assertThat(
                connectionDescriptor.getTargetId(),
                Matchers.equalTo(serviceRef.getServiceUri().toString()));
        MatcherAssert.assertThat(
                connectionDescriptor.getCredentials().get(), Matchers.equalTo(credentials));

        IConstrainedMap<String> actualRecordingOptions = recordingOptionsCaptor.getValue();
        MatcherAssert.assertThat(actualRecordingOptions, Matchers.sameInstance(recordingOptions));

        MatcherAssert.assertThat(templateNameCaptor.getValue(), Matchers.equalTo("Continuous"));

        MatcherAssert.assertThat(templateTypeCaptor.getValue(), Matchers.nullValue());

        MatcherAssert.assertThat(metadataCaptor.getValue(), Matchers.equalTo(new Metadata()));

        ArgumentCaptor<Runnable> handlerCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executor)
                .scheduleAtFixedRate(
                        handlerCaptor.capture(),
                        Mockito.eq((long) rule.getInitialDelaySeconds()),
                        Mockito.eq((long) rule.getArchivalPeriodSeconds()),
                        Mockito.eq(TimeUnit.SECONDS));

        Mockito.verify(periodicArchiver, Mockito.times(1)).run();
        handlerCaptor.getValue().run();
        Mockito.verify(periodicArchiver, Mockito.times(2)).run();
    }

    @Test
    void testSuccessfulArchiverRuleActivationWithCredentials() throws Exception {
        Mockito.when(
                        targetConnectionManager.executeConnectedTaskAsync(
                                Mockito.any(), Mockito.any()))
                .thenAnswer(
                        arg0 ->
                                CompletableFuture.completedFuture(
                                        ((TargetConnectionManager.ConnectedTask<Object>)
                                                        arg0.getArgument(1))
                                                .execute(connection)));

        Mockito.when(connection.getService()).thenReturn(service);

        IRecordingDescriptor snapshot = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(snapshot.getName()).thenReturn("Snapshot-1");
        Mockito.when(service.getSnapshotRecording()).thenReturn(snapshot);

        String jmxUrl = "service:jmx:rmi://localhost:9091/jndi/rmi://fooHost:9091/jmxrmi";
        ServiceRef serviceRef = new ServiceRef("id", new URI(jmxUrl), "com.example.App");

        Credentials credentials = new Credentials("foouser", "barpassword");
        Mockito.when(credentialsManager.getCredentials(serviceRef)).thenReturn(credentials);

        TargetDiscoveryEvent tde = new TargetDiscoveryEvent(EventKind.FOUND, serviceRef);

        Rule rule =
                new Rule.Builder()
                        .name("Test Rule")
                        .description("Automated unit test rule")
                        .matchExpression("target.alias == 'com.example.App'")
                        .eventSpecifier("archive")
                        .build();

        Mockito.when(registry.getRules(serviceRef)).thenReturn(Set.of(rule));

        Mockito.when(recordingArchiveHelper.saveRecording(Mockito.any(), Mockito.any()))
                .thenReturn(
                        CompletableFuture.completedFuture(
                                Mockito.mock(ArchivedRecordingInfo.class)));

        processor.accept(tde);

        ArgumentCaptor<ConnectionDescriptor> connectionDescriptorCaptor =
                ArgumentCaptor.forClass(ConnectionDescriptor.class);
        ArgumentCaptor<String> recordingSaveNameCaptor = ArgumentCaptor.forClass(String.class);

        InOrder inOrder = Mockito.inOrder(service, recordingArchiveHelper);
        inOrder.verify(service).getSnapshotRecording();
        inOrder.verify(recordingArchiveHelper)
                .saveRecording(
                        connectionDescriptorCaptor.capture(), recordingSaveNameCaptor.capture());
        inOrder.verify(service).close(snapshot);

        ConnectionDescriptor connectionDescriptor = connectionDescriptorCaptor.getValue();
        MatcherAssert.assertThat(
                connectionDescriptor.getTargetId(),
                Matchers.equalTo(serviceRef.getServiceUri().toString()));
        MatcherAssert.assertThat(
                connectionDescriptor.getCredentials().get(), Matchers.equalTo(credentials));
        MatcherAssert.assertThat(
                recordingSaveNameCaptor.getValue(), Matchers.equalTo(snapshot.getName()));
    }

    @Test
    void testTaskCancellationOnFailure() throws Exception {
        String jmxUrl = "service:jmx:rmi://localhost:9091/jndi/rmi://fooHost:9091/jmxrmi";
        ServiceRef serviceRef = new ServiceRef("id", new URI(jmxUrl), "com.example.App");

        Credentials credentials = new Credentials("foouser", "barpassword");
        Mockito.when(credentialsManager.getCredentials(serviceRef)).thenReturn(credentials);

        TargetDiscoveryEvent tde = new TargetDiscoveryEvent(EventKind.FOUND, serviceRef);

        Rule rule =
                new Rule.Builder()
                        .name("Test Rule")
                        .description("Automated unit test rule")
                        .matchExpression("target.alias == 'com.example.App'")
                        .eventSpecifier("template=Continuous")
                        .maxAgeSeconds(30)
                        .maxSizeBytes(1234)
                        .preservedArchives(5)
                        .archivalPeriodSeconds(67)
                        .build();

        RecordingOptionsBuilder recordingOptionsBuilder =
                Mockito.mock(RecordingOptionsBuilder.class);
        Mockito.when(recordingOptionsBuilder.name(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.toDisk(Mockito.anyBoolean()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.maxAge(Mockito.anyLong()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.maxSize(Mockito.anyLong()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilderFactory.create(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        IConstrainedMap<String> recordingOptions = Mockito.mock(IConstrainedMap.class);
        Mockito.when(recordingOptionsBuilder.build()).thenReturn(recordingOptions);

        Mockito.when(
                        targetConnectionManager.executeConnectedTaskAsync(
                                Mockito.any(), Mockito.any()))
                .thenAnswer(
                        arg0 ->
                                CompletableFuture.completedFuture(
                                        ((TargetConnectionManager.ConnectedTask<Object>)
                                                        arg0.getArgument(1))
                                                .execute(connection)));

        Mockito.when(registry.getRules(serviceRef)).thenReturn(Set.of(rule));

        IRecordingDescriptor autoRule = Mockito.mock(IRecordingDescriptor.class);

        Mockito.when(recordingTargetHelper.getDescriptorByName(Mockito.any(), Mockito.any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(autoRule));

        Mockito.when(
                        recordingTargetHelper.startRecording(
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.anyBoolean()))
                .thenReturn(autoRule);

        Metadata metadata = new Metadata(Map.of());

        Mockito.when(metadataManager.getMetadata(Mockito.any(), Mockito.any()))
                .thenReturn(metadata);

        PeriodicArchiver[] pa = new PeriodicArchiver[1];
        Mockito.when(
                        periodicArchiverFactory.create(
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any()))
                .thenAnswer(
                        new Answer<PeriodicArchiver>() {
                            @Override
                            public PeriodicArchiver answer(InvocationOnMock invocation)
                                    throws Throwable {
                                CredentialsManager cm = invocation.getArgument(1);
                                Function<Pair<String, Rule>, Void> fn = invocation.getArgument(4);
                                PeriodicArchiver p =
                                        new PeriodicArchiver(
                                                serviceRef,
                                                cm,
                                                rule,
                                                recordingArchiveHelper,
                                                fn,
                                                logger);
                                pa[0] = p;
                                return p;
                            }
                        });

        ScheduledFuture task = Mockito.mock(ScheduledFuture.class);
        Mockito.doReturn(task)
                .when(executor)
                .scheduleAtFixedRate(
                        Mockito.any(), Mockito.anyLong(), Mockito.anyLong(), Mockito.any());

        processor.accept(tde);

        Mockito.verify(executor)
                .scheduleAtFixedRate(
                        Mockito.any(),
                        Mockito.eq((long) rule.getInitialDelaySeconds()),
                        Mockito.eq((long) rule.getArchivalPeriodSeconds()),
                        Mockito.eq(TimeUnit.SECONDS));

        Mockito.verify(periodicArchiverFactory)
                .create(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(task, Mockito.never()).cancel(Mockito.anyBoolean());

        Mockito.when(recordingArchiveHelper.getRecordings(Mockito.any()))
                .thenReturn(CompletableFuture.failedFuture(new SecurityException()));
        pa[0].run();

        Mockito.verify(task).cancel(false);
    }

    @Test
    void testEventCallOnCredentialsChange() throws Exception {

        Credentials credentials = new Credentials("foouser", "barpassword");
        String jmxUrl = "service:jmx:rmi://localhost:9091/jndi/rmi://fooHost:9091/jmxrmi";
        ServiceRef serviceRef = new ServiceRef("id", new URI(jmxUrl), "com.example.App");
        String matchExpression = "target.alias == 'com.example.App'";

        RecordingOptionsBuilder recordingOptionsBuilder =
                Mockito.mock(RecordingOptionsBuilder.class);
        Mockito.when(recordingOptionsBuilder.name(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.toDisk(Mockito.anyBoolean()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.maxAge(Mockito.anyLong()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.maxSize(Mockito.anyLong()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilderFactory.create(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        IConstrainedMap<String> recordingOptions = Mockito.mock(IConstrainedMap.class);
        Mockito.when(recordingOptionsBuilder.build()).thenReturn(recordingOptions);

        Mockito.when(
                        targetConnectionManager.executeConnectedTaskAsync(
                                Mockito.any(), Mockito.any()))
                .thenAnswer(
                        arg0 ->
                                CompletableFuture.completedFuture(
                                        ((TargetConnectionManager.ConnectedTask<Object>)
                                                        arg0.getArgument(1))
                                                .execute(connection)));
        Mockito.when(connection.getService()).thenReturn(service);

        Event<CredentialsEvent, String> event = Mockito.mock(Event.class);
        Mockito.when(event.getEventType()).thenReturn(CredentialsEvent.ADDED);
        Mockito.when(credentialsManager.resolveMatchingTargets(event.getPayload()))
                .thenReturn(Set.of(serviceRef));

        Rule rule =
                new Rule.Builder()
                        .name("Test Rule")
                        .description("Automated unit test rule")
                        .matchExpression(matchExpression)
                        .eventSpecifier("template=Continuous")
                        .maxAgeSeconds(30)
                        .maxSizeBytes(1234)
                        .preservedArchives(5)
                        .archivalPeriodSeconds(67)
                        .build();

        Mockito.when(registry.getRules(serviceRef)).thenReturn(Set.of(rule));

        EventListener<CredentialsManager.CredentialsEvent, String> listener =
                processor.credentialsListener();

        Mockito.doAnswer(
                        new Answer<Void>() {

                            @Override
                            public Void answer(InvocationOnMock invocation) throws Throwable {
                                listener.onEvent(event);
                                return null;
                            }
                        })
                .when(credentialsManager)
                .addCredentials(matchExpression, credentials);

        MatcherAssert.assertThat(
                credentialsManager.addCredentials(matchExpression, credentials),
                Matchers.greaterThan(-1));

        Mockito.verify(recordingOptionsBuilder).name("auto_Test_Rule");
        Mockito.verify(recordingOptionsBuilder).maxAge(30);
        Mockito.verify(recordingOptionsBuilder).maxSize(1234);

        ArgumentCaptor<ReplacementPolicy> replaceCaptor =
                ArgumentCaptor.forClass(ReplacementPolicy.class);

        ArgumentCaptor<ConnectionDescriptor> connectionDescriptorCaptor =
                ArgumentCaptor.forClass(ConnectionDescriptor.class);

        ArgumentCaptor<IConstrainedMap<String>> recordingOptionsCaptor =
                ArgumentCaptor.forClass(IConstrainedMap.class);

        ArgumentCaptor<String> templateNameCaptor = ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<TemplateType> templateTypeCaptor =
                ArgumentCaptor.forClass(TemplateType.class);

        ArgumentCaptor<Metadata> metadataCaptor = ArgumentCaptor.forClass(Metadata.class);

        ArgumentCaptor<Boolean> archiveOnStopCaptor = ArgumentCaptor.forClass(Boolean.class);

        Mockito.verify(recordingTargetHelper)
                .startRecording(
                        replaceCaptor.capture(),
                        connectionDescriptorCaptor.capture(),
                        recordingOptionsCaptor.capture(),
                        templateNameCaptor.capture(),
                        templateTypeCaptor.capture(),
                        metadataCaptor.capture(),
                        archiveOnStopCaptor.capture());

        MatcherAssert.assertThat(
                replaceCaptor.getValue(), Matchers.equalTo(ReplacementPolicy.STOPPED));

        IConstrainedMap<String> actualRecordingOptions = recordingOptionsCaptor.getValue();
        MatcherAssert.assertThat(actualRecordingOptions, Matchers.sameInstance(recordingOptions));

        MatcherAssert.assertThat(templateNameCaptor.getValue(), Matchers.equalTo("Continuous"));

        MatcherAssert.assertThat(templateTypeCaptor.getValue(), Matchers.nullValue());

        MatcherAssert.assertThat(metadataCaptor.getValue(), Matchers.equalTo(new Metadata()));
    }

    @Test
    void testSuccessfulRuleNonActivationWithCredentials() throws Exception {
        RecordingOptionsBuilder recordingOptionsBuilder =
                Mockito.mock(RecordingOptionsBuilder.class);

        String jmxUrl = "service:jmx:rmi://localhost:9091/jndi/rmi://fooHost:9091/jmxrmi";
        ServiceRef serviceRef = new ServiceRef("id", new URI(jmxUrl), "com.example.App");

        TargetDiscoveryEvent tde = new TargetDiscoveryEvent(EventKind.FOUND, serviceRef);

        Rule rule =
                new Rule.Builder()
                        .name("Test Rule")
                        .description("Automated unit test rule")
                        .matchExpression("target.alias == 'com.example.App'")
                        .eventSpecifier("template=Continuous")
                        .maxAgeSeconds(30)
                        .maxSizeBytes(1234)
                        .preservedArchives(5)
                        .archivalPeriodSeconds(67)
                        .enabled(false)
                        .build();

        Mockito.when(registry.getRules(serviceRef)).thenReturn(Set.of(rule));

        processor.accept(tde);

        Mockito.verify(recordingOptionsBuilder, Mockito.never()).name("auto_Test_Rule");

        ArgumentCaptor<ReplacementPolicy> replaceCaptor =
                ArgumentCaptor.forClass(ReplacementPolicy.class);

        ArgumentCaptor<ConnectionDescriptor> connectionDescriptorCaptor =
                ArgumentCaptor.forClass(ConnectionDescriptor.class);

        ArgumentCaptor<IConstrainedMap<String>> recordingOptionsCaptor =
                ArgumentCaptor.forClass(IConstrainedMap.class);

        ArgumentCaptor<String> templateNameCaptor = ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<TemplateType> templateTypeCaptor =
                ArgumentCaptor.forClass(TemplateType.class);

        ArgumentCaptor<Metadata> metadataCaptor = ArgumentCaptor.forClass(Metadata.class);

        ArgumentCaptor<Boolean> archiveOnStopCaptor = ArgumentCaptor.forClass(Boolean.class);

        Mockito.verify(recordingTargetHelper, Mockito.never())
                .startRecording(
                        replaceCaptor.capture(),
                        connectionDescriptorCaptor.capture(),
                        recordingOptionsCaptor.capture(),
                        templateNameCaptor.capture(),
                        templateTypeCaptor.capture(),
                        metadataCaptor.capture(),
                        archiveOnStopCaptor.capture());
    }
}
