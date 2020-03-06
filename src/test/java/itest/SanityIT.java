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
