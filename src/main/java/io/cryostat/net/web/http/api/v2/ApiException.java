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

import io.netty.handler.codec.http.HttpResponseStatus;

public class ApiException extends RuntimeException {

    protected final int statusCode;
    protected final String apiStatus;
    protected final String reason;

    public ApiException() {
        this(500);
    }

    public ApiException(int statusCode, String apiStatus, String reason, Throwable cause) {
        super(reason, cause);
        this.statusCode = statusCode;
        this.apiStatus =
                apiStatus != null
                        ? apiStatus
                        : HttpResponseStatus.valueOf(statusCode).reasonPhrase();
        this.reason = reason;
    }

    public ApiException(int statusCode, String apiStatus, String reason) {
        this(statusCode, apiStatus, reason, null);
    }

    public ApiException(int statusCode, String reason, Throwable cause) {
        this(statusCode, null, reason, cause);
    }

    public ApiException(int statusCode, String reason) {
        this(statusCode, null, reason, null);
    }

    public ApiException(int statusCode, Throwable cause) {
        this(statusCode, null, cause.getMessage(), cause);
    }

    public ApiException(int statusCode) {
        this(statusCode, (String) null);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getApiStatus() {
        return apiStatus;
    }

    public String getFailureReason() {
        return reason;
    }
}
