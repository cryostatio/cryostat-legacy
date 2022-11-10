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
package io.cryostat.net.web.http.api.v1;

import java.util.Optional;
import java.util.Set;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.FlightRecorderException;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.templates.TemplateService;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class TargetTemplateGetHandlerTest {

    TargetTemplateGetHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock DiscoveryStorage storage;
    @Mock JFRConnection conn;
    @Mock TemplateService templateService;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        this.handler =
                new TargetTemplateGetHandler(
                        auth, credentialsManager, targetConnectionManager, storage, logger);
    }

    @Test
    void shouldHandleGET() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(
                handler.path(),
                Matchers.equalTo(
                        "/api/v1/targets/:targetId/templates/:templateName/type/:templateType"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(),
                Matchers.equalTo(Set.of(ResourceAction.READ_TEMPLATE, ResourceAction.READ_TARGET)));
    }

    @Test
    void shouldThrowIfTargetConnectionManagerThrows() throws Exception {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        Mockito.when(ctx.pathParam("targetId")).thenReturn("localhost");
        Mockito.when(ctx.pathParam("templateName")).thenReturn("FooTemplate");
        Mockito.when(ctx.pathParam("templateType")).thenReturn("CUSTOM");
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());

        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenThrow(FlightRecorderException.class);

        Assertions.assertThrows(
                FlightRecorderException.class, () -> handler.handleAuthenticated(ctx));
    }

    @Test
    void shouldThrowIfNoMatchingTemplateFound() throws Exception {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        Mockito.when(ctx.pathParam("targetId")).thenReturn("localhost");
        Mockito.when(ctx.pathParam("templateName")).thenReturn("FooTemplate");
        Mockito.when(ctx.pathParam("templateType")).thenReturn("TARGET");
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());

        Mockito.when(conn.getTemplateService()).thenReturn(templateService);
        Mockito.when(templateService.getXml("FooTemplate", TemplateType.TARGET))
                .thenReturn(Optional.empty());

        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Optional<Document> answer(InvocationOnMock args)
                                    throws Throwable {
                                TargetConnectionManager.ConnectedTask ct =
                                        (TargetConnectionManager.ConnectedTask)
                                                args.getArguments()[1];
                                return (Optional<Document>) ct.execute(conn);
                            }
                        });

        HttpException ex =
                Assertions.assertThrows(
                        HttpException.class, () -> handler.handleAuthenticated(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
    }

    @Test
    void shouldRespondWithXmlDocument() throws Exception {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        Mockito.when(ctx.pathParam("targetId")).thenReturn("localhost");
        Mockito.when(ctx.pathParam("templateName")).thenReturn("FooTemplate");
        Mockito.when(ctx.pathParam("templateType")).thenReturn("CUSTOM");
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());

        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);

        Mockito.when(conn.getTemplateService()).thenReturn(templateService);

        Document doc = Mockito.mock(Document.class);
        Mockito.when(templateService.getXml("FooTemplate", TemplateType.CUSTOM))
                .thenReturn(Optional.of(doc));
        Mockito.when(doc.toString()).thenReturn("Mock Document XML");

        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Optional<Document> answer(InvocationOnMock args)
                                    throws Throwable {
                                TargetConnectionManager.ConnectedTask ct =
                                        (TargetConnectionManager.ConnectedTask)
                                                args.getArguments()[1];
                                return (Optional<Document>) ct.execute(conn);
                            }
                        });

        handler.handleAuthenticated(ctx);

        Mockito.verify(ctx, Mockito.atLeastOnce()).response();
        Mockito.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JFC.mime());
        Mockito.verify(resp).end("Mock Document XML");
    }
}
