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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.net.Credentials;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
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
        } catch (Exception e) {
            if (e.getCause() instanceof SecurityException) {
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
                    if (e2.getCause() instanceof SecurityException) {
                        return new IntermediateResponse<CredentialTestResult>()
                                .body(CredentialTestResult.FAILURE);
                    }
                    throw new ApiException(500, e2);
                }
            }
            throw new ApiException(500, e);
        }
    }

    static enum CredentialTestResult {
        SUCCESS,
        FAILURE,
        NA;
    }
}
