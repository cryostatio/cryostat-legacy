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
package io.cryostat.platform.discovery;

public enum BaseNodeType implements NodeType {
    // represents the entire deployment scenario Cryostat finds itself in
    UNIVERSE("Universe"),
    // represents a division of the deployment scenario - the universe may consist of a
    // Kubernetes Realm and a JDP Realm, for example
    REALM("Realm"),
    // represents a plain target JVM, connectable over JMX
    JVM("JVM"),
    // represents a target JVM using the Cryostat Agent, *not* connectable over JMX. Agent instances
    // that do publish a JMX Service URL should publish themselves with the JVM NodeType.
    AGENT("CryostatAgent"),
    ;

    private final String kind;

    BaseNodeType(String kind) {
        this.kind = kind;
    }

    @Override
    public String getKind() {
        return kind;
    }

    @Override
    public String toString() {
        return getKind();
    }
}
