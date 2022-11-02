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
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.net.Credentials;
import io.cryostat.net.AuthManager;
import io.cryostat.net.AuthorizationErrorException;
import io.cryostat.net.security.PermissionedAction;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.recordings.RecordingMetadataManager.SecurityContext;

import graphql.GraphQLContext;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.graphql.GraphQLHandler;

abstract class AbstractPermissionedDataFetcher<T> implements DataFetcher<T>, PermissionedAction {

    protected final AuthManager auth;
    protected final CredentialsManager credentialsManager;

    AbstractPermissionedDataFetcher(AuthManager auth, CredentialsManager credentialsManager) {
        this.auth = auth;
        this.credentialsManager = credentialsManager;
    }

    abstract Set<String> applicableContexts();

    abstract String name();

    boolean blocking() {
        return true;
    }

    @Override
    public final T get(DataFetchingEnvironment environment) throws Exception {
        GraphQLContext graphCtx = environment.getGraphQlContext();
        RoutingContext ctx = graphCtx.get(RoutingContext.class);
        boolean authenticated =
                auth.validateHttpHeader(
                                () -> ctx.request().getHeader(HttpHeaders.AUTHORIZATION),
                                securityContext(ctx),
                                resourceActions())
                        .get();
        if (!authenticated) {
            throw new AuthorizationErrorException("Unauthorized");
        }
        return getAuthenticated(environment);
    }

    abstract T getAuthenticated(DataFetchingEnvironment environment) throws Exception;

    // FIXME targetId should not be supplied, this method should either figure it out from context,
    // or the X-JMX-Authorization header should actually have a value that encodes a map from
    // targetId to credentials
    protected Optional<Credentials> getSessionCredentials(
            DataFetchingEnvironment environment, String targetId) {
        RoutingContext ctx = GraphQLHandler.getRoutingContext(environment.getGraphQlContext());
        if (!ctx.request()
                .headers()
                .contains(AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER)) {
            return Optional.empty();
        }
        String proxyAuth =
                ctx.request()
                        .getHeader(AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER);
        Matcher m = AbstractAuthenticatedRequestHandler.AUTH_HEADER_PATTERN.matcher(proxyAuth);
        if (!m.find()) {
            ctx.response()
                    .putHeader(
                            AbstractAuthenticatedRequestHandler.JMX_AUTHENTICATE_HEADER, "Basic");
            throw new ApiException(
                    427,
                    "Invalid "
                            + AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER
                            + " format");
        }
        String t = m.group("type");
        if (!"basic".equals(t.toLowerCase())) {
            ctx.response()
                    .putHeader(
                            AbstractAuthenticatedRequestHandler.JMX_AUTHENTICATE_HEADER, "Basic");
            throw new ApiException(
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
                            AbstractAuthenticatedRequestHandler.JMX_AUTHENTICATE_HEADER, "Basic");
            throw new ApiException(
                    427,
                    AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER
                            + " credentials do not appear to be Base64-encoded",
                    iae);
        }
        String[] parts = c.split(":");
        if (parts.length != 2) {
            ctx.response()
                    .putHeader(
                            AbstractAuthenticatedRequestHandler.JMX_AUTHENTICATE_HEADER, "Basic");
            throw new ApiException(
                    427,
                    "Unrecognized "
                            + AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER
                            + " credential format");
        }
        Credentials credentials = new Credentials(parts[0], parts[1]);
        credentialsManager.setSessionCredentials(targetId, credentials);
        ctx.addEndHandler(unused -> credentialsManager.setSessionCredentials(targetId, null));
        return Optional.of(credentials);
    }

    // FIXME this should be abstract and implemented by each concrete subclass
    SecurityContext securityContext(RoutingContext ctx) {
        return SecurityContext.DEFAULT;
    }
}
