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

    @Inject
    CredentialsGetHandler(
            AuthManager auth, CredentialsManager credentialsManager, Gson gson, Logger logger) {
        super(auth, credentialsManager, gson);
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
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
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
            int id = entry.getKey();
            Cred cred = new Cred();
            cred.id = id;
            cred.matchExpression = entry.getValue();
            cred.numMatchingTargets = credentialsManager.resolveMatchingTargets(id).size();
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
        int numMatchingTargets;

        @Override
        public int hashCode() {
            return Objects.hash(id, matchExpression, numMatchingTargets);
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
            return id == other.id
                    && Objects.equals(matchExpression, other.matchExpression)
                    && numMatchingTargets == other.numMatchingTargets;
        }
    }
}
