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
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import itest.bases.StandardSelfTest;
import itest.util.Utils;

public class JvmIdWebRequest {
    private static final Gson gson = MainModule.provideGson(Logger.INSTANCE);

    public static final int REQUEST_TIMEOUT_SECONDS = 10;
    public static final WebClient webClient = Utils.getWebClient();

    // shouldn't be percent-encoded i.e. String.format("service:jmx:rmi:///jndi/rmi://%s:9091/jmxrmi", Podman.POD_NAME)
    public static String jvmIdRequest(String targetId)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<TargetNodesQueryResponse> resp = new CompletableFuture<>();

        JsonObject query = new JsonObject();
        query.put(
                "query",
                String.format("query { targetNodes(filter: { name: \"%s\" }) { ", targetId)
                        + " target { jvmId } } }");
        webClient
                .post("/api/v2.2/graphql")
                .putHeader(
                        "X-JMX-Authorization",
                        "Basic "
                                + Base64.getUrlEncoder()
                                        .encodeToString("admin:adminpass123".getBytes()))
                .sendJson(
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
        return jvmIdRequest(serviceUri.toString());
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
