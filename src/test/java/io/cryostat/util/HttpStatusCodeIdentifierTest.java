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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpStatusCodeIdentifierTest {

    @ParameterizedTest
    @ValueSource(ints = {100, 150, 199})
    void shouldIdentifyInformationCodes(int code) {
        Assertions.assertTrue(HttpStatusCodeIdentifier.isInformationCode(code));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 99, 200})
    void shouldIdentifyNonInformationCodes(int code) {
        Assertions.assertFalse(HttpStatusCodeIdentifier.isInformationCode(code));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 250, 299})
    void shouldIdentifySuccessCodes(int code) {
        Assertions.assertTrue(HttpStatusCodeIdentifier.isSuccessCode(code));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 199, 300})
    void shouldIdentifyNonSuccessCodes(int code) {
        Assertions.assertFalse(HttpStatusCodeIdentifier.isSuccessCode(code));
    }

    @ParameterizedTest
    @ValueSource(ints = {300, 350, 399})
    void shouldIdentifyRedirectCodes(int code) {
        Assertions.assertTrue(HttpStatusCodeIdentifier.isRedirectCode(code));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 299, 400})
    void shouldIdentifyNonRedirectCodes(int code) {
        Assertions.assertFalse(HttpStatusCodeIdentifier.isRedirectCode(code));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 450, 499})
    void shouldIdentifyClientErrorCodes(int code) {
        Assertions.assertTrue(HttpStatusCodeIdentifier.isClientErrorCode(code));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 399, 500})
    void shouldIdentifyNonClientErrorCodes(int code) {
        Assertions.assertFalse(HttpStatusCodeIdentifier.isClientErrorCode(code));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 550, 599})
    void shouldIdentifyServerErrorCodes(int code) {
        Assertions.assertTrue(HttpStatusCodeIdentifier.isServerErrorCode(code));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 499, 600})
    void shouldIdentifyNonServerErrorCodes(int code) {
        Assertions.assertFalse(HttpStatusCodeIdentifier.isServerErrorCode(code));
    }
}
