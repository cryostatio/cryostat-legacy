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
package io.cryostat.net.web.http.api.v1;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import com.google.gson.Gson;

import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.RecordingOptionsCustomizer;
import io.cryostat.core.RecordingOptionsCustomizer.OptionKey;
import io.cryostat.core.log.Logger;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingMetadataManager.SecurityContext;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

class TargetRecordingOptionsPatchHandler extends AbstractAuthenticatedRequestHandler {

    static final String PATH = TargetRecordingOptionsGetHandler.PATH;
    private static final String UNSET_KEYWORD = "unset";
    private final RecordingOptionsCustomizer customizer;
    private final DiscoveryStorage discoveryStorage;
    private final TargetConnectionManager connectionManager;
    private final RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    private final Gson gson;

    @Inject
    TargetRecordingOptionsPatchHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            RecordingOptionsCustomizer customizer,
            DiscoveryStorage discoveryStorage,
            TargetConnectionManager connectionManager,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            Gson gson,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.customizer = customizer;
        this.discoveryStorage = discoveryStorage;
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
    public SecurityContext securityContext(RoutingContext ctx) {
        ConnectionDescriptor cd = getConnectionDescriptorFromContext(ctx);
        return
            discoveryStorage.lookupServiceByTargetId(cd.getTargetId()).map(SecurityContext::new).orElse(null);
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
