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
package io.cryostat.net.web.http.api;

import java.util.Objects;

import io.cryostat.net.web.http.HttpMimeType;

public class ApiMeta {
    protected HttpMimeType type;
    protected String status;

    public ApiMeta(HttpMimeType type, String status) {
        this.type = Objects.requireNonNull(type);
        this.status = status == null ? "OK" : status;
    }

    public ApiMeta(HttpMimeType type) {
        this(type, null);
    }

    public HttpMimeType getMimeType() {
        return this.type;
    }

    public String getStatus() {
        return this.status;
    }
}
