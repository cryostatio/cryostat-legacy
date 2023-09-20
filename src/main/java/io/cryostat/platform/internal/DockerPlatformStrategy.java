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
package io.cryostat.platform.internal;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnectionToolkit;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;

import com.google.gson.Gson;
import dagger.Lazy;
import io.netty.channel.epoll.Epoll;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;

class DockerPlatformStrategy implements PlatformDetectionStrategy<DockerPlatformClient> {

    private static final String DOCKER_SOCKET_PATH = "/var/run/docker.sock";
    private final Logger logger;
    private final Lazy<? extends AuthManager> authMgr;
    private final Lazy<WebClient> webClient;
    private final Lazy<Vertx> vertx;
    private final Lazy<JFRConnectionToolkit> connectionToolkit;
    private final Gson gson;
    private final FileSystem fs;

    DockerPlatformStrategy(
            Logger logger,
            Lazy<? extends AuthManager> authMgr,
            Lazy<WebClient> webClient,
            Lazy<Vertx> vertx,
            Lazy<JFRConnectionToolkit> connectionToolkit,
            Gson gson,
            FileSystem fs) {
        this.logger = logger;
        this.authMgr = authMgr;
        this.webClient = webClient;
        this.vertx = vertx;
        this.connectionToolkit = connectionToolkit;
        this.gson = gson;
        this.fs = fs;
    }

    @Override
    public boolean isAvailable() {
        logger.info(
                "Testing {} Availability via {}", getClass().getSimpleName(), DOCKER_SOCKET_PATH);

        boolean socketExists = fs.isReadable(fs.pathOf(DOCKER_SOCKET_PATH));
        boolean nativeEnabled = vertx.get().isNativeTransportEnabled();

        try {
            if (!nativeEnabled && !Epoll.isAvailable()) {
                Epoll.unavailabilityCause().printStackTrace();
            }
        } catch (NoClassDefFoundError noClassDefFoundError) {
            logger.warn(new UnsupportedOperationException(noClassDefFoundError));
            return false;
        }

        boolean serviceReachable = false;
        if (socketExists && nativeEnabled) {
            serviceReachable = testDockerApi();
        }

        boolean available = socketExists && nativeEnabled && serviceReachable;
        logger.info("{} available? {}", getClass().getSimpleName(), available);
        return available;
    }

    private boolean testDockerApi() {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        URI requestPath = URI.create("http://d/v1.41/info");
        new Thread(
                        () -> {
                            webClient
                                    .get()
                                    .request(
                                            HttpMethod.GET,
                                            getSocket(),
                                            80,
                                            "localhost",
                                            requestPath.toString())
                                    .timeout(2_000L)
                                    .as(BodyCodec.none())
                                    .send(
                                            ar -> {
                                                if (ar.failed()) {
                                                    Throwable t = ar.cause();
                                                    logger.info("Docker API request failed", t);
                                                    result.complete(false);
                                                    return;
                                                }
                                                result.complete(true);
                                            });
                        })
                .start();
        try {
            return result.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.error(e);
            return false;
        }
    }

    @Override
    public DockerPlatformClient getPlatformClient() {
        logger.info("Selected {} Strategy", getClass().getSimpleName());
        return new DockerPlatformClient(
                webClient, vertx, getSocket(), connectionToolkit, gson, logger);
    }

    @Override
    public AuthManager getAuthManager() {
        return authMgr.get();
    }

    private static SocketAddress getSocket() {
        return SocketAddress.domainSocketAddress(DOCKER_SOCKET_PATH);
    }
}
