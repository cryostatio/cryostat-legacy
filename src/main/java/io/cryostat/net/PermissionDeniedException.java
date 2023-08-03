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
package io.cryostat.net;

public class PermissionDeniedException extends Exception {
    private final String namespace;
    private final String resource;
    private final String verb;

    public PermissionDeniedException(
            String namespace, String resource, String verb, String reason) {
        super(
                String.format(
                        "Requesting client in namespace \"%s\" cannot %s %s: %s",
                        namespace, verb, resource, reason));
        this.namespace = namespace;
        this.resource = resource;
        this.verb = verb;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getResourceType() {
        return resource;
    }

    public String getVerb() {
        return verb;
    }
}
