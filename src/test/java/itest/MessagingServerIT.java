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
package itest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.vertx.core.json.JsonObject;
import itest.bases.StandardSelfTest;
import itest.util.Utils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MessagingServerIT extends StandardSelfTest {

    public static final String DEPRECATED_COMMAND_PATH = "/api/v1/command";

    public static CompletableFuture<JsonObject> sendMessage(String command, String... args)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();

        Utils.HTTP_CLIENT.webSocket(
                DEPRECATED_COMMAND_PATH,
                ar -> {
                    if (ar.failed()) {
                        future.completeExceptionally(ar.cause());
                    } else {
                        future.complete(null);
                    }
                });

        return future;
    }

    @Test
    public void shouldRejectDeprecatedCommandPathWith410StatusCode() throws Exception {
        ExecutionException ex =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> sendMessage("ping").get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        String errorMessage = "WebSocket upgrade failure: 410";
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo(errorMessage));
    }
}
