package es.andrewazor.containertest;

import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.net.DatagramSocket;
import java.net.InetAddress;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NetworkResolverTest {

    NetworkResolver resolver;
    @Mock DatagramSocket socket;
    @Mock InetAddress address;

    @BeforeEach
    void setup() {
        resolver = new NetworkResolver(() -> socket);
    }

    @Test
    void shouldUseSocketHostName() throws Exception {
        String hostname = "foo-host";
        when(socket.getLocalAddress()).thenReturn(address);
        when(address.getHostName()).thenReturn(hostname);

        MatcherAssert.assertThat(resolver.getHostName(), Matchers.equalTo(hostname));
    }

    @Test
    void shouldUseSocketHostAddress() throws Exception {
        String hostAddress = "a1b2c3d4";
        when(socket.getLocalAddress()).thenReturn(address);
        when(address.getHostAddress()).thenReturn(hostAddress);

        MatcherAssert.assertThat(resolver.getHostAddress(), Matchers.equalTo(hostAddress));
    }

}