import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import java.util.NoSuchElementException;
import java.util.Scanner;

import org.openjdk.jmc.rjmx.internal.JMXConnectionDescriptor;
import org.openjdk.jmc.rjmx.internal.RJMXConnection;
import org.openjdk.jmc.rjmx.internal.ServerDescriptor;
import org.openjdk.jmc.rjmx.preferences.JMXRMIPreferences;
import org.openjdk.jmc.ui.common.security.InMemoryCredentials;
import org.openjdk.jmc.ui.common.security.SecurityManagerFactory;

class JMXClient {
    public static void main(String[] args) throws Exception {
        //TODO: implement non-interactive JMC SecurityManager
        System.out.println(String.format("SecurityManager: %s", SecurityManagerFactory.getSecurityManager()));

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

        Scanner scanner = new Scanner(System.in);
        String in;
        do {
            System.out.print("> ");
            try {
                in = scanner.nextLine().trim();
            } catch (NoSuchElementException e) {
                break;
            }
            System.out.println(String.format("\t\"%s\"", in));

            if (in.equals("list")) {
                System.out.println("MBeans:");
                System.out.println(conn.getMBeanNames());
            }
        } while (!in.toLowerCase().equals("exit") && !in.toLowerCase().equals("quit"));
        System.out.println("exit");
    }

    private static void abort() {
        System.err.println("Connection failed");
        System.exit(1);
    }
}
