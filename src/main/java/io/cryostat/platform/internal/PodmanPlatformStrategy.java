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
package io.cryostat.platform.internal;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;

import com.google.gson.Gson;
import com.sun.security.auth.module.UnixSystem;
import dagger.Lazy;
import io.vertx.core.Vertx;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.client.WebClient;

class PodmanPlatformStrategy implements PlatformDetectionStrategy<PodmanPlatformClient> {

    private final Logger logger;
    private final Lazy<? extends AuthManager> authMgr;
    private final Lazy<WebClient> webClient;
    private final Lazy<Vertx> vertx;
    private final Gson gson;
    private final FileSystem fs;

    PodmanPlatformStrategy(
            Logger logger,
            Lazy<? extends AuthManager> authMgr,
            Lazy<WebClient> webClient,
            Lazy<Vertx> vertx,
            Gson gson,
            FileSystem fs) {
        this.logger = logger;
        this.authMgr = authMgr;
        this.webClient = webClient;
        this.vertx = vertx;
        this.gson = gson;
        this.fs = fs;
    }

    @Override
    public boolean isAvailable() {
        String socketPath = getSocketPath();
        logger.info("Testing {} Availability via {}", getClass().getSimpleName(), socketPath);
        // TODO check that the service is actually available on the socket using an HTTP request
        boolean available = fs.isReadable(fs.pathOf(socketPath));
        logger.info("{} available? {}", getClass().getSimpleName(), available);
        return available;
    }

    @Override
    public PodmanPlatformClient getPlatformClient() {
        logger.info("Selected {} Strategy", getClass().getSimpleName());
        return new PodmanPlatformClient(webClient, vertx, getSocket(), gson, logger);
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
