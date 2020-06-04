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

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.HttpServer;
import com.redhat.rhjmc.containerjfr.net.web.HttpMimeType;
import com.redhat.rhjmc.containerjfr.net.web.WebModule;

import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;

@Singleton
public class ReportGetCacheHandler extends AbstractAuthenticatedRequestHandler {

    private final FileSystem fs;
    private final String reportCachePath;
    private final Logger logger;

    @Inject
    ReportGetCacheHandler(
            AuthManager auth,
            @Named(WebModule.WEBSERVER_TEMP_DIR_PATH) Path webserverTempPath,
            HttpServer httpServer,
            Logger logger) {
        super(auth);
        this.fs = httpServer.getVertx().fileSystem();
        this.reportCachePath = webserverTempPath.toString();
        this.logger = logger;
    }

    @Override
    public int getPriority() {
        return DEFAULT_PRIORITY - 1;
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
    void handleAuthenticated(RoutingContext ctx) {
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.HTML.mime());
        String recordingName = ctx.pathParam("recordingName");
        String cachedFile = getCachedReportPath(reportCachePath, recordingName);
        fs.exists(
                cachedFile,
                res -> {
                    if (res.failed()) {
                        throw new HttpStatusException(500, res.cause());
                    }
                    if (res.result()) {
                        logger.info(
                                String.format(
                                        "(%s): %s %s served from reports cache",
                                        ctx.request().remoteAddress().toString(),
                                        ctx.request().method().toString(),
                                        ctx.request().path()));
                        ctx.response().sendFile(cachedFile);
                    } else {
                        ctx.next();
                    }
                });
    }

    public void deleteCachedReport(String recordingName) {
        String recordingPath = getCachedReportPath(reportCachePath, recordingName);
        fs.exists(
                recordingPath,
                res -> {
                    if (res.succeeded() && res.result()) {
                        fs.delete(
                                recordingPath,
                                res2 -> {
                                    logger.info(String.format("Deleted %s", recordingPath));
                                });
                    }
                });
    }

    static String getCachedReportPath(String path, String recordingName) {
        String fileName = recordingName + ".report.html";
        String filePath = Paths.get(path, fileName).toAbsolutePath().toString();
        return filePath;
    }
}
