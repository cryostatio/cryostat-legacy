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

public final class HttpStatusCodeIdentifier {

    private HttpStatusCodeIdentifier() {}

    public static boolean isInformationCode(int code) {
        return 100 <= code && code < 200;
    }

    public static boolean isSuccessCode(int code) {
        return 200 <= code && code < 300;
    }

    public static boolean isRedirectCode(int code) {
        return 300 <= code && code < 400;
    }

    public static boolean isClientErrorCode(int code) {
        return 400 <= code && code < 500;
    }

    public static boolean isServerErrorCode(int code) {
        return 500 <= code && code < 600;
    }
}
