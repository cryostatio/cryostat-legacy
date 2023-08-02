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
package io.cryostat.net.web;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

class DeprecatedHandlerDecorator implements Handler<RoutingContext> {

    private final boolean forRemoval;
    private final String alternateLocation;

    DeprecatedHandlerDecorator(boolean forRemoval, String alternateLocation) {
        this.forRemoval = forRemoval;
        this.alternateLocation = alternateLocation;
    }

    @Override
    public void handle(RoutingContext ctx) {
        ctx.response().putHeader("deprecation", "true");
        ctx.response().putHeader("link", String.format("%s; rel=alternate", alternateLocation));
        if (forRemoval) {
            ctx.response().putHeader(HttpHeaders.LOCATION, alternateLocation);
            ctx.response().setStatusCode(410 /*Gone*/).end("Deprecated. See " + alternateLocation);
        } else {
            ctx.next();
        }
    }
}
