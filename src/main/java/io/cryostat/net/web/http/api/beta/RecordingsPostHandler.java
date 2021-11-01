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

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.gson.Gson;

import org.apache.commons.lang3.StringUtils;
import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException;
import org.openjdk.jmc.flightrecorder.internal.FlightRecordingLoader;
import org.openjdk.jmc.flightrecorder.internal.InvalidJfrFileException;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Clock;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.spi.FutureFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;

class RecordingsPostHandler implements RequestHandler {

    private static final Pattern RECORDING_FILENAME_PATTERN =
            Pattern.compile("([A-Za-z\\d-]*)_([A-Za-z\\d-_]*)_([\\d]*T[\\d]*Z)(\\.[\\d]+)?");
    private static final String NOTIFICATION_CATEGORY = "RecordingSaved";
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final AuthManager auth;
    private final Gson gson;
    private final Path savedRecordingsPath;
    private final FileSystem fs;
    private final Clock clock;
    private final FutureFactory futureFactory;
    private final NotificationFactory notificationFactory;
    private final Logger logger;
    private final AtomicLong lastReadTimestamp = new AtomicLong(0);

    @Inject
    RecordingsPostHandler(
            AuthManager auth,
            Gson gson,
            @Named(MainModule.RECORDINGS_PATH) Path savedRecordingsPath,
            FileSystem fs,
            Clock clock,
            FutureFactory futureFactory,
            NotificationFactory notificationFactory,
            Logger logger) {
        this.auth = auth;
        this.gson = gson;
        this.savedRecordingsPath = savedRecordingsPath;
        this.fs = fs;
        this.clock = clock;
        this.futureFactory = futureFactory;
        this.notificationFactory = notificationFactory;
        this.logger = logger;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.BETA;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.CREATE_RECORDING);
    }

    @Override
    public String path() {
        return basePath() + "recordings/:recordingName";
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public boolean isOrdered() {
        return false;
    }

    private Future<Boolean> validateRequestAuthorization(HttpServerRequest req) throws Exception {
        return auth.validateHttpHeader(
                () -> req.getHeader(HttpHeaders.AUTHORIZATION), resourceActions());
    }

    @Override
    public void handle(RoutingContext ctx) {
        ctx.request().pause();

        try {
            boolean permissionGranted = validateRequestAuthorization(ctx.request()).get();
            if (!permissionGranted) {
                ctx.fail(401, new HttpStatusException(401, "HTTP Authorization Failure"));
                return;
            }
        } catch (Exception e) {
            ctx.fail(e);
            return;
        }

        if (!fs.isDirectory(savedRecordingsPath)) {
            ctx.fail(503, new HttpStatusException(503, "Recording saving not available"));
            return;
        }

        if (!"100-continue".equals(ctx.request().getHeader(HttpHeaders.EXPECT))) {
            ctx.fail(400, new HttpStatusException(400, "Expect:100-continue header is required"));
            return;
        }

        if (!HttpMimeType.OCTET_STREAM.mime().equals(
                ctx.request().getHeader(HttpHeaders.CONTENT_TYPE))) {
            ctx.fail(400);
            return;
        }

        if (ctx.request().getHeader(HttpHeaders.CONTENT_LENGTH) == null
                || Long.parseLong(ctx.request().getHeader(HttpHeaders.CONTENT_LENGTH)) < 1) {
            ctx.fail(
                    400,
                    new HttpStatusException(400, "Content-Length header must be a positive value"));
            return;
        }

        String desiredSaveName = ctx.pathParam("recordingName");
        if (StringUtils.isBlank(desiredSaveName)) {
            ctx.fail(400, new HttpStatusException(400, "Recording name must not be empty"));
            return;
        }

        if (desiredSaveName.endsWith(".jfr")) {
            desiredSaveName = desiredSaveName.substring(0, desiredSaveName.length() - 4);
        }
        Matcher m = RECORDING_FILENAME_PATTERN.matcher(desiredSaveName);
        if (!m.matches()) {
            ctx.fail(400, new HttpStatusException(400, "Incorrect recording file name pattern"));
            return;
        }

        String destinationFile =
                savedRecordingsPath
                        .toAbsolutePath()
                        .resolve("file-uploads")
                        .resolve(UUID.randomUUID().toString())
                        .toString();
        CompletableFuture<String> fileUploadPath = new CompletableFuture<>();
        long timerId =
                ctx.vertx()
                        .setPeriodic(
                                READ_TIMEOUT.toMillis(),
                                id -> {
                                    if (clock.getMonotonicTime() - lastReadTimestamp.get()
                                            > READ_TIMEOUT.toNanos()) {
                                        fileUploadPath.completeExceptionally(
                                                new TimeoutException());
                                    }
                                });
        ctx.vertx()
                .fileSystem()
                .open(
                        destinationFile,
                        new OpenOptions().setAppend(true).setCreateNew(true),
                        openFile -> {
                            if (openFile.failed()) {
                                ctx.fail(new HttpStatusException(500, openFile.cause()));
                                return;
                            }
                            ctx.request()
                                    .handler(
                                            buffer -> {
                                                openFile.result().write(buffer);
                                                lastReadTimestamp.set(clock.getMonotonicTime());
                                            })
                                    .exceptionHandler(fileUploadPath::completeExceptionally)
                                    .endHandler(
                                            v -> {
                                                ctx.vertx().cancelTimer(timerId);
                                                openFile.result().close();
                                                fileUploadPath.complete(destinationFile);
                                            });
                            ctx.addEndHandler(
                                    ar -> {
                                        ctx.vertx().cancelTimer(timerId);
                                    });
                            ctx.request().resume();
                        });

        ctx.vertx()
                .executeBlocking(
                        event -> {
                            try {
                                event.complete(fileUploadPath.get());
                            } catch (ExecutionException | InterruptedException e) {
                                event.fail(e);
                            }
                        },
                        ar -> {
                            if (ar.failed()) {
                                ctx.vertx().fileSystem().deleteBlocking(destinationFile);
                                ctx.fail(ar.cause());
                                return;
                            }
                            String upload = (String) ar.result();

                            String targetName = m.group(1);
                            String recordingName = m.group(2);
                            String timestamp = m.group(3);
                            int count =
                                    m.group(4) == null || m.group(4).isEmpty()
                                            ? 0
                                            : Integer.parseInt(m.group(4).substring(1));

                            final String subdirectoryName = "unlabelled";
                            final String basename =
                                    String.format("%s_%s_%s", targetName, recordingName, timestamp);
                            validateRecording(
                                    ctx.vertx(),
                                    upload,
                                    res -> {
                                        if (res.failed()) {
                                            ctx.fail(400, res.cause());
                                            return;
                                        }
                                        saveRecording(
                                                ctx.vertx(),
                                                subdirectoryName,
                                                basename,
                                                upload,
                                                count,
                                                res2 -> {
                                                    if (res2.failed()) {
                                                        ctx.fail(res2.cause());
                                                        return;
                                                    }

                                                    ctx.response()
                                                            .putHeader(
                                                                    HttpHeaders.CONTENT_TYPE,
                                                                    HttpMimeType.JSON.mime())
                                                            .end(
                                                                    gson.toJson(
                                                                            Map.of(
                                                                                    "name",
                                                                                    res2
                                                                                            .result())));

                                                    notificationFactory
                                                            .createBuilder()
                                                            .metaCategory(NOTIFICATION_CATEGORY)
                                                            .metaType(HttpMimeType.JSON)
                                                            .message(
                                                                    Map.of(
                                                                            "recording",
                                                                            res2.result()))
                                                            .build()
                                                            .send();
                                                });
                                    });
                        });
    }

    private void validateRecording(
            Vertx vertx, String recordingFile, Handler<AsyncResult<Void>> handler) {
        vertx.executeBlocking(
                event -> {
                    try {
                        // try loading chunk info to see if it's a valid file
                        try (var is = new BufferedInputStream(new FileInputStream(recordingFile))) {
                            var supplier = FlightRecordingLoader.createChunkSupplier(is);
                            var chunks = FlightRecordingLoader.readChunkInfo(supplier);
                            if (chunks.size() < 1) {
                                event.fail(new InvalidJfrFileException());
                            }
                        }
                        event.complete();
                    } catch (CouldNotLoadRecordingException | IOException e) {
                        // FIXME need to reject the request and clean up the file here
                        event.fail(e);
                    }
                },
                res -> {
                    if (res.failed()) {
                        Throwable t;
                        if (res.cause() instanceof CouldNotLoadRecordingException) {
                            t =
                                    new HttpStatusException(
                                            400, "Not a valid JFR recording file", res.cause());
                        } else {
                            t = res.cause();
                        }
                        vertx.fileSystem().deleteBlocking(recordingFile);

                        handler.handle(futureFactory.failedFuture(t));
                        return;
                    }

                    handler.handle(futureFactory.succeededFuture());
                });
    }

    private void saveRecording(
            Vertx vertx,
            String subdirectoryName,
            String basename,
            String tmpFile,
            int counter,
            Handler<AsyncResult<String>> handler) {
        // TODO byte-sized rename limit is arbitrary. Probably plenty since recordings
        // are also differentiated by second-resolution timestamp
        if (counter >= Byte.MAX_VALUE) {
            handler.handle(
                    futureFactory.failedFuture(
                            new IOException(
                                    "Recording could not be saved. File already exists and rename attempts were exhausted.")));
            return;
        }

        String filename = counter > 1 ? basename + "." + counter + ".jfr" : basename + ".jfr";
        Path specificRecordingsPath = savedRecordingsPath.resolve(subdirectoryName);

        if (!fs.exists(specificRecordingsPath)) {
            try {
                Files.createDirectory(specificRecordingsPath);
            } catch (IOException e) {
                handler.handle(futureFactory.failedFuture(e));
                return;
            }
        }

        vertx.fileSystem()
                .exists(
                        specificRecordingsPath.resolve(filename).toString(),
                        (res) -> {
                            if (res.failed()) {
                                handler.handle(futureFactory.failedFuture(res.cause()));
                                return;
                            }

                            if (res.result()) {
                                saveRecording(
                                        vertx,
                                        subdirectoryName,
                                        basename,
                                        tmpFile,
                                        counter + 1,
                                        handler);
                                return;
                            }

                            // verified no name clash at this time
                            vertx.fileSystem()
                                    .move(
                                            tmpFile,
                                            specificRecordingsPath.resolve(filename).toString(),
                                            (res2) -> {
                                                if (res2.failed()) {
                                                    handler.handle(
                                                            futureFactory.failedFuture(
                                                                    res2.cause()));
                                                    return;
                                                }

                                                handler.handle(
                                                        futureFactory.succeededFuture(filename));
                                            });
                        });
    }
}
