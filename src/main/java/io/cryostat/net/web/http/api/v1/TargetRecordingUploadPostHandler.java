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
package io.cryostat.net.web.http.api.v1;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.configuration.Variables;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.HttpModule;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingNotFoundException;
import io.cryostat.util.HttpStatusCodeIdentifier;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.ext.web.multipart.MultipartForm;
import org.apache.commons.validator.routines.UrlValidator;

class TargetRecordingUploadPostHandler extends AbstractAuthenticatedRequestHandler {

    private final Environment env;
    private final TargetConnectionManager targetConnectionManager;
    private final long httpTimeoutSeconds;
    private final WebClient webClient;
    private final FileSystem fs;

    @Inject
    TargetRecordingUploadPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            Environment env,
            TargetConnectionManager targetConnectionManager,
            @Named(HttpModule.HTTP_REQUEST_TIMEOUT_SECONDS) long httpTimeoutSeconds,
            WebClient webClient,
            FileSystem fs,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.env = env;
        this.targetConnectionManager = targetConnectionManager;
        this.httpTimeoutSeconds = httpTimeoutSeconds;
        this.webClient = webClient;
        this.fs = fs;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V1;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public String path() {
        return basePath() + "targets/:targetId/recordings/:recordingName/upload";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_TARGET, ResourceAction.READ_RECORDING);
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.PLAINTEXT);
    }

    @Override
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        try {
            URL uploadUrl = new URL(env.getEnv(Variables.GRAFANA_DATASOURCE_ENV));
            boolean isValidUploadUrl =
                    new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS).isValid(uploadUrl.toString());
            if (!isValidUploadUrl) {
                throw new HttpException(
                        501,
                        String.format(
                                "$%s=%s is an invalid datasource URL",
                                Variables.GRAFANA_DATASOURCE_ENV, uploadUrl.toString()));
            }
            ResponseMessage response = doPost(ctx, uploadUrl);
            if (!HttpStatusCodeIdentifier.isSuccessCode(response.statusCode)
                    || response.statusMessage == null
                    || response.body == null) {
                throw new HttpException(
                        512,
                        String.format(
                                "Invalid response from datasource server; datasource URL may be"
                                    + " incorrect, or server may not be functioning properly: %d"
                                    + " %s",
                                response.statusCode, response.statusMessage));
            }
            ctx.response().setStatusCode(response.statusCode);
            ctx.response().setStatusMessage(response.statusMessage);
            ctx.response().end(response.body);
        } catch (MalformedURLException e) {
            throw new HttpException(501, e);
        } catch (RecordingNotFoundException e) {
            throw new HttpException(404, e);
        }
    }

    private ResponseMessage doPost(RoutingContext ctx, URL uploadUrl) throws Exception {
        String targetId = ctx.pathParam("targetId");
        String recordingName = ctx.pathParam("recordingName");
        Path recordingPath =
                targetConnectionManager.executeConnectedTask(
                        getConnectionDescriptorFromContext(ctx),
                        connection ->
                                getRecordingCopyPath(connection, targetId, recordingName)
                                        .orElseThrow(
                                                () ->
                                                        new RecordingNotFoundException(
                                                                targetId, recordingName)));

        MultipartForm form =
                MultipartForm.create()
                        .binaryFileUpload(
                                "file",
                                WebServer.DATASOURCE_FILENAME,
                                recordingPath.toString(),
                                HttpMimeType.OCTET_STREAM.toString());

        CompletableFuture<ResponseMessage> future = new CompletableFuture<>();
        try {
            webClient
                    .postAbs(uploadUrl.toURI().resolve("/load").normalize().toString())
                    .addQueryParam("overwrite", "true")
                    .timeout(TimeUnit.SECONDS.toMillis(httpTimeoutSeconds))
                    .sendMultipartForm(
                            form,
                            uploadHandler -> {
                                if (uploadHandler.failed()) {
                                    future.completeExceptionally(uploadHandler.cause());
                                    return;
                                }
                                HttpResponse<Buffer> response = uploadHandler.result();
                                future.complete(
                                        new ResponseMessage(
                                                response.statusCode(),
                                                response.statusMessage(),
                                                response.bodyAsString()));
                            });
            return future.get();
        } finally {
            fs.deleteIfExists(recordingPath);
        }
    }

    Optional<Path> getRecordingCopyPath(
            JFRConnection connection, String targetId, String recordingName) throws Exception {
        return connection.getService().getAvailableRecordings().stream()
                .filter(recording -> recording.getName().equals(recordingName))
                .findFirst()
                .map(
                        descriptor -> {
                            try {
                                Path tempFile = fs.createTempFile(null, null);
                                try (InputStream stream =
                                        connection.getService().openStream(descriptor, false)) {
                                    fs.copy(stream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                                }
                                return tempFile;
                            } catch (Exception e) {
                                throw new HttpException(500, e);
                            }
                        });
    }

    private static class ResponseMessage {
        final int statusCode;
        final String statusMessage;
        final String body;

        ResponseMessage(int statusCode, String statusMessage, String body) {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.body = body;
        }
    }
}
