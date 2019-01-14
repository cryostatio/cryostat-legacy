package es.andrewazor.dockertest;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.eclipse.core.runtime.RegistryFactory;

import org.openjdk.jmc.rjmx.IConnectionListener;
import org.openjdk.jmc.rjmx.internal.DefaultConnectionHandle;
import org.openjdk.jmc.rjmx.internal.JMXConnectionDescriptor;
import org.openjdk.jmc.rjmx.internal.RJMXConnection;
import org.openjdk.jmc.rjmx.internal.ServerDescriptor;
import org.openjdk.jmc.rjmx.preferences.JMXRMIPreferences;
import org.openjdk.jmc.rjmx.services.jfr.internal.FlightRecorderServiceFactory;
import org.openjdk.jmc.ui.common.security.InMemoryCredentials;
import org.openjdk.jmc.ui.common.security.SecurityManagerFactory;

class JMXClient {
    public static void main(String[] args) throws Exception {
        RegistryFactory.setDefaultRegistryProvider(new RegistryProvider());

        JMXConnectionDescriptor cd = new JMXConnectionDescriptor(
                new JMXServiceURL("service:jmx:rmi:///jndi/rmi://:9091/jmxrmi"),
                new InMemoryCredentials(null, null)
                );
        ServerDescriptor sd = new ServerDescriptor(
                null,
                "Docker",
                null
                );
        RJMXConnection conn = new RJMXConnection(cd, sd, JMXClient::abort);
        if (!conn.connect()) {
            abort();
        }

        Thread t = new Thread(new JMXConnectionHandler(
                    new FlightRecorderServiceFactory().getServiceInstance(
                        new DefaultConnectionHandle(conn, "RJMX Connection", new IConnectionListener[0])
                        )
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
