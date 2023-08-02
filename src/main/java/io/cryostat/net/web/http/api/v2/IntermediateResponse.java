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
package io.cryostat.net.web.http.api.v2;

import java.util.HashMap;
import java.util.Map;

public class IntermediateResponse<T> {

    private final Map<CharSequence, CharSequence> headers = new HashMap<>();
    private int statusCode = 200;
    private String statusMessage;
    private T body;

    public IntermediateResponse<T> addHeader(CharSequence key, CharSequence value) {
        this.headers.put(key, value);
        return this;
    }

    public IntermediateResponse<T> removeHeader(CharSequence key) {
        this.headers.remove(key);
        return this;
    }

    public IntermediateResponse<T> statusCode(int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public IntermediateResponse<T> statusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
        return this;
    }

    public IntermediateResponse<T> body(T body) {
        this.body = body;
        return this;
    }

    public int getStatusCode() {
        return this.statusCode;
    }

    public Map<CharSequence, CharSequence> getHeaders() {
        return new HashMap<CharSequence, CharSequence>(this.headers);
    }

    public String getStatusMessage() {
        return this.statusMessage;
    }

    public T getBody() {
        return this.body;
    }
}
