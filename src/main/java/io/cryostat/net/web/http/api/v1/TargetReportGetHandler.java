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
import io.cryostat.net.reports.ReportService;
import io.cryostat.net.reports.ReportsModule;
import io.cryostat.net.reports.SubprocessReportGenerator;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingNotFoundException;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.apache.commons.lang3.exception.ExceptionUtils;

class TargetReportGetHandler extends AbstractAuthenticatedRequestHandler {

    protected final ReportService reportService;
    protected final long reportGenerationTimeoutSeconds;
    protected final Logger logger;

    @Inject
    TargetReportGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            ReportService reportService,
            @Named(ReportsModule.REPORT_GENERATION_TIMEOUT_SECONDS)
                    long reportGenerationTimeoutSeconds,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.reportService = reportService;
        this.reportGenerationTimeoutSeconds = reportGenerationTimeoutSeconds;
        this.logger = logger;
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
    public String path() {
        return basePath() + "targets/:targetId/reports/:recordingName";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(
                ResourceAction.READ_TARGET,
                ResourceAction.READ_RECORDING,
                ResourceAction.CREATE_REPORT,
                ResourceAction.READ_REPORT);
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
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        String recordingName = ctx.pathParam("recordingName");
        List<String> queriedFilter = ctx.queryParam("filter");
        String rawFilter = queriedFilter.isEmpty() ? "" : queriedFilter.get(0);
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.HTML.mime());
        try {
            ctx.response()
                    .end(
                            reportService
                                    .get(
                                            getConnectionDescriptorFromContext(ctx),
                                            recordingName,
                                            rawFilter)
                                    .get(reportGenerationTimeoutSeconds, TimeUnit.SECONDS));
        } catch (CompletionException | ExecutionException ee) {

            Exception rootCause = (Exception) ExceptionUtils.getRootCause(ee);

            if (targetRecordingNotFound(rootCause)) {
                throw new HttpException(404, ee);
            }
            throw ee;
        }
    }

    // TODO this needs to also handle the case where sidecar report generator container responds 404
    private boolean targetRecordingNotFound(Exception rootCause) {
        if (rootCause instanceof RecordingNotFoundException) {
            return true;
        }
        boolean isReportGenerationException =
                rootCause instanceof SubprocessReportGenerator.SubprocessReportGenerationException;
        if (!isReportGenerationException) {
            return false;
        }
        SubprocessReportGenerator.SubprocessReportGenerationException generationException =
                (SubprocessReportGenerator.SubprocessReportGenerationException) rootCause;
        boolean isTargetConnectionFailure =
                generationException.getStatus()
                        == SubprocessReportGenerator.ExitStatus.TARGET_CONNECTION_FAILURE;
        boolean isNoSuchRecording =
                generationException.getStatus()
                        == SubprocessReportGenerator.ExitStatus.NO_SUCH_RECORDING;
        return isTargetConnectionFailure || isNoSuchRecording;
    }
}
