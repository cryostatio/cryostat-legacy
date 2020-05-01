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
package com.redhat.rhjmc.containerjfr.platform.openshift;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.platform.ServiceRef;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsList;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.openshift.client.OpenShiftClient;

@ExtendWith(MockitoExtension.class)
class OpenShiftPlatformClientTest {

    @Mock Logger logger;
    @Mock OpenShiftClient osClient;
    @Mock FileSystem fs;
    OpenShiftPlatformClient platformClient;

    @BeforeEach
    void setup() {
        this.platformClient = new OpenShiftPlatformClient(logger, osClient, fs);
    }

    @Test
    void shouldReturnEmptyListIfNoEndpointsFound() throws Exception {
        String namespace = "foo-namespace";
        setMockNamespace(namespace);

        MixedOperation mockNamespaceOperation = Mockito.mock(MixedOperation.class);
        Mockito.when(osClient.endpoints()).thenReturn(mockNamespaceOperation);

        ArgumentCaptor<String> namespaceCaptor = ArgumentCaptor.forClass(String.class);
        NonNamespaceOperation mockOperation = Mockito.mock(NonNamespaceOperation.class);
        Mockito.when(mockNamespaceOperation.inNamespace(namespaceCaptor.capture()))
                .thenReturn(mockOperation);

        EndpointsList mockListable = Mockito.mock(EndpointsList.class);
        Mockito.when(mockOperation.list()).thenReturn(mockListable);

        List<Endpoints> mockEndpoints = Collections.emptyList();
        Mockito.when(mockListable.getItems()).thenReturn(mockEndpoints);

        List<ServiceRef> result = platformClient.listDiscoverableServices();
        MatcherAssert.assertThat(namespaceCaptor.getValue(), Matchers.equalTo(namespace));
        MatcherAssert.assertThat(result, Matchers.equalTo(Collections.emptyList()));
    }

    @Test
    void shouldReturnListOfMatchingEndpointRefs() throws Exception {
        String namespace = "foo-namespace";
        setMockNamespace(namespace);

        MixedOperation mockNamespaceOperation = Mockito.mock(MixedOperation.class);
        Mockito.when(osClient.endpoints()).thenReturn(mockNamespaceOperation);

        ArgumentCaptor<String> namespaceCaptor = ArgumentCaptor.forClass(String.class);
        NonNamespaceOperation mockOperation = Mockito.mock(NonNamespaceOperation.class);
        Mockito.when(mockNamespaceOperation.inNamespace(namespaceCaptor.capture()))
                .thenReturn(mockOperation);

        EndpointsList mockListable = Mockito.mock(EndpointsList.class);
        Mockito.when(mockOperation.list()).thenReturn(mockListable);

        ObjectReference objRef1 = Mockito.mock(ObjectReference.class);
        // Mockito.when(objRef1.getName()).thenReturn("targetA");
        ObjectReference objRef2 = Mockito.mock(ObjectReference.class);
        Mockito.when(objRef2.getName()).thenReturn("targetB");
        ObjectReference objRef3 = Mockito.mock(ObjectReference.class);
        Mockito.when(objRef3.getName()).thenReturn("targetC");
        ObjectReference objRef4 = Mockito.mock(ObjectReference.class);
        Mockito.when(objRef4.getName()).thenReturn("targetD");

        EndpointAddress address1 = Mockito.mock(EndpointAddress.class);
        // Mockito.when(address1.getIp()).thenReturn("127.0.0.1");
        // Mockito.when(address1.getTargetRef()).thenReturn(objRef1);
        EndpointAddress address2 = Mockito.mock(EndpointAddress.class);
        Mockito.when(address2.getIp()).thenReturn("127.0.0.2");
        Mockito.when(address2.getTargetRef()).thenReturn(objRef2);
        EndpointAddress address3 = Mockito.mock(EndpointAddress.class);
        Mockito.when(address3.getIp()).thenReturn("127.0.0.3");
        Mockito.when(address3.getTargetRef()).thenReturn(objRef3);
        EndpointAddress address4 = Mockito.mock(EndpointAddress.class);
        Mockito.when(address4.getIp()).thenReturn("127.0.0.4");
        Mockito.when(address4.getTargetRef()).thenReturn(objRef4);

        EndpointPort port1 = Mockito.mock(EndpointPort.class);
        Mockito.when(port1.getPort()).thenReturn(80);
        Mockito.when(port1.getName()).thenReturn("tcp-80");
        EndpointPort port2 = Mockito.mock(EndpointPort.class);
        Mockito.when(port2.getPort()).thenReturn(9999);
        Mockito.when(port2.getName()).thenReturn("jfr-jmx");
        EndpointPort port3 = Mockito.mock(EndpointPort.class);
        Mockito.when(port3.getPort()).thenReturn(9091);
        Mockito.when(port3.getName()).thenReturn("tcp-9091");

        EndpointSubset subset1 = Mockito.mock(EndpointSubset.class);
        // Mockito.when(subset1.getAddresses()).thenReturn(Collections.singletonList(address1));
        Mockito.when(subset1.getPorts()).thenReturn(Collections.singletonList(port1));
        EndpointSubset subset2 = Mockito.mock(EndpointSubset.class);
        Mockito.when(subset2.getAddresses()).thenReturn(Arrays.asList(address2, address3));
        Mockito.when(subset2.getPorts()).thenReturn(Collections.singletonList(port2));
        EndpointSubset subset3 = Mockito.mock(EndpointSubset.class);
        Mockito.when(subset3.getAddresses()).thenReturn(Collections.singletonList(address4));
        Mockito.when(subset3.getPorts()).thenReturn(Collections.singletonList(port3));

        Endpoints endpoint = Mockito.mock(Endpoints.class);
        Mockito.when(endpoint.getSubsets()).thenReturn(Arrays.asList(subset1, subset2, subset3));

        Mockito.when(mockListable.getItems()).thenReturn(Collections.singletonList(endpoint));

        List<ServiceRef> result = platformClient.listDiscoverableServices();
        MatcherAssert.assertThat(namespaceCaptor.getValue(), Matchers.equalTo(namespace));
        MatcherAssert.assertThat(
                result,
                Matchers.equalTo(
                        Arrays.asList(
                                new ServiceRef(
                                        address2.getIp(),
                                        address2.getTargetRef().getName(),
                                        port2.getPort()),
                                new ServiceRef(
                                        address3.getIp(),
                                        address3.getTargetRef().getName(),
                                        port2.getPort()),
                                new ServiceRef(
                                        address4.getIp(),
                                        address4.getTargetRef().getName(),
                                        port3.getPort()))));
    }

    private void setMockNamespace(String namespace) throws IOException {
        BufferedReader mockReader = Mockito.mock(BufferedReader.class);
        Mockito.when(fs.readFile(Mockito.any(Path.class))).thenReturn(mockReader);
        Mockito.when(mockReader.lines()).thenReturn(Collections.singletonList(namespace).stream());
    }
}
