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

import java.io.InputStream;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.core.templates.LocalStorageTemplateService;
import io.cryostat.core.templates.MutableTemplateService.InvalidEventTemplateException;
import io.cryostat.core.templates.MutableTemplateService.InvalidXmlException;
import io.cryostat.core.templates.Template;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

class TemplatesPostHandler extends AbstractAuthenticatedRequestHandler {

    static final String PATH = "templates";

    private final LocalStorageTemplateService templateService;
    private final FileSystem fs;
    private final Logger logger;
    private final NotificationFactory notificationFactory;
    private static final String NOTIFICATION_CATEGORY = "TemplateUploaded";

    @Inject
    TemplatesPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            LocalStorageTemplateService templateService,
            FileSystem fs,
            NotificationFactory notificationFactory,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.notificationFactory = notificationFactory;
        this.templateService = templateService;
        this.fs = fs;
        this.logger = logger;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V1;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.CREATE_TEMPLATE);
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public List<HttpMimeType> consumes() {
        return List.of(HttpMimeType.MULTIPART_FORM);
    }

    @Override
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        boolean handledUpload = false;
        try {
            for (FileUpload u : ctx.fileUploads()) {
                Path path = fs.pathOf(u.uploadedFileName());
                if (!"template".equals(u.name())) {
                    fs.deleteIfExists(path);
                    continue;
                }
                handledUpload = true;
                try (InputStream is = fs.newInputStream(path)) {
                    Template t = templateService.addTemplate(is);
                    notificationFactory
                            .createBuilder()
                            .metaCategory(NOTIFICATION_CATEGORY)
                            .metaType(HttpMimeType.JSON)
                            .message(Map.of("template", t))
                            .build()
                            .send();
                } finally {
                    fs.deleteIfExists(path);
                }
            }
        } catch (InvalidXmlException | InvalidEventTemplateException e) {
            throw new HttpException(400, e.getMessage(), e);
        }
        if (!handledUpload) {
            throw new HttpException(400, "No template submission");
        }
        ctx.response().end();
    }
}
