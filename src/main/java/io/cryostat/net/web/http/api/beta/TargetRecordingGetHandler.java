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

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.JwtFactory;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.ApiException;

import com.nimbusds.jwt.JWT;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

class TargetRecordingGetHandler extends AbstractJwtConsumingHandler {
    protected static final int WRITE_BUFFER_SIZE = 64 * 1024; // 64 KB

    private final TargetConnectionManager targetConnectionManager;

    @Inject
    TargetRecordingGetHandler(
            AuthManager auth,
            JwtFactory jwtFactory,
            TargetConnectionManager targetConnectionManager,
            Logger logger) {
        super(auth, jwtFactory, logger);
        this.targetConnectionManager = targetConnectionManager;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.BETA;
    }

    @Override
    public String path() {
        return basePath() + "targets/:targetId/recordings/:recordingName";
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_TARGET, ResourceAction.READ_RECORDING);
    }

    @Override
    public void handleWithValidJwt(RoutingContext ctx, JWT jwt) throws Exception {
        String recordingName = ctx.pathParam("recordingName");
        if (recordingName != null && recordingName.endsWith(".jfr")) {
            recordingName = recordingName.substring(0, recordingName.length() - 4);
        }
        handleRecordingDownloadRequest(ctx, jwt, recordingName);
    }

    void handleRecordingDownloadRequest(RoutingContext ctx, JWT jwt, String recordingName)
            throws Exception {
        ConnectionDescriptor connectionDescriptor = getConnectionDescriptorFromJwt(ctx, jwt);
        Optional<InputStream> stream =
                targetConnectionManager.executeConnectedTask(
                        connectionDescriptor,
                        conn ->
                                conn.getService().getAvailableRecordings().stream()
                                        .filter(r -> Objects.equals(recordingName, r.getName()))
                                        .map(
                                                desc -> {
                                                    try {
                                                        return conn.getService()
                                                                .openStream(desc, false);
                                                    } catch (Exception e) {
                                                        logger.error(e);
                                                        return null;
                                                    }
                                                })
                                        .filter(Objects::nonNull)
                                        .findFirst());
        if (stream.isEmpty()) {
            throw new ApiException(404, String.format("%s not found", recordingName));
        }

        ctx.response().setChunked(true);
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.OCTET_STREAM.mime());
        try (InputStream s = stream.get()) {
            byte[] buff = new byte[WRITE_BUFFER_SIZE];
            int n;
            while ((n = s.read(buff)) != -1) {
                // FIXME replace this with Vertx async IO, ie. ReadStream/WriteStream/Pump
                ctx.response().write(Buffer.buffer(n).appendBytes(buff, 0, n));
                if (!targetConnectionManager.markConnectionInUse(connectionDescriptor)) {
                    throw new IOException(
                            "Target connection unexpectedly closed while streaming recording");
                }
            }

            ctx.response().end();
        }
    }
}
