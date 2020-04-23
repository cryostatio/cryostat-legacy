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
