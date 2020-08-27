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

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.net.web.WebServer;
import com.redhat.rhjmc.containerjfr.tui.ws.MessagingServer;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Promise;

public class MainVerticle extends AbstractVerticle {

    private final HttpServer http;
    private final WebServer web;
    private final MessagingServer messaging;
    private final Logger logger;

    MainVerticle(HttpServer http, WebServer web, MessagingServer messaging, Logger logger) {
        logger.info("MainVerticle created");
        this.http = http;
        this.web = web;
        this.messaging = messaging;
        this.logger = logger;
    }

    @Override
    public void start(Promise<Void> promise) {
        logger.info("MainVerticle starting");
        Promise<Void> httpPromise = Promise.promise();
        Promise<Void> webPromise = Promise.promise();
        Promise<Void> messagingPromise = Promise.promise();
        try {
            this.http.start(httpPromise);
            this.web.start(webPromise);
            this.messaging.start(messagingPromise);
        } catch (Exception e) {
            logger.error(e);
            promise.fail(e);
            return;
        }
        CompositeFuture.all(httpPromise.future(), webPromise.future(), messagingPromise.future())
            .onSuccess(ar -> {
                promise.complete();
                logger.info("MainVerticle started");
            })
            .onFailure(ar -> {
                promise.fail(ar.getCause());
                logger.info("MainVertice failed");
            });
    }

    @Override
    public void stop() {
        logger.info("MainVerticle stopped");
        this.messaging.stop();
        this.web.stop();
        this.http.stop();
    }

}
