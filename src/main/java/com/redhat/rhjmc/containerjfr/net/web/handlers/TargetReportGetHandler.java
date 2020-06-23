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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.reports.ReportGenerator;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.web.HttpMimeType;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;

class TargetReportGetHandler extends AbstractAuthenticatedRequestHandler {

    protected final TargetConnectionManager targetConnectionManager;
    protected final ReportGenerator reportGenerator;
    protected final LoadingCache<String, String> cache;
    protected final Logger logger;

    @Inject
    TargetReportGetHandler(
            AuthManager auth,
            TargetConnectionManager targetConnectionManager,
            ReportGenerator reportGenerator,
            Logger logger) {
        super(auth);
        this.targetConnectionManager = targetConnectionManager;
        this.reportGenerator = reportGenerator;
        this.logger = logger;

        // TODO somehow allow for explicit invalidation when recordings are deleted
        this.cache =
                Caffeine.newBuilder()
                        .initialCapacity(4)
                        .expireAfterAccess(30, TimeUnit.MINUTES)
                        .refreshAfterWrite(5, TimeUnit.MINUTES)
                        .softValues()
                        .build(this::getReportFromKey);
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    @Override
    public String path() {
        return "/api/v1/targets/:targetId/reports/:recordingName";
    }

    @Override
    void handleAuthenticated(RoutingContext ctx) {
        String targetId = ctx.pathParam("targetId");
        String recordingName = ctx.pathParam("recordingName");
        String key = recordingName + "@" + targetId;
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.HTML.mime());
        ctx.response().end(cache.get(key));
    }

    String getReportFromKey(String key) throws Exception {
        String[] keyParts = key.split("@");
        String recordingName = keyParts[0];
        String targetId = keyParts[1];
        Pair<Optional<InputStream>, JFRConnection> pair =
                getRecordingStream(targetId, recordingName);
        try (JFRConnection c = pair.getRight();
                InputStream stream =
                        pair.getLeft()
                                .orElseThrow(
                                        () ->
                                                new HttpStatusException(
                                                        404,
                                                        String.format(
                                                                "%s not found", recordingName)))) {
            return reportGenerator.generateReport(stream);
        }
    }

    Pair<Optional<InputStream>, JFRConnection> getRecordingStream(
            String targetId, String recordingName) throws Exception {
        JFRConnection connection = targetConnectionManager.connect(targetId);
        Optional<InputStream> desc =
                connection.getService().getAvailableRecordings().stream()
                        .filter(rec -> Objects.equals(recordingName, rec.getName()))
                        .findFirst()
                        .flatMap(
                                rec -> {
                                    try {
                                        return Optional.of(
                                                connection.getService().openStream(rec, false));
                                    } catch (FlightRecorderException e) {
                                        logger.warn(e);
                                        return Optional.empty();
                                    }
                                });
        return Pair.of(desc, connection);
    }
}
