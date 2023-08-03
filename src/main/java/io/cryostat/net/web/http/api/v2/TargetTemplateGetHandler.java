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

import java.util.EnumSet;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.net.AuthManager;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.jwt.AssetJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import com.nimbusds.jwt.JWT;
import dagger.Lazy;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

class TargetTemplateGetHandler extends AbstractAssetJwtConsumingHandler {

    private final TargetConnectionManager targetConnectionManager;

    @Inject
    TargetTemplateGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            AssetJwtHelper jwt,
            Lazy<WebServer> webServer,
            TargetConnectionManager targetConnectionManager,
            Logger logger) {
        super(auth, credentialsManager, jwt, webServer, logger);
        this.targetConnectionManager = targetConnectionManager;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2_1;
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
    public void handleWithValidJwt(RoutingContext ctx, JWT jwt) throws Exception {
        String templateName = ctx.pathParam("templateName");
        TemplateType templateType = TemplateType.valueOf(ctx.pathParam("templateType"));
        targetConnectionManager
                .executeConnectedTask(
                        getConnectionDescriptorFromJwt(ctx, jwt),
                        conn -> conn.getTemplateService().getXml(templateName, templateType))
                .ifPresentOrElse(
                        doc -> {
                            ctx.response()
                                    .putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JFC.mime());
                            ctx.response().end(doc.toString());
                        },
                        () -> {
                            throw new ApiException(404);
                        });
    }
}
