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
package io.cryostat.net.web.http;

import java.util.EnumSet;

public enum HttpMimeType {
    PLAINTEXT("text/plain"),
    HTML("text/html"),
    JSON("application/json"),
    JSON_RAW("application/json"),
    OCTET_STREAM("application/octet-stream"),
    JFC("application/jfc+xml"),
    XML("application/xml"),
    MULTIPART_FORM("multipart/form-data"),
    URLENCODED_FORM("application/x-www-form-urlencoded"),
    UNKNOWN(null);

    private final String mime;

    HttpMimeType(String mime) {
        this.mime = mime;
    }

    public String mime() {
        return mime;
    }

    public String type() {
        return mime().split("/")[0];
    }

    public String subType() {
        return mime().split("/")[1];
    }

    public static HttpMimeType fromString(String type) {
        for (HttpMimeType mime : EnumSet.complementOf(EnumSet.of(UNKNOWN))) {
            if (mime.mime().equals(type)) {
                return mime;
            }
        }
        return UNKNOWN;
    }
}
