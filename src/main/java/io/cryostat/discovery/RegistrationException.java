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
package io.cryostat.discovery;

import java.net.URI;

public class RegistrationException extends Exception {

    public RegistrationException(String realm, URI callback, Exception cause, String msg) {
        super(
                String.format(
                        "Failed to register new provider for \"%s\" @ \"%s\": %s",
                        realm, callback, msg),
                cause);
    }

    public RegistrationException(Exception cause) {
        super(cause);
    }
}
