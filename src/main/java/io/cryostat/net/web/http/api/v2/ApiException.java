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
