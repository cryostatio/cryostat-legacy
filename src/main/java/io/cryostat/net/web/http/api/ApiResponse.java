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

public class ApiResponse<T extends ApiData> {
    protected ApiMeta meta;
    protected T data;

    public ApiResponse(ApiMeta meta, T data) {
        this.meta = meta;
        this.data = data;
    }

    public ApiMeta getMeta() {
        return this.meta;
    }

    public ApiData getData() {
        return this.data;
    }
}
