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

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.multipart.MultipartForm;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class UploadCertificateIT extends TestBase {

    static final String CERT_NAME = "cert";
    static final String FILE_NAME = "empty.cer";
    static final String TRUSTSTORE_CERT = "truststore/" + FILE_NAME;
    static final String MEDIA_TYPE = "application/pkix-cert";

    @Test
    public void shouldNotAddMalformedCertToTrustStore() throws Exception {

        CompletableFuture<Integer> uploadRespFuture = new CompletableFuture<>();
        ClassLoader classLoader = getClass().getClassLoader();
        File emptyCert = new File(classLoader.getResource(FILE_NAME).getFile());
        String path = emptyCert.getAbsolutePath();

        MultipartForm form =
                MultipartForm.create()
                        .attribute("name", CERT_NAME)
                        .binaryFileUpload(CERT_NAME, FILE_NAME, path, MEDIA_TYPE);

        webClient
                .post(String.format("/api/v2/certificates"))
                .sendMultipartForm(
                        form,
                        ar -> {
                            if (ar.failed()) {
                                uploadRespFuture.completeExceptionally(ar.cause());
                                return;
                            }
                            HttpResponse<Buffer> result = ar.result();
                            uploadRespFuture.complete(result.statusCode());
                        });

        int statusCode = uploadRespFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        MatcherAssert.assertThat(statusCode, Matchers.equalTo(500));
        MatcherAssert.assertThat(classLoader.getResource(TRUSTSTORE_CERT), Matchers.equalTo(null));
    }
}
