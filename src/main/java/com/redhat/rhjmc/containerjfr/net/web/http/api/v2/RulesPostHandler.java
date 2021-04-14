/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package com.redhat.rhjmc.containerjfr.net.web.http.api.v2;

import java.io.IOException;
import java.util.function.Function;

import javax.inject.Inject;

import com.google.gson.Gson;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.web.http.HttpMimeType;
import com.redhat.rhjmc.containerjfr.net.web.http.api.ApiVersion;
import com.redhat.rhjmc.containerjfr.rules.Rule;
import com.redhat.rhjmc.containerjfr.rules.RuleRegistry;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;

class RulesPostHandler extends AbstractV2RequestHandler<String> {

    static final String PATH = "rules";

    private final RuleRegistry ruleRegistry;
    private final Logger logger;

    @Inject
    RulesPostHandler(AuthManager auth, RuleRegistry ruleRegistry, Gson gson, Logger logger) {
        super(auth, gson);
        this.ruleRegistry = ruleRegistry;
        this.logger = logger;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public HttpMimeType mimeType() {
        return HttpMimeType.PLAINTEXT;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    @Override
    public IntermediateResponse<String> handle(RequestParameters params) throws ApiException {
        Rule rule;
        String rawMime = params.getHeaders().get(HttpHeaders.CONTENT_TYPE);
        if (rawMime == null) {
            throw new ApiException(415, "Bad content type: null");
        }
        String firstMime = rawMime.split(";")[0];
        HttpMimeType mime = HttpMimeType.fromString(firstMime);
        if (mime == null) {
            throw new ApiException(415, "Bad content type: " + rawMime);
        }
        switch (mime) {
            case MULTIPART_FORM:
            case URLENCODED_FORM:
                Rule.Builder builder =
                        new Rule.Builder()
                                .name(
                                        params.getFormAttributes()
                                                .get(Rule.Attribute.NAME.getSerialKey()))
                                .targetAlias(
                                        params.getFormAttributes()
                                                .get(Rule.Attribute.TARGET_ALIAS.getSerialKey()))
                                .description(
                                        params.getFormAttributes()
                                                .get(Rule.Attribute.DESCRIPTION.getSerialKey()))
                                .eventSpecifier(
                                        params.getFormAttributes()
                                                .get(
                                                        Rule.Attribute.EVENT_SPECIFIER
                                                                .getSerialKey()));

                builder = setOptionalInt(builder, Rule.Attribute.ARCHIVAL_PERIOD_SECONDS, params);
                builder = setOptionalInt(builder, Rule.Attribute.PRESERVED_ARCHIVES, params);
                builder = setOptionalInt(builder, Rule.Attribute.MAX_AGE_SECONDS, params);
                builder = setOptionalInt(builder, Rule.Attribute.MAX_SIZE_BYTES, params);

                try {
                    rule = builder.build();
                } catch (IllegalArgumentException iae) {
                    throw new ApiException(400, iae);
                }
                break;
            case JSON:
                rule = gson.fromJson(params.getBody(), Rule.class);
                break;
            default:
                throw new ApiException(415, "Bad content type: " + rawMime);
        }

        try {
            rule = this.ruleRegistry.addRule(rule);
        } catch (IOException e) {
            throw new ApiException(
                    500,
                    "IOException occurred while writing rule definition: " + e.getMessage(),
                    e);
        }

        return new IntermediateResponse<String>()
                .statusCode(201)
                .addHeader(HttpHeaders.LOCATION, String.format("%s/%s", path(), rule.getName()))
                .body(rule.getName());
    }

    private Rule.Builder setOptionalInt(
            Rule.Builder builder, Rule.Attribute key, RequestParameters params)
            throws IllegalArgumentException {
        MultiMap attrs = params.getFormAttributes();
        if (!attrs.contains(key.getSerialKey())) {
            return builder;
        }
        Function<Integer, Rule.Builder> fn;
        switch (key) {
            case ARCHIVAL_PERIOD_SECONDS:
                fn = builder::archivalPeriodSeconds;
                break;
            case PRESERVED_ARCHIVES:
                fn = builder::preservedArchives;
                break;
            case MAX_AGE_SECONDS:
                fn = builder::maxAgeSeconds;
                break;
            case MAX_SIZE_BYTES:
                fn = builder::maxSizeBytes;
                break;
            default:
                throw new IllegalArgumentException("Unknown key " + key);
        }
        int value;
        try {
            value = Integer.parseInt(attrs.get(key.getSerialKey()));
        } catch (NumberFormatException nfe) {
            throw new ApiException(
                    400,
                    String.format(
                            "\"%s\" is an invalid (non-integer) value for \"%s\"",
                            attrs.get(key.getSerialKey()), key),
                    nfe);
        }
        return fn.apply(value);
    }
}
