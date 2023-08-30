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
package io.cryostat.net.security;

import static io.cryostat.net.security.ResourceType.CERTIFICATE;
import static io.cryostat.net.security.ResourceType.CREDENTIALS;
import static io.cryostat.net.security.ResourceType.MATCH_EXPRESSION;
import static io.cryostat.net.security.ResourceType.RECORDING;
import static io.cryostat.net.security.ResourceType.REPORT;
import static io.cryostat.net.security.ResourceType.RULE;
import static io.cryostat.net.security.ResourceType.TARGET;
import static io.cryostat.net.security.ResourceType.TEMPLATE;
import static io.cryostat.net.security.ResourceVerb.CREATE;
import static io.cryostat.net.security.ResourceVerb.DELETE;
import static io.cryostat.net.security.ResourceVerb.READ;
import static io.cryostat.net.security.ResourceVerb.UPDATE;

import java.util.EnumSet;
import java.util.stream.Collectors;

/**
 * API action types(), at a high level, which are mapped to underlying platform permissions and
 * checked by AuthManagers.
 */
public enum ResourceAction {
    CREATE_TARGET(CREATE, TARGET),
    READ_TARGET(READ, TARGET),
    UPDATE_TARGET(UPDATE, TARGET),
    DELETE_TARGET(DELETE, TARGET),

    CREATE_RECORDING(CREATE, RECORDING),
    READ_RECORDING(READ, RECORDING),
    UPDATE_RECORDING(UPDATE, RECORDING),
    DELETE_RECORDING(DELETE, RECORDING),

    CREATE_TEMPLATE(CREATE, TEMPLATE),
    READ_TEMPLATE(READ, TEMPLATE),
    UPDATE_TEMPLATE(UPDATE, TEMPLATE),
    DELETE_TEMPLATE(DELETE, TEMPLATE),

    CREATE_PROBE_TEMPLATE(CREATE, TEMPLATE),
    DELETE_PROBE_TEMPLATE(DELETE, TEMPLATE),
    READ_PROBE_TEMPLATE(READ, TEMPLATE),

    CREATE_REPORT(CREATE, REPORT),
    READ_REPORT(READ, REPORT),
    UPDATE_REPORT(UPDATE, REPORT),
    DELETE_REPORT(DELETE, REPORT),

    CREATE_MATCH_EXPRESSION(CREATE, MATCH_EXPRESSION),
    READ_MATCH_EXPRESSION(READ, MATCH_EXPRESSION),
    UPDATE_MATCH_EXPRESSION(UPDATE, MATCH_EXPRESSION),
    DELETE_MATCH_EXPRESSION(DELETE, MATCH_EXPRESSION),

    CREATE_CREDENTIALS(CREATE, CREDENTIALS),
    READ_CREDENTIALS(READ, CREDENTIALS),
    UPDATE_CREDENTIALS(UPDATE, CREDENTIALS),
    DELETE_CREDENTIALS(DELETE, CREDENTIALS),

    CREATE_RULE(CREATE, RULE),
    READ_RULE(READ, RULE),
    UPDATE_RULE(UPDATE, RULE),
    DELETE_RULE(DELETE, RULE),

    CREATE_CERTIFICATE(CREATE, CERTIFICATE),
    READ_CERTIFICATE(READ, CERTIFICATE),
    UPDATE_CERTIFICATE(UPDATE, CERTIFICATE),
    DELETE_CERTIFICATE(DELETE, CERTIFICATE),
    ;

    public static final EnumSet<ResourceAction> ALL = EnumSet.allOf(ResourceAction.class);
    public static final EnumSet<ResourceAction> NONE = EnumSet.noneOf(ResourceAction.class);
    public static final EnumSet<ResourceAction> READ_ALL =
            EnumSet.copyOf(
                    ALL.stream()
                            .filter(p -> ResourceVerb.READ == p.verb)
                            .collect(Collectors.toSet()));
    public static final EnumSet<ResourceAction> WRITE_ALL = EnumSet.complementOf(READ_ALL);

    private final ResourceVerb verb;
    private final ResourceType resource;

    ResourceAction(ResourceVerb verb, ResourceType resource) {
        this.verb = verb;
        this.resource = resource;
    }

    public ResourceVerb getVerb() {
        return verb;
    }

    public ResourceType getResource() {
        return resource;
    }
}
