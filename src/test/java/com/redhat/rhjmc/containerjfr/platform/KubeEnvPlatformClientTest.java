package com.redhat.rhjmc.containerjfr.platform;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.redhat.rhjmc.containerjfr.core.sys.Environment;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KubeEnvPlatformClientTest {

    @Mock Environment env;
    KubeEnvPlatformClient client;

    @BeforeEach
    void setup() {
        client = new KubeEnvPlatformClient(env);
    }

    @Nested
    class DiscoverableServicesTests {

        @Test
        void shouldDiscoverNoServicesIfEnvsEmpty() {
            when(env.getEnv()).thenReturn(Collections.emptyMap());
            MatcherAssert.assertThat(client.listDiscoverableServices(), Matchers.empty());
            verifyNoMoreInteractions(env);
        }

        @Test
        void shouldDiscoverNoServicesIfEnvsNotRelevant() {
            when(env.getEnv()).thenReturn(Collections.singletonMap("SOME_OTHER_ENV", "127.0.0.1"));
            MatcherAssert.assertThat(client.listDiscoverableServices(), Matchers.empty());
            verifyNoMoreInteractions(env);
        }

        @Test
        @Disabled("Tests fail due to inability to connect to faked network services, need to provide mock socket tester")
        void shouldDiscoverServicesByEnv() {
            when(env.getEnv()).thenReturn(Map.of(
                "FOO_PORT_1234_TCP_ADDR", "127.0.0.1",
                "BAR_PORT_9999_TCP_ADDR", "1.2.3.4",
                "BAZ_PORT_9876_UDP_ADDR", "5.6.7.8"
            ));
            List<ServiceRef> services = client.listDiscoverableServices();
            MatcherAssert.assertThat(services, Matchers.containsInAnyOrder(
                new ServiceRef("127.0.0.1", "foo", 1234),
                new ServiceRef("1.2.3.4", "bar", 9999)
            ));
            MatcherAssert.assertThat(services, Matchers.hasSize(2));
            verifyNoMoreInteractions(env);
        }

    }

}
