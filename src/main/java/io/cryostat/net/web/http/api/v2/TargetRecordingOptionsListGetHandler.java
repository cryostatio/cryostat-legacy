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
import java.util.Set;

import javax.inject.Inject;

import org.openjdk.jmc.common.unit.IOptionDescriptor;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.jmc.serialization.SerializableOptionDescriptor;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.SecurityContext;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;

class TargetRecordingOptionsListGetHandler
        extends AbstractV2RequestHandler<List<SerializableOptionDescriptor>> {

    private final TargetConnectionManager connectionManager;
    private final DiscoveryStorage discoveryStorage;

    @Inject
    TargetRecordingOptionsListGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            TargetConnectionManager connectionManager,
            DiscoveryStorage discoveryStorage,
            Gson gson) {
        super(auth, credentialsManager, gson);
        this.connectionManager = connectionManager;
        this.discoveryStorage = discoveryStorage;
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
    public SecurityContext securityContext(RequestParameters params) {
        ConnectionDescriptor cd = getConnectionDescriptorFromParams(params);
        return discoveryStorage
                .lookupServiceByTargetId(cd.getTargetId())
                .map(auth::contextFor)
                .orElseThrow(() -> new ApiException(404));
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
