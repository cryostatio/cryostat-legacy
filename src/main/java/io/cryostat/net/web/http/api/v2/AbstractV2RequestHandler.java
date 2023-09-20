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
package io.cryostat.net.web.http.api.v2;

import static io.cryostat.util.StringUtil.requireNonBlank;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.net.Credentials;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiMeta;
import io.cryostat.net.web.http.api.ApiResponse;
import io.cryostat.net.web.http.api.ApiResultData;

import com.google.gson.Gson;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

public abstract class AbstractV2RequestHandler<T> implements RequestHandler {

    public abstract boolean requiresAuthentication();

    public static final Pattern AUTH_HEADER_PATTERN =
            Pattern.compile("(?<type>[\\w]+)[\\s]+(?<credentials>[\\S]+)");
    public static final String JMX_AUTHENTICATE_HEADER = "X-JMX-Authenticate";
    public static final String JMX_AUTHORIZATION_HEADER = "X-JMX-Authorization";

    protected final AuthManager auth;
    protected final CredentialsManager credentialsManager;
    protected final Gson gson;

    protected AbstractV2RequestHandler(
            AuthManager auth, CredentialsManager credentialsManager, Gson gson) {
        this.auth = auth;
        this.credentialsManager = credentialsManager;
        this.gson = gson;
    }

    public abstract IntermediateResponse<T> handle(RequestParameters requestParams)
            throws Exception;

    @Override
    public final void handle(RoutingContext ctx) {
        RequestParameters requestParams = RequestParameters.from(ctx);
        try {
            if (requiresAuthentication()) {
                boolean permissionGranted =
                        validateRequestAuthorization(
                                        requestParams.getHeaders().get(HttpHeaders.AUTHORIZATION))
                                .get();
                if (!permissionGranted) {
                    // expected to go into catch clause below
                    throw new ApiException(401, "HTTP Authorization Failure");
                }
            }
            writeResponse(ctx, handle(requestParams));
        } catch (ApiException | HttpException e) {
            throw e;
        } catch (Exception e) {
            if (AbstractAuthenticatedRequestHandler.isAuthenticationFailure(e)) {
                throw new ApiException(401, "HTTP Unauthorized", e);
            }
            if (AbstractAuthenticatedRequestHandler.isAuthorizationFailure(e)) {
                throw new ApiException(403, "HTTP Forbidden", e);
            }
            if (AbstractAuthenticatedRequestHandler.isTargetConnectionFailure(e)) {
                handleConnectionException(ctx, e);
            }
            throw new ApiException(500, e);
        }
    }

    protected Future<Boolean> validateRequestAuthorization(String authHeader) throws Exception {
        return auth.validateHttpHeader(() -> authHeader, resourceActions());
    }

    protected ConnectionDescriptor getConnectionDescriptorFromParams(RequestParameters params) {
        String targetId = params.getPathParams().get("targetId");
        try {
            Credentials credentials;
            if (params.getHeaders().contains(JMX_AUTHORIZATION_HEADER)) {
                String proxyAuth = params.getHeaders().get(JMX_AUTHORIZATION_HEADER);
                Matcher m = AUTH_HEADER_PATTERN.matcher(proxyAuth);
                if (!m.find()) {
                    params.getHeaders().set(JMX_AUTHENTICATE_HEADER, "Basic");
                    throw new ApiException(427, "Invalid " + JMX_AUTHORIZATION_HEADER + " format");
                } else {
                    String t = m.group("type");
                    if (!"basic".equals(t.toLowerCase())) {
                        params.getHeaders().set(JMX_AUTHENTICATE_HEADER, "Basic");
                        throw new ApiException(
                                427, "Unacceptable " + JMX_AUTHORIZATION_HEADER + " type");
                    } else {
                        String c;
                        try {
                            c =
                                    new String(
                                            Base64.getUrlDecoder().decode(m.group("credentials")),
                                            StandardCharsets.UTF_8);
                        } catch (IllegalArgumentException iae) {
                            params.getHeaders().set(JMX_AUTHENTICATE_HEADER, "Basic");
                            throw new ApiException(
                                    427,
                                    JMX_AUTHORIZATION_HEADER
                                            + " credentials do not appear to be Base64-encoded",
                                    iae);
                        }
                        String[] parts = c.split(":");
                        if (parts.length != 2) {
                            params.getHeaders().set(JMX_AUTHENTICATE_HEADER, "Basic");
                            throw new ApiException(
                                    427,
                                    "Unrecognized "
                                            + JMX_AUTHORIZATION_HEADER
                                            + " credential format");
                        }
                        credentials = new Credentials(parts[0], parts[1]);
                    }
                }
            } else {
                credentials = credentialsManager.getCredentialsByTargetId(targetId);
            }
            return new ConnectionDescriptor(targetId, credentials);
        } catch (ScriptException e) {
            throw new ApiException(500, e);
        }
    }

