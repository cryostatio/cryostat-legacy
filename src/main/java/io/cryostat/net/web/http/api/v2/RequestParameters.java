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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.cryostat.core.log.Logger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;

public class RequestParameters {

    static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private final String acceptableContentType;
    private final InetAddress addr;
    private final Map<String, String> pathParams;
    private final MultiMap queryParams;
    private final MultiMap headers;
    private final MultiMap formAttributes;
    private final Set<FileUpload> fileUploads;
    private final String body;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "InetAddress is mutable but there is no immutable form or copy constructor")
    public RequestParameters(
            String acceptableContentType,
            InetAddress addr,
            Map<String, String> pathParams,
            MultiMap queryParams,
            MultiMap headers,
            MultiMap formAttributes,
            Set<FileUpload> fileUploads,
            String body) {
        this.acceptableContentType = acceptableContentType;
        this.addr = addr;
        this.pathParams = new HashMap<>(pathParams);
        this.queryParams = MultiMap.caseInsensitiveMultiMap();
        this.queryParams.addAll(queryParams);
        this.headers = MultiMap.caseInsensitiveMultiMap();
        this.headers.addAll(headers);
        this.formAttributes = MultiMap.caseInsensitiveMultiMap();
        this.formAttributes.addAll(formAttributes);
        this.fileUploads = new HashSet<>(fileUploads);
        this.body = body;
    }

    public static RequestParameters from(RoutingContext ctx) {
        Objects.requireNonNull(ctx, "ctx");

        String acceptableContentType = ctx.getAcceptableContentType();

        InetAddress addr = null;
        if (ctx.request() != null && ctx.request().remoteAddress() != null) {
            addr = tryResolveAddress(addr, ctx.request().remoteAddress().host());
        }

        Map<String, String> pathParams = new HashMap<>();
        if (ctx.pathParams() != null) {
            pathParams.putAll(ctx.pathParams());
        }

        MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
        if (ctx.queryParams() != null) {
            queryParams.addAll(ctx.queryParams());
        }

        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        if (ctx.request() != null && ctx.request().headers() != null) {
            MultiMap h = ctx.request().headers();
            headers.addAll(h);
            addr = tryResolveAddress(addr, h.get(X_FORWARDED_FOR));
        }

        MultiMap formAttributes = MultiMap.caseInsensitiveMultiMap();
        if (ctx.request() != null && ctx.request().formAttributes() != null) {
            formAttributes.addAll(ctx.request().formAttributes());
        }

        Set<FileUpload> fileUploads = new HashSet<>();
        if (ctx.fileUploads() != null) {
            fileUploads.addAll(ctx.fileUploads());
        }

        String body = ctx.body().asString();

        return new RequestParameters(
                acceptableContentType,
                addr,
                pathParams,
                queryParams,
                headers,
                formAttributes,
                fileUploads,
                body);
    }

    private static InetAddress tryResolveAddress(InetAddress addr, String host) {
        if (StringUtils.isBlank(host)) {
            return addr;
        }
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            Logger.INSTANCE.error(e);
        }
        return addr;
    }

    public String getAcceptableContentType() {
        return acceptableContentType;
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification =
                    "InetAddress is mutable but there is no immutable form or copy constructor")
    public InetAddress getAddress() {
        return this.addr;
    }

    public Map<String, String> getPathParams() {
        return new HashMap<>(this.pathParams);
    }

    public MultiMap getQueryParams() {
        return this.queryParams;
    }

    public MultiMap getHeaders() {
        return this.headers;
    }

    public MultiMap getFormAttributes() {
        return this.formAttributes;
    }

    public Set<FileUpload> getFileUploads() {
        return new HashSet<>(this.fileUploads);
    }

    public String getBody() {
        return this.body;
    }
}
