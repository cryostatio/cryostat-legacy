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
package io.cryostat.net.web.http.api.v1;

import java.util.Optional;
import java.util.Set;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.FlightRecorderException;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.templates.TemplateService;
import io.cryostat.core.templates.TemplateType;
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
    @Mock JFRConnection conn;
    @Mock TemplateService templateService;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        this.handler =
                new TargetTemplateGetHandler(
                        auth, credentialsManager, targetConnectionManager, logger);
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
