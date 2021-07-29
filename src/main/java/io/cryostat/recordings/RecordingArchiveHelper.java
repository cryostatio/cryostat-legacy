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
package io.cryostat.recordings;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Provider;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.sys.Clock;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.reports.ReportService;
import io.cryostat.net.web.WebServer;
import io.cryostat.platform.PlatformClient;
import io.cryostat.rules.ArchivePathException;
import io.cryostat.rules.ArchivedRecordingInfo;
import io.cryostat.util.URIUtil;

import org.apache.commons.codec.binary.Base32;

public class RecordingArchiveHelper {

    private final TargetConnectionManager targetConnectionManager;
    private final FileSystem fs;
    private final Provider<WebServer> webServerProvider;
    private final Logger logger;
    private final Path recordingsPath;
    private final Clock clock;
    private final PlatformClient platformClient;
    private final ReportService reportService;

    RecordingArchiveHelper(
            FileSystem fs,
            Provider<WebServer> webServerProvider,
            Logger logger,
            @Named(MainModule.RECORDINGS_PATH) Path recordingsPath,
            TargetConnectionManager targetConnectionManager,
            Clock clock,
            PlatformClient platformClient,
            ReportService reportService) {
        this.fs = fs;
        this.webServerProvider = webServerProvider;
        this.logger = logger;
        this.recordingsPath = recordingsPath;
        this.targetConnectionManager = targetConnectionManager;
        this.clock = clock;
        this.platformClient = platformClient;
        this.reportService = reportService;
    }

    public Future<String> saveRecording(
            ConnectionDescriptor connectionDescriptor, String recordingName) throws Exception {

        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            String saveName =
                    targetConnectionManager.executeConnectedTask(
                            connectionDescriptor,
                            connection -> {
                                Optional<IRecordingDescriptor> descriptor =
                                        this.getDescriptorByName(connection, recordingName);

                                if (descriptor.isPresent()) {
                                    return writeRecordingToDestination(
                                            connection, descriptor.get());
                                } else {
                                    throw new RecordingNotFoundException(recordingName);
                                }
                            });
            future.complete(saveName);
        } catch (RecordingNotFoundException e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    public Future<Void> deleteRecording(String recordingName) throws Exception {

        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            Path archivedRecording = getRecording(recordingName);
            fs.deleteIfExists(archivedRecording);
            future.complete(null);
        } catch (RecordingNotFoundException e) {
            future.completeExceptionally(e);
        } catch (IOException e) {
            future.completeExceptionally(e);
        } finally {
            reportService.delete(recordingName);
        }

        return future;
    }

    public Future<List<ArchivedRecordingInfo>> getRecordings() throws Exception {

        CompletableFuture<List<ArchivedRecordingInfo>> future = new CompletableFuture<>();

        try {
            if (!fs.exists(recordingsPath)) {
                throw new ArchivePathException(recordingsPath.toString(), "does not exist");
            }
            if (!fs.isReadable(recordingsPath)) {
                throw new ArchivePathException(recordingsPath.toString(), "is not readable");
            }
            if (!fs.isDirectory(recordingsPath)) {
                throw new ArchivePathException(recordingsPath.toString(), "is not a directory");
            }
            WebServer webServer = webServerProvider.get();
            List<String> subdirectories = this.fs.listDirectoryChildren(recordingsPath);
            List<ArchivedRecordingInfo> archivedRecordings = new ArrayList<>();
            for (String subdirectory : subdirectories) {
                List<String> files =
                        this.fs.listDirectoryChildren(recordingsPath.resolve(subdirectory));
                List<ArchivedRecordingInfo> temp =
                        files.stream()
                                .map(
                                        file -> {
                                            try {
                                                return new ArchivedRecordingInfo(
                                                        subdirectory,
                                                        file,
                                                        webServer.getArchivedReportURL(file),
                                                        webServer.getArchivedDownloadURL(file));
                                            } catch (SocketException
                                                    | UnknownHostException
                                                    | URISyntaxException e) {
                                                logger.warn(e);
                                                return null;
                                            }
                                        })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());
                archivedRecordings.addAll(temp);
            }
            future.complete(archivedRecordings);
        } catch (ArchivePathException e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    public Path getRecording(String recordingName) throws Exception {
        Path archivedRecording = null;
        Boolean recordingFound = false;
        List<String> subdirectories = this.fs.listDirectoryChildren(recordingsPath);
        for (String subdirectory : subdirectories) {
            List<String> files =
                    this.fs.listDirectoryChildren(recordingsPath.resolve(subdirectory));

            for (String file : files) {
                if (recordingName.equals(file)) {
                    archivedRecording = recordingsPath.resolve(subdirectory + "/" + file);
                    recordingFound = true;
                    break;
                }
            }

            if (recordingFound) {
                break;
            }
        }
        if (!fs.exists(archivedRecording)) {
            throw new RecordingNotFoundException(recordingName);
        }
        return archivedRecording;
    }

    private String writeRecordingToDestination(
            JFRConnection connection, IRecordingDescriptor descriptor) throws Exception {
        Base32 base32 = new Base32();

        URI serviceUri = URIUtil.convert(connection.getJMXURL());
        String encodedServiceUri = base32.encodeAsString(serviceUri.toString().getBytes());
        Path specificRecordingsPath = recordingsPath.resolve(encodedServiceUri);

        if (!fs.exists(specificRecordingsPath)) {
            Files.createDirectory(specificRecordingsPath);
        }

        String recordingName = descriptor.getName();
        if (recordingName.endsWith(".jfr")) {
            recordingName = recordingName.substring(0, recordingName.length() - 4);
        }

        // TODO: To avoid having to perform this lookup each time, we should implement
        // something like a map from targetIds to corresponding ServiceRefs
        String targetName =
                platformClient.listDiscoverableServices().stream()
                        .filter(
                                serviceRef -> {
                                    return serviceRef.getServiceUri().equals(serviceUri)
                                            && serviceRef.getAlias().isPresent();
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
        while (fs.exists(specificRecordingsPath.resolve(destination + ".jfr"))) {
            destination =
                    String.format("%s_%s_%s.%d", targetName, recordingName, timestamp, count++);
            if (count == Byte.MAX_VALUE) {
                throw new IOException(
                        "Recording could not be saved; file already exists and rename attempts were exhausted.");
            }
        }
        destination += ".jfr";
        try (InputStream stream = connection.getService().openStream(descriptor, false)) {
            fs.copy(stream, specificRecordingsPath.resolve(destination));
        }
        return destination;
    }

    private Optional<IRecordingDescriptor> getDescriptorByName(
            JFRConnection connection, String recordingName) throws Exception {
        return connection.getService().getAvailableRecordings().stream()
                .filter(recording -> recording.getName().equals(recordingName))
                .findFirst();
    }
}
