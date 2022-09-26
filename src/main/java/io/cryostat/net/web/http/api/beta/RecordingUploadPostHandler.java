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

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import io.cryostat.configuration.Variables;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
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
            Environment env,
            @Named(HttpModule.HTTP_REQUEST_TIMEOUT_SECONDS) long httpTimeoutSeconds,
            WebClient webClient,
            RecordingArchiveHelper recordingArchiveHelper,
            Gson gson) {
        super(auth, gson);
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
    public HttpMimeType mimeType() {
        return HttpMimeType.PLAINTEXT;
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
                if (e.getCause() instanceof RecordingNotFoundException || e.getCause() instanceof RecordingSourceTargetNotFoundException) {
                    throw new ApiException(404, e.getMessage(), e);
                }
            throw e;
        }

        MultipartForm form =
                MultipartForm.create()
                        .binaryFileUpload(
                                "file",
                                recordingName,
                                recordingPath.toString(),
                                HttpMimeType.OCTET_STREAM.toString());

        CompletableFuture<ResponseMessage> future = new CompletableFuture<>();
        webClient
                .postAbs(uploadUrl.toURI().resolve("/load").normalize().toString())
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