    protected void writeResponse(RoutingContext ctx, IntermediateResponse<T> intermediateResponse) {
        HttpServerResponse response = ctx.response();
        response.setStatusCode(intermediateResponse.getStatusCode());
        if (intermediateResponse.getStatusMessage() != null) {
            response.setStatusMessage(intermediateResponse.getStatusMessage());
        }
        intermediateResponse.getHeaders().forEach(response::putHeader);
        HttpMimeType contentType = HttpMimeType.fromString(ctx.getAcceptableContentType());
        if (contentType == HttpMimeType.UNKNOWN && !produces().isEmpty()) {
            contentType = produces().get(0);
        }
        response.putHeader(HttpHeaders.CONTENT_TYPE, contentType.mime());

        switch (contentType) {
            case PLAINTEXT:
            case JSON:
                if (!produces().contains(HttpMimeType.JSON_RAW)) {
                    ApiMeta meta = new ApiMeta(contentType, response.getStatusMessage());
                    ApiResultData<T> data = new ApiResultData<>(intermediateResponse.getBody());
                    ApiResponse<ApiResultData<T>> body = new ApiResponse<>(meta, data);

                    response.end(gson.toJson(body));
                    return;
                }
            default:
                if (intermediateResponse.getBody() instanceof File) {
                    response.sendFile(((File) intermediateResponse.getBody()).getPath());
                } else if (intermediateResponse.getBody() instanceof Path) {
                    response.sendFile(((Path) intermediateResponse.getBody()).toString());
                } else if (intermediateResponse.getBody() instanceof Buffer) {
                    response.end((Buffer) intermediateResponse.getBody());
                } else {
                    response.end(intermediateResponse.getBody().toString());
                }
                return;
        }
    }

    protected String getNonBlankFormAttribute(RequestParameters params, String key)
            throws IllegalArgumentException {
        return requireNonBlank(params.getFormAttributes().get(key), key);
    }

    protected String getNonBlankJsonAttribute(RequestParameters params, String key)
            throws IllegalArgumentException {
        return requireNonBlank(new JsonObject(params.getBody()).getString(key), key);
    }

    private void handleConnectionException(RoutingContext ctx, Exception e) {
        if (AbstractAuthenticatedRequestHandler.isJmxAuthFailure(e)) {
            ctx.response().putHeader(JMX_AUTHENTICATE_HEADER, "Basic");
            throw new ApiException(427, "Authentication Failure", "JMX Authentication Failure", e);
        }
        if (AbstractAuthenticatedRequestHandler.isUnknownTargetFailure(e)) {
            throw new ApiException(404, "Connection Failure", "Target Not Found", e);
        }
        if (AbstractAuthenticatedRequestHandler.isJmxSslFailure(e)) {
            throw new ApiException(502, "Connection Failure", "Target SSL Untrusted", e);
        }
        if (AbstractAuthenticatedRequestHandler.isServiceTypeFailure(e)) {
            throw new ApiException(504, "Connection Failure", "Non-JMX Port", e);
        }
    }
}
