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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.openjdk.jmc.rjmx.ConnectionException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.net.Credentials;
import io.cryostat.net.AgentClient;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.beta.CredentialTestPostHandler.CredentialTestResult;
import io.cryostat.net.web.http.api.v2.AbstractV2RequestHandler;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class CredentialTestPostHandler extends AbstractV2RequestHandler<CredentialTestResult> {

    static final String PATH = "credentials/:targetId";

    private final TargetConnectionManager tcm;

    @Inject
    CredentialTestPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            Gson gson,
            TargetConnectionManager tcm) {
        super(auth, credentialsManager, gson);
        this.tcm = tcm;
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
        return EnumSet.of(ResourceAction.READ_TARGET);
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
    public IntermediateResponse<CredentialTestResult> handle(RequestParameters params)
            throws Exception {
        String targetId = params.getPathParams().get("targetId");
        String username = params.getFormAttributes().get("username");
        String password = params.getFormAttributes().get("password");
        if (StringUtils.isAnyBlank(targetId, username, password)) {
            StringBuilder sb = new StringBuilder();
            if (StringUtils.isBlank(targetId)) {
                sb.append("\"targetId\" is required.");
            }
            if (StringUtils.isBlank(username)) {
                sb.append("\"username\" is required.");
            }
            if (StringUtils.isBlank(password)) {
                sb.append(" \"password\" is required.");
            }

            throw new ApiException(400, sb.toString().trim());
        }
        ConnectionDescriptor noCreds = new ConnectionDescriptor(targetId, null);

        try {
            return new IntermediateResponse<CredentialTestResult>()
                    .body(
                            tcm.executeConnectedTask(
                                    noCreds,
                                    (conn) -> {
                                        conn.connect();
                                        return CredentialTestResult.NA;
                                    }));
        } catch (Exception e1) {
            if (AbstractAuthenticatedRequestHandler.isJmxAuthFailure(e1)
                    || isAgentAuthFailure(e1)) {
                ConnectionDescriptor creds =
                        new ConnectionDescriptor(targetId, new Credentials(username, password));
                try {
                    return new IntermediateResponse<CredentialTestResult>()
                            .body(
                                    tcm.executeConnectedTask(
                                            creds,
                                            (conn) -> {
                                                conn.connect();
                                                return CredentialTestResult.SUCCESS;
                                            }));
                } catch (Exception e2) {
                    if (AbstractAuthenticatedRequestHandler.isJmxAuthFailure(e2)
                            || isAgentAuthFailure(e2)) {
                        return new IntermediateResponse<CredentialTestResult>()
                                .body(CredentialTestResult.FAILURE);
                    }
                    throw e2;
                }
            }
            throw e1;
        }
    }

    boolean isAgentAuthFailure(Exception e) {
        int index = ExceptionUtils.indexOfType(e, ConnectionException.class);
        if (index >= 0) {
            Throwable ce = ExceptionUtils.getThrowableList(e).get(index);
            return ce.getMessage().contains(AgentClient.NULL_CREDENTIALS);
        }
        return false;
    }

    static enum CredentialTestResult {
        SUCCESS,
        FAILURE,
        NA;
    }
}
