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

import io.cryostat.core.agent.LocalProbeTemplateService;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;

import javax.inject.Inject;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

class ProbeTemplateUploadHandler extends AbstractAuthenticatedRequestHandler {

    static final String PATH = "probes";

    private static Logger logger;
    private final NotificationFactory notificationFactory;
    private final LocalProbeTemplateService probeTemplateService;
    private final FileSystem fs;
    private static final String  NOTIFICATION_CATEGORY = "ProbeTemplateUploaded";

    @Inject
    ProbeTemplateUploadHandler(
            AuthManager auth,
            NotificationFactory notificationFactory,
            LocalProbeTemplateService probeTemplateService,
            Logger logger,
            FileSystem fs
    ) {
        super(auth);
        this.notificationFactory = notificationFactory;
        this.logger = logger;
        this.probeTemplateService = probeTemplateService;
        this.fs = fs;
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
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        try {
            for (FileUpload u : ctx.fileUploads()) {
                Path path = fs.pathOf(u.uploadedFileName());
                logger.info("Found path: " + u.uploadedFileName());
                try (InputStream is = fs.newInputStream(path)) {
                    notificationFactory.createBuilder()
                        .metaCategory(NOTIFICATION_CATEGORY)
                        .metaType(HttpMimeType.JSON)
                        .message(Map.of("probeTemplate", u.uploadedFileName()))
                        .build()
                        .send();
                    logger.info("Adding template to probeTemplateService: " + path.toString());
                    probeTemplateService.addTemplate(is);
                } finally {
                    fs.deleteIfExists(path);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new HttpStatusException(400, e.getMessage(), e);
        }
        ctx.response().end();
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.CREATE_PROBE_TEMPLATE);
    }
}
