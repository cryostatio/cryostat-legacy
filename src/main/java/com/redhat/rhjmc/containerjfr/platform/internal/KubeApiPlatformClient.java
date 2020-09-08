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
package com.redhat.rhjmc.containerjfr.platform.internal;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnectionToolkit;
import com.redhat.rhjmc.containerjfr.net.NetworkResolver;
import com.redhat.rhjmc.containerjfr.platform.PlatformClient;
import com.redhat.rhjmc.containerjfr.platform.ServiceRef;

import dagger.Lazy;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Service;

class KubeApiPlatformClient implements PlatformClient {

    private final CoreV1Api api;
    private final String namespace;
    private final Lazy<JFRConnectionToolkit> connectionToolkit;
    private final NetworkResolver resolver;
    private final Logger logger;

    KubeApiPlatformClient(
            CoreV1Api api,
            String namespace,
            Lazy<JFRConnectionToolkit> connectionToolkit,
            NetworkResolver resolver,
            Logger logger) {
        this.api = api;
        this.namespace = namespace;
        this.connectionToolkit = connectionToolkit;
        this.resolver = resolver;
        this.logger = logger;
    }

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        try {
            return api
                    .listNamespacedService(
                            namespace, null, null, null, null, null, null, null, null, null)
                    .getItems().stream()
                    .map(V1Service::getSpec)
                    .peek(spec -> logger.trace("Service spec: " + spec.toString()))
                    .filter(s -> s.getPorts() != null)
                    .flatMap(
                            s ->
                                    s.getPorts().stream()
                                            .map(
                                                    p -> {
                                                        try {
                                                            return new ServiceRef(
                                                                    connectionToolkit.get(),
                                                                    s.getClusterIP(),
                                                                    p.getPort(),
                                                                    resolver
                                                                            .resolveCanonicalHostName(
                                                                                    s
                                                                                            .getClusterIP()));
                                                        } catch (Exception e) {
                                                            logger.warn(e);
                                                            return null;
                                                        }
                                                    }))
                    .parallel()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (ApiException e) {
            logger.warn(e.getMessage());
            logger.warn(e.getResponseBody());
            return Collections.emptyList();
        } catch (Exception e) {
            logger.warn(e);
            return Collections.emptyList();
        }
    }
}
