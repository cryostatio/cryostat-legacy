package com.redhat.rhjmc.containerjfr.net;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    void smokeTestNoArgsConstructor() {
        assertDoesNotThrow(() -> new NetworkResolver());
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

    @Test
    void shouldUseSocketRawHostAddress() throws Exception {
        byte[] rawHostAddress = new byte[] {1, 2, 3, 4};
        when(socket.getLocalAddress()).thenReturn(address);
        when(address.getAddress()).thenReturn(rawHostAddress);

        MatcherAssert.assertThat(resolver.getRawHostAddress(), Matchers.equalTo(rawHostAddress));
    }

    @Test
    void shouldResolveAddress() throws Exception {
        MatcherAssert.assertThat(
                resolver.resolveAddress(new byte[] {127, 0, 0, 1}).getHostName(),
                Matchers.equalTo("localhost"));
    }

    @Test
    void shouldLookup() throws Exception {
        MatcherAssert.assertThat(
                resolver.lookup("localhost").getAddress(),
                Matchers.equalTo(new byte[] {127, 0, 0, 1}));
    }

    @Test
    void shouldUseInetAddressCanonicalHostName() {
        String canonicalHostName = "canonical-host-name";
        when(address.getCanonicalHostName()).thenReturn(canonicalHostName);

        MatcherAssert.assertThat(
                resolver.resolveCanonicalHostName(address), Matchers.equalTo(canonicalHostName));
    }

    @Test
    void testResolveCanonicalHostName() throws Exception {
        MatcherAssert.assertThat(
                resolver.resolveCanonicalHostName("127.0.0.1"), Matchers.equalTo("localhost"));
    }
}
