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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.net.NetworkResolver;
import com.redhat.rhjmc.containerjfr.platform.ServiceRef;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceList;
import io.kubernetes.client.models.V1ServicePort;
import io.kubernetes.client.models.V1ServiceSpec;

@ExtendWith(MockitoExtension.class)
class KubeApiPlatformClientTest {

    @Mock Logger logger;
    @Mock CoreV1Api api;
    String namespace = "someNamespace";
    @Mock NetworkResolver resolver;
    KubeApiPlatformClient client;

    @BeforeEach
    void setup() {
        client = new KubeApiPlatformClient(api, namespace, resolver, logger);
    }

    @Nested
    class DiscoverableServicesTests {

        @Test
        void discoversNoServicesIfApiThrows() throws ApiException {
            when(api.listNamespacedService(
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any()))
                    .thenThrow(ApiException.class);

            assertThat(client.listDiscoverableServices(), Matchers.empty());

            verify(api)
                    .listNamespacedService(
                            namespace, null, null, null, null, null, null, null, null, null);
            verifyNoMoreInteractions(api);
            verifyZeroInteractions(resolver);
        }

        @Test
        void discoversAndResolvesServices()
                throws ApiException, UnknownHostException, MalformedURLException {
            V1ServiceList mockServiceList = mock(V1ServiceList.class);

            V1Service mockServiceA = mock(V1Service.class);
            V1ServiceSpec aSpec = mock(V1ServiceSpec.class);
            when(aSpec.getClusterIP()).thenReturn("127.0.0.1");
            when(mockServiceA.getSpec()).thenReturn(aSpec);
            V1ServicePort aPort1 = mock(V1ServicePort.class);
            when(aPort1.getPort()).thenReturn(123);
            V1ServicePort aPort2 = mock(V1ServicePort.class);
            when(aPort2.getPort()).thenReturn(456);
            when(aSpec.getPorts()).thenReturn(Arrays.asList(aPort1, aPort2));

            V1Service mockServiceB = mock(V1Service.class);
            V1ServiceSpec bSpec = mock(V1ServiceSpec.class);
            when(bSpec.getClusterIP()).thenReturn("10.0.0.1");
            when(mockServiceB.getSpec()).thenReturn(bSpec);
            V1ServicePort bPort = mock(V1ServicePort.class);
            when(bPort.getPort()).thenReturn(7899);
            when(bSpec.getPorts()).thenReturn(Arrays.asList(bPort));

            when(mockServiceList.getItems()).thenReturn(Arrays.asList(mockServiceA, mockServiceB));
            when(api.listNamespacedService(
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any()))
                    .thenReturn(mockServiceList);
            when(resolver.resolveCanonicalHostName("127.0.0.1")).thenReturn("ServiceA.local");
            when(resolver.resolveCanonicalHostName("10.0.0.1")).thenReturn("b-service.example.com");

            List<ServiceRef> result = client.listDiscoverableServices();

            ServiceRef serv1 = new ServiceRef("127.0.0.1", 123, "ServiceA.local");
            ServiceRef serv2 = new ServiceRef("127.0.0.1", 456, "ServiceA.local");
            ServiceRef serv3 = new ServiceRef("10.0.0.1", 7899, "b-service.example.com");

            assertThat(result, Matchers.contains(serv1, serv2, serv3));

            assertThat(result, Matchers.hasSize(3));

            verify(resolver, Mockito.times(2)).resolveCanonicalHostName("127.0.0.1");
            verify(resolver).resolveCanonicalHostName("10.0.0.1");
            verifyNoMoreInteractions(resolver);
            verify(api)
                    .listNamespacedService(
                            namespace, null, null, null, null, null, null, null, null, null);
            verifyNoMoreInteractions(api);
        }

        @Test
        void ignoresUnresolveableServices()
                throws ApiException, UnknownHostException, MalformedURLException {
            V1ServiceList mockServiceList = mock(V1ServiceList.class);

            V1Service mockServiceA = mock(V1Service.class);
            V1ServiceSpec aSpec = mock(V1ServiceSpec.class);
            when(aSpec.getClusterIP()).thenReturn("127.0.0.1");
            when(mockServiceA.getSpec()).thenReturn(aSpec);
            V1ServicePort aPort1 = mock(V1ServicePort.class);
            when(aPort1.getPort()).thenReturn(123);
            V1ServicePort aPort2 = mock(V1ServicePort.class);
            when(aPort2.getPort()).thenReturn(456);
            when(aSpec.getPorts()).thenReturn(Arrays.asList(aPort1, aPort2));

            V1Service mockServiceB = mock(V1Service.class);
            V1ServiceSpec bSpec = mock(V1ServiceSpec.class);
            when(bSpec.getClusterIP()).thenReturn("10.0.0.1");
            when(mockServiceB.getSpec()).thenReturn(bSpec);
            V1ServicePort bPort = mock(V1ServicePort.class);
            when(bPort.getPort()).thenReturn(7899);
            when(bSpec.getPorts()).thenReturn(Arrays.asList(bPort));

            when(mockServiceList.getItems()).thenReturn(Arrays.asList(mockServiceA, mockServiceB));
            when(api.listNamespacedService(
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any()))
                    .thenReturn(mockServiceList);
            when(resolver.resolveCanonicalHostName("127.0.0.1")).thenReturn("ServiceA.local");
            when(resolver.resolveCanonicalHostName("10.0.0.1"))
                    .thenThrow(UnknownHostException.class);

            List<ServiceRef> result = client.listDiscoverableServices();

            ServiceRef serv1 = new ServiceRef("127.0.0.1", 123, "ServiceA.local");
            ServiceRef serv2 = new ServiceRef("127.0.0.1", 456, "ServiceA.local");

            assertThat(result, Matchers.contains(serv1, serv2));
            assertThat(result, Matchers.hasSize(2));

            verify(resolver, Mockito.times(2)).resolveCanonicalHostName("127.0.0.1");
            verify(resolver).resolveCanonicalHostName("10.0.0.1");
            verifyNoMoreInteractions(resolver);
            verify(api)
                    .listNamespacedService(
                            namespace, null, null, null, null, null, null, null, null, null);
            verifyNoMoreInteractions(api);
        }
    }
}
