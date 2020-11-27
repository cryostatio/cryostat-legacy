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
package com.redhat.rhjmc.containerjfr.net.web.http.api.v1;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.MainModule;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.Clock;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.ConnectionDescriptor;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.platform.PlatformClient;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;

class TargetRecordingPatchSave {

    private final FileSystem fs;
    private final Path recordingsPath;
    private final TargetConnectionManager targetConnectionManager;
    private final Clock clock;
    private final PlatformClient platformClient;

    @Inject
    TargetRecordingPatchSave(
            FileSystem fs,
            @Named(MainModule.RECORDINGS_PATH) Path recordingsPath,
            TargetConnectionManager targetConnectionManager,
            Clock clock,
            PlatformClient platformClient) {
        this.fs = fs;
        this.recordingsPath = recordingsPath;
        this.targetConnectionManager = targetConnectionManager;
        this.clock = clock;
        this.platformClient = platformClient;
    }

    void handle(RoutingContext ctx, ConnectionDescriptor connectionDescriptor) throws Exception {
        String recordingName = ctx.pathParam("recordingName");

        String saveName =
                targetConnectionManager.executeConnectedTask(
                        connectionDescriptor,
                        connection -> {
                            Optional<IRecordingDescriptor> descriptor =
                                    connection.getService().getAvailableRecordings().stream()
                                            .filter(
                                                    recording ->
                                                            recording
                                                                    .getName()
                                                                    .equals(recordingName))
                                            .findFirst();
                            if (descriptor.isPresent()) {
                                return saveRecording(connection, descriptor.get());
                            } else {
                                throw new HttpStatusException(
                                        404,
                                        String.format(
                                                "Recording with name \"%s\" not found",
                                                recordingName));
                            }
                        });
        ctx.response().setStatusCode(200);
        ctx.response().end(saveName);
    }

    private String saveRecording(JFRConnection connection, IRecordingDescriptor descriptor)
            throws Exception {
        String recordingName = descriptor.getName();
        if (recordingName.endsWith(".jfr")) {
            recordingName = recordingName.substring(0, recordingName.length() - 4);
        }

        String targetName =
                platformClient.listDiscoverableServices().stream()
                        .filter(
                                serviceRef -> {
                                    try {
                                        return serviceRef
                                                        .getJMXServiceUrl()
                                                        .equals(connection.getJMXURL())
                                                && serviceRef.getAlias().isPresent();
                                    } catch (IOException ioe) {
                                        return false;
                                    }
                                })
                        .map(s -> s.getAlias().get())
                        .findFirst()
                        .orElse(connection.getHost())
                        .replaceAll("[\\._]+", "-");

        String timestamp =
                clock.now().truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[-:]+", "");
        String destination = String.format("%s_%s_%s", targetName, recordingName, timestamp);
        // TODO byte-sized rename limit is arbitrary. Probably plenty since recordings are also
        // differentiated by second-resolution timestamp
        byte count = 1;
        while (fs.exists(recordingsPath.resolve(destination + ".jfr"))) {
            destination =
                    String.format("%s_%s_%s.%d", targetName, recordingName, timestamp, count++);
            if (count == Byte.MAX_VALUE) {
                throw new IOException(
                        "Recording could not be saved. File already exists and rename attempts were exhausted.");
            }
        }
        destination += ".jfr";
        try (InputStream stream = connection.getService().openStream(descriptor, false)) {
            fs.copy(stream, recordingsPath.resolve(destination));
        }
        return destination;
    }
}
