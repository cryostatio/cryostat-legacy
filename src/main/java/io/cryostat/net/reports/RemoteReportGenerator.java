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
    public CompletableFuture<Path> exec(Path recording, Path destination, String filter) {
        String reportGenerator = env.getEnv(Variables.REPORT_GENERATOR_ENV);
        logger.trace("POSTing {} to {}", recording, reportGenerator);
        var form =
                MultipartForm.create()
                        .attribute("filter", filter)
                        .binaryFileUpload(
                                "file",
                                recording.getFileName().toString(),
                                recording.toAbsolutePath().toString(),
                                HttpMimeType.OCTET_STREAM.mime());

        var f = new CompletableFuture<Path>();
        String acceptHeader = HttpMimeType.JSON.mime();
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
