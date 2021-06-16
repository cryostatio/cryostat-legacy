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

import java.util.concurrent.atomic.AtomicInteger;

import javax.management.remote.JMXServiceURL;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.platform.ServiceRef;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class PeriodicArchiverTest {

    PeriodicArchiver archiver;
    String jmxUrl = "service:jmx:rmi://localhost:9091/jndi/rmi://fooHost:9091/jmxrmi";
    ServiceRef serviceRef;
    Credentials credentials = new Credentials("foouser", "barpassword");
    Rule rule =
            new Rule.Builder()
                    .name("Test Rule")
                    .description("Automated unit test rule")
                    .targetAlias("com.example.App")
                    .eventSpecifier("template=Continuous")
                    .maxAgeSeconds(30)
                    .maxSizeBytes(1234)
                    .preservedArchives(2)
                    .archivalPeriodSeconds(67)
                    .build();
    @Mock WebClient webClient;
    @Mock MultiMap headers;
    AtomicInteger failureCounter;
    @Mock Logger logger;

    @BeforeEach
    void setup() throws Exception {
        this.serviceRef = new ServiceRef(new JMXServiceURL(jmxUrl), "com.example.App");
        this.failureCounter = new AtomicInteger();
        this.archiver =
                new PeriodicArchiver(
                        serviceRef,
                        credentials,
                        rule,
                        webClient,
                        c -> headers,
                        p -> {
                            failureCounter.incrementAndGet();
                            return null;
                        },
                        logger);
    }

    @Test
    void testPerformArchival() throws Exception {
        HttpRequest<Buffer> request = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> response = Mockito.mock(HttpResponse.class);
        Mockito.when(response.statusCode()).thenReturn(200);
        Mockito.when(response.bodyAsString()).thenReturn(rule.getRecordingName() + "_1");

        Mockito.when(webClient.patch(Mockito.any())).thenReturn(request);
        Mockito.when(request.putHeaders(Mockito.any())).thenReturn(request);
        Mockito.doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock invocation) throws Throwable {
                                AsyncResult res = Mockito.mock(AsyncResult.class);
                                Mockito.when(res.failed()).thenReturn(false);
                                Mockito.when(res.result()).thenReturn(response);
                                ((Handler<AsyncResult>) invocation.getArgument(1)).handle(res);
                                return null;
                            }
                        })
                .when(request)
                .sendBuffer(Mockito.any(), Mockito.any());

        archiver.performArchival();

        ArgumentCaptor<Buffer> patchActionCaptor = ArgumentCaptor.forClass(Buffer.class);
        Mockito.verify(request).sendBuffer(patchActionCaptor.capture(), Mockito.any());
        Buffer patchAction = patchActionCaptor.getValue();
        MatcherAssert.assertThat(patchAction.toString(), Matchers.equalTo("save"));

        ArgumentCaptor<MultiMap> headersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        Mockito.verify(request).putHeaders(headersCaptor.capture());
        MultiMap capturedHeaders = headersCaptor.getValue();
        MatcherAssert.assertThat(capturedHeaders, Matchers.sameInstance(headers));

        Mockito.verify(webClient)
                .patch(
                        "/api/v1/targets/service:jmx:rmi:%2F%2Flocalhost:9091%2Fjndi%2Frmi:%2F%2FfooHost:9091%2Fjmxrmi/recordings/auto_Test_Rule");
    }

    @Test
    void testNotifyOnFailure() throws Exception {
        HttpRequest<Buffer> request = Mockito.mock(HttpRequest.class);

        Mockito.when(webClient.patch(Mockito.any())).thenReturn(request);
        Mockito.when(request.putHeaders(Mockito.any())).thenReturn(request);
        Mockito.doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock invocation) throws Throwable {
                                AsyncResult res = Mockito.mock(AsyncResult.class);
                                Mockito.when(res.failed()).thenReturn(true);
                                Mockito.when(res.cause()).thenReturn(new Exception());
                                ((Handler<AsyncResult>) invocation.getArgument(1)).handle(res);
                                return null;
                            }
                        })
                .when(request)
                .sendBuffer(Mockito.any(), Mockito.any());

        MatcherAssert.assertThat(failureCounter.intValue(), Matchers.equalTo(0));

        archiver.run();

        MatcherAssert.assertThat(failureCounter.intValue(), Matchers.equalTo(1));
    }

    @Test
    void testPruneArchive() throws Exception {
        // get the archiver into a state where it is tracking a previously-archived recording
        testPerformArchival();

        HttpRequest<Buffer> request = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> response = Mockito.mock(HttpResponse.class);
        Mockito.when(response.statusCode()).thenReturn(200);

        Mockito.when(webClient.delete(Mockito.any())).thenReturn(request);
        Mockito.when(request.putHeaders(Mockito.any())).thenReturn(request);
        Mockito.doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock invocation) throws Throwable {
                                AsyncResult res = Mockito.mock(AsyncResult.class);
                                Mockito.when(res.failed()).thenReturn(false);
                                Mockito.when(res.result()).thenReturn(response);
                                ((Handler<AsyncResult>) invocation.getArgument(0)).handle(res);
                                return null;
                            }
                        })
                .when(request)
                .send(Mockito.any());

        boolean result = archiver.pruneArchive(rule.getRecordingName() + "_1").get();
        Assertions.assertTrue(result);

        ArgumentCaptor<MultiMap> headersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        Mockito.verify(request).putHeaders(headersCaptor.capture());
        MultiMap capturedHeaders = headersCaptor.getValue();
        MatcherAssert.assertThat(capturedHeaders, Matchers.sameInstance(headers));

        Mockito.verify(webClient).delete("/api/v1/recordings/auto_Test_Rule_1");
    }
}
