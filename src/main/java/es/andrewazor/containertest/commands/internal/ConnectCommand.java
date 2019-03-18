package es.andrewazor.containertest.commands.internal;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.management.remote.JMXServiceURL;

import org.openjdk.jmc.rjmx.IConnectionListener;
import org.openjdk.jmc.rjmx.internal.DefaultConnectionHandle;
import org.openjdk.jmc.rjmx.internal.JMXConnectionDescriptor;
import org.openjdk.jmc.rjmx.internal.RJMXConnection;
import org.openjdk.jmc.rjmx.internal.ServerDescriptor;
import org.openjdk.jmc.ui.common.security.InMemoryCredentials;

import es.andrewazor.containertest.ConnectionListener;
import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.commands.Command;

@Singleton 
class ConnectCommand implements Command {

    private static final String URL_FORMAT = "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi";
    private static final Pattern HOST_PATTERN = Pattern.compile("^([^:\\s]+)(?::(\\d{1,5}))?$");

    private final Set<ConnectionListener> connectionListeners;

    @Inject ConnectCommand(Set<ConnectionListener> connectionListeners) {
        this.connectionListeners = connectionListeners;
    }

    @Override
    public String getName() {
        return "connect";
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 1) {
            return false;
        }
        return HOST_PATTERN.matcher(args[0]).matches();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void execute(String[] args) throws Exception {
        Matcher m = HOST_PATTERN.matcher(args[0]);
        m.find();
        String host = m.group(1);
        String port = m.group(2);
        if (port == null) {
            port = "9091";
        }
        RJMXConnection rjmxConnection = attemptConnect(host, Integer.parseInt(port));
        JMCConnection connection = new JMCConnection(rjmxConnection,
                new DefaultConnectionHandle(rjmxConnection, "RJMX Connection", new IConnectionListener[0]));
        connectionListeners.forEach(listener -> listener.connectionChanged(connection));
    }

    private static RJMXConnection attemptConnect(String host, int port) throws Exception {
        return attemptConnect(host, port, 0);
    }

    private static RJMXConnection attemptConnect(String host, int port, int maxRetry) throws Exception {
        JMXConnectionDescriptor cd = new JMXConnectionDescriptor(
                new JMXServiceURL(String.format(URL_FORMAT, host, port)),
                new InMemoryCredentials(null, null));
        ServerDescriptor sd = new ServerDescriptor(null, "Container", null);

        int attempts = 0;
        while (true) {
            try {
                RJMXConnection conn = new RJMXConnection(cd, sd, ConnectCommand::failConnection);
                if (!conn.connect()) {
                    failConnection();
                }
                return conn;
            } catch (Exception e) {
                attempts++;
                System.out.println(String.format("Connection attempt #%s failed", attempts));
                if (attempts >= maxRetry) {
                    System.out.println("Aborting...");
                    throw e;
                } else {
                    e.printStackTrace();
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private static void failConnection() {
        throw new RuntimeException("Connection Failed");
    }

}