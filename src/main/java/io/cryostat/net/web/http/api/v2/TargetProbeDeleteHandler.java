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

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import com.google.gson.Gson;

import org.apache.commons.lang3.StringUtils;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.agent.AgentJMXHelper;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingMetadataManager.SecurityContext;
import io.vertx.core.http.HttpMethod;

class TargetProbeDeleteHandler extends AbstractV2RequestHandler<Void> {

    static final String PATH = "targets/:targetId/probes";

    private final Logger logger;
    private final NotificationFactory notificationFactory;
    private final FileSystem fs;
    private final DiscoveryStorage discoveryStorage;
    private final TargetConnectionManager connectionManager;
    private final Environment env;
    private static final String NOTIFICATION_CATEGORY = "ProbesRemoved";

    @Inject
    TargetProbeDeleteHandler(
            Logger logger,
            NotificationFactory notificationFactory,
            FileSystem fs,
            AuthManager auth,
            CredentialsManager credentialsManager,
            DiscoveryStorage discoveryStorage,
            TargetConnectionManager connectionManager,
            Environment env,
            Gson gson) {
        super(auth, credentialsManager, gson);
        this.logger = logger;
        this.notificationFactory = notificationFactory;
        this.discoveryStorage = discoveryStorage;
        this.connectionManager = connectionManager;
        this.env = env;
        this.fs = fs;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2;
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.DELETE;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return ResourceAction.NONE;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

	@Override
	public SecurityContext securityContext(RequestParameters params) {
                        ConnectionDescriptor cd = getConnectionDescriptorFromParams(params);
                        return
                            discoveryStorage.lookupServiceByTargetId(cd.getTargetId()).map(SecurityContext::new).orElse(null);
	}

    @Override
    public IntermediateResponse<Void> handle(RequestParameters requestParams) throws Exception {
        Map<String, String> pathParams = requestParams.getPathParams();
        String targetId = pathParams.get("targetId");
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isBlank(targetId)) {
            sb.append("targetId is required.");
            throw new ApiException(400, sb.toString().trim());
        }
        try {
            return connectionManager.executeConnectedTask(
                    getConnectionDescriptorFromParams(requestParams),
                    connection -> {
                        AgentJMXHelper helper = new AgentJMXHelper(connection.getHandle());
                        // The convention for removing probes in the agent controller mbean is to
                        // call defineEventProbes with a null argument.
                        helper.defineEventProbes(null);
                        notificationFactory
                                .createBuilder()
                                .metaCategory(NOTIFICATION_CATEGORY)
                                .metaType(HttpMimeType.JSON)
                                .message(Map.of("target", targetId))
                                .build()
                                .send();
                        return new IntermediateResponse<Void>().body(null);
                    });
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.PLAINTEXT);
    }

    @Override
    public boolean isAsync() {
        return false;
    }
}
