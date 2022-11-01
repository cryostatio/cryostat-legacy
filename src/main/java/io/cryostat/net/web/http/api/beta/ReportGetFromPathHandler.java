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
package io.cryostat.net.web.http.api.beta;

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
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.AbstractV2RequestHandler;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.recordings.RecordingMetadataManager.SecurityContext;
import io.cryostat.recordings.RecordingNotFoundException;
import io.cryostat.rules.ArchivePathException;
import io.cryostat.rules.ArchivedRecordingInfo;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class ReportGetFromPathHandler extends AbstractV2RequestHandler<Path> {

    static final String PATH = "fs/reports/:subdirectoryName/:recordingName";

    private final RecordingArchiveHelper archiveHelper;
    private final ReportService reportService;
    private final long reportGenerationTimeoutSeconds;
    private final Logger logger;

    @Inject
    ReportGetFromPathHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            Gson gson,
            RecordingArchiveHelper archiveHelper,
            ReportService reportService,
            @Named(ReportsModule.REPORT_GENERATION_TIMEOUT_SECONDS)
                    long reportGenerationTimeoutSeconds,
            Logger logger) {
        super(auth, credentialsManager, gson);
        this.archiveHelper = archiveHelper;
        this.reportService = reportService;
        this.reportGenerationTimeoutSeconds = reportGenerationTimeoutSeconds;
        this.logger = logger;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.BETA;
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
        return basePath() + PATH;
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.HTML, HttpMimeType.JSON_RAW);
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public SecurityContext securityContext(RequestParameters params) {
        String subdirectoryName = params.getPathParams().get("subdirectoryName");
        String recordingName = params.getPathParams().get("recordingName");
        return archiveHelper.getRecordingsFromPath(subdirectoryName).stream()
                .filter(r -> r.getName().equals(recordingName))
                .findFirst()
                .map(ArchivedRecordingInfo::getMetadata)
                .map(Metadata::getSecurityContext)
                .orElse(SecurityContext.DEFAULT);
    }

    @Override
    public IntermediateResponse<Path> handle(RequestParameters params) throws Exception {
        String subdirectoryName = params.getPathParams().get("subdirectoryName");
        String recordingName = params.getPathParams().get("recordingName");
        try {
            List<String> queriedFilter = params.getQueryParams().getAll("filter");
            String rawFilter = queriedFilter.isEmpty() ? "" : queriedFilter.get(0);
            String contentType =
                    (params.getAcceptableContentType() == null)
                            ? HttpMimeType.HTML.mime()
                            : params.getAcceptableContentType();
            boolean formatted = contentType.equals(HttpMimeType.HTML.mime());
            Path report =
                    reportService
                            .getFromPath(subdirectoryName, recordingName, rawFilter, formatted)
                            .get(reportGenerationTimeoutSeconds, TimeUnit.SECONDS);
            return new IntermediateResponse<Path>().body(report);
        } catch (ExecutionException | CompletionException e) {
            if (ExceptionUtils.getRootCause(e) instanceof ReportGenerationException) {
                ReportGenerationException rge =
                        (ReportGenerationException) ExceptionUtils.getRootCause(e);
                throw new ApiException(rge.getStatusCode(), e.getMessage());
            }
            if (ExceptionUtils.getRootCause(e) instanceof RecordingNotFoundException
                    || ExceptionUtils.getRootCause(e) instanceof ArchivePathException) {
                throw new ApiException(404, e.getMessage(), e);
            }
            throw e;
        }
    }
}
