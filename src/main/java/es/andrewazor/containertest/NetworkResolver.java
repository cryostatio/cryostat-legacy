package es.andrewazor.containertest;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.function.Function;

public class NetworkResolver {
    public String getHostName() throws SocketException, UnknownHostException {
        return getLocalAddressProperty(InetAddress::getHostName);
    }

    public String getHostAddress() throws SocketException, UnknownHostException {
        return getLocalAddressProperty(InetAddress::getHostAddress);
    }

    private <T> T getLocalAddressProperty(Function<InetAddress, T> fn) throws SocketException, UnknownHostException {
        try (DatagramSocket s = new DatagramSocket()) {
            s.connect(InetAddress.getByName("1.1.1.1"), 80);
            return fn.apply(s.getLocalAddress());
        }
    }
}