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
import java.util.concurrent.TimeUnit;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import itest.bases.StandardSelfTest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class GrafanaSetupIT extends StandardSelfTest {

    @Test
    public void shouldHaveConfiguredDatasource() throws Exception {
        HttpRequest<Buffer> req = webClient.get("/api/v1/grafana_datasource_url");
        CompletableFuture<Integer> future = new CompletableFuture<>();
        req.send(
                ar -> {
                    if (ar.succeeded()) {
                        future.complete(ar.result().statusCode());
                    } else {
                        future.completeExceptionally(ar.cause());
                    }
                });
        MatcherAssert.assertThat(
                future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS), Matchers.equalTo(200));
    }

    @Test
    public void shouldHaveConfiguredDashboard() throws Exception {
        HttpRequest<Buffer> req = webClient.get("/api/v1/grafana_dashboard_url");
        CompletableFuture<Integer> future = new CompletableFuture<>();
        req.send(
                ar -> {
                    if (ar.succeeded()) {
                        future.complete(ar.result().statusCode());
                    } else {
                        future.completeExceptionally(ar.cause());
                    }
                });
        MatcherAssert.assertThat(
                future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS), Matchers.equalTo(200));
    }
}
