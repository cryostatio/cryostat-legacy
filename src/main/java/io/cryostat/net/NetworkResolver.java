/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.net;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.function.Function;

public class NetworkResolver {

    private final CheckedSupplier<DatagramSocket, SocketException> socketSupplier;

    NetworkResolver() {
        this(DatagramSocket::new);
    }

    // Testing-only constructor
    NetworkResolver(CheckedSupplier<DatagramSocket, SocketException> socketSupplier) {
        this.socketSupplier = socketSupplier;
    }

    public String getHostName() throws SocketException, UnknownHostException {
        return getLocalAddressProperty(InetAddress::getHostName);
    }

    public String getHostAddress() throws SocketException, UnknownHostException {
        return getLocalAddressProperty(InetAddress::getHostAddress);
    }

    public byte[] getRawHostAddress() throws SocketException, UnknownHostException {
        return getLocalAddressProperty(InetAddress::getAddress);
    }

    public InetAddress resolveAddress(byte[] addr) throws UnknownHostException {
        return InetAddress.getByAddress(addr);
    }

    public InetAddress lookup(String host) throws UnknownHostException {
        return InetAddress.getByName(host);
    }

    public String resolveCanonicalHostName(InetAddress addr) {
        return addr.getCanonicalHostName();
    }

    public String resolveCanonicalHostName(String host) throws UnknownHostException {
        return lookup(host).getCanonicalHostName();
    }

    public boolean testConnection(InetAddress host, int port, int timeout) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeout);
            return true;
        } catch (SocketTimeoutException e) {
            return false;
        }
    }

    private <T> T getLocalAddressProperty(Function<InetAddress, T> fn)
            throws SocketException, UnknownHostException {
        try (DatagramSocket s = socketSupplier.get()) {
            s.connect(lookup("1.1.1.1"), 80);
            return fn.apply(s.getLocalAddress());
        }
    }

    interface CheckedSupplier<T, E extends Throwable> {
        T get() throws E;
    }
}
