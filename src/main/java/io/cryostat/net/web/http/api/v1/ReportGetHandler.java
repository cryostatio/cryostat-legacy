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
package io.cryostat.net.web.http.api.v1;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.reports.ReportGenerationException;
import io.cryostat.net.reports.ReportService;
import io.cryostat.net.reports.ReportsModule;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.SecurityContext;
import io.cryostat.net.web.DeprecatedApi;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.recordings.RecordingNotFoundException;
import io.cryostat.rules.ArchivedRecordingInfo;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.apache.commons.lang3.exception.ExceptionUtils;

@DeprecatedApi(
        deprecated = @Deprecated(forRemoval = true),
        alternateLocation = "/api/beta/reports/:sourceTarget/:recordingName")
class ReportGetHandler extends AbstractAuthenticatedRequestHandler {

    private final ReportService reportService;
    private final RecordingArchiveHelper archiveHelper;
    private final long reportGenerationTimeoutSeconds;

    @Inject
    ReportGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            ReportService reportService,
            RecordingArchiveHelper archiveHelper,
            @Named(ReportsModule.REPORT_GENERATION_TIMEOUT_SECONDS)
                    long reportGenerationTimeoutSeconds,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.reportService = reportService;
        this.archiveHelper = archiveHelper;
        this.reportGenerationTimeoutSeconds = reportGenerationTimeoutSeconds;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V1;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(
                ResourceAction.READ_RECORDING,
                ResourceAction.CREATE_REPORT,
                ResourceAction.READ_REPORT);
    }

    @Override
    public String path() {
        return basePath() + "reports/:recordingName";
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public boolean isOrdered() {
        return false;
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.HTML);
    }

    @Override
    public SecurityContext securityContext(RoutingContext ctx) {
        String recordingName = ctx.pathParam("recordingName");
        try {

            return archiveHelper.getRecordings().get().stream()
                    .filter(r -> r.getName().equals(recordingName))
                    .findFirst()
                    .map(ArchivedRecordingInfo::getMetadata)
                    .map(Metadata::getSecurityContext)
                    .orElseThrow(() -> new HttpException(404));
        } catch (InterruptedException | ExecutionException e) {
            logger.error(e);
            throw new HttpException(500, e);
        }
    }

    @Override
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        String recordingName = ctx.pathParam("recordingName");
        List<String> queriedFilter = ctx.queryParam("filter");
        String rawFilter = queriedFilter.isEmpty() ? "" : queriedFilter.get(0);
        try {

            Path report =
                    reportService
                            .get(recordingName, rawFilter, true)
                            .get(reportGenerationTimeoutSeconds, TimeUnit.SECONDS);
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.HTML.mime());
            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(report.toFile().length()));
            ctx.response().sendFile(report.toAbsolutePath().toString());
        } catch (ExecutionException | CompletionException ee) {
            if (ExceptionUtils.getRootCause(ee) instanceof ReportGenerationException) {
                ReportGenerationException rge =
                        (ReportGenerationException) ExceptionUtils.getRootCause(ee);
                throw new HttpException(rge.getStatusCode(), ee.getMessage());
            }
            if (ExceptionUtils.getRootCause(ee) instanceof RecordingNotFoundException) {
                throw new HttpException(404, ee);
            }
            throw ee;
        }
    }
}
