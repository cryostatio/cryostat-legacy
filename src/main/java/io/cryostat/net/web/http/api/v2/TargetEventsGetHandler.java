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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.jmc.serialization.SerializableEventTypeInfo;
import io.cryostat.net.AuthManager;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;

class TargetEventsGetHandler extends AbstractV2RequestHandler<List<SerializableEventTypeInfo>> {

    private final TargetConnectionManager targetConnectionManager;

    @Inject
    TargetEventsGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            TargetConnectionManager targetConnectionManager,
            Gson gson) {
        super(auth, credentialsManager, gson);
        this.targetConnectionManager = targetConnectionManager;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_TARGET);
    }

    @Override
    public String path() {
        return basePath() + "targets/:targetId/events";
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
    public IntermediateResponse<List<SerializableEventTypeInfo>> handle(RequestParameters params)
            throws Exception {
        return targetConnectionManager.executeConnectedTask(
                getConnectionDescriptorFromParams(params),
                connection -> {
                    String q = params.getQueryParams().get("q");
                    List<SerializableEventTypeInfo> matchingEvents =
                            connection.getService().getAvailableEventTypes().stream()
                                    .filter(
                                            event ->
                                                    StringUtils.isBlank(q)
                                                            || eventMatchesSearchTerm(
                                                                    event, q.toLowerCase()))
                                    .map(SerializableEventTypeInfo::new)
                                    .collect(Collectors.toList());
                    return new IntermediateResponse<List<SerializableEventTypeInfo>>()
                            .body(matchingEvents);
                });
    }

    private boolean eventMatchesSearchTerm(IEventTypeInfo event, String term) {
        Set<String> terms = new HashSet<>();
        terms.add(event.getEventTypeID().getFullKey());
        terms.addAll(Arrays.asList(event.getHierarchicalCategory()));
        terms.add(event.getDescription());
        terms.add(event.getName());

        return terms.stream()
                .filter(s -> s != null)
                .map(String::toLowerCase)
                .anyMatch(s -> s.contains(term));
    }
}
