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

import java.net.URI;
import java.net.URISyntaxException;

import javax.management.remote.JMXServiceURL;

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
}
