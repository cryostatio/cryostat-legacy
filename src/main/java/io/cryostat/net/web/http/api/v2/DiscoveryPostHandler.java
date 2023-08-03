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
import java.util.UUID;
import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Named;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.discovery.DiscoveryStorage.NotFoundException;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.jwt.DiscoveryJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.util.StringUtil;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.nimbusds.jwt.JWT;
import dagger.Lazy;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

class DiscoveryPostHandler extends AbstractDiscoveryJwtConsumingHandler<Void> {

    static final String PATH = "discovery/:id";
    private final DiscoveryStorage storage;
    private final Function<String, UUID> uuidFromString;
    private final Gson gson;

    @Inject
    DiscoveryPostHandler(
            AuthManager auth,
            DiscoveryJwtHelper jwtFactory,
            Lazy<WebServer> webServer,
            DiscoveryStorage storage,
            @Named(MainModule.UUID_FROM_STRING) Function<String, UUID> uuidFromString,
            Gson gson,
            Logger logger) {
        super(storage, auth, jwtFactory, webServer, uuidFromString, logger);
        this.storage = storage;
        this.uuidFromString = uuidFromString;
        this.gson = gson;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2_2;
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
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(
                ResourceAction.CREATE_TARGET,
                ResourceAction.UPDATE_TARGET,
                ResourceAction.DELETE_TARGET);
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    void handleWithValidJwt(RoutingContext ctx, JWT jwt) throws Exception {
        try {
            UUID id =
                    this.uuidFromString.apply(
                            StringUtil.requireNonBlank(ctx.pathParam("id"), "id"));
            String body = ctx.body().asString();
            Set<AbstractNode> nodes =
                    gson.fromJson(
                            StringUtil.requireNonBlank(body, "body"),
                            new TypeToken<Set<AbstractNode>>() {}.getType());
            // TODO validate the nodes more thoroughly, all branches should terminate in leaves, no
            // fields should be null, etc.
            storage.update(id, nodes);

            writeResponse(ctx, new IntermediateResponse<Void>());
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            throw new ApiException(400, e);
        } catch (NotFoundException e) {
            throw new ApiException(404, e);
        }
    }
}
