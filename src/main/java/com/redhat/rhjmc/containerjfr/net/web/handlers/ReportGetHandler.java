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
package com.redhat.rhjmc.containerjfr.net.web.handlers;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import com.redhat.rhjmc.containerjfr.MainModule;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.reports.ReportGenerator;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.HttpServer;
import com.redhat.rhjmc.containerjfr.net.web.WebModule;
import com.redhat.rhjmc.containerjfr.net.web.WebServer.DownloadDescriptor;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;

class ReportGetHandler extends AbstractAuthenticatedRequestHandler {

    private final Path savedRecordingsPath;
    private final ReportGenerator reportGenerator;
    private final FileSystem fs;
    private final Logger logger;
    private final String reportCachePath;

    @Inject
    ReportGetHandler(
            AuthManager auth,
            @Named(MainModule.RECORDINGS_PATH) Path savedRecordingsPath,
            @Named(WebModule.WEBSERVER_TEMP_DIR_PATH) Path webserverTempPath,
            ReportGenerator reportGenerator,
            HttpServer httpServer,
            Logger logger) {
        super(auth);
        this.savedRecordingsPath = savedRecordingsPath;
        this.reportGenerator = reportGenerator;
        this.fs = httpServer.getVertx().fileSystem();
        this.logger = logger;
        this.reportCachePath = webserverTempPath.toString();
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    @Override
    public String path() {
        return "/api/v1/reports/:recordingName";
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    @Override
    void handleAuthenticated(RoutingContext ctx) {
        String recordingName = ctx.pathParam("recordingName");
        handleReportPageRequest(null, recordingName, ctx);
    }

    void handleReportPageRequest(String targetId, String recordingName, RoutingContext ctx) {
        try {
            Optional<DownloadDescriptor> descriptor =
                    getRecordingDescriptor(targetId, recordingName);
            if (descriptor.isEmpty()) {
                throw new HttpStatusException(404, String.format("%s not found", recordingName));
            }

            try (InputStream stream = descriptor.get().stream) {
                ctx.response().end(reportFromStream(recordingName, stream));
            } finally {
                descriptor
                        .get()
                        .resource
                        .ifPresent(
                                resource -> {
                                    try {
                                        resource.close();
                                    } catch (Exception e) {
                                        logger.warn(e);
                                    }
                                });
            }
        } catch (HttpStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new HttpStatusException(500, e);
        }
    }

    // TODO refactor, this is duplicated from RecordingGetRequestHandler
    Optional<DownloadDescriptor> getRecordingDescriptor(String targetId, String recordingName) {
        try {
            // TODO refactor Files calls into FileSystem for testability
            Optional<Path> savedRecording =
                    Files.list(savedRecordingsPath)
                            .filter(
                                    saved ->
                                            saved.getFileName()
                                                    .toFile()
                                                    .getName()
                                                    .equals(recordingName))
                            .findFirst();
            if (savedRecording.isPresent()) {
                return Optional.of(
                        new DownloadDescriptor(
                                Files.newInputStream(savedRecording.get(), StandardOpenOption.READ),
                                Files.size(savedRecording.get()),
                                null));
            }
        } catch (Exception e) {
            logger.error(e);
        }
        return Optional.empty();
    }

    Buffer reportFromStream(String recordingName, InputStream stream) {
        String cachedReport =
                ReportGetCacheHandler.getCachedReportPath(reportCachePath, recordingName);
        Buffer reportBuffer = Buffer.buffer(reportGenerator.generateReport(stream));
        fs.createFileBlocking(cachedReport).writeFileBlocking(cachedReport, reportBuffer);

        return reportBuffer;
    }
}
