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

import java.net.URI;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.cryostat.commands.internal.RecordingOptionsBuilderFactory;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.TargetDiscoveryEvent;
import io.cryostat.recordings.RecordingCreationHelper;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuleProcessorTest {

    RuleProcessor processor;
    @Mock PlatformClient platformClient;
    @Mock RuleRegistry registry;
    @Mock ScheduledExecutorService scheduler;
    @Mock CredentialsManager credentialsManager;
    @Mock RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock RecordingCreationHelper recordingCreationHelper;
    @Mock PeriodicArchiverFactory periodicArchiverFactory;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        this.processor =
                new RuleProcessor(
                        platformClient,
                        registry,
                        scheduler,
                        credentialsManager,
                        recordingOptionsBuilderFactory,
                        targetConnectionManager,
                        recordingCreationHelper,
                        periodicArchiverFactory,
                        logger);
    }

    @Test
    void testEnableAddsProcessorAsDiscoveryListener() {
        Mockito.verifyNoInteractions(platformClient);

        processor.enable();

        Mockito.verify(platformClient).addTargetDiscoveryListener(processor);
    }

    @Test
    void testDisableRemovesProcessorAsDiscoveryListener() {
        Mockito.verifyNoInteractions(platformClient);

        processor.disable();

        Mockito.verify(platformClient).removeTargetDiscoveryListener(processor);
    }

    @Test
    @Disabled
    void testSuccessfulRuleActivationWithCredentials() throws Exception {
        // RecordingOptionsBuilder recordingOptionsBuilder =
        // Mockito.mock(RecordingOptionsBuilder.class);
        // Mockito.when(recordingOptionsBuilderFactory.create(Mockito.any())).thenReturn(recordingOptionsBuilder);

        // IConstrainedMap eventSettings = Mockito.mock(IConstrainedMap.class);
        // Mockito.when(targetConnectionManager.)

        String jmxUrl = "service:jmx:rmi://localhost:9091/jndi/rmi://fooHost:9091/jmxrmi";
        ServiceRef serviceRef = new ServiceRef(new URI(jmxUrl), "com.example.App");

        Credentials credentials = new Credentials("foouser", "barpassword");
        Mockito.when(credentialsManager.getCredentials(jmxUrl)).thenReturn(credentials);

        TargetDiscoveryEvent tde = new TargetDiscoveryEvent(EventKind.FOUND, serviceRef);

        Rule rule =
                new Rule.Builder()
                        .name("Test Rule")
                        .description("Automated unit test rule")
                        .targetAlias("com.example.App")
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
                                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(periodicArchiver);

        processor.accept(tde);

        // ArgumentCaptor<MultipartForm> formCaptor = ArgumentCaptor.forClass(MultipartForm.class);
        // Mockito.verify(request).sendMultipartForm(formCaptor.capture(), Mockito.any());
        // MultipartForm form = formCaptor.getValue();
        // Set<String> formAttributes = new HashSet<>();
        // form.iterator()
        //         .forEachRemaining(
        //                 part -> {
        //                     FormDataPartImpl impl = (FormDataPartImpl) part;
        //                     formAttributes.add(String.format("%s=%s", impl.name(),
        // impl.value()));
        //                 });
        // MatcherAssert.assertThat(
        //         formAttributes,
        //         Matchers.containsInAnyOrder(
        //                 "recordingName=auto_Test_Rule",
        //                 "events=template=Continuous",
        //                 "maxAge=30",
        //                 "maxSize=1234"));

        // ArgumentCaptor<MultiMap> headersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        // Mockito.verify(request).putHeaders(headersCaptor.capture());
        // MultiMap capturedHeaders = headersCaptor.getValue();
        // MatcherAssert.assertThat(capturedHeaders, Matchers.sameInstance(headers));

        //         Mockito.verify(recordingOptionsBuilder).name("auto_Test_Rule");
        //         Mockito.verify(recordingOptionsBuilder).toDisk(true);
        //         Mockito.verify(recordingOptionsBuilder).maxAge(30);
        //         Mockito.verify(recordingOptionsBuilder).maxSize(1234);

        Mockito.verify(scheduler).scheduleAtFixedRate(periodicArchiver, 67, 67, TimeUnit.SECONDS);
    }

    @Test
    @Disabled
    void testTaskCancellationOnFailure() throws Exception {
        // HttpRequest<Buffer> request = Mockito.mock(HttpRequest.class);
        // HttpResponse<Buffer> response = Mockito.mock(HttpResponse.class);
        // Mockito.when(response.statusCode()).thenReturn(200);

        // Mockito.when(webClient.post(Mockito.any())).thenReturn(request);
        // Mockito.when(request.putHeaders(Mockito.any())).thenReturn(request);
        // Mockito.doAnswer(
        //                 new Answer<Void>() {
        //                     @Override
        //                     public Void answer(InvocationOnMock invocation) throws Throwable {
        //                         AsyncResult res = Mockito.mock(AsyncResult.class);
        //                         Mockito.when(res.failed()).thenReturn(false);
        //                         Mockito.when(res.result()).thenReturn(response);
        //                         ((Handler<AsyncResult>) invocation.getArgument(1)).handle(res);
        //                         return null;
        //                     }
        //                 })
        //         .when(request)
        //         .sendMultipartForm(Mockito.any(), Mockito.any());

        String jmxUrl = "service:jmx:rmi://localhost:9091/jndi/rmi://fooHost:9091/jmxrmi";
        ServiceRef serviceRef = new ServiceRef(new URI(jmxUrl), "com.example.App");

        Credentials credentials = new Credentials("foouser", "barpassword");
        Mockito.when(credentialsManager.getCredentials(jmxUrl)).thenReturn(credentials);

        TargetDiscoveryEvent tde = new TargetDiscoveryEvent(EventKind.FOUND, serviceRef);

        Rule rule =
                new Rule.Builder()
                        .name("Test Rule")
                        .description("Automated unit test rule")
                        .targetAlias("com.example.App")
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
                                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(periodicArchiver);

        ScheduledFuture task = Mockito.mock(ScheduledFuture.class);
        Mockito.when(
                        scheduler.scheduleAtFixedRate(
                                Mockito.any(Runnable.class),
                                Mockito.anyLong(),
                                Mockito.anyLong(),
                                Mockito.any(TimeUnit.class)))
                .thenReturn(task);

        processor.accept(tde);

        Mockito.verify(scheduler).scheduleAtFixedRate(periodicArchiver, 67, 67, TimeUnit.SECONDS);

        ArgumentCaptor<Function<Pair<ServiceRef, Rule>, Void>> functionCaptor =
                ArgumentCaptor.forClass(Function.class);
        Mockito.verify(periodicArchiverFactory)
                .create(Mockito.any(), Mockito.any(), Mockito.any(), functionCaptor.capture());
        Function<Pair<ServiceRef, Rule>, Void> failureFunction = functionCaptor.getValue();
        Mockito.verify(task, Mockito.never()).cancel(Mockito.anyBoolean());

        failureFunction.apply(Pair.of(serviceRef, rule));

        Mockito.verify(task).cancel(true);
    }
}
