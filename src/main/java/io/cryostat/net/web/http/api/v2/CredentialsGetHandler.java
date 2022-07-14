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
package io.cryostat.net.web.http.api.v2;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.CredentialsGetHandler.Cred;

import com.google.gson.Gson;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.http.HttpMethod;

class CredentialsGetHandler extends AbstractV2RequestHandler<List<Cred>> {

    private final CredentialsManager credentialsManager;

    @Inject
    CredentialsGetHandler(
            AuthManager auth, CredentialsManager credentialsManager, Gson gson, Logger logger) {
        super(auth, gson);
        this.credentialsManager = credentialsManager;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2_2;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_CREDENTIALS);
    }

    @Override
    public String path() {
        return basePath() + "credentials";
    }

    @Override
    public HttpMimeType mimeType() {
        return HttpMimeType.JSON;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public IntermediateResponse<List<Cred>> handle(RequestParameters requestParams)
            throws Exception {
        Map<Integer, String> credentials = credentialsManager.getAll();
        List<Cred> result = new ArrayList<>(credentials.size());
        for (Map.Entry<Integer, String> entry : credentials.entrySet()) {
            Cred cred = new Cred();
            cred.id = entry.getKey();
            cred.matchExpression = entry.getValue();
            result.add(cred);
        }
        return new IntermediateResponse<List<Cred>>().body(result);
    }

    @SuppressFBWarnings(
            value = "URF_UNREAD_FIELD",
            justification =
                    "The fields are serialized for JSON HTTP response instead of accessed directly")
    static class Cred {
        int id;
        String matchExpression;

        @Override
        public int hashCode() {
            return Objects.hash(id, matchExpression);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Cred other = (Cred) obj;
            return id == other.id && Objects.equals(matchExpression, other.matchExpression);
        }
    }
}
