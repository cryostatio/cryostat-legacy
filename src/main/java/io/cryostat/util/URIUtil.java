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
package io.cryostat.util;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.management.remote.JMXServiceURL;

import io.cryostat.core.net.JFRConnection;
import io.cryostat.net.AgentConnection;

public class URIUtil {
    private URIUtil() {}

    public static URI createAbsolute(String uri) throws URISyntaxException, RelativeURIException {
        URI u = new URI(uri);
        if (!u.isAbsolute()) {
            throw new RelativeURIException(u);
        }
        return u;
    }

    public static URI convert(JMXServiceURL serviceUrl) throws URISyntaxException {
        return new URI(serviceUrl.toString());
    }

    public static boolean isJmxUrl(URI uri) {
        return isJmxUrl(uri.toString());
    }

    public static boolean isJmxUrl(String uri) {
        try {
            new JMXServiceURL(uri);
            return true;
        } catch (MalformedURLException mue) {
            return false;
        }
    }

    public static boolean isRmiUrl(JMXServiceURL serviceUrl) {
        String rmiPart = "/jndi/rmi://";
        String pathPart = serviceUrl.getURLPath();
        return pathPart.startsWith(rmiPart);
    }

    public static URI getRmiTarget(JMXServiceURL serviceUrl) throws URISyntaxException {
        String pathPart = serviceUrl.getURLPath();
        if (!isRmiUrl(serviceUrl)) {
            throw new IllegalArgumentException(serviceUrl.getURLPath());
        }
        return new URI(pathPart.substring("/jndi/".length(), pathPart.length()));
    }

    public static URI getConnectionUri(JFRConnection connection) throws IOException {
        // TODO this is a hack, the JFRConnection interface should be refactored to expose a more
        // general connection URL / targetId method since the JMX implementation is now only one
        // possible implementation
        if (connection instanceof AgentConnection) {
            return ((AgentConnection) connection).getUri();
        }
        return URI.create(connection.getJMXURL().toString());
    }
}
