package es.andrewazor.containertest;

import java.util.Arrays;
import java.util.logging.LogManager;

import javax.management.remote.JMXServiceURL;

import org.eclipse.core.runtime.RegistryFactory;

import org.openjdk.jmc.rjmx.IConnectionListener;
import org.openjdk.jmc.rjmx.internal.DefaultConnectionHandle;
import org.openjdk.jmc.rjmx.internal.JMXConnectionDescriptor;
import org.openjdk.jmc.rjmx.internal.RJMXConnection;
import org.openjdk.jmc.rjmx.internal.ServerDescriptor;
import org.openjdk.jmc.ui.common.security.InMemoryCredentials;

import es.andrewazor.containertest.jmc.RegistryProvider;

class JMXClient {
    public static void main(String[] args) throws Exception {
        LogManager.getLogManager().reset();

        System.out.println(String.format("JMXClient started. args: %s", Arrays.asList(args).toString()));
        RegistryFactory.setDefaultRegistryProvider(new RegistryProvider());

        RJMXConnection conn = attemptConnect(args[0], 10);

        Thread t = new Thread(new JMXConnectionHandler(
                    Arrays.copyOfRange(args, 1, args.length),
                    new JMCConnection(new DefaultConnectionHandle(conn, "RJMX Connection", new IConnectionListener[0]))
                    )
                );
        t.run();
        t.join();
    }

    private static RJMXConnection attemptConnect(String host, int maxRetry) throws Exception {
        JMXConnectionDescriptor cd = new JMXConnectionDescriptor(
                new JMXServiceURL(String.format("service:jmx:rmi:///jndi/rmi://%s/jmxrmi", host)),
                new InMemoryCredentials(null, null)
                );
        ServerDescriptor sd = new ServerDescriptor(
                null,
                "Container",
                null
                );

        int attempts = 0;
        while (true) {
            try {
                RJMXConnection conn = new RJMXConnection(cd, sd, () -> {});
                if (!conn.connect()) {
                    throw new RuntimeException("Connection Failed");
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
                } catch (InterruptedException ignored) { }
            }
        }
    }
}
