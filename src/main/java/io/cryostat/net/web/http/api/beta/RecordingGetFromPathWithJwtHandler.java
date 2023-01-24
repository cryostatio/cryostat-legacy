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
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.SecurityContext;
import io.cryostat.net.security.jwt.AssetJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.AbstractAssetJwtConsumingHandler;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.net.web.http.api.v2.RequestParameters;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingNotFoundException;
import io.cryostat.rules.ArchivePathException;

import com.nimbusds.jwt.JWT;
import dagger.Lazy;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

public class RecordingGetFromPathWithJwtHandler extends AbstractAssetJwtConsumingHandler {

    static final String PATH = "fs/recordings/:subdirectoryName/:recordingName/jwt";

    private final RecordingArchiveHelper recordingArchiveHelper;

    @Inject
    RecordingGetFromPathWithJwtHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            AssetJwtHelper jwtFactory,
            Lazy<WebServer> webServer,
            RecordingArchiveHelper recordingArchiveHelper,
            Logger logger) {
        super(auth, credentialsManager, jwtFactory, webServer, logger);
        this.recordingArchiveHelper = recordingArchiveHelper;
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
        return EnumSet.of(ResourceAction.READ_RECORDING);
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public SecurityContext securityContext(RequestParameters params) {
        String subdirectoryName = params.getPathParams().get("subdirectoryName");
        String recordingName = params.getPathParams().get("recordingName");
        return recordingArchiveHelper
                .getRecordingFromPath(subdirectoryName, recordingName)
                .orElseThrow(() -> new ApiException(404))
                .getMetadata()
                .getSecurityContext();
    }

    @Override
    public void handleWithValidJwt(RoutingContext ctx, JWT jwt) throws Exception {
        String subdirectoryName = ctx.pathParam("subdirectoryName");
        String recordingName = ctx.pathParam("recordingName");
        try {
            Path archivedRecording =
                    recordingArchiveHelper
                            .getRecordingPathFromPath(subdirectoryName, recordingName)
                            .get();
            ctx.response()
                    .putHeader(
                            HttpHeaders.CONTENT_DISPOSITION,
                            String.format("attachment; filename=\"%s\"", recordingName));
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.OCTET_STREAM.mime());
            ctx.response().sendFile(archivedRecording.toAbsolutePath().toString());
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RecordingNotFoundException
                    || e.getCause() instanceof ArchivePathException) {
                throw new ApiException(404, e.getMessage(), e);
            }
            throw e;
        }
    }
}
