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

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.configuration.Variables;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.HttpModule;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.AbstractV2RequestHandler;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingNotFoundException;
import io.cryostat.recordings.RecordingSourceTargetNotFoundException;
import io.cryostat.util.HttpStatusCodeIdentifier;

import com.google.gson.Gson;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;
import org.apache.commons.validator.routines.UrlValidator;

class RecordingUploadPostHandler extends AbstractV2RequestHandler<String> {

    static final String PATH = "recordings/:sourceTarget/:recordingName/upload";

    private final Environment env;
    private final long httpTimeoutSeconds;
    private final WebClient webClient;
    private final RecordingArchiveHelper recordingArchiveHelper;

    @Inject
    RecordingUploadPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            Environment env,
            @Named(HttpModule.HTTP_REQUEST_TIMEOUT_SECONDS) long httpTimeoutSeconds,
            WebClient webClient,
            RecordingArchiveHelper recordingArchiveHelper,
            Gson gson) {
        super(auth, credentialsManager, gson);
        this.env = env;
        this.httpTimeoutSeconds = httpTimeoutSeconds;
        this.webClient = webClient;
        this.recordingArchiveHelper = recordingArchiveHelper;
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
        return HttpMethod.POST;
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
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public IntermediateResponse<String> handle(RequestParameters params) throws Exception {
        String sourceTarget = params.getPathParams().get("sourceTarget");
        String recordingName = params.getPathParams().get("recordingName");
        try {
            URL uploadUrl = new URL(env.getEnv(Variables.GRAFANA_DATASOURCE_ENV));
            boolean isValidUploadUrl =
                    new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS).isValid(uploadUrl.toString());
            if (!isValidUploadUrl) {
                throw new ApiException(
                        501,
                        String.format(
                                "$%s=%s is an invalid datasource URL",
                                Variables.GRAFANA_DATASOURCE_ENV, uploadUrl.toString()));
            }
            ResponseMessage response = doPost(sourceTarget, recordingName, uploadUrl);
            if (!HttpStatusCodeIdentifier.isSuccessCode(response.statusCode)
                    || response.statusMessage == null
                    || response.body == null) {
                throw new ApiException(
                        512,
                        String.format(
                                "Invalid response from datasource server; datasource URL may be"
                                    + " incorrect, or server may not be functioning properly: %d"
                                    + " %s",
                                response.statusCode, response.statusMessage));
            }
            return new IntermediateResponse<String>().body(response.body);
        } catch (MalformedURLException e) {
            throw new ApiException(501, e);
        }
    }

    private ResponseMessage doPost(String sourceTarget, String recordingName, URL uploadUrl)
            throws Exception {
        Path recordingPath = null;
        try {
            recordingArchiveHelper.validateSourceTarget(sourceTarget);
            recordingPath =
                    recordingArchiveHelper.getRecordingPath(sourceTarget, recordingName).get();
        } catch (RecordingSourceTargetNotFoundException e) {
            throw new ApiException(404, e.getMessage(), e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RecordingNotFoundException) {
                throw new ApiException(404, e.getMessage(), e);
            }
            throw e;
        }

        MultipartForm form =
                MultipartForm.create()
                        .binaryFileUpload(
                                "file",
                                WebServer.DATASOURCE_FILENAME,
                                recordingPath.toString(),
                                HttpMimeType.OCTET_STREAM.toString());

        CompletableFuture<ResponseMessage> future = new CompletableFuture<>();
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
