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
import io.cryostat.recordings.RecordingNotFoundException;
import io.cryostat.rules.ArchivePathException;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class ReportGetFromPathHandler extends AbstractV2RequestHandler<Path> {

    static final String PATH = "fs/reports/:subdirectoryName/:recordingName";

    private final ReportService reportService;
    private final long reportGenerationTimeoutSeconds;

    @Inject
    ReportGetFromPathHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            Gson gson,
            ReportService reportService,
            @Named(ReportsModule.REPORT_GENERATION_TIMEOUT_SECONDS)
                    long reportGenerationTimeoutSeconds) {
        super(auth, credentialsManager, gson);
        this.reportService = reportService;
        this.reportGenerationTimeoutSeconds = reportGenerationTimeoutSeconds;
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
        return List.of(HttpMimeType.JSON_RAW);
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public IntermediateResponse<Path> handle(RequestParameters params) throws Exception {
        String subdirectoryName = params.getPathParams().get("subdirectoryName");
        String recordingName = params.getPathParams().get("recordingName");
        try {
            List<String> queriedFilter = params.getQueryParams().getAll("filter");
            String rawFilter = queriedFilter.isEmpty() ? "" : queriedFilter.get(0);
            Path report =
                    reportService
                            .getFromPath(subdirectoryName, recordingName, rawFilter)
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
