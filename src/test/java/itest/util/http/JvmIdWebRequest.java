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
package itest.util.http;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;

import com.google.gson.Gson;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import itest.bases.StandardSelfTest;
import itest.util.Utils;
import org.apache.commons.lang3.tuple.Pair;

public class JvmIdWebRequest {
    private static final Gson gson = MainModule.provideGson(Logger.INSTANCE);

    public static final int REQUEST_TIMEOUT_SECONDS = 10;
    public static final WebClient webClient = Utils.getWebClient();

    // shouldn't be percent-encoded i.e.
    // String.format("service:jmx:rmi:///jndi/rmi://%s:9091/jmxrmi", Podman.POD_NAME)
    public static String jvmIdRequest(String targetId)
            throws InterruptedException, ExecutionException, TimeoutException {
        return jvmIdRequest(targetId, null);
    }

    public static String jvmIdRequest(String targetId, Pair<String, String> credentials)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<TargetNodesQueryResponse> resp = new CompletableFuture<>();

        JsonObject query = new JsonObject();
        query.put(
                "query",
                String.format(
                        "query { targetNodes(filter: { name: \"%s\" }) { target { jvmId } } }",
                        targetId));
        HttpRequest<Buffer> buffer = webClient.post("/api/v2.2/graphql");
        if (credentials != null) {
            buffer.putHeader(
                    "X-JMX-Authorization",
                    "Basic "
                            + Base64.getUrlEncoder()
                                    .encodeToString(
                                            (credentials.getLeft() + ":" + credentials.getRight())
                                                    .getBytes()));
        }
        buffer.sendJson(
                query,
                ar -> {
                    if (StandardSelfTest.assertRequestStatus(ar, resp)) {
                        resp.complete(
                                gson.fromJson(
                                        ar.result().bodyAsString(),
                                        TargetNodesQueryResponse.class));
                    }
                });
        TargetNodesQueryResponse response = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return response.data.targetNodes.get(0).target.jvmId;
    }

    public static String jvmIdRequest(URI serviceUri)
            throws InterruptedException, ExecutionException, TimeoutException {
        return jvmIdRequest(serviceUri.toString(), null);
    }

    public static String jvmIdRequest(URI serviceUri, Pair<String, String> credentials)
            throws InterruptedException, ExecutionException, TimeoutException {
        return jvmIdRequest(serviceUri.toString(), credentials);
    }

    static class TargetNodesQueryResponse {
        TargetNodes data;
    }

    static class TargetNodes {
        List<TargetNode> targetNodes;
    }

    static class TargetNode {
        Target target;
    }

    static class Target {
        String jvmId;
    }
}
