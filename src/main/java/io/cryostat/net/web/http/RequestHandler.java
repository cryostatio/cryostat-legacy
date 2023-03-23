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
package io.cryostat.net.web.http;

import java.util.List;

import io.cryostat.net.security.PermissionedAction;
import io.cryostat.net.security.SecurityContext;
import io.cryostat.net.web.http.api.ApiVersion;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

public interface RequestHandler<T> extends Handler<RoutingContext>, PermissionedAction {
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

    SecurityContext securityContext(T ctx);

    default List<SecurityContext> securityContexts(T ctx) {
        return List.of(securityContext(ctx));
    }

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
