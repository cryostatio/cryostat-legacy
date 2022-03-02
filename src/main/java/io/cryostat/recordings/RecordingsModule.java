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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;

import io.cryostat.MainModule;
import io.cryostat.configuration.ConfigurationModule;
import io.cryostat.core.RecordingOptionsCustomizer;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Clock;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.core.tui.ClientWriter;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.reports.ReportService;
import io.cryostat.net.web.WebModule;
import io.cryostat.net.web.WebServer;
import io.cryostat.platform.PlatformClient;

import com.google.gson.Gson;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import org.apache.commons.codec.binary.Base32;

@Module
public abstract class RecordingsModule {

    static final String NOTIFICATION_SCHEDULER = "NOTIFICATION_SCHEDULER";
    public static final String METADATA_SUBDIRECTORY = "metadata";

    @Provides
    @Singleton
    static RecordingTargetHelper provideRecordingTargetHelper(
            TargetConnectionManager targetConnectionManager,
            Lazy<WebServer> webServer,
            EventOptionsBuilder.Factory eventOptionsBuilderFactory,
            NotificationFactory notificationFactory,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            ReportService reportService,
            @Named(NOTIFICATION_SCHEDULER) ScheduledExecutorService scheduler,
            RecordingMetadataManager recordingMetadataManager,
            Logger logger) {
        return new RecordingTargetHelper(
                targetConnectionManager,
                webServer,
                eventOptionsBuilderFactory,
                notificationFactory,
                recordingOptionsBuilderFactory,
                reportService,
                scheduler,
                recordingMetadataManager,
                logger);
    }

    @Provides
    @Singleton
    static RecordingArchiveHelper provideRecordingArchiveHelper(
            FileSystem fs,
            Provider<WebServer> webServerProvider,
            Logger logger,
            @Named(MainModule.RECORDINGS_PATH) Path archivedRecordingsPath,
            @Named(WebModule.WEBSERVER_TEMP_DIR_PATH) Path archivedRecordingsReportPath,
            TargetConnectionManager targetConnectionManager,
            RecordingMetadataManager recordingMetadataManager,
            Clock clock,
            PlatformClient platformClient,
            NotificationFactory notificationFactory,
            Base32 base32) {
        return new RecordingArchiveHelper(
                fs,
                webServerProvider,
                logger,
                archivedRecordingsPath,
                archivedRecordingsReportPath,
                targetConnectionManager,
                recordingMetadataManager,
                clock,
                platformClient,
                notificationFactory,
                base32);
    }

    @Provides
    static EventOptionsBuilder.Factory provideEventOptionsBuilderFactory(ClientWriter cw) {
        return new EventOptionsBuilder.Factory(cw);
    }

    @Provides
    static RecordingOptionsBuilderFactory provideRecordingOptionsBuilderFactory(
            RecordingOptionsCustomizer customizer) {
        return service -> customizer.apply(new RecordingOptionsBuilder(service));
    }

    @Provides
    @Singleton
    static RecordingOptionsCustomizer provideRecordingOptionsCustomizer(ClientWriter cw) {
        return new RecordingOptionsCustomizer(cw);
    }

    @Provides
    @Named(NOTIFICATION_SCHEDULER)
    static ScheduledExecutorService provideNotificationScheduler() {
        ScheduledExecutorService ses =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = Executors.defaultThreadFactory().newThread(r);
                            t.setDaemon(true);
                            return t;
                        });
        Runtime.getRuntime().addShutdownHook(new Thread(ses::shutdown));
        return ses;
    }

    @Provides
    @Singleton
    static RecordingMetadataManager provideRecordingMetadataManager(
        // FIXME Use a database connection or create a new filesystem path instead of CONFIGURATION_PATH
            @Named(ConfigurationModule.CONFIGURATION_PATH) Path confDir,
            FileSystem fs,
            Gson gson,
            Base32 base32,
            Logger logger) {
        try {
            Path metadataDir = confDir.resolve(METADATA_SUBDIRECTORY);
            if (!fs.isDirectory(metadataDir)) {
                Files.createDirectory(
                        metadataDir,
                        PosixFilePermissions.asFileAttribute(
                                Set.of(
                                        PosixFilePermission.OWNER_READ,
                                        PosixFilePermission.OWNER_WRITE,
                                        PosixFilePermission.OWNER_EXECUTE)));
            }
            return new RecordingMetadataManager(metadataDir, fs, gson, base32, logger);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
