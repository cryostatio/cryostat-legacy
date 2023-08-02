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

import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Named;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.jwt.DiscoveryJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.util.StringUtil;

import com.nimbusds.jwt.JWT;
import dagger.Lazy;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

class DiscoveryRegistrationCheckHandler extends AbstractDiscoveryJwtConsumingHandler<Void> {

    static final String PATH = "discovery/:id";
    private final DiscoveryStorage storage;
    private final Function<String, UUID> uuidFromString;

    @Inject
    DiscoveryRegistrationCheckHandler(
            AuthManager auth,
            DiscoveryJwtHelper jwtFactory,
            Lazy<WebServer> webServer,
            DiscoveryStorage storage,
            @Named(MainModule.UUID_FROM_STRING) Function<String, UUID> uuidFromString,
            Logger logger) {
        super(storage, auth, jwtFactory, webServer, uuidFromString, logger);
        this.storage = storage;
        this.uuidFromString = uuidFromString;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2_2;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return Set.of();
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    void handleWithValidJwt(RoutingContext ctx, JWT jwt) throws Exception {
        UUID id = this.uuidFromString.apply(StringUtil.requireNonBlank(ctx.pathParam("id"), "id"));
        if (storage.getById(id).isPresent()) {
            writeResponse(ctx, new IntermediateResponse<Void>());
        } else {
            throw new ApiException(404);
        }
    }
}
