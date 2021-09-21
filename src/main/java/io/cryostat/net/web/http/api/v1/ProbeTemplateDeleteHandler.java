package io.cryostat.net.web.http.api.v1;

import io.cryostat.core.agent.LocalProbeTemplateService;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;

import javax.inject.Inject;
import java.util.Map;

public class ProbeTemplateDeleteHandler extends AbstractAuthenticatedRequestHandler {


    static final String PATH = "probes";

    private static Logger logger;
    private final NotificationFactory notificationFactory;
    private final LocalProbeTemplateService probeTemplateService;
    private final FileSystem fs;
    private static final String  NOTIFICATION_CATEGORY = "ProbeTemplateUploaded";

    @Inject
    ProbeTemplateDeleteHandler(
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
        String probeTemplateName = ctx.pathParam("templateName");
        try {
            this.probeTemplateService.deleteTemplate(probeTemplateName);
            notificationFactory.createBuilder()
                .metaCategory(NOTIFICATION_CATEGORY)
                .metaType(HttpMimeType.JSON)
                .message(Map.of("probeTemplate", probeTemplateName))
                .build()
                .send();
            ctx.response().end();
        } catch (Exception e) {
            throw new HttpStatusException(400, e.getMessage(), e);
        }
    }
}
