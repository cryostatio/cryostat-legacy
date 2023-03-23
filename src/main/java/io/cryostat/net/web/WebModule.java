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
package io.cryostat.net.web;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutorService;

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
import io.vertx.core.Vertx;

@Module(includes = {HttpModule.class})
public abstract class WebModule {
    public static final String WEBSERVER_TEMP_DIR_PATH = "WEBSERVER_TEMP_DIR_PATH";
    public static final String VERTX_EXECUTOR = "VERTX_EXECUTOR";

    @Provides
    @Singleton
    static WebServer provideWebServer(
            HttpServer httpServer,
            NetworkConfiguration netConf,
            Set<RequestHandler<?>> requestHandlers,
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

    @Provides
    @Singleton
    @Named(VERTX_EXECUTOR)
    static ExecutorService provideVertexecutor(Vertx vertx, Logger logger) {
        return new Vertexecutor(vertx, logger);
    }
}
