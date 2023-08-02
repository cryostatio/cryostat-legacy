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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;

import com.google.gson.Gson;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

class TargetRecordingOptionsGetHandler extends AbstractAuthenticatedRequestHandler {

    static final String PATH = "targets/:targetId/recordingOptions";
    private final TargetConnectionManager connectionManager;
    private final RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    private final Gson gson;

    @Inject
    TargetRecordingOptionsGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            TargetConnectionManager connectionManager,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            Gson gson,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.connectionManager = connectionManager;
        this.recordingOptionsBuilderFactory = recordingOptionsBuilderFactory;
        this.gson = gson;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V1;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_TARGET);
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
    }

    @Override
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        Map<String, Object> optionMap =
                connectionManager.executeConnectedTask(
                        getConnectionDescriptorFromContext(ctx),
                        connection -> {
                            RecordingOptionsBuilder builder =
                                    recordingOptionsBuilderFactory.create(connection.getService());
                            return getRecordingOptions(connection.getService(), builder);
                        });
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
        ctx.response().end(gson.toJson(optionMap));
    }

    static Map<String, Object> getRecordingOptions(
            IFlightRecorderService service, RecordingOptionsBuilder builder) throws Exception {
        IConstrainedMap<String> recordingOptions = builder.build();

        Map<String, IOptionDescriptor<?>> targetRecordingOptions =
                service.getAvailableRecordingOptions();

        Map<String, Object> map = new HashMap<String, Object>();

        if (recordingOptions.get("toDisk") != null) {
            map.put("toDisk", recordingOptions.get("toDisk"));
        } else {
            map.put("toDisk", targetRecordingOptions.get("disk").getDefault());
        }

        map.put("maxAge", getNumericOption("maxAge", recordingOptions, targetRecordingOptions));
        map.put("maxSize", getNumericOption("maxSize", recordingOptions, targetRecordingOptions));

        return map;
    }

    private static Long getNumericOption(
            String name,
            IConstrainedMap<String> defaultOptions,
            Map<String, IOptionDescriptor<?>> targetOptions) {
        Object value;

        if (defaultOptions.get(name) != null) {
            value = defaultOptions.get(name);
        } else {
            value = targetOptions.get(name).getDefault();
        }

        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        return null;
    }
}
