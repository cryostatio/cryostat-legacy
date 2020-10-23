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
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import javax.management.remote.JMXServiceURL;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnectionToolkit;
import com.redhat.rhjmc.containerjfr.platform.ServiceRef;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1EndpointAddress;
import io.kubernetes.client.models.V1EndpointPort;
import io.kubernetes.client.models.V1EndpointSubset;
import io.kubernetes.client.models.V1Endpoints;
import io.kubernetes.client.models.V1EndpointsList;
import io.kubernetes.client.models.V1ObjectReference;

@ExtendWith(MockitoExtension.class)
class KubeApiPlatformClientTest {

    KubeApiPlatformClient client;
    @Mock CoreV1Api api;
    String namespace = "someNamespace";
    @Mock JFRConnectionToolkit connectionToolkit;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        client = new KubeApiPlatformClient(api, namespace, () -> connectionToolkit, logger);
    }

    @Test
    void discoversNoServicesIfApiThrows() throws ApiException {
        when(api.listNamespacedEndpoints(
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
                .listNamespacedEndpoints(
                        namespace, null, null, null, null, null, null, null, null, null);
        verifyNoMoreInteractions(api);
    }

    @Test
    void discoversServices() throws ApiException, UnknownHostException, MalformedURLException {
        V1EndpointsList mockServiceList = mock(V1EndpointsList.class);

        V1Endpoints mockServiceA = mock(V1Endpoints.class);
        V1EndpointAddress aAddr = mock(V1EndpointAddress.class);
        when(aAddr.getIp()).thenReturn("127.0.0.1");
        V1ObjectReference aRef = mock(V1ObjectReference.class);
        when(aRef.getName()).thenReturn("ServiceA.local");
        when(aAddr.getTargetRef()).thenReturn(aRef);
        V1EndpointSubset aSubset = mock(V1EndpointSubset.class);
        when(aSubset.getAddresses()).thenReturn(List.of(aAddr));
        when(mockServiceA.getSubsets()).thenReturn(List.of(aSubset));
        V1EndpointPort aPort1 = mock(V1EndpointPort.class);
        when(aPort1.getName()).thenReturn("jfr-jmx");
        when(aPort1.getPort()).thenReturn(123);
        V1EndpointPort aPort2 = mock(V1EndpointPort.class);
        when(aPort2.getName()).thenReturn("port-name");
        when(aPort2.getPort()).thenReturn(456);
        when(aSubset.getPorts()).thenReturn(List.of(aPort1, aPort2));

        V1Endpoints mockServiceB = mock(V1Endpoints.class);
        V1EndpointAddress bAddr = mock(V1EndpointAddress.class);
        when(bAddr.getIp()).thenReturn("10.0.0.1");
        V1ObjectReference bRef = mock(V1ObjectReference.class);
        when(bRef.getName()).thenReturn("b-service.example.com");
        when(bAddr.getTargetRef()).thenReturn(bRef);
        V1EndpointSubset bSubset = mock(V1EndpointSubset.class);
        when(bSubset.getAddresses()).thenReturn(List.of(bAddr));
        when(mockServiceB.getSubsets()).thenReturn(List.of(bSubset));
        V1EndpointPort bPort1 = mock(V1EndpointPort.class);
        when(bPort1.getName()).thenReturn("jfr-jmx");
        when(bPort1.getPort()).thenReturn(7899);
        when(bSubset.getPorts()).thenReturn(List.of(bPort1));

        when(mockServiceList.getItems()).thenReturn(Arrays.asList(mockServiceA, mockServiceB));
        when(api.listNamespacedEndpoints(
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

        when(connectionToolkit.createServiceURL(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public JMXServiceURL answer(InvocationOnMock args) throws Throwable {
                                String host = args.getArgument(0);
                                int port = args.getArgument(1);
                                return new JMXServiceURL(
                                        "rmi",
                                        "",
                                        0,
                                        "/jndi/rmi://" + host + ":" + port + "/jmxrmi");
                            }
                        });

        List<ServiceRef> result = client.listDiscoverableServices();

        ServiceRef serv1 = new ServiceRef(connectionToolkit, "127.0.0.1", 123, "ServiceA.local");
        ServiceRef serv2 =
                new ServiceRef(connectionToolkit, "10.0.0.1", 7899, "b-service.example.com");

        assertThat(result, Matchers.equalTo(List.of(serv1, serv2)));

        verify(api)
                .listNamespacedEndpoints(
                        namespace, null, null, null, null, null, null, null, null, null);
        verifyNoMoreInteractions(api);
    }
}
