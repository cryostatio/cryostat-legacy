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
package io.cryostat.net.web;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;
import io.cryostat.net.HttpServer;
import io.cryostat.net.NetworkConfiguration;
import io.cryostat.net.web.http.HttpModule;
import io.cryostat.net.web.http.RequestHandler;

import com.google.gson.Gson;
import dagger.Module;
import dagger.Provides;

@Module(includes = {HttpModule.class})
public abstract class WebModule {
    public static final String WEBSERVER_TEMP_DIR_PATH = "WEBSERVER_TEMP_DIR_PATH";

    @Provides
    static WebServer provideWebServer(
            HttpServer httpServer,
            NetworkConfiguration netConf,
            Set<RequestHandler> requestHandlers,
            Gson gson,
            AuthManager authManager,
            Logger logger,
            @Named(MainModule.RECORDINGS_PATH) Path archivedRecordingsPath) {
        return new WebServer(
                httpServer,
                netConf,
                requestHandlers,
                gson,
                authManager,
                logger,
                archivedRecordingsPath);
    }

    @Provides
    @Singleton
    @Named(WEBSERVER_TEMP_DIR_PATH)
    static Path provideWebServerTempDirPath(FileSystem fs) {
        try {
            return fs.createTempDirectory("cryostat").toAbsolutePath();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }
}
