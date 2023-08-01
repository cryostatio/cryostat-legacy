/*
 * Copyright The Cryostat Authors
 *
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
 */
package io.cryostat.rules;

import static org.mockito.Mockito.never;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.MockVertx;
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
import io.cryostat.util.events.Event;
import io.cryostat.util.events.EventListener;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
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
    Vertx vertx;
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
        this.vertx = MockVertx.vertx();
        this.processor =
                new RuleProcessor(
                        vertx,
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

        ArgumentCaptor<Boolean> restartCaptor = ArgumentCaptor.forClass(Boolean.class);

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
                        restartCaptor.capture(),
                        connectionDescriptorCaptor.capture(),
                        recordingOptionsCaptor.capture(),
                        templateNameCaptor.capture(),
                        templateTypeCaptor.capture(),
                        metadataCaptor.capture(),
                        archiveOnStopCaptor.capture());

        Assertions.assertTrue(restartCaptor.getValue());

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

        ArgumentCaptor<Handler<Long>> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        Mockito.verify(vertx).setTimer(Mockito.eq(67_000L), handlerCaptor.capture());

        Mockito.verify(periodicArchiver, Mockito.times(0)).run();
        handlerCaptor.getValue().handle(1234L);
        Mockito.verify(periodicArchiver, Mockito.times(1)).run();

        Mockito.verify(vertx).setPeriodic(Mockito.eq(67_000L), handlerCaptor.capture());

        handlerCaptor.getValue().handle(1234L);
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

        Mockito.when(registry.getRules(serviceRef)).thenReturn(Set.of(rule));

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

        Mockito.verify(vertx).setTimer(Mockito.eq(67_000L), Mockito.any());

        ArgumentCaptor<Function<Pair<ServiceRef, Rule>, Void>> functionCaptor =
                ArgumentCaptor.forClass(Function.class);
        Mockito.verify(periodicArchiverFactory)
                .create(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        functionCaptor.capture());
        Function<Pair<ServiceRef, Rule>, Void> failureFunction = functionCaptor.getValue();
        Mockito.verify(vertx, Mockito.never()).cancelTimer(MockVertx.TIMER_ID);

        failureFunction.apply(Pair.of(serviceRef, rule));

        Mockito.verify(vertx).cancelTimer(MockVertx.TIMER_ID);
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

        ArgumentCaptor<Boolean> restartCaptor = ArgumentCaptor.forClass(Boolean.class);

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
                        restartCaptor.capture(),
                        connectionDescriptorCaptor.capture(),
                        recordingOptionsCaptor.capture(),
                        templateNameCaptor.capture(),
                        templateTypeCaptor.capture(),
                        metadataCaptor.capture(),
                        archiveOnStopCaptor.capture());

        Assertions.assertTrue(restartCaptor.getValue());

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

        Mockito.verify(recordingOptionsBuilder, never()).name("auto_Test_Rule");

        ArgumentCaptor<Boolean> restartCaptor = ArgumentCaptor.forClass(Boolean.class);

        ArgumentCaptor<ConnectionDescriptor> connectionDescriptorCaptor =
                ArgumentCaptor.forClass(ConnectionDescriptor.class);

        ArgumentCaptor<IConstrainedMap<String>> recordingOptionsCaptor =
                ArgumentCaptor.forClass(IConstrainedMap.class);

        ArgumentCaptor<String> templateNameCaptor = ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<TemplateType> templateTypeCaptor =
                ArgumentCaptor.forClass(TemplateType.class);

        ArgumentCaptor<Metadata> metadataCaptor = ArgumentCaptor.forClass(Metadata.class);

        ArgumentCaptor<Boolean> archiveOnStopCaptor = ArgumentCaptor.forClass(Boolean.class);

        Mockito.verify(recordingTargetHelper, never())
                .startRecording(
                        restartCaptor.capture(),
                        connectionDescriptorCaptor.capture(),
                        recordingOptionsCaptor.capture(),
                        templateNameCaptor.capture(),
                        templateTypeCaptor.capture(),
                        metadataCaptor.capture(),
                        archiveOnStopCaptor.capture());
    }
}
