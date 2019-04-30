package es.andrewazor.containertest.tui.ws;

import java.util.concurrent.Semaphore;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import es.andrewazor.containertest.tui.ClientReader;
import es.andrewazor.containertest.tui.ClientWriter;

class MessagingServer {

    private final Server server;
    private final Semaphore semaphore = new Semaphore(0, true);
    private WsClientReaderWriter connection;

    MessagingServer(int listenPort) {
        this.server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(listenPort);
        server.addConnector(connector);

        ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        contextHandler.setContextPath("/");
        server.setHandler(contextHandler);
        contextHandler.addServlet(new ServletHolder(new MessagingServlet(this)), "/command");
    }

    // testing-only constructor
    MessagingServer(Server server) {
        this.server = server;
    }

    void start() throws Exception {
        server.start();
        server.dump(System.err);
    }

    private void closeConnection() {
        if (connection != null) {
            semaphore.drainPermits();
            connection.close();
        }
    }

    void setConnection(WsClientReaderWriter crw) {
        closeConnection();
        this.connection = crw;
        semaphore.release();
    }

    ClientReader getClientReader() {
        return new ClientReader() {
            @Override
            public void close() {
                closeConnection();
            }

            @Override
            public String readLine() {
                try {
                    semaphore.acquireUninterruptibly();
                    return connection.readLine();
                } finally {
                    semaphore.release();
                }
            }
        };
    }

    ClientWriter getClientWriter() {
        return new ClientWriter() {
            @Override
            public void print(String s) {
                try {
                    semaphore.acquireUninterruptibly();
                    connection.print(s);
                } finally {
                    semaphore.release();
                }
            }
        };
    }

}