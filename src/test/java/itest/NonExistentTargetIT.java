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

import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.handler.HttpException;
import itest.bases.StandardSelfTest;
import org.apache.http.client.utils.URLEncodedUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NonExistentTargetIT extends StandardSelfTest {

    static final String BAD_TARGET_CONNECT_URL =
            "service:jmx:rmi:///jndi/rmi://nosuchhost:9091/jmxrmi";
    static final String BAD_TARGET_CONNECT_URL_ENCODED =
            URLEncodedUtils.formatSegments(BAD_TARGET_CONNECT_URL);

    @Test
    public void testConnectionFailsAsExpected() throws Exception {
        CompletableFuture<JsonArray> response = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/recordings", BAD_TARGET_CONNECT_URL_ENCODED))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, response)) {
                                response.complete(ar.result().bodyAsJsonArray());
                            }
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(404));
    }
}
