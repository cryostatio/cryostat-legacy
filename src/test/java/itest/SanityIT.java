/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package itest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class SanityIT {

    CloseableHttpClient http;

    @BeforeEach
    void setup() throws Exception {
        http = IntegrationTestUtils.createHttpClient();
        Thread.sleep(500);
    }

    @AfterEach
    void cleanup() throws Exception {
        http.close();
    }

    @Nested
    class GetClientUrl {

        @Test
        public void shouldReturn200() throws Exception {
            String url =
                    String.format("http://0.0.0.0:%d/clienturl", IntegrationTestUtils.WEB_PORT);
            HttpGet get = new HttpGet(url);
            try (CloseableHttpResponse resp = http.execute(get)) {
                MatcherAssert.assertThat(
                        resp.getStatusLine().getStatusCode(), Matchers.equalTo(200));
            }
        }

        @Test
        public void shouldReturnOk() throws Exception {
            String url =
                    String.format("http://0.0.0.0:%d/clienturl", IntegrationTestUtils.WEB_PORT);
            HttpGet get = new HttpGet(url);
            try (CloseableHttpResponse resp = http.execute(get)) {
                MatcherAssert.assertThat(
                        resp.getStatusLine().getReasonPhrase(), Matchers.equalTo("OK"));
            }
        }

        @Test
        public void shouldReturnContentTypeJson() throws Exception {
            String url =
                    String.format("http://0.0.0.0:%d/clienturl", IntegrationTestUtils.WEB_PORT);
            HttpGet get = new HttpGet(url);
            try (CloseableHttpResponse resp = http.execute(get)) {
                Header header = resp.getEntity().getContentType();
                MatcherAssert.assertThat(header.getValue(), Matchers.equalTo("application/json"));
            }
        }

        @Test
        public void shouldReturnJsonMessage() throws Exception {
            String url =
                    String.format("http://0.0.0.0:%d/clienturl", IntegrationTestUtils.WEB_PORT);
            HttpGet get = new HttpGet(url);
            try (CloseableHttpResponse resp = http.execute(get)) {
                InputStream content = resp.getEntity().getContent();
                String body = IOUtils.toString(content, StandardCharsets.UTF_8);
                MatcherAssert.assertThat(
                        body,
                        Matchers.equalTo(
                                String.format(
                                        "{\"clientUrl\":\"ws://0.0.0.0:%d/command\"}",
                                        IntegrationTestUtils.WEB_PORT)));
            }
        }
    }
}
