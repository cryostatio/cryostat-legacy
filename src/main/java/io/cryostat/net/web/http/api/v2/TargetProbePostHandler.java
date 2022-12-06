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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.agent.AgentJMXHelper;
import io.cryostat.core.agent.Event;
import io.cryostat.core.agent.LocalProbeTemplateService;
import io.cryostat.core.agent.ProbeTemplate;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;

/**
 * TargetProbePostHandler will facilitate adding probes to a target and will have the following form
 * and response types:
 *
 * <p>POST /api/v2/targets/:targetId/probes/
 *
 * <p>targetId - The location of the target JVM to connect to, in the form of a service:rmi:jmx://
 * JMX Service URL. Should use percent-encoding.
 *
 * <p>Parameters
 *
 * <p>probeTemplate - name of the probe template to use
 *
 * <p>Responses
 *
 * <p>200 - No body
 *
 * <p>401 - User authentication failed. The body is an error message. There will be an
 * X-WWW-Authenticate: $SCHEME header that indicates the authentication scheme that is used.
 *
 * <p>404 - The target could not be found. The body is an error message.
 *
 * <p>427 - JMX authentication failed. The body is an error message. There will be an
 * X-JMX-Authenticate: $SCHEME header that indicates the authentication scheme that is used.
 */
class TargetProbePostHandler extends AbstractV2RequestHandler<Void> {

    static final String PATH = "targets/:targetId/probes/:probeTemplate";

    private final Logger logger;
    private final NotificationFactory notificationFactory;
    private final LocalProbeTemplateService probeTemplateService;
    private final FileSystem fs;
    private final TargetConnectionManager connectionManager;
    private final Environment env;
    private static final String NOTIFICATION_CATEGORY = "ProbeTemplateApplied";

    @Inject
    TargetProbePostHandler(
            Logger logger,
            NotificationFactory notificationFactory,
            LocalProbeTemplateService service,
            FileSystem fs,
            AuthManager auth,
            CredentialsManager credentialsManager,
            TargetConnectionManager connectionManager,
            Environment env,
            Gson gson) {
        super(auth, credentialsManager, gson);
        this.logger = logger;
        this.notificationFactory = notificationFactory;
        this.probeTemplateService = service;
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
        return HttpMethod.POST;
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
    public IntermediateResponse<Void> handle(RequestParameters requestParams) throws Exception {
        Map<String, String> pathParams = requestParams.getPathParams();
        String targetId = pathParams.get("targetId");
        String probeTemplate = pathParams.get("probeTemplate");
        if (StringUtils.isAnyBlank(targetId, probeTemplate)) {
            StringBuilder sb = new StringBuilder();
            if (StringUtils.isBlank(targetId)) {
                sb.append("targetId is required.");
            }
            if (StringUtils.isBlank(probeTemplate)) {
                sb.append("\"probeTemplate\" is required.");
            }
            throw new ApiException(400, sb.toString().trim());
        }
        return connectionManager.executeConnectedTask(
                getConnectionDescriptorFromParams(requestParams),
                connection -> {
                    AgentJMXHelper helper = new AgentJMXHelper(connection.getHandle());
                    String templateContent = probeTemplateService.getTemplate(probeTemplate);
                    helper.defineEventProbes(templateContent);
                    ProbeTemplate template = new ProbeTemplate();
                    template.deserialize(
                            new ByteArrayInputStream(
                                    templateContent.getBytes(StandardCharsets.UTF_8)));
                    List<Event> events = Arrays.asList(template.getEvents());
                    notificationFactory
                            .createBuilder()
                            .metaCategory(NOTIFICATION_CATEGORY)
                            .metaType(HttpMimeType.JSON)
                            .message(
                                    Map.of(
                                            "targetId",
                                            targetId,
                                            "probeTemplate",
                                            probeTemplate,
                                            "events",
                                            events))
                            .build()
                            .send();
                    return new IntermediateResponse<Void>().body(null);
                });
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
