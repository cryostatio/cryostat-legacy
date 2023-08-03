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
import java.util.Set;

import javax.inject.Inject;

import org.openjdk.jmc.common.unit.IOptionDescriptor;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.jmc.serialization.SerializableOptionDescriptor;
import io.cryostat.net.AuthManager;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;

class TargetRecordingOptionsListGetHandler
        extends AbstractV2RequestHandler<List<SerializableOptionDescriptor>> {

    private final TargetConnectionManager connectionManager;

    @Inject
    TargetRecordingOptionsListGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            TargetConnectionManager connectionManager,
            Gson gson) {
        super(auth, credentialsManager, gson);
        this.connectionManager = connectionManager;
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
        return basePath() + "targets/:targetId/recordingOptionsList";
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
    public IntermediateResponse<List<SerializableOptionDescriptor>> handle(
            RequestParameters requestParams) throws Exception {
        List<SerializableOptionDescriptor> options =
                connectionManager.executeConnectedTask(
                        getConnectionDescriptorFromParams(requestParams),
                        connection -> {
                            Map<String, IOptionDescriptor<?>> origOptions =
                                    connection.getService().getAvailableRecordingOptions();
                            List<SerializableOptionDescriptor> serializableOptions =
                                    new ArrayList<>(origOptions.size());
                            for (IOptionDescriptor<?> option : origOptions.values()) {
                                serializableOptions.add(new SerializableOptionDescriptor(option));
                            }
                            return serializableOptions;
                        });
        return new IntermediateResponse<List<SerializableOptionDescriptor>>().body(options);
    }
}
