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

import javax.inject.Provider;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.util.HttpStatusCodeIdentifier;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;

class RemoteReportGenerator extends AbstractReportGeneratorService {

    private final Vertx vertx;
    private final WebClient http;
    private final Environment env;

    RemoteReportGenerator(
            TargetConnectionManager targetConnectionManager,
            FileSystem fs,
            Provider<Path> tempFileProvider,
            Vertx vertx,
            WebClient http,
            Environment env,
            Logger logger) {
        super(targetConnectionManager, fs, tempFileProvider, logger);
        this.vertx = vertx;
        this.http = http;
        this.env = env;
    }

    @Override
    public CompletableFuture<Path> exec(Path recording, Path destination) {
        String reportGenerator = env.getEnv("CRYOSTAT_REPORT_GENERATOR");
        logger.info("POSTing {} to {}", recording, reportGenerator);
        var form =
                MultipartForm.create()
                        .binaryFileUpload(
                                "file",
                                recording.getFileName().toString(),
                                recording.toAbsolutePath().toString(),
                                HttpMimeType.OCTET_STREAM.mime());
        var f = new CompletableFuture<Path>();
        this.http
                .postAbs(String.format("%s/report", reportGenerator))
                .timeout(TimeUnit.MINUTES.toMillis(1))
                .sendMultipartForm(
                        form,
                        ar -> {
                            if (ar.failed()) {
                                f.completeExceptionally(ar.cause());
                                return;
                            }
                            if (!HttpStatusCodeIdentifier.isSuccessCode(ar.result().statusCode())) {
                                f.completeExceptionally(
                                        new ReportGenerationException(ar.result().statusMessage()));
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
                                                        "Report response for {} success",
                                                        recording);
                                            });
                        });
        return f;
    }
}
