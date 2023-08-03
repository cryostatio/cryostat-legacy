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
package io.cryostat.net.web.http;

import java.util.List;

import io.cryostat.net.security.PermissionedAction;
import io.cryostat.net.web.http.api.ApiVersion;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

public interface RequestHandler extends Handler<RoutingContext>, PermissionedAction {
    /** Lower number == higher priority handler */
    static final int DEFAULT_PRIORITY = 100;

    static final String ALL_PATHS = "*";

    default int getPriority() {
        return DEFAULT_PRIORITY;
    }

    ApiVersion apiVersion();

    default String basePath() {
        switch (apiVersion()) {
            case GENERIC:
                return "/";
            default:
                return "/api/" + apiVersion().getVersionString() + "/";
        }
    }

    String path();

    /**
     * Implementations should only implement this OR {@link path()}, not both. Either return
     * non-null here to have the WebServer treat this as a regex path, or leave this as null and use
     * {@link path()} as a non-regex path.
     */
    default String pathRegex() {
        return null;
    }

    HttpMethod httpMethod();

    default List<HttpMimeType> produces() {
        return List.of();
    }

    default List<HttpMimeType> consumes() {
        return List.of();
    }

    default boolean isAvailable() {
        return true;
    }

    default boolean isAsync() {
        return true;
    }

    default boolean isOrdered() {
        return true;
    }
}
