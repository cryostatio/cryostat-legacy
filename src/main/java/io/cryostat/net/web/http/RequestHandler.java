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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import io.cryostat.core.log.Logger;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.api.ApiVersion;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;

public interface RequestHandler extends Handler<RoutingContext> {
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

    HttpMethod httpMethod();

    /**
     * Default or fallback set of resource actions that this handler performs.
     **/
    Set<ResourceAction> resourceActions();

    /**
     * Set of resource actions that this handler performs. This will be read from the
     * com.example.package.ClassName.properties resource file.
     * The expected file format looks like: "resourceactions: READ_TARGET;CREATE_RECORDING".
     * That is, the key "resourceactions" has a string value which is a list of
     * semicolon-delimited {@link io.cryosat.net.security.ResourceAction} enum member names.
     * If this file is not present then the defaults from {@link #resourceActions()} will be used
     * instead. If the file is present but unreadable or contains malformed contents, an exception
     * will be thrown, likely resulting in an HTTP API 500 response.
     **/
    default Set<ResourceAction> effectiveResourceActions() {
        Class<? extends RequestHandler> klazz = getClass();
        InputStream resource =
                klazz.getResourceAsStream(String.format("%s.properties", klazz.getSimpleName()));
        if (resource == null) {
            Logger.INSTANCE.warn("Class {} has no {}.properties resource file", klazz.getName(), klazz.getSimpleName());
            return resourceActions();
        }
        Properties properties = new Properties();
        try {
            properties.load(resource);
        } catch (IOException e) {
            Logger.INSTANCE.error(e);
            throw new RuntimeException(e);
        }
        String actionsString = properties.getProperty("resourceactions");
        if (StringUtils.isBlank(actionsString)) {
            return ResourceAction.NONE;
        }
        return Arrays.asList(actionsString.split(";")).stream()
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .map(ResourceAction::valueOf)
                .collect(Collectors.toSet());
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
