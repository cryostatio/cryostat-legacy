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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.SecurityContext;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

class TargetTemplateGetHandler extends AbstractAuthenticatedRequestHandler {

    private final TargetConnectionManager targetConnectionManager;
    private final DiscoveryStorage discoveryStorage;

    @Inject
    TargetTemplateGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            TargetConnectionManager targetConnectionManager,
            DiscoveryStorage discoveryStorage,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.targetConnectionManager = targetConnectionManager;
        this.discoveryStorage = discoveryStorage;
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
        return basePath() + "targets/:targetId/templates/:templateName/type/:templateType";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_TARGET, ResourceAction.READ_TEMPLATE);
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JFC, HttpMimeType.XML);
    }

    @Override
    public SecurityContext securityContext(RoutingContext ctx) {
        ConnectionDescriptor cd = getConnectionDescriptorFromContext(ctx);
        return discoveryStorage
                .lookupServiceByTargetId(cd.getTargetId())
                .map(auth::contextFor)
                .orElseThrow(() -> new HttpException(404));
    }

    @Override
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        String templateName = ctx.pathParam("templateName");
        TemplateType templateType = TemplateType.valueOf(ctx.pathParam("templateType"));
        targetConnectionManager
                .executeConnectedTask(
                        getConnectionDescriptorFromContext(ctx),
                        conn -> conn.getTemplateService().getXml(templateName, templateType))
                .ifPresentOrElse(
                        doc -> {
                            ctx.response()
                                    .putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JFC.mime());
                            ctx.response().end(doc.toString());
                        },
                        () -> {
                            throw new HttpException(404);
                        });
    }
}
