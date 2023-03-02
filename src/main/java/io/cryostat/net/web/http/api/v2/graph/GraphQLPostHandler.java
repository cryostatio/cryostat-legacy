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
package io.cryostat.net.web.http.api.v2.graph;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.ApiException;

import graphql.GraphQL;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.ext.web.handler.graphql.GraphQLHandler;

class GraphQLPostHandler implements RequestHandler {

    static final String PATH = "graphql";

    private final GraphQLHandler handler;
    private final AuthManager auth;
    private final Logger logger;

    @Inject
    GraphQLPostHandler(GraphQL graph, AuthManager auth, Logger logger) {
        this.handler = GraphQLHandler.create(graph);
        this.auth = auth;
        this.logger = logger;
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
    public Set<ResourceAction> resourceActions() {
        // no permissions directly required here. Specific permissions may be required by fetchers
        // and mutators that we invoke - see AbstractPermissionedDataFetcher
        return ResourceAction.NONE;
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
    public void handle(RoutingContext ctx) {
        try {
            if (!auth.validateHttpHeader(
                            () -> ctx.request().getHeader(HttpHeaders.AUTHORIZATION),
                            resourceActions())
                    .get()) {
                throw new ApiException(401);
            }

            String targetId = ctx.pathParam("targetId");
            Credentials credentials;
            if (ctx.request()
                    .headers()
                    .contains(AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER)) {
                String proxyAuth =
                        ctx.request()
                                .getHeader(
                                        AbstractAuthenticatedRequestHandler
                                                .JMX_AUTHORIZATION_HEADER);
                Matcher m =
                        AbstractAuthenticatedRequestHandler.AUTH_HEADER_PATTERN.matcher(proxyAuth);
                if (!m.find()) {
                    ctx.response()
                            .putHeader(
                                    AbstractAuthenticatedRequestHandler.JMX_AUTHENTICATE_HEADER,
                                    "Basic");
                    throw new HttpException(
                            427,
                            "Invalid "
                                    + AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER
                                    + " format");
                }
                String t = m.group("type");
                if (!"basic".equals(t.toLowerCase())) {
                    ctx.response()
                            .putHeader(
                                    AbstractAuthenticatedRequestHandler.JMX_AUTHENTICATE_HEADER,
                                    "Basic");
                    throw new HttpException(
                            427,
                            "Unacceptable "
                                    + AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER
                                    + " type");
                }
                String c;
                try {
                    c =
                            new String(
                                    Base64.getUrlDecoder().decode(m.group("credentials")),
                                    StandardCharsets.UTF_8);
                } catch (IllegalArgumentException iae) {
                    ctx.response()
                            .putHeader(
                                    AbstractAuthenticatedRequestHandler.JMX_AUTHENTICATE_HEADER,
                                    "Basic");
                    throw new HttpException(
                            427,
                            AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER
                                    + " credentials do not appear to be Base64-encoded",
                            iae);
                }
                String[] parts = c.split(":");
                if (parts.length != 2) {
                    ctx.response()
                            .putHeader(
                                    AbstractAuthenticatedRequestHandler.JMX_AUTHENTICATE_HEADER,
                                    "Basic");
                    throw new HttpException(
                            427,
                            "Unrecognized "
                                    + AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER
                                    + " credential format");
                }
                credentials = new Credentials(parts[0], parts[1]);
                CredentialsManager.SESSION_JMX_CREDENTIALS.get().put(targetId, credentials);
                ctx.addEndHandler(
                        unused ->
                                CredentialsManager.SESSION_JMX_CREDENTIALS.get().remove(targetId));
            }

        } catch (InterruptedException | ExecutionException e) {
            throw new ApiException(500, e);
        }
        JsonObject body = ctx.getBodyAsJson();
        logger.info("GraphQL query: {}", body.getString("query"));
        this.handler.handle(ctx);
    }
}
