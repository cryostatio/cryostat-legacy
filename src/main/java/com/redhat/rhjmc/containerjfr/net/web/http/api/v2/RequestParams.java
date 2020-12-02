/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package com.redhat.rhjmc.containerjfr.net.web.http.api.v2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.vertx.core.MultiMap;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;

class RequestParams {

    private final Map<String, String> pathParams;
    private final MultiMap queryParams;
    private final MultiMap headers;
    private final Set<FileUpload> fileUploads;

    RequestParams(
            Map<String, String> pathParams,
            MultiMap queryParams,
            MultiMap headers,
            Set<FileUpload> fileUploads) {
        this.pathParams = new HashMap<>(pathParams);
        this.queryParams = MultiMap.caseInsensitiveMultiMap();
        this.queryParams.addAll(queryParams);
        this.headers = MultiMap.caseInsensitiveMultiMap();
        this.headers.addAll(headers);
        this.fileUploads = new HashSet<>(fileUploads);
    }

    static RequestParams from(RoutingContext ctx) {
        Map<String, String> pathParams = new HashMap<>();
        if (ctx != null && ctx.pathParams() != null) {
            pathParams.putAll(ctx.pathParams());
        }

        MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
        if (ctx != null && ctx.queryParams() != null) {
            queryParams.addAll(ctx.queryParams());
        }

        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        if (ctx != null && ctx.request() != null && ctx.request().headers() != null) {
            headers.addAll(ctx.request().headers());
        }

        Set<FileUpload> fileUploads = new HashSet<>();
        if (ctx != null && ctx.fileUploads() != null) {
            fileUploads.addAll(ctx.fileUploads());
        }

        return new RequestParams(pathParams, queryParams, headers, fileUploads);
    }

    Map<String, String> getPathParams() {
        return this.pathParams;
    }

    MultiMap getQueryParams() {
        return this.queryParams;
    }

    MultiMap getHeaders() {
        return this.headers;
    }

    Set<FileUpload> getFileUploads() {
        return this.fileUploads;
    }
}
