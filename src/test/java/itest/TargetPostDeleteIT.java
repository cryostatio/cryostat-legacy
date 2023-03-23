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
package itest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import itest.bases.StandardSelfTest;
import org.apache.http.client.utils.URLEncodedUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TargetPostDeleteIT extends StandardSelfTest {
    static final String REQ_URL = "/api/v2/targets";

    @Test
    public void testPostTargetThrowsWithoutConnectUrlAttribute() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("alias", "someAlias");

        webClient
                .post(REQ_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void testPostTargetThrowsWithoutAliasAttribute() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("connectUrl", SELF_REFERENCE_TARGET_ID);

        webClient
                .post(REQ_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void testPostTargetThrowsWithEmptyConnectUrl() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("alias", "someAlias");
        form.add("connectUrl", "");

        webClient
                .post(REQ_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void testPostTargetThrowsWithEmptyAlias() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("alias", "");
        form.add("connectUrl", SELF_REFERENCE_TARGET_ID);

        webClient
                .post(REQ_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void testPostTargetThrowsWithInvalidConnectUrl() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("connectUrl", "invalidConnectUrl");

        webClient
                .post(REQ_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void testPostTargetThrowsWithDuplicateConnectUrl() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("alias", "duplicateCryostat");
        form.add("connectUrl", SELF_REFERENCE_TARGET_ID);

        webClient
                .post(REQ_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void testDeleteTargetThrowsWithInvalidConnectUrl() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();

        webClient
                .delete(String.format("%s/%s", REQ_URL, "invalidTargetId"))
                .send(
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(404));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Not Found"));
    }

    @Test
    public void testDeleteTargetThrowsWithNonExistentConnectUrl() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        final String nonExistentTargetId =
                URLEncodedUtils.formatSegments(
                        String.format("service:jmx:rmi:///jndi/rmi://invalid:9091/jmxrmi"));

        webClient
                .delete(String.format("%s/%s", REQ_URL, nonExistentTargetId))
                .send(
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(404));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Not Found"));
    }
}
