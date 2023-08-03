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

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.security.ResourceAction;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TargetRecordingPatchHandlerTest {

    TargetRecordingPatchHandler handler;
    @Mock AuthManager authManager;
    @Mock CredentialsManager credentialsManager;
    @Mock TargetRecordingPatchSave patchSave;
    @Mock TargetRecordingPatchStop patchStop;
    @Mock RoutingContext ctx;
    @Mock HttpServerRequest req;
    @Mock HttpServerResponse resp;
    @Mock ConnectionDescriptor connectionDescriptor;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        this.handler =
                new TargetRecordingPatchHandler(
                        authManager, credentialsManager, patchSave, patchStop, logger);
    }

    @Test
    void shouldHandlePATCH() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.PATCH));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(
                handler.path(),
                Matchers.equalTo("/api/v1/targets/:targetId/recordings/:recordingName"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(),
                Matchers.equalTo(
                        Set.of(
                                ResourceAction.READ_TARGET,
                                ResourceAction.READ_RECORDING,
                                ResourceAction.UPDATE_RECORDING)));
    }

    @Test
    void shouldNotBeAsync() {
        // recording saving is a blocking operation, so the handler should be marked as such
        Assertions.assertFalse(handler.isAsync());
    }

    @Test
    void shouldThrow401IfAuthFails() {
        Mockito.when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(false));

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "start", "dump"})
    @NullAndEmptySource
    void shouldThrow400InvalidOperations(String mtd) {
        Mockito.when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        RequestBody body = Mockito.mock(RequestBody.class);
        Mockito.when(ctx.body()).thenReturn(body);
        Mockito.when(body.asString()).thenReturn(mtd);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "stop"})
    void shouldDelegateSupportedOperations(String mtd) throws Exception {
        Mockito.when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(ctx.pathParam("targetId")).thenReturn("fooHost:1234");
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        RequestBody body = Mockito.mock(RequestBody.class);
        Mockito.when(ctx.body()).thenReturn(body);
        Mockito.when(body.asString()).thenReturn(mtd);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);

        handler.handle(ctx);

        switch (mtd) {
            case "save":
                Mockito.verify(patchSave)
                        .handle(Mockito.eq(ctx), Mockito.any(ConnectionDescriptor.class));
                break;
            case "stop":
                Mockito.verify(patchStop)
                        .handle(Mockito.eq(ctx), Mockito.any(ConnectionDescriptor.class));
                break;
            default:
                throw new IllegalArgumentException(mtd);
        }
    }
}
