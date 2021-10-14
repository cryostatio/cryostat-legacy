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
package io.cryostat.net.web.http.api.beta;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.api.v2.AbstractV2RequestHandler;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;

import com.google.gson.Gson;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;

abstract class AbstractJwtConsumingHandler<T> extends AbstractV2RequestHandler<T> {

    protected final JWTAuth jwtAuth;

    protected AbstractJwtConsumingHandler(JWTAuth jwtAuth, AuthManager auth, Gson gson) {
        super(auth, gson);
        this.jwtAuth = jwtAuth;
    }

    @Override
    public final Set<ResourceAction> resourceActions() {
        return ResourceAction.NONE;
    }

    @Override
    public final boolean requiresAuthentication() {
        return false;
    }

    @Override
    public final IntermediateResponse<T> handle(RequestParameters requestParams) throws Exception {
        String token = requestParams.getQueryParams().get("token");
        if (token == null) {
            throw new ApiException(401);
        }
        JsonObject authInfo = new JsonObject();
        authInfo.put("jwt", token);
        CompletableFuture<User> result = new CompletableFuture<>();
        jwtAuth.authenticate(
                authInfo,
                ar -> {
                    if (ar.succeeded()) {
                        result.complete(ar.result());
                    } else {
                        result.completeExceptionally(new ApiException(401));
                    }
                });

        User user;
        try {
            user = result.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ApiException) {
                throw (ApiException) cause;
            }
            throw new ApiException(500, cause);
        }

        String rawRequestUri = requestParams.getAbsoluteUri();
        // We know there is a '?' (query param separator) because we checked for the 'token' query
        // param earlier
        String requestUri = rawRequestUri.substring(0, rawRequestUri.indexOf('?'));
        if (!requestUri.equals(user.principal().getString("resource"))) {
            throw new ApiException(401);
        }

        return handleWithValidJwt(requestParams);
    }

    abstract IntermediateResponse<T> handleWithValidJwt(RequestParameters requestParams)
            throws Exception;
}
