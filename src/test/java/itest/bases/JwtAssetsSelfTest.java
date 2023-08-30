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
package itest.bases;

import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.vertx.core.MultiMap;
import itest.util.ITestCleanupFailedException;

public class JwtAssetsSelfTest extends StandardSelfTest {

    public String getTokenDownloadUrl(URL resource) throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("resource", resource.toString());
        webClient
                .post("/api/v2.1/auth/token")
                .sendForm(
                        form,
                        ar -> {
                            if (assertRequestStatus(ar, future)) {
                                future.complete(
                                        ar.result()
                                                .bodyAsJsonObject()
                                                .getJsonObject("data")
                                                .getJsonObject("result")
                                                .getString("resourceUrl"));
                            }
                        });
        return future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public void cleanupCreatedResources(String path) throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();
        webClient
                .delete(path)
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, future)) {
                                future.complete(null);
                            }
                        });

        try {
            future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new ITestCleanupFailedException(
                    String.format("Failed to delete resource %s", path), e);
        }
    }
}
