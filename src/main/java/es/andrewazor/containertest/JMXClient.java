package es.andrewazor.containertest;

import java.util.Arrays;

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
        System.out.println(String.format("JMXClient started. args: %s", Arrays.asList(args).toString()));
        RegistryFactory.setDefaultRegistryProvider(new RegistryProvider());

        JMXConnectionDescriptor cd = new JMXConnectionDescriptor(
                new JMXServiceURL(String.format("service:jmx:rmi:///jndi/rmi://%s/jmxrmi", args[0])),
                new InMemoryCredentials(null, null)
                );
        ServerDescriptor sd = new ServerDescriptor(
                null,
                "Container",
                null
                );
        RJMXConnection conn = new RJMXConnection(cd, sd, JMXClient::abort);
        if (!conn.connect()) {
            abort();
        }

        Thread t = new Thread(new JMXConnectionHandler(
                    Arrays.copyOfRange(args, 1, args.length),
                    new JMCConnection(new DefaultConnectionHandle(conn, "RJMX Connection", new IConnectionListener[0]))
                    )
                );
        t.run();
        t.join();
    }

    private static void abort() {
        System.err.println("Connection failed");
        System.exit(1);
    }
}
