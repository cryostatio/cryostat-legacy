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

import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.agent.LocalProbeTemplateService;
import io.cryostat.core.agent.ProbeTemplate;
import io.cryostat.core.agent.ProbeValidationException;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.FileUpload;

class ProbeTemplateUploadHandler extends AbstractV2RequestHandler<Void> {

    static final String PATH = "probes/:probetemplateName";

    private final Logger logger;
    private final NotificationFactory notificationFactory;
    private final LocalProbeTemplateService probeTemplateService;
    private final FileSystem fs;
    private static final String NOTIFICATION_CATEGORY = "ProbeTemplateUploaded";

    @Inject
    ProbeTemplateUploadHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            NotificationFactory notificationFactory,
            LocalProbeTemplateService probeTemplateService,
            Logger logger,
            FileSystem fs,
            Gson gson) {
        super(auth, credentialsManager, gson);
        this.notificationFactory = notificationFactory;
        this.logger = logger;
        this.probeTemplateService = probeTemplateService;
        this.fs = fs;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
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
    public boolean isOrdered() {
        return true;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.CREATE_PROBE_TEMPLATE);
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public IntermediateResponse<Void> handle(RequestParameters requestParams) throws Exception {
        try {
            for (FileUpload u : requestParams.getFileUploads()) {
                String templateName = requestParams.getPathParams().get("probetemplateName");
                Path path = fs.pathOf(u.uploadedFileName());
                if (!u.name().equals("probeTemplate")) {
                    fs.deleteIfExists(path);
                    continue;
                }
                try (InputStream is = fs.newInputStream(path)) {
                    ProbeTemplate template = probeTemplateService.addTemplate(is, templateName);
                    notificationFactory
                            .createBuilder()
                            .metaCategory(NOTIFICATION_CATEGORY)
                            .metaType(HttpMimeType.JSON)
                            .message(
                                    Map.of(
                                            "probeTemplate",
                                            templateName,
                                            "templateContent",
                                            template.serialize()))
                            .build()
                            .send();
                } finally {
                    fs.deleteIfExists(path);
                }
            }
        } catch (ProbeValidationException pve) {
            logger.error(pve.getMessage());
            throw new ApiException(400, pve.getMessage(), pve);
        } catch (FileAlreadyExistsException faee) {
            logger.error(faee.getMessage());
            throw new ApiException(400, faee.getMessage(), faee);
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new ApiException(500, e.getMessage(), e);
        }
        return new IntermediateResponse<Void>().body(null);
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.PLAINTEXT);
    }
}
