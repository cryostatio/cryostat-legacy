package es.andrewazor.containertest.net;

public class JMCConnectionToolkit {
    public JMCConnection connect(String host) throws Exception {
        return connect(host, JMCConnection.DEFAULT_PORT);
    }

    public JMCConnection connect(String host, int port) throws Exception {
        return new JMCConnection(host, port);
    }
}