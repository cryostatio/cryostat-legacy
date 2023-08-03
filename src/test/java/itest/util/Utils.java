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
package itest.util;

import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.ext.web.client.WebClient;

public class Utils {

    public static final int WEB_PORT;
    public static final String WEB_HOST;

    static {
        WEB_PORT = Integer.valueOf(System.getProperty("cryostatWebPort"));
        WEB_HOST = System.getProperty("cryostatWebHost", "localhost");
    }

    public static final HttpClientOptions HTTP_CLIENT_OPTIONS;

    static {
        HTTP_CLIENT_OPTIONS =
                new HttpClientOptions()
                        .setSsl(false)
                        .setTrustAll(true)
                        .setVerifyHost(false)
                        .setDefaultHost(WEB_HOST)
                        .setDefaultPort(WEB_PORT)
                        .setLogActivity(true);
    }

    private static final Vertx VERTX = Vertx.vertx();
    public static final HttpClient HTTP_CLIENT = VERTX.createHttpClient(HTTP_CLIENT_OPTIONS);
    private static final WebClient WEB_CLIENT_INSTANCE = WebClient.wrap(HTTP_CLIENT);

    public static WebClient getWebClient() {
        return WEB_CLIENT_INSTANCE;
    }

    public static FileSystem getFileSystem() {
        return VERTX.fileSystem();
    }
}
