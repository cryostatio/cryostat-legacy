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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.RecordingOptionsCustomizer;
import io.cryostat.core.RecordingOptionsCustomizer.OptionKey;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

class TargetRecordingOptionsPatchHandler extends AbstractAuthenticatedRequestHandler {

    static final String PATH = TargetRecordingOptionsGetHandler.PATH;
    private static final String UNSET_KEYWORD = "unset";
    private final RecordingOptionsCustomizer customizer;
    private final TargetConnectionManager connectionManager;
    private final RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    private final Gson gson;

    @Inject
    TargetRecordingOptionsPatchHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            RecordingOptionsCustomizer customizer,
            TargetConnectionManager connectionManager,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            Gson gson,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.customizer = customizer;
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
        return HttpMethod.PATCH;
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_TARGET, ResourceAction.UPDATE_TARGET);
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
    public List<HttpMimeType> consumes() {
        return List.of(HttpMimeType.URLENCODED_FORM, HttpMimeType.MULTIPART_FORM);
    }

    @Override
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        Pattern bool = Pattern.compile("true|false|" + UNSET_KEYWORD);
        MultiMap attrs = ctx.request().formAttributes();
        if (attrs.contains("toDisk")) {
            Matcher m = bool.matcher(attrs.get("toDisk"));
            if (!m.matches()) throw new HttpException(400, "Invalid options");
        }
        Arrays.asList("maxAge", "maxSize")
                .forEach(
                        key -> {
                            if (attrs.contains(key)) {
                                try {
                                    String v = attrs.get(key);
                                    if (UNSET_KEYWORD.equals(v)) {
                                        return;
                                    }
                                    Long.parseLong(v);
                                } catch (NumberFormatException e) {
                                    throw new HttpException(400, "Invalid options");
                                }
                            }
                        });
        Map<String, Object> updatedMap =
                connectionManager.executeConnectedTask(
                        getConnectionDescriptorFromContext(ctx),
                        connection -> {
                            Arrays.asList("toDisk", "maxAge", "maxSize")
                                    .forEach(
                                            key -> {
                                                if (attrs.contains(key)) {
                                                    String v = attrs.get(key);
                                                    OptionKey optionKey =
                                                            OptionKey.fromOptionName(key).get();
                                                    if (UNSET_KEYWORD.equals(v)) {
                                                        customizer.unset(optionKey);
                                                    } else {
                                                        customizer.set(optionKey, v);
                                                    }
                                                }
                                            });

                            RecordingOptionsBuilder builder =
                                    recordingOptionsBuilderFactory.create(connection.getService());
                            return TargetRecordingOptionsGetHandler.getRecordingOptions(
                                    connection.getService(), builder);
                        });

        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
        ctx.response().setStatusCode(200);
        ctx.response().end(gson.toJson(updatedMap));
    }
}
