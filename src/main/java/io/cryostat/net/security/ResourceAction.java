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
