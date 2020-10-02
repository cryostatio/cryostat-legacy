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
package com.redhat.rhjmc.containerjfr.net;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.client.utils.URIBuilder;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.net.web.http.generic.HealthGetHandler;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;

public class RedirectorVerticle extends AbstractVerticle {

    private final Vertx vertx;
    private io.vertx.core.http.HttpServer server;
    private final NetworkConfiguration netConf;
    // FIXME refactor this and the RequestHandler interface to designate certain handlers to be used
    // by either the HTTP and/or HTTPS verticles without redirecting the client
    private final HealthGetHandler healthHandler;
    private final Logger logger;
    private final boolean enabled;

    RedirectorVerticle(
            Vertx vertx,
            SslConfiguration sslConf,
            NetworkConfiguration netConf,
            HealthGetHandler healthHandler,
            Logger logger) {
        this.vertx = vertx;
        this.netConf = netConf;
        this.healthHandler = healthHandler;
        this.logger = logger;
        this.enabled = sslConf.enabled();
    }

    @Override
    public void start(Promise<Void> promise) {
        if (!enabled) {
            logger.trace("RedirectorVerticle skipped");
            promise.complete();
            return;
        }
        Router router = Router.router(vertx);
        router.route(healthHandler.httpMethod(), healthHandler.path())
                .blockingHandler(healthHandler);

        router.route()
                .handler(
                        ctx -> {
                            logger.info(
                                    String.format(
                                            "(%s) HTTPS upgrade %s %s",
                                            ctx.request().remoteAddress().toString(),
                                            ctx.request().method(),
                                            ctx.request().path()));
                            try {
                                URI mapped =
                                        new URIBuilder(ctx.request().absoluteURI())
                                                .setScheme("https")
                                                .setPort(netConf.getExternalWebServerPrimaryPort())
                                                .build();
                                ctx.response()
                                        .setStatusCode(HttpStatus.SC_MOVED_PERMANENTLY)
                                        .putHeader(HttpHeaders.LOCATION, mapped.toString())
                                        .end();
                            } catch (URISyntaxException e) {
                                logger.error(e);
                                ctx.response().setStatusCode(HttpStatus.SC_BAD_REQUEST).end();
                            }
                        });

        this.server =
                getVertx()
                        .createHttpServer(
                                new HttpServerOptions()
                                        .setPort(netConf.getInternalWebServerSecondaryPort())
                                        .setLogActivity(true))
                        .requestHandler(router)
                        .listen(
                                ar -> {
                                    if (ar.succeeded()) {
                                        logger.trace("RedirectorVerticle started");
                                        promise.complete();
                                    } else {
                                        logger.trace("RedirectorVerticle failed");
                                        promise.fail(ar.cause());
                                    }
                                });
    }

    @Override
    public void stop(Promise<Void> promise) {
        logger.trace("RedirectorVerticle stopped");
        if (server != null) {
            server.close(
                    ar -> {
                        if (ar.succeeded()) {
                            promise.complete();
                        } else {
                            promise.fail(ar.cause());
                        }
                    });
        } else {
            promise.complete();
        }
    }
}
