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
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.reports.ReportService;
import io.cryostat.net.reports.ReportsModule;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.jwt.AssetJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.AbstractAssetJwtConsumingHandler;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingNotFoundException;
import io.cryostat.recordings.RecordingSourceTargetNotFoundException;

import com.nimbusds.jwt.JWT;
import dagger.Lazy;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.exception.ExceptionUtils;

class ReportGetWithJwtHandler extends AbstractAssetJwtConsumingHandler {

    static final String PATH = "reports/:sourceTarget/:recordingName/jwt";

    private final ReportService reportService;
    private final RecordingArchiveHelper recordingArchiveHelper;
    private final long generationTimeoutSeconds;

    @Inject
    ReportGetWithJwtHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            AssetJwtHelper jwtFactory,
            Lazy<WebServer> webServer,
            ReportService reportService,
            RecordingArchiveHelper recordingArchiveHelper,
            @Named(ReportsModule.REPORT_GENERATION_TIMEOUT_SECONDS) long generationTimeoutSeconds,
            Logger logger) {
        super(auth, credentialsManager, jwtFactory, webServer, logger);
        this.reportService = reportService;
        this.recordingArchiveHelper = recordingArchiveHelper;
        this.generationTimeoutSeconds = generationTimeoutSeconds;
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
        return List.of(HttpMimeType.JSON);
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
    public void handleWithValidJwt(RoutingContext ctx, JWT jwt) throws Exception {
        String sourceTarget = ctx.pathParam("sourceTarget");
        String recordingName = ctx.pathParam("recordingName");
        try {
            recordingArchiveHelper.validateSourceTarget(sourceTarget);
            List<String> queriedFilter = ctx.queryParam("filter");
            String rawFilter = queriedFilter.isEmpty() ? "" : queriedFilter.get(0);
            Path report =
                    reportService
                            .get(sourceTarget, recordingName, rawFilter)
                            .get(generationTimeoutSeconds, TimeUnit.SECONDS);
            ctx.response().putHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, ctx.getAcceptableContentType());
            ctx.response().sendFile(report.toAbsolutePath().toString());
        } catch (RecordingSourceTargetNotFoundException e) {
            throw new ApiException(404, e.getMessage(), e);
        } catch (ExecutionException | CompletionException ee) {
            if (ExceptionUtils.getRootCause(ee) instanceof RecordingNotFoundException) {
                throw new ApiException(404, ee.getMessage(), ee);
            }
            throw ee;
        }
    }
}
