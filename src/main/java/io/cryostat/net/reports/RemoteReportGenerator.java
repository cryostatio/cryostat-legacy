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
package io.cryostat.net.reports;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Named;

import io.cryostat.configuration.Variables;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.util.HttpStatusCodeIdentifier;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;

class RemoteReportGenerator extends AbstractReportGeneratorService {

    private final Vertx vertx;
    private final WebClient http;
    private final Environment env;
    private final long generationTimeoutSeconds;

    RemoteReportGenerator(
            TargetConnectionManager targetConnectionManager,
            FileSystem fs,
            Vertx vertx,
            WebClient http,
            Environment env,
            @Named(ReportsModule.REPORT_GENERATION_TIMEOUT_SECONDS) long generationTimeoutSeconds,
            Logger logger) {
        super(targetConnectionManager, fs, logger);
        this.vertx = vertx;
        this.http = http;
        this.env = env;
        this.generationTimeoutSeconds = generationTimeoutSeconds;
    }

    @Override
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public CompletableFuture<Path> exec(
            Path recording, Path destination, String filter, boolean formatted) {
        String reportGenerator = env.getEnv(Variables.REPORT_GENERATOR_ENV);
        logger.info("POSTing {} to {}", recording, reportGenerator);
        var form =
                MultipartForm.create()
                        .attribute("filter", filter)
                        .binaryFileUpload(
                                "file",
                                recording.getFileName().toString(),
                                recording.toAbsolutePath().toString(),
                                HttpMimeType.OCTET_STREAM.mime());

        var f = new CompletableFuture<Path>();
        String acceptHeader = formatted ? HttpMimeType.HTML.mime() : HttpMimeType.JSON.mime();
        this.http
                .postAbs(String.format("%s/report", reportGenerator))
                .putHeader(HttpHeaders.ACCEPT.toString(), acceptHeader)
                .timeout(TimeUnit.SECONDS.toMillis(generationTimeoutSeconds))
                .sendMultipartForm(
                        form,
                        ar -> {
                            if (ar.failed()) {
                                f.completeExceptionally(ar.cause());
                                return;
                            }
                            if (!HttpStatusCodeIdentifier.isSuccessCode(ar.result().statusCode())) {
                                f.completeExceptionally(
                                        new ReportGenerationException(
                                                ar.result().statusCode(),
                                                ar.result().statusMessage()));
                                return;
                            }
                            var body = ar.result().bodyAsBuffer();
                            vertx.fileSystem()
                                    .writeFile(
                                            destination.toString(),
                                            body,
                                            ar2 -> {
                                                if (ar2.failed()) {
                                                    f.completeExceptionally(ar.cause());
                                                    return;
                                                }
                                                f.complete(destination);
                                                logger.info(
                                                        "Report response for {}" + " success",
                                                        recording);
                                            });
                        });
        return f;
    }
}
