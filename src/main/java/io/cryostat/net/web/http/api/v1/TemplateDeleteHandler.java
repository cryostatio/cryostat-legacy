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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.templates.LocalStorageTemplateService;
import io.cryostat.core.templates.MutableTemplateService.InvalidEventTemplateException;
import io.cryostat.core.templates.Template;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

class TemplateDeleteHandler extends AbstractAuthenticatedRequestHandler {

    private final LocalStorageTemplateService templateService;
    private final NotificationFactory notificationFactory;
    private static final String NOTIFICATION_CATEGORY = "TemplateDeleted";

    @Inject
    TemplateDeleteHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            LocalStorageTemplateService templateService,
            NotificationFactory notificationFactory,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.templateService = templateService;
        this.notificationFactory = notificationFactory;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V1;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.DELETE;
    }

    @Override
    public String path() {
        return basePath() + "templates/:templateName";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.DELETE_TEMPLATE);
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        String templateName = ctx.pathParam("templateName");
        try {
            Optional<Template> opt =
                    templateService.getTemplates().stream()
                            .filter(t -> Objects.equals(templateName, t.getName()))
                            .findFirst();
            Template t = opt.orElseThrow(() -> new HttpException(404, templateName));
            templateService.deleteTemplate(t);
            ctx.response().end();
            notificationFactory
                    .createBuilder()
                    .metaCategory(NOTIFICATION_CATEGORY)
                    .metaType(HttpMimeType.JSON)
                    .message(Map.of("template", t))
                    .build()
                    .send();
        } catch (InvalidEventTemplateException iete) {
            throw new HttpException(400, iete.getMessage(), iete);
        }
    }
}
