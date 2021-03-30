/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 Cryostat
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
package io.cryostat.net;

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
