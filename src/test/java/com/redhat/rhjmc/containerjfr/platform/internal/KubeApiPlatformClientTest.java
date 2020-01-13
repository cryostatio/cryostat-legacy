package com.redhat.rhjmc.containerjfr.platform.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.documentation_messages.DocumentationMessageManager;
import com.redhat.rhjmc.containerjfr.net.NetworkResolver;
import com.redhat.rhjmc.containerjfr.platform.ServiceRef;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceList;
import io.kubernetes.client.models.V1ServicePort;
import io.kubernetes.client.models.V1ServiceSpec;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KubeApiPlatformClientTest {

    @Mock Logger logger;
    @Mock CoreV1Api api;
    @Mock DocumentationMessageManager dmm;
    String namespace = "someNamespace";
    @Mock NetworkResolver resolver;
    KubeApiPlatformClient client;

    @BeforeEach
    void setup() {
        client = new KubeApiPlatformClient(logger, api, namespace, resolver, dmm);
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
        void discoversAndResolvesServices() throws ApiException, UnknownHostException {
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

            assertThat(
                    result,
                    Matchers.contains(
                            new ServiceRef("ServiceA.local", 123),
                            new ServiceRef("ServiceA.local", 456),
                            new ServiceRef("b-service.example.com", 7899)));
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
        void ignoresUnresolveableServices() throws ApiException, UnknownHostException {
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

            assertThat(
                    result,
                    Matchers.contains(
                            new ServiceRef("ServiceA.local", 123),
                            new ServiceRef("ServiceA.local", 456)));
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
