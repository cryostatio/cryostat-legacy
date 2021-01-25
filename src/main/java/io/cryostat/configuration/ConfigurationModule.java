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
package io.cryostat.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;

import com.google.gson.Gson;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Module
public abstract class ConfigurationModule {
    public static final String CONFIGURATION_PATH = "CONFIGURATION_PATH";
    public static final String CREDENTIALS_SUBDIRECTORY = "credentials";

    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    @Provides
    @Singleton
    @Named(CONFIGURATION_PATH)
    static Path provideConfigurationPath(Logger logger, Environment env) {
        String path = env.getEnv("CRYOSTAT_CONFIG_PATH", "/opt/cryostat.d/conf.d");
        logger.info(String.format("Local config path set as %s", path));
        return Paths.get(path);
    }

    @Provides
    @Singleton
    static CredentialsManager provideCredentialsManager(
            @Named(CONFIGURATION_PATH) Path confDir, FileSystem fs, Gson gson, Logger logger) {
        try {
            Path credentialsDir = confDir.resolve(CREDENTIALS_SUBDIRECTORY);
            if (!fs.isDirectory(credentialsDir)) {
                Files.createDirectory(
                        credentialsDir,
                        PosixFilePermissions.asFileAttribute(
                                Set.of(
                                        PosixFilePermission.OWNER_READ,
                                        PosixFilePermission.OWNER_WRITE,
                                        PosixFilePermission.OWNER_EXECUTE)));
            }
            return new CredentialsManager(credentialsDir, fs, gson, logger);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
