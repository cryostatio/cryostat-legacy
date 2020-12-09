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
package com.redhat.rhjmc.containerjfr.rules;

import java.util.function.Consumer;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.utils.URLEncodedUtils;

import com.redhat.rhjmc.containerjfr.configuration.CredentialsManager;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.Credentials;
import com.redhat.rhjmc.containerjfr.core.net.discovery.JvmDiscoveryClient.EventKind;
import com.redhat.rhjmc.containerjfr.net.web.http.AbstractAuthenticatedRequestHandler;
import com.redhat.rhjmc.containerjfr.net.web.http.RequestHandler;
import com.redhat.rhjmc.containerjfr.platform.PlatformClient;
import com.redhat.rhjmc.containerjfr.platform.TargetDiscoveryEvent;
import com.redhat.rhjmc.containerjfr.util.HttpStatusCodeIdentifier;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;

public class RuleProcessor implements Consumer<TargetDiscoveryEvent> {

    private final PlatformClient platformClient;
    private final RuleRegistry registry;
    private final CredentialsManager credentialsManager;
    private final WebClient webClient;
    private final RequestHandler postHandler;
    private final Logger logger;

    RuleProcessor(
            PlatformClient platformClient,
            RuleRegistry registry,
            CredentialsManager credentialsManager,
            WebClient webClient,
            RequestHandler postHandler,
            Logger logger) {
        this.platformClient = platformClient;
        this.registry = registry;
        this.credentialsManager = credentialsManager;
        this.webClient = webClient;
        this.postHandler = postHandler;
        this.logger = logger;
    }

    public void enable() {
        this.platformClient.addTargetDiscoveryListener(this);
    }

    public void disable() {
        this.platformClient.removeTargetDiscoveryListener(this);
    }

    @Override
    public void accept(TargetDiscoveryEvent tde) {
        if (!EventKind.FOUND.equals(tde.getEventKind())) {
            return;
        }
        registry.getRules(tde.getServiceRef())
                .forEach(
                        rule -> {
                            if (tde.getServiceRef().getAlias().isPresent()
                                    && !tde.getServiceRef()
                                            .getAlias()
                                            .get()
                                            .equals(rule.targetAlias)) {
                                return;
                            }
                            this.logger.trace(
                                    String.format(
                                            "Activating rule %s for target %s",
                                            rule.name,
                                            rule.description,
                                            tde.getServiceRef().getJMXServiceUrl()));

                            // FIXME using an HTTP request to localhost here works well enough, but
                            // is
                            // needlessly complex. The API handler targeted here should be
                            // refactored to
                            // extract the logic that creates the recording from the logic that
                            // simply
                            // figures out the recording parameters from the POST form, path param,
                            // and
                            // headers. Then the handler should consume the API exposed by this
                            // refactored
                            // chunk, and this refactored chunk can also be consumed here rather
                            // than firing
                            // HTTP requests to ourselves
                            MultipartForm form = MultipartForm.create();
                            form.attribute(
                                    "recordingName", RuleRegistry.sanitizeRuleName(rule.name));
                            form.attribute("events", rule.eventSpecifier);
                            if (rule.duration > 0) {
                                form.attribute("duration", String.valueOf(rule.duration));
                            }
                            String path =
                                    postHandler
                                            .path()
                                            .replaceAll(
                                                    ":targetId",
                                                    URLEncodedUtils.formatSegments(
                                                            tde.getServiceRef()
                                                                    .getJMXServiceUrl()
                                                                    .toString()));
                            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
                            Credentials credentials =
                                    credentialsManager.getCredentials(
                                            tde.getServiceRef().getAlias().get());
                            if (credentials != null) {
                                headers.add(
                                        AbstractAuthenticatedRequestHandler
                                                .JMX_AUTHORIZATION_HEADER,
                                        String.format(
                                                "Basic %s",
                                                Base64.encodeBase64String(
                                                        String.format(
                                                                        "%s:%s",
                                                                        credentials.getUsername(),
                                                                        credentials.getPassword())
                                                                .getBytes())));
                            }
                            this.webClient
                                    .post(path)
                                    .timeout(30_000L)
                                    .putHeaders(headers)
                                    .sendMultipartForm(
                                            form,
                                            ar -> {
                                                if (ar.failed()) {
                                                    this.logger.error(
                                                            new RuntimeException(
                                                                    "Activation of automatic rule failed",
                                                                    ar.cause()));
                                                    return;
                                                }
                                                HttpResponse<Buffer> resp = ar.result();
                                                if (!HttpStatusCodeIdentifier.isSuccessCode(
                                                        resp.statusCode())) {
                                                    this.logger.error(resp.bodyAsString());
                                                }
                                            });
                        });
    }
}
