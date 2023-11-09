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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnectionToolkit;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;

import com.google.gson.Gson;
import com.sun.security.auth.module.UnixSystem;
import dagger.Lazy;
import io.netty.channel.epoll.Epoll;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;

class PodmanPlatformStrategy implements PlatformDetectionStrategy<PodmanPlatformClient> {

    private final Logger logger;
    private final Lazy<? extends AuthManager> authMgr;
    private final Lazy<WebClient> webClient;
    private final Lazy<Vertx> vertx;
    private final Lazy<JFRConnectionToolkit> connectionToolkit;
    private final Gson gson;
    private final FileSystem fs;

    PodmanPlatformStrategy(
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
        String socketPath = getSocketPath();
        logger.info("Testing {} Availability via {}", getClass().getSimpleName(), socketPath);

        boolean socketExists = fs.isReadable(fs.pathOf(socketPath));
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
            serviceReachable = testPodmanApi();
        }

        boolean available = socketExists && nativeEnabled && serviceReachable;
        logger.info("{} available? {}", getClass().getSimpleName(), available);
        return available;
    }

    private boolean testPodmanApi() {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        URI requestPath = URI.create("http://d/info");
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
                                                    logger.info("Podman API request failed", t);
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
    public PodmanPlatformClient getPlatformClient() {
        logger.info("Selected {} Strategy", getClass().getSimpleName());
        return new PodmanPlatformClient(
                Executors.newSingleThreadExecutor(),
                webClient,
                vertx,
                getSocket(),
                connectionToolkit,
                gson,
                logger);
    }

    @Override
    public AuthManager getAuthManager() {
        return authMgr.get();
    }

    private static String getSocketPath() {
        long uid = new UnixSystem().getUid();
        String socketPath = String.format("/run/user/%d/podman/podman.sock", uid);
        return socketPath;
    }

    private static SocketAddress getSocket() {
        return SocketAddress.domainSocketAddress(getSocketPath());
    }
}
